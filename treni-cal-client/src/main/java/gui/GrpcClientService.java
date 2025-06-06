package gui;

import gui.TrainDisplay;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import proto.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class GrpcClientService {
    private final ManagedChannel channel;
    private final TreniCalGrpc.TreniCalBlockingStub trainServiceBlockingStub;
    private final TicketServiceGrpc.TicketServiceBlockingStub ticketServiceBlockingStub;
    private final NotificationServiceGrpc.NotificationServiceStub notificationServiceAsyncStub;
    private String currentUserId = "fxUser1";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());


    public GrpcClientService(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.trainServiceBlockingStub = TreniCalGrpc.newBlockingStub(channel);
        this.ticketServiceBlockingStub = TicketServiceGrpc.newBlockingStub(channel);
        this.notificationServiceAsyncStub = NotificationServiceGrpc.newStub(channel);
    }

    public List<TrainDisplay> searchTrains(Station from, Station to, TravelDate date) {
        SearchTrainRequest request = SearchTrainRequest.newBuilder()
                .setDepartureStation(from)
                .setArrivalStation(to)
                .setTravelDate(date)
                .build();
        List<TrainDisplay> displayList = new ArrayList<>();
        try {
            SearchTrainResponse response = trainServiceBlockingStub.searchTrains(request);
            for (Train train : response.getAvailableTrainsList()) {
                displayList.add(new TrainDisplay(
                        train.getId(),
                        train.getTrainNumber(),
                        train.getDepartureStation().getName(),
                        train.getArrivalStation().getName(),
                        TIME_FORMATTER.format(Instant.ofEpochSecond(train.getDepartureTime().getSeconds())),
                        TIME_FORMATTER.format(Instant.ofEpochSecond(train.getArrivalTime().getSeconds())),
                        train.getServiceClass(),
                        train.getPrice(),
                        train.getAvailableSeats()
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return displayList;
    }

    public PurchaseTicketResponse purchaseTicket(String trainId, String serviceClass, int numTickets) {
        PurchaseTicketRequest request = PurchaseTicketRequest.newBuilder()
                .setUserId(currentUserId)
                .setTrainId(trainId)
                .setNumberOfTickets(numTickets)
                .setServiceClass(serviceClass)
                .setPaymentMethodToken("sim_fx_payment_token")
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
                .setUserId(currentUserId)
                .setTicketId(ticketId)
                .build();

        notificationServiceAsyncStub.subscribeToTripChanges(request, new StreamObserver<TripChangeNotification>() {
            @Override
            public void onNext(TripChangeNotification notification) {
                onNotification.accept(notification);
            }

            @Override
            public void onError(Throwable t) {
                onError.accept(t);
            }

            @Override
            public void onCompleted() {
                onCompleted.run();
            }
        });
    }



    public List<Station> getAvailableStations(){
        System.out.println("[Client GUI] Requesting available stations from server...");
        EmptyRequest request = EmptyRequest.newBuilder().build();
        try {
            StationListResponse response = trainServiceBlockingStub.getAvailableStations(request);
            System.out.println("[Client GUI] Received " + response.getStationsCount() + " stations.");
            return response.getStationsList();
        } catch (Exception e) {
            System.err.println("[Client GUI] Error fetching stations: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>(); // in caso di errore ritono una lista vuota
        }
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
