package gui;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import proto.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class GrpcClientService {
    private static final Logger logger = Logger.getLogger(GrpcClientService.class.getName());

    private final ManagedChannel channel;
    private final TreniCalGrpc.TreniCalBlockingStub trainServiceBlockingStub;
    private final TicketServiceGrpc.TicketServiceBlockingStub ticketServiceBlockingStub;
    private final NotificationServiceGrpc.NotificationServiceStub notificationServiceAsyncStub;
    private String currentUserId = "user_" + System.currentTimeMillis();

    // Circuit breaker state for resilience
    private int consecutiveFailures = 0;
    private long lastFailureTime = 0;
    private static final int FAILURE_THRESHOLD = 3;
    private static final long RECOVERY_TIMEOUT_MS = 30000; // 30 seconds

    public GrpcClientService(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .keepAliveTime(60, TimeUnit.SECONDS)
                .keepAliveTimeout(20, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .maxInboundMessageSize(4 * 1024 * 1024)  // 4MB limit
                .build();


        this.trainServiceBlockingStub = TreniCalGrpc.newBlockingStub(channel)
                .withDeadlineAfter(120, TimeUnit.SECONDS);
        this.ticketServiceBlockingStub = TicketServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(90, TimeUnit.SECONDS);
        this.notificationServiceAsyncStub = NotificationServiceGrpc.newStub(channel);

        logger.info("GrpcClientService initialized for user: " + currentUserId);
    }

    public List<Station> searchStations(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Check circuit breaker
        if (isCircuitOpen()) {
            logger.warning("Circuit breaker is OPEN - rejecting station search request");
            return new ArrayList<>();
        }

        int maxRetries = 2;
        long backoffMs = 500;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                SearchStationRequest request = SearchStationRequest.newBuilder()
                        .setSearchQuery(query.trim())
                        .build();


                StationListResponse response = trainServiceBlockingStub
                        .withDeadlineAfter(150, TimeUnit.SECONDS)
                        .searchStations(request);


                consecutiveFailures = 0;

                logger.info("Found " + response.getStationsList().size() + " stations for query: " + query);
                return response.getStationsList();

            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED && attempt < maxRetries) {
                    logger.warning("Timeout on attempt " + attempt + "/" + maxRetries + " for query: " + query +
                            ". Retrying in " + (backoffMs * attempt) + "ms...");
                    try {
                        Thread.sleep(backoffMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                recordFailure();
                logger.warning("gRPC error in searchStations (attempt " + attempt + "): " + e.getStatus());

                // For certain errors, don't retry
                if (e.getStatus().getCode() == Status.Code.UNAVAILABLE ||
                        e.getStatus().getCode() == Status.Code.FAILED_PRECONDITION) {
                    break;
                }

            } catch (Exception e) {
                recordFailure();
                logger.severe("Unexpected error in searchStations: " + e.getMessage());
                break;
            }
        }

        logger.warning("All attempts failed for station search query: " + query);
        return new ArrayList<>();
    }

    public List<TrainDisplay> searchTrains(Station from, Station to) {
        if (from == null || to == null) {
            logger.warning("Invalid stations provided for search");
            return new ArrayList<>();
        }

        if (isCircuitOpen()) {
            logger.warning("Circuit breaker is OPEN - rejecting train search request");
            throw new RuntimeException("Servizio temporaneamente non disponibile. Riprova tra qualche secondo.");
        }

        SearchTrainRequest request = SearchTrainRequest.newBuilder()
                .setDepartureStation(from)
                .setArrivalStation(to)
                .build();

        List<TrainDisplay> displayList = new ArrayList<>();

        try {
            SearchTrainResponse response = trainServiceBlockingStub
                    .withDeadlineAfter(120, TimeUnit.SECONDS)
                    .searchTrains(request);


            consecutiveFailures = 0;

            for (Train train : response.getAvailableTrainsList()) {
                try {
                    TrainDisplay trainDisplay = new TrainDisplay(
                            train.getId(),
                            train.getTrainNumber(),
                            train.getDepartureStation().getName(),
                            train.getArrivalStation().getName(),
                            Instant.ofEpochSecond(train.getDepartureTime().getSeconds(), train.getDepartureTime().getNanos()),
                            Instant.ofEpochSecond(train.getArrivalTime().getSeconds(), train.getArrivalTime().getNanos()),
                            train.getServiceClass(),
                            train.getPrice(),
                            train.getAvailableSeats()
                    );
                    displayList.add(trainDisplay);
                } catch (Exception e) {
                    logger.warning("Error creating TrainDisplay for train " + train.getId() + ": " + e.getMessage());
                }
            }

            logger.info("Successfully converted " + displayList.size() + " trains for display");

        } catch (StatusRuntimeException e) {
            recordFailure();
            logger.warning("gRPC error in searchTrains: " + e.getStatus());

            String userMessage;
            switch (e.getStatus().getCode()) {
                case DEADLINE_EXCEEDED:
                    userMessage = "⏱️ La ricerca sta richiedendo più tempo del previsto. Riprova.";
                    break;
                case UNAVAILABLE:
                    userMessage = "🔧 Servizio temporaneamente non disponibile. Riprova tra qualche secondo.";
                    break;
                case NOT_FOUND:
                    userMessage = "❌ Nessun treno trovato per questa tratta.";
                    break;
                default:
                    userMessage = "Errore di comunicazione con il server: " + e.getStatus().getDescription();
            }

            throw new RuntimeException(userMessage);
        } catch (Exception e) {
            recordFailure();
            logger.severe("Unexpected error in searchTrains: " + e.getMessage());
            throw new RuntimeException("Errore imprevisto durante la ricerca: " + e.getMessage());
        }

        return displayList;
    }

    public String getRealTimeTrainInfo(String trainId) {
        if (trainId == null || trainId.trim().isEmpty()) {
            return "ID treno non valido";
        }

        if (isCircuitOpen()) {
            return "🔧 Servizio real-time temporaneamente non disponibile";
        }

        try {
            TrainInfoRequest request = TrainInfoRequest.newBuilder()
                    .setTrainId(trainId.trim())
                    .build();


            Iterator<TrainRealTimeUpdate> responseIterator = trainServiceBlockingStub
                    .withDeadlineAfter(60, TimeUnit.SECONDS)
                    .getTrainRealTimeInfo(request);

            if (responseIterator.hasNext()) {
                TrainRealTimeUpdate response = responseIterator.next();
                consecutiveFailures = 0;
                String result = "🚄 " + response.getStatusUpdate();
                logger.info("Real-time info retrieved for train: " + trainId);
                return result;
            } else {
                recordFailure();
                logger.warning("No real-time update received for train: " + trainId);
                return "❌ Nessun aggiornamento in tempo reale disponibile per questo treno.";
            }

        } catch (StatusRuntimeException e) {
            recordFailure();
            logger.warning("gRPC error in getRealTimeTrainInfo: " + e.getStatus());

            switch (e.getStatus().getCode()) {
                case NOT_FOUND:
                    return "❌ Treno non trovato nel sistema Viaggiatreno";
                case DEADLINE_EXCEEDED:
                    return "⏱️ Timeout durante il recupero delle informazioni";
                case UNAVAILABLE:
                    return "🔧 Servizio real-time temporaneamente non disponibile";
                default:
                    return "❌ Errore del servizio: " + e.getStatus().getDescription();
            }
        } catch (Exception e) {
            recordFailure();
            logger.severe("Unexpected error in getRealTimeTrainInfo: " + e.getMessage());
            return "❌ Errore imprevisto: " + e.getMessage();
        }
    }

    public PurchaseTicketResponse purchaseTicket(String trainId, String serviceClass, int numTickets) {
        if (trainId == null || serviceClass == null || numTickets <= 0) {
            return PurchaseTicketResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Parametri di acquisto non validi")
                    .build();
        }

        if (isCircuitOpen()) {
            return PurchaseTicketResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Servizio di acquisto temporaneamente non disponibile")
                    .build();
        }

        PurchaseTicketRequest request = PurchaseTicketRequest.newBuilder()
                .setUserId(currentUserId)
                .setTrainId(trainId)
                .setNumberOfTickets(numTickets)
                .setServiceClass(serviceClass)
                .setPaymentMethodToken("sim_payment_" + System.currentTimeMillis())
                .build();

        try {
            PurchaseTicketResponse response = ticketServiceBlockingStub
                    .withDeadlineAfter(90, TimeUnit.SECONDS)
                    .purchaseTickets(request);

            // Reset circuit breaker on success
            consecutiveFailures = 0;

            logger.info("Purchase attempt for train " + trainId + ": " + response.getSuccess());
            return response;

        } catch (StatusRuntimeException e) {
            recordFailure();
            logger.warning("gRPC error in purchaseTicket: " + e.getStatus());

            String errorMessage;
            switch (e.getStatus().getCode()) {
                case DEADLINE_EXCEEDED:
                    errorMessage = "⏱️ Timeout durante l'acquisto. Riprova.";
                    break;
                case UNAVAILABLE:
                    errorMessage = "🔧 Servizio acquisti temporaneamente non disponibile.";
                    break;
                default:
                    errorMessage = "Errore di comunicazione: " + e.getStatus().getDescription();
            }

            return PurchaseTicketResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(errorMessage)
                    .build();
        } catch (Exception e) {
            recordFailure();
            logger.severe("Unexpected error in purchaseTicket: " + e.getMessage());
            return PurchaseTicketResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Errore imprevisto: " + e.getMessage())
                    .build();
        }
    }

    public void subscribeToTripChanges(String ticketId,
                                       Consumer<TripChangeNotification> onNotification,
                                       Consumer<Throwable> onError,
                                       Runnable onCompleted) {

        if (ticketId == null || ticketId.trim().isEmpty()) {
            onError.accept(new IllegalArgumentException("ID biglietto non valido"));
            return;
        }

        if (isCircuitOpen()) {
            onError.accept(new RuntimeException("Servizio notifiche temporaneamente non disponibile"));
            return;
        }

        TripSubscriptionRequest request = TripSubscriptionRequest.newBuilder()
                .setUserId(currentUserId)
                .setTicketId(ticketId.trim())
                .build();

        try {
            notificationServiceAsyncStub.subscribeToTripChanges(request, new StreamObserver<TripChangeNotification>() {
                @Override
                public void onNext(TripChangeNotification notification) {
                    logger.info("Notification received for ticket: " + ticketId);
                    consecutiveFailures = 0; // Reset on successful notification
                    onNotification.accept(notification);
                }

                @Override
                public void onError(Throwable t) {
                    recordFailure();
                    logger.warning("Error in subscription for ticket " + ticketId + ": " + t.getMessage());
                    onError.accept(t);
                }

                @Override
                public void onCompleted() {
                    logger.info("Subscription completed for ticket: " + ticketId);
                    onCompleted.run();
                }
            });

            logger.info("Subscription initiated for ticket: " + ticketId);

        } catch (Exception e) {
            recordFailure();
            logger.severe("Failed to initiate subscription: " + e.getMessage());
            onError.accept(e);
        }
    }

    // Circuit breaker metodi
    private boolean isCircuitOpen() {
        if (consecutiveFailures < FAILURE_THRESHOLD) {
            return false;
        }


        boolean isOpen = (System.currentTimeMillis() - lastFailureTime) < RECOVERY_TIMEOUT_MS;

        if (!isOpen) {
            logger.info("Circuit breaker transitioning to HALF-OPEN state");
            consecutiveFailures = FAILURE_THRESHOLD - 1; // Allow one test request
        }

        return isOpen;
    }

    private void recordFailure() {
        consecutiveFailures++;
        lastFailureTime = System.currentTimeMillis();

        if (consecutiveFailures == FAILURE_THRESHOLD) {
            logger.warning("Circuit breaker OPENED after " + consecutiveFailures + " consecutive failures");
        }
    }

    public String getCurrentUserId() {
        return currentUserId;
    }


    public ModifyTicketResponse modifyTicket(ModifyTicketRequest request) {
        try {
            return ticketServiceBlockingStub.modifyTicket(request);
        } catch (StatusRuntimeException e) {
            logger.warning("Errore gRPC in modifyTicket: " + e.getStatus());
            return ModifyTicketResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Errore durante la modifica del biglietto: " + e.getStatus().getDescription())
                    .build();
        }
    }

    public boolean isChannelHealthy() {
        try {
            return !channel.isShutdown() && !channel.isTerminated() && !isCircuitOpen();
        } catch (Exception e) {
            return false;
        }
    }

    public String getConnectionStatus() {
        if (channel.isShutdown()) return "🔴 Disconnesso";
        if (channel.isTerminated()) return "🔴 Terminato";
        if (isCircuitOpen()) return "🟡 Servizio limitato";
        return "🟢 Connesso";
    }

    public void shutdown() throws InterruptedException {
        logger.info("Shutting down gRPC client...");

        try {
            channel.shutdown();
            if (!channel.awaitTermination(15, TimeUnit.SECONDS)) { // Increased from 10s
                logger.warning("Channel did not terminate gracefully, forcing shutdown");
                channel.shutdownNow();
                channel.awaitTermination(5, TimeUnit.SECONDS);
            }
            logger.info("gRPC client shutdown completed");
        } catch (InterruptedException e) {
            logger.warning("Interrupted during shutdown");
            channel.shutdownNow();
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}