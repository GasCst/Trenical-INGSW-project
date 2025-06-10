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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


public class TrainServiceImpl extends TreniCalGrpc.TreniCalImplBase {


    private final RubyViaggiatrenoClient rubyClient;
    private static final String DB_PATH = "jdbc:sqlite:trenical.db";

    public TrainServiceImpl(RubyViaggiatrenoClient rubyClient) {
        this.rubyClient = rubyClient;
    }

    @Override
    public void searchStations(SearchStationRequest request, StreamObserver<StationListResponse> responseObserver) {
        String query = request.getSearchQuery();
        System.out.println("[Server Java] Inoltro richiesta searchStations a Ruby. Query: '" + query + "'");
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
            responseObserver.onNext(javaResponseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription("Errore ricerca stazioni: " + e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getTrainRealTimeInfo(TrainInfoRequest request, StreamObserver<TrainRealTimeUpdate> responseObserver) {
        String trainNumber = request.getTrainId();
        System.out.println("[Server] Ricevuta richiesta GetTrainRealTimeInfo per il treno: " + trainNumber);

        try {
            TrainStatusResponse rubyResponse = rubyClient.getTrainStatus(trainNumber);

            if (rubyResponse.getFound()) {
                String statusUpdateMessage = String.format("%s. Ritardo: %d min. Ultima rilevazione: %s",
                        rubyResponse.getTrainStatusDescription(),
                        rubyResponse.getDelayMinutes(),
                        rubyResponse.getLastDetectedStation());

                TrainRealTimeUpdate update = TrainRealTimeUpdate.newBuilder()
                        .setTrainId(rubyResponse.getTrainNumber())
                        .setStatusUpdate(statusUpdateMessage)
                        .build();

                responseObserver.onNext(update);
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Treno non trovato o errore dal servizio Viaggiatreno: " + rubyResponse.getErrorMessage())
                        .asRuntimeException());
            }
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Fallita la comunicazione con il servizio dati real-time: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    public void searchTrains(SearchTrainRequest request, StreamObserver<SearchTrainResponse> responseObserver) {
        System.out.println("[Server Java] Ricevuta richiesta searchTrains per: " +
                request.getDepartureStation().getName() + " -> " + request.getArrivalStation().getName());

        List<Train> foundTrains = new ArrayList<>();

        // Updated SQL query to work with the new GTFS schema
        String sql = "SELECT DISTINCT " +
                "    t.trip_id, " +
                "    t.trip_short_name, " +
                "    r.route_id, " +
                "    r.route_short_name, " +
                "    r.route_long_name, " +
                "    r.route_desc, " +
                "    a.agency_name, " +
                "    dep_st.stop_id AS departure_stop_id, " +
                "    dep_st.stop_name AS departure_station_name, " +
                "    arr_st.stop_id AS arrival_stop_id, " +
                "    arr_st.stop_name AS arrival_station_name, " +
                "    dep.departure_time, " +
                "    arr.arrival_time, " +
                "    t.trip_headsign " +
                "FROM stop_times AS dep " +
                "JOIN stop_times AS arr ON dep.trip_id = arr.trip_id " +
                "JOIN trips AS t ON dep.trip_id = t.trip_id " +
                "JOIN routes AS r ON t.route_id = r.route_id " +
                "JOIN agencies AS a ON r.agency_id = a.agency_id " +
                "JOIN stations AS dep_st ON dep.stop_id = dep_st.stop_id " +
                "JOIN stations AS arr_st ON arr.stop_id = arr_st.stop_id " +
                "WHERE dep.stop_id = ? AND arr.stop_id = ? " +
                "AND dep.stop_sequence < arr.stop_sequence " +
                "ORDER BY dep.departure_time";

        try (Connection conn = DriverManager.getConnection(DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, request.getDepartureStation().getId());
            pstmt.setString(2, request.getArrivalStation().getId());

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Station departureStation = Station.newBuilder()
                        .setId(rs.getString("departure_stop_id"))
                        .setName(rs.getString("departure_station_name"))
                        .build();

                Station arrivalStation = Station.newBuilder()
                        .setId(rs.getString("arrival_stop_id"))
                        .setName(rs.getString("arrival_station_name"))
                        .build();

                Timestamp departureTimestamp = convertStringToTimestamp(rs.getString("departure_time"));
                Timestamp arrivalTimestamp = convertStringToTimestamp(rs.getString("arrival_time"));

                // Extract train number from trip_short_name (e.g., "FR 9001" -> "9001")
                String tripShortName = rs.getString("trip_short_name");
                String trainNumber = extractTrainNumber(tripShortName);

                // Determine service class and price based on route information
                String serviceClass = rs.getString("route_short_name");
                double price = calculatePrice(serviceClass, rs.getString("route_desc"));

                Train train = Train.newBuilder()
                        .setId(rs.getString("trip_id"))
                        .setTrainNumber(trainNumber)
                        .setDepartureStation(departureStation)
                        .setArrivalStation(arrivalStation)
                        .setDepartureTime(departureTimestamp)
                        .setArrivalTime(arrivalTimestamp)
                        .setServiceClass(serviceClass)
                        .setPrice(price)
                        .setAvailableSeats(getAvailableSeats(serviceClass))
                        .build();
                foundTrains.add(train);
            }
            System.out.println("[Server Java] Trovati " + foundTrains.size() + " treni nel database.");

        } catch (SQLException e) {
            System.err.println("Errore query su SQLite: " + e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Errore database").asRuntimeException());
            return;
        }

        SearchTrainResponse response = SearchTrainResponse.newBuilder().addAllAvailableTrains(foundTrains).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private String extractTrainNumber(String tripShortName) {
        if (tripShortName == null || tripShortName.isEmpty()) {
            return "N/A";
        }
        // Extract number from strings like "FR 9001", "IC 501", "NTV 9701"
        String[] parts = tripShortName.split("\\s+");
        if (parts.length >= 2) {
            return parts[1]; // Return the number part
        }
        return tripShortName; // Return as-is if format is unexpected
    }

    private double calculatePrice(String serviceClass, String routeDesc) {
        if (serviceClass == null) return 25.00;

        switch (serviceClass.toUpperCase()) {
            case "FRECCIAROSSA":
            case "FRECCIARGENTO":
            case "FRECCIABIANCA":
            case "ITALO":
                return 65.00; // High-speed trains
            case "IC":
            case "ICN":
                return 45.00; // Intercity trains
            case "REG":
            case "FL1": case "FL2": case "FL3": case "FL4": case "FL5": case "FL6": case "FL7": case "FL8":
            case "S1": case "S2": case "S3": case "S4": case "S5": case "S6": case "S7": case "S8":
            case "S9": case "S10": case "S11": case "S12": case "S13":
            case "SFM1": case "SFM2": case "SFM3": case "SFM4":
                return 15.00; // Regional trains
            default:
                return 25.00; // Default price
        }
    }

    private int getAvailableSeats(String serviceClass) {
        if (serviceClass == null) return 50;

        switch (serviceClass.toUpperCase()) {
            case "FRECCIAROSSA":
            case "FRECCIARGENTO":
            case "FRECCIABIANCA":
            case "ITALO":
                return 300; // High-speed trains have more seats
            case "IC":
            case "ICN":
                return 200; // Intercity trains
            case "REG":
            case "FL1": case "FL2": case "FL3": case "FL4": case "FL5": case "FL6": case "FL7": case "FL8":
            case "S1": case "S2": case "S3": case "S4": case "S5": case "S6": case "S7": case "S8":
            case "S9": case "S10": case "S11": case "S12": case "S13":
            case "SFM1": case "SFM2": case "SFM3": case "SFM4":
                return 150; // Regional trains
            default:
                return 100; // Default seats
        }
    }



    private Timestamp convertStringToTimestamp(String timeStr) {
        try {
            // Handle times that go beyond 24:00:00 (common in GTFS)
            if (timeStr != null && timeStr.contains(":")) {
                String[] timeParts = timeStr.split(":");
                int hours = Integer.parseInt(timeParts[0]);
                int minutes = Integer.parseInt(timeParts[1]);
                int seconds = Integer.parseInt(timeParts[2]);

                // If hours >= 24, it means next day
                LocalDate baseDate = LocalDate.now();
                if (hours >= 24) {
                    hours -= 24;
                    baseDate = baseDate.plusDays(1);
                }

                LocalTime time = LocalTime.of(hours, minutes, seconds);
                ZonedDateTime zdt = ZonedDateTime.of(baseDate, time, ZoneId.of("Europe/Rome"));
                return Timestamp.newBuilder()
                        .setSeconds(zdt.toEpochSecond())
                        .setNanos(zdt.getNano())
                        .build();
            }
        } catch (Exception e) {
            System.err.println("Error parsing time: " + timeStr + " - " + e.getMessage());
        }

        // Fallback to current time
        Instant now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }

}