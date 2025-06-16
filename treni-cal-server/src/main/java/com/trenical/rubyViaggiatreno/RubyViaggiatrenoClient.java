package com.trenical.rubyViaggiatreno;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import ruby_viaggiatreno_microservizio.StationListResponse;
import ruby_viaggiatreno_microservizio.TrainStatusRequest;
import ruby_viaggiatreno_microservizio.TrainStatusResponse;
import ruby_viaggiatreno_microservizio.ViaggiatrenoServiceGrpc;
import ruby_viaggiatreno_microservizio.SearchStationRequest;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RubyViaggiatrenoClient {
    private static final Logger logger = Logger.getLogger(RubyViaggiatrenoClient.class.getName());
    private final ManagedChannel channel;
    private final ViaggiatrenoServiceGrpc.ViaggiatrenoServiceBlockingStub blockingStub;

    public RubyViaggiatrenoClient(String host, int port) {

        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        this.blockingStub = ViaggiatrenoServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(30, TimeUnit.SECONDS);

        logger.info("RubyViaggiatrenoClient connected to " + host + ":" + port);
    }

    public StationListResponse searchStations(String query) {
        logger.info("Requesting stations from Ruby service with query: '" + query + "'");
        try {
            SearchStationRequest request = SearchStationRequest.newBuilder()
                    .setSearchQuery(query)
                    .build();
            return blockingStub.searchStations(request);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "RPC to Ruby service failed for searchStations", e);
            // In caso di errore, restituisce una lista vuota per non bloccare l'applicazione.
            return StationListResponse.newBuilder().build();
        }
    }

    public TrainStatusResponse getTrainStatus(String trainNumber) {
        if (trainNumber == null || trainNumber.isEmpty()) {
            logger.warning("Train number is null or empty.");
            return TrainStatusResponse.newBuilder().setFound(false).setErrorMessage("Train number cannot be empty.").build();
        }

        TrainStatusRequest request = TrainStatusRequest.newBuilder()
                .setTrainNumber(trainNumber)
                .build();
        logger.info("Requesting status for train: " + trainNumber);
        try {
            TrainStatusResponse response = blockingStub.getTrainRealtimeStatus(request);
            if (response.getFound()) {
                logger.info("Status received for train " + trainNumber + ": " + response.getTrainStatusDescription());
            } else {
                logger.warning("Train " + trainNumber + " not found or error: " + response.getErrorMessage());
            }
            return response;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "RPC to Ruby Viaggiatreno service failed for train " + trainNumber, e);
            return TrainStatusResponse.newBuilder().setFound(false).setErrorMessage("Failed to connect to Viaggiatreno service: " + e.getMessage()).build();
        }
    }

    public void shutdown() throws InterruptedException {
        logger.info("Shutting down RubyViaggiatrenoClient channel.");
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}