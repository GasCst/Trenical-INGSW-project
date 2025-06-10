package gui;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import proto.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class GrpcClientService {
    private final ManagedChannel channel;
    private final TreniCalGrpc.TreniCalBlockingStub trainServiceBlockingStub;
    private final TicketServiceGrpc.TicketServiceBlockingStub ticketServiceBlockingStub;
    private final NotificationServiceGrpc.NotificationServiceStub notificationServiceAsyncStub;
    private String currentUserId = "fxUser1";

    public GrpcClientService(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.trainServiceBlockingStub = TreniCalGrpc.newBlockingStub(channel);
        this.ticketServiceBlockingStub = TicketServiceGrpc.newBlockingStub(channel);
        this.notificationServiceAsyncStub = NotificationServiceGrpc.newStub(channel);
    }

    public List<Station> searchStations(String query) {
        try {
            SearchStationRequest request = SearchStationRequest.newBuilder().setSearchQuery(query).build();
            return trainServiceBlockingStub.searchStations(request).getStationsList();
        } catch (Exception e) {
            System.err.println("Errore ricerca stazioni: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<TrainDisplay> searchTrains(Station from, Station to) {
        SearchTrainRequest request = SearchTrainRequest.newBuilder().setDepartureStation(from).setArrivalStation(to).build();
        List<TrainDisplay> displayList = new ArrayList<>();
        try {
            SearchTrainResponse response = trainServiceBlockingStub.searchTrains(request);
            for (Train train : response.getAvailableTrainsList()) {
                displayList.add(new TrainDisplay(
                        train.getId(), train.getTrainNumber(), train.getDepartureStation().getName(),
                        train.getArrivalStation().getName(),
                        Instant.ofEpochSecond(train.getDepartureTime().getSeconds(), train.getDepartureTime().getNanos()),
                        Instant.ofEpochSecond(train.getArrivalTime().getSeconds(), train.getArrivalTime().getNanos()),
                        train.getServiceClass(), train.getPrice(), train.getAvailableSeats()
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return displayList;
    }

    public String getRealTimeTrainInfo(String trainNumber) {
        try {
            TrainInfoRequest request = TrainInfoRequest.newBuilder().setTrainId(trainNumber).build();

            // 1. La chiamata ora restituisce un Iterator, non un singolo oggetto
            java.util.Iterator<TrainRealTimeUpdate> responseIterator = trainServiceBlockingStub.getTrainRealTimeInfo(request);

            // 2. Controlliamo se c'è almeno una risposta nello stream
            if (responseIterator.hasNext()) {
                // 3. Prendiamo la prima (e unica) risposta
                TrainRealTimeUpdate response = responseIterator.next();
                return "Info per Treno " + response.getTrainId() + ": " + response.getStatusUpdate();
            } else {
                // Caso in cui il server chiude lo stream senza inviare dati
                return "Nessun aggiornamento in tempo reale ricevuto per il treno " + trainNumber;
            }

        } catch (StatusRuntimeException e) {
            return "Errore dal servizio: " + e.getStatus().getDescription();
        } catch (Exception e) {
            return "Errore imprevisto: " + e.getMessage();
        }
    }

    public PurchaseTicketResponse purchaseTicket(String trainId, String serviceClass, int numTickets) {
        PurchaseTicketRequest request = PurchaseTicketRequest.newBuilder()
                .setUserId(currentUserId).setTrainId(trainId).setNumberOfTickets(numTickets)
                .setServiceClass(serviceClass).setPaymentMethodToken("sim_fx_payment_token")
                .build();
        try {
            return ticketServiceBlockingStub.purchaseTickets(request);
        } catch (Exception e) {
            e.printStackTrace();
            return PurchaseTicketResponse.newBuilder().setSuccess(false).setMessage("Error: " + e.getMessage()).build();
        }
    }


    public void subscribeToTripChanges(String ticketId, Consumer<TripChangeNotification> onNotification, Consumer<Throwable> onError, Runnable onCompleted) {
        TripSubscriptionRequest request = TripSubscriptionRequest.newBuilder()
                .setUserId(currentUserId).setTicketId(ticketId).build();
        notificationServiceAsyncStub.subscribeToTripChanges(request, new StreamObserver<>() {
            @Override public void onNext(TripChangeNotification n) { onNotification.accept(n); }
            @Override public void onError(Throwable t) { onError.accept(t); }
            @Override public void onCompleted() { onCompleted.run(); }
        });
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
