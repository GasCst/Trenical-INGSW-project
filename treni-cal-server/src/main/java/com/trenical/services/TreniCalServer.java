package com.trenical.services;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import com.trenical.services.TrainServiceImpl;
import com.trenical.services.TicketServiceImpl;
import com.trenical.services.NotificationServiceImpl;
import com.trenical.observer.NotificationEngine;
import com.trenical.database.TicketDatabase;
import proto.Ticket;

import java.io.IOException;
import java.util.concurrent.TimeUnit;


public class TreniCalServer {
    private Server server;

    private void start() throws IOException {
        int port = 50051;
        server = ServerBuilder.forPort(port)
                .addService(new TrainServiceImpl())
                .addService(new TicketServiceImpl())
                .addService(new NotificationServiceImpl())
                .build()
                .start();
        System.out.println("[Server] Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            try {
                TreniCalServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** server shut down");
        }));


        new Thread(() -> {
            try {
                Thread.sleep(20000);
                TicketDatabase ticketDb = TicketDatabase.getInstance();
                if (!ticketDb.getTicketsForTrain("TR001").isEmpty()) {
                    Ticket sampleTicket = ticketDb.getTicketsForTrain("TR001").get(0);
                    if (sampleTicket != null) {
                        NotificationEngine.getInstance().simulateTrainDelay(
                                sampleTicket.getTrainDetails().getId(),
                                sampleTicket.getId(),
                                "Train " + sampleTicket.getTrainDetails().getTrainNumber() + " is now delayed by 30 minutes due to technical issues."
                        );
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final TreniCalServer server = new TreniCalServer();
        server.start();
        server.blockUntilShutdown();
    }
}
