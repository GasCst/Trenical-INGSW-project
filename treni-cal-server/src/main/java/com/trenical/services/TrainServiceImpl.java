package com.trenical.services;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import proto.*;
import com.trenical.database.TrainDatabase;
import com.trenical.database.TicketDatabase;
import com.google.protobuf.Timestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

public class TrainServiceImpl extends TreniCalGrpc.TreniCalImplBase{

    private final TrainDatabase trainDatabase = TrainDatabase.getInstance();
    private final TicketDatabase ticketDatabase = TicketDatabase.getInstance();

    @Override
    public void searchTrains (SearchTrainRequest request, StreamObserver<SearchTrainResponse> responseObserver){
        //DEBUG PRINT
        System.out.println("[Server] Received SearchTrains request for: " +
                request.getDepartureStation().getName() +
                " to " + request.getArrivalStation().getName() +
                " on " + request.getTravelDate().getYear() + "-" +
                request.getTravelDate().getMonth() + "-" +
                request.getTravelDate().getDay());


        LocalDate searchDate = LocalDate.of(request.getTravelDate().getYear(),
                request.getTravelDate().getMonth(),
                request.getTravelDate().getDay());

        List<Train> allTrains = trainDatabase.getAllTrains();

        // uso lo stream di java per lavorare con la collezione di dati "allTrains"
        List<Train> foundTrains = allTrains.stream()
                .filter(train -> train.getDepartureStation().getId().equals(request.getDepartureStation().getId()) &&
                        train.getArrivalStation().getId().equals(request.getArrivalStation().getId()))
                .filter(train -> {
                    Instant trainDepartureInstant = Instant.ofEpochSecond(train.getDepartureTime().getSeconds(), train.getDepartureTime().getNanos());
                    LocalDate trainDepartureDate = LocalDate.ofInstant(trainDepartureInstant, ZoneId.systemDefault());
                    return trainDepartureDate.equals(searchDate);
                })
                .filter(train -> request.getPreferredTrainType().isEmpty() || train.getTrainType().equalsIgnoreCase(request.getPreferredTrainType()))
                .filter(train -> request.getPreferredServiceClass().isEmpty() || train.getServiceClass().equalsIgnoreCase(request.getPreferredServiceClass()))

                .map(train -> {
                    // Disponibilità dei posti simulata, in realtà non si fa così.
                    // In un sistema reale, in teoria bisognerebbe contare il numero di biglietti venduti per questo treno e anche per la classe.
                    int availableSeats = trainDatabase.getAvailableSeats(train.getId(), train.getServiceClass());
                    return train.toBuilder().setAvailableSeats(availableSeats).build();
                })
                .filter(train -> train.getAvailableSeats() > 0)
                .collect(Collectors.toList());

        // costruisco il messaggio di risposta contenente la lista
        // dei treni disponibili,
        // lo invio al client, e poi chiudo la comunicazione,
        // ( siccome in questo caso sto facendo una chiamata unary).

        SearchTrainResponse response = SearchTrainResponse.newBuilder()
                .addAllAvailableTrains(foundTrains)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted(); // chiamata unary in questo caso, .onCompleted serve per chiudere la comunicazione correttamente.

    }


    @Override
    public void getTrainRealTimeInfo(TrainInfoRequest request, StreamObserver<TrainRealTimeUpdate> responseObserver) {
        String trainId = request.getTrainId();
        System.out.println("[Server] Received GetTrainRealTimeInfo request for train ID: " + trainId);

        for (int i = 0; i < 5; i++) { // Send a few updates
            try {
                Thread.sleep(2000); // simulo un delay tra un update e un altro
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("Stream interrupted").asRuntimeException());
                return;
            }
            if (Thread.currentThread().isInterrupted()) {
                responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("Stream interrupted by client").asRuntimeException());
                return;
            }


            Train train = trainDatabase.getTrainById(trainId);
            if (train == null) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND.withDescription("Train not found").asRuntimeException());
                return;
            }

            // Simulo dei Cambiamenti
            Timestamp updatedArrivalTime = Timestamp.newBuilder()
                    .setSeconds(train.getArrivalTime().getSeconds() + (i * 60)) // ritardo di i minuti
                    .build();
            String platform = (i % 2 == 0) ? "5" : "5B";

            TrainRealTimeUpdate update = TrainRealTimeUpdate.newBuilder()
                    .setTrainId(trainId)
                    .setUpdatedArrivalTime(updatedArrivalTime)
                    .setPlatform(platform)
                    .setStatusUpdate("Train status update " + (i + 1) + ": Delayed, new platform " + platform)
                    .build();
            responseObserver.onNext(update);
        }

        responseObserver.onCompleted();
    }


    @Override
    public void getAvailableStations( EmptyRequest request, StreamObserver<StationListResponse> responseObserver){
        System.out.println("[Server] Received GetAvailableStations request");
        try{
            List<Station> stations = trainDatabase.getAllUniqueStations();
            StationListResponse response = StationListResponse.newBuilder()
                    .addAllStations(stations)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e ){
            System.err.println("[Server] Error getting available stations: " + e.getMessage());
            e.printStackTrace();
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Error fetching stations: "+ e.getMessage())
                    .asRuntimeException());
        }
    }


}
