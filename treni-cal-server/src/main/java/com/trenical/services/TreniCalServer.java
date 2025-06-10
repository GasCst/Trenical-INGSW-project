// File: TreniCalServer.java
package com.trenical.services;

import com.trenical.observer.NotificationEngine;
import com.trenical.rubyViaggiatreno.RubyViaggiatrenoClient;
import com.trenical.util.TrainStatusPoller; // Creeremo questa classe nel prossimo step
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
        // --- 1. Inizializza il client gRPC per il microservizio Ruby ---
        // In un'app reale, "localhost" e 50052 andrebbero in un file di configurazione
        this.rubyClient = new RubyViaggiatrenoClient("localhost", 50052);

        // --- 2. Inizializza il motore delle notifiche e il nuovo poller ---
        // Il poller userà il client Ruby per ottenere i dati live.
        NotificationEngine notificationEngine = NotificationEngine.getInstance();
        TrainStatusPoller trainStatusPoller = new TrainStatusPoller(rubyClient, notificationEngine);

        // --- 3. Avvia il server gRPC principale per il client JavaFX ---
        int port = 50051;
        server = ServerBuilder.forPort(port)
                .addService(new TrainServiceImpl(rubyClient)) // Passa il rubyClient al servizio!
                .addService(new TicketServiceImpl())
                .addService(new NotificationServiceImpl())
                .build()
                .start();
        System.out.println("[Server] TreniCal gRPC server avviato, in ascolto sulla porta " + port);

        // --- 4. Avvia il task di polling in background ---
        // Questo controllerà gli aggiornamenti dei treni ogni 30 secondi.
        scheduler.scheduleAtFixedRate(trainStatusPoller, 10, 30, TimeUnit.SECONDS);
        System.out.println("[Server] TrainStatusPoller avviato. Controllo aggiornamenti ogni 30 secondi.");

        // --- 5. Aggiungi uno shutdown hook per fermare tutto correttamente ---
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
        // Ferma prima il poller
        if (!scheduler.isShutdown()) {
            System.out.println("Fermando il TrainStatusPoller...");
            scheduler.shutdownNow();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        }
        // Ferma il server gRPC principale
        if (server != null) {
            System.out.println("Fermando il server gRPC TreniCal...");
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
        // Ferma la connessione al servizio Ruby
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