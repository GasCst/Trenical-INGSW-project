
package com.trenical.services;

import com.trenical.observer.NotificationEngine;
import com.trenical.rubyViaggiatreno.RubyViaggiatrenoClient;
import com.trenical.util.TrainStatusPoller;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TreniCalServer {
    private Server server;
    private RubyViaggiatrenoClient rubyClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private void start() throws IOException {
        this.rubyClient = new RubyViaggiatrenoClient("localhost", 50052);
        NotificationEngine notificationEngine = NotificationEngine.getInstance();
        TrainStatusPoller trainStatusPoller = new TrainStatusPoller(rubyClient, notificationEngine);

        int port = 50051;
        server = ServerBuilder.forPort(port)
                .addService(new TrainServiceImpl(rubyClient))
                .addService(new TicketServiceImpl())
                .addService(new NotificationServiceImpl())
                .build()
                .start();
        System.out.println("[Server] TreniCal gRPC server avviato, in ascolto sulla porta " + port);


        scheduler.scheduleAtFixedRate(trainStatusPoller, 10, 30, TimeUnit.SECONDS);
        System.out.println("[Server] TrainStatusPoller avviato. Controllo aggiornamenti ogni 30 secondi.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** Spegnimento del server gRPC, poller e client Ruby a causa dello spegnimento della JVM ***");
            try {
                TreniCalServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** Spegnimento del server completato ***");
        }));
    }

    private void stop() throws InterruptedException {

        if (!scheduler.isShutdown()) {
            System.out.println("Fermando il TrainStatusPoller...");
            scheduler.shutdownNow();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        }

        if (server != null) {
            System.out.println("Fermando il server gRPC TreniCal...");
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }

        if (rubyClient != null) {
            System.out.println("Chiudendo il canale del RubyViaggiatrenoClient...");
            rubyClient.shutdown();
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