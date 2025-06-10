package com.trenical.services;


import com.trenical.rubyViaggiatreno.RubyViaggiatrenoClient;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import proto.*;
import ruby_viaggiatreno_microservizio.TrainStatusResponse;


public class TrainServiceImpl extends TreniCalGrpc.TreniCalImplBase {


    private final RubyViaggiatrenoClient rubyClient;

    public TrainServiceImpl(RubyViaggiatrenoClient rubyClient) {
        this.rubyClient = rubyClient;
    }

    @Override
    public void searchStations(SearchStationRequest request, StreamObserver<StationListResponse> responseObserver) {
        String query = request.getSearchQuery();
        System.out.println("[Java Server] Ricevuta richiesta SearchStations. Query: '" + query + "'. Inoltro a Ruby...");

        try {
            // Chiama il client Ruby per ottenere la lista filtrata
            ruby_viaggiatreno_microservizio.StationListResponse rubyResponse = rubyClient.searchStations(query);

            System.out.println("[Java Server] Ricevute " + rubyResponse.getStationsCount() + " stazioni da Ruby. Le traduco per il client Java.");

            proto.StationListResponse.Builder javaResponseBuilder = proto.StationListResponse.newBuilder();

            for (ruby_viaggiatreno_microservizio.Station rubyStation : rubyResponse.getStationsList()) {
                // Per ogni stazione di tipo Ruby, ne creo una nuova di tipo Java
                proto.Station javaStation = proto.Station.newBuilder()
                        .setId(rubyStation.getId())
                        .setName(rubyStation.getName())
                        .build();
                javaResponseBuilder.addStations(javaStation);
            }

            responseObserver.onNext(javaResponseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            System.err.println("[Java Server] Fallita comunicazione con il servizio Ruby: " + e.getMessage());
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Errore interno durante la ricerca delle stazioni.")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void getTrainRealTimeInfo(TrainInfoRequest request, StreamObserver<TrainRealTimeUpdate> responseObserver) {
        // NOTA: Assumiamo che l'ID passato dal client (es. "TR001") debba essere mappato
        // a un numero di treno reale (es. "9600"). Per ora, usiamo l'ID direttamente.
        String trainNumber = request.getTrainId();
        System.out.println("[Server] Ricevuta richiesta GetTrainRealTimeInfo per il treno: " + trainNumber);

        try {
            // Chiama il microservizio Ruby tramite il nostro client Java
            TrainStatusResponse rubyResponse = rubyClient.getTrainStatus(trainNumber);

            if (rubyResponse.getFound()) {
                // Mappa la risposta dal servizio Ruby al messaggio di risposta per il nostro client JavaFX
                String statusUpdateMessage = String.format("%s. Ritardo: %d min. Ultima rilevazione: %s",
                        rubyResponse.getTrainStatusDescription(),
                        rubyResponse.getDelayMinutes(),
                        rubyResponse.getLastDetectedStation());

                TrainRealTimeUpdate update = TrainRealTimeUpdate.newBuilder()
                        .setTrainId(rubyResponse.getTrainNumber())
                        .setStatusUpdate(statusUpdateMessage)
                        // Potresti aggiungere binario e orario di arrivo aggiornato se disponibili
                        // .setPlatform(...)
                        .build();

                responseObserver.onNext(update);
                responseObserver.onCompleted();
            } else {
                // Treno non trovato dal servizio Ruby, informa il client
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

    @Override
    public void searchTrains(SearchTrainRequest request, StreamObserver<SearchTrainResponse> responseObserver) {
        System.out.println("Richiesta searchTrains ricevuta per: " + request.getDepartureStation().getName() + " -> " + request.getArrivalStation().getName());

        SearchTrainResponse response = SearchTrainResponse.newBuilder().build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}