package com.trenical.services;

import com.trenical.rubyViaggiatreno.RubyViaggiatrenoClient;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import proto.*;
import ruby_viaggiatreno_microservizio.TrainStatusResponse;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class TrainServiceImpl extends TreniCalGrpc.TreniCalImplBase {

    private static final Logger logger = Logger.getLogger(TrainServiceImpl.class.getName());
    private final RubyViaggiatrenoClient rubyClient;
    private static final String DB_PATH = "jdbc:sqlite:../../trenical.db";
    private final TrainSearchStrategy searchStrategy;


    public TrainServiceImpl(RubyViaggiatrenoClient rubyClient) {
        this.rubyClient = rubyClient;
        logger.info("TrainServiceImpl initialized with Ruby client - ONLY REAL GTFS DATA");
        this.searchStrategy = new GtfsTrainSearchStrategy();
    }

    @Override
    public void searchStations(SearchStationRequest request, StreamObserver<StationListResponse> responseObserver) {
        String query = request.getSearchQuery();
        logger.info("Inoltro richiesta searchStations a Ruby. Query: '" + query + "'");

        try {
            ruby_viaggiatreno_microservizio.StationListResponse rubyResponse = rubyClient.searchStations(query);
            StationListResponse.Builder javaResponseBuilder = StationListResponse.newBuilder();

            for (ruby_viaggiatreno_microservizio.Station rubyStation : rubyResponse.getStationsList()) {
                Station javaStation = Station.newBuilder()
                        .setId(rubyStation.getId())
                        .setName(rubyStation.getName())
                        .build();
                javaResponseBuilder.addStations(javaStation);
            }

            logger.info("Ritornate " + rubyResponse.getStationsCount() + " stazioni per query: " + query);
            responseObserver.onNext(javaResponseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.severe("Errore ricerca stazioni: " + e.getMessage());
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Errore ricerca stazioni: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getTrainRealTimeInfo(TrainInfoRequest request, StreamObserver<TrainRealTimeUpdate> responseObserver) {
        String trainId = request.getTrainId();
        logger.info("Ricevuta richiesta GetTrainRealTimeInfo per il treno ID: " + trainId);

        try {

            String actualTrainNumber = getActualTrainNumber(trainId);

            if (actualTrainNumber == null || actualTrainNumber.equals("N/A")) {
                logger.warning("Impossibile trovare numero treno valido per ID: " + trainId);
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Impossibile trovare numero treno valido per ID: " + trainId)
                        .asRuntimeException());
                return;
            }

            logger.info("Requesting real-time info for train number: " + actualTrainNumber);
            TrainStatusResponse rubyResponse = rubyClient.getTrainStatus(actualTrainNumber);

            if (rubyResponse.getFound()) {
                String statusUpdateMessage = String.format("Treno %s: %s. Ritardo: %d min. Ultima rilevazione: %s",
                        actualTrainNumber,
                        rubyResponse.getTrainStatusDescription(),
                        rubyResponse.getDelayMinutes(),
                        rubyResponse.getLastDetectedStation());

                logger.info("Status retrieved successfully for train " + actualTrainNumber);

                TrainRealTimeUpdate update = TrainRealTimeUpdate.newBuilder()
                        .setTrainId(trainId)
                        .setStatusUpdate(statusUpdateMessage)
                        .build();

                responseObserver.onNext(update);
                responseObserver.onCompleted();
            } else {
                String errorMsg = "Treno " + actualTrainNumber + " non trovato o errore: " + rubyResponse.getErrorMessage();
                logger.warning(errorMsg);
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription(errorMsg)
                        .asRuntimeException());
            }
        } catch (Exception e) {
            logger.severe("Errore comunicazione servizio real-time: " + e.getMessage());
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Errore comunicazione servizio real-time: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void searchTrains(SearchTrainRequest request, StreamObserver<SearchTrainResponse> responseObserver) {
        logger.info("Ricevuta richiesta searchTrains per: " +
                request.getDepartureStation().getName() + " -> " + request.getArrivalStation().getName());

        try {
            List<Train> foundTrains = searchStrategy.searchTrains(request);
            SearchTrainResponse response = SearchTrainResponse.newBuilder()
                    .addAllAvailableTrains(foundTrains)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.severe("Errore durante searchTrains: " + e.getMessage());
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Errore nella ricerca dei treni")
                    .augmentDescription(e.getMessage())
                    .asRuntimeException());
        }
    }



    private String extractCleanTrainNumber(String tripShortName) {
        if (tripShortName == null || tripShortName.trim().isEmpty()) {
            return null;
        }

        String trimmed = tripShortName.trim();
        logger.fine("Extracting train number from: '" + trimmed + "'");


        String[] parts = trimmed.split("\\s+");
        for (String part : parts) {

            if (part.matches("\\d{3,5}")) {
                logger.fine("Extracted train number: " + part + " from: " + trimmed);
                return part;
            }
        }


        String numbers = trimmed.replaceAll("[^0-9]", "");
        if (numbers.length() >= 3 && numbers.length() <= 5) {
            logger.fine("Extracted train number from cleanup: " + numbers + " from: " + trimmed);
            return numbers;
        }


        if (trimmed.matches("[A-Z]{1,3}\\d{3,5}")) {
            String extracted = trimmed.replaceAll("^[A-Z]+", "");
            if (extracted.length() >= 3 && extracted.length() <= 5) {
                logger.fine("Extracted from pattern: " + extracted + " from: " + trimmed);
                return extracted;
            }
        }

        logger.warning("Could not extract valid train number from: '" + trimmed + "'");
        return null;
    }

    private String getActualTrainNumber(String tripId) throws SQLException {
        String sql = "SELECT trip_short_name, route_id FROM trips WHERE trip_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tripId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String shortName = rs.getString("trip_short_name");
                String routeId = rs.getString("route_id");

                logger.info("Found trip: ID=" + tripId + ", short_name=" + shortName + ", route=" + routeId);

                if (shortName != null && !shortName.trim().isEmpty()) {
                    String extracted = extractCleanTrainNumber(shortName);

                    if (extracted != null) {
                        logger.info("Successfully extracted train number: " + extracted + " from trip: " + tripId);
                        return extracted;
                    } else {
                        logger.warning("Could not extract valid train number from trip_short_name: " + shortName);


                        return shortName.replaceAll("[^0-9]", "");
                    }
                } else {
                    logger.warning("trip_short_name is null or empty for trip_id: " + tripId);
                }
            } else {
                logger.warning("No trip found with ID: " + tripId);
            }
        } catch (SQLException e) {
            logger.severe("Database error getting train number for trip " + tripId + ": " + e.getMessage());
            throw e;
        }

        return null;
    }


}