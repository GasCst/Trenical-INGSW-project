
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import proto.*;
import com.google.protobuf.Timestamp;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TreniCalClient {
    private final ManagedChannel channel;
    private final TreniCalGrpc.TreniCalBlockingStub trainServiceBlockingStub;
    private final TreniCalGrpc.TreniCalStub trainServiceAsyncStub;
    private final TicketServiceGrpc.TicketServiceBlockingStub ticketServiceBlockingStub;
    private final NotificationServiceGrpc.NotificationServiceStub notificationServiceAsyncStub;

    private String currentUserId = "user123";

    public TreniCalClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.trainServiceBlockingStub = TreniCalGrpc.newBlockingStub(channel);
        this.trainServiceAsyncStub = TreniCalGrpc.newStub(channel);
        this.ticketServiceBlockingStub = TicketServiceGrpc.newBlockingStub(channel);
        this.notificationServiceAsyncStub = NotificationServiceGrpc.newStub(channel);
    }


    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void searchTrains(String fromStationId, String toStationId, LocalDate date, String prefTrainType, String prefClass) {
        System.out.println("Searching trains from " + fromStationId + " to " + toStationId + " for " + date);
        Station departure = Station.newBuilder().setId(fromStationId).setName(fromStationId).build();
        Station arrival = Station.newBuilder().setId(toStationId).setName(toStationId).build();
        TravelDate travelDate = TravelDate.newBuilder()
                .setYear(date.getYear()).setMonth(date.getMonthValue()).setDay(date.getDayOfMonth()).build();

        SearchTrainRequest request = SearchTrainRequest.newBuilder()
                .setDepartureStation(departure)
                .setArrivalStation(arrival)
                .setTravelDate(travelDate)
                .setPreferredTrainType(prefTrainType == null ? "" : prefTrainType)
                .setPreferredServiceClass(prefClass == null ? "" : prefClass)
                .build();
        try {
            SearchTrainResponse response = trainServiceBlockingStub.searchTrains(request);
            if (response.getAvailableTrainsCount() > 0) {
                System.out.println("Found " + response.getAvailableTrainsCount() + " trains:");
                for (Train train : response.getAvailableTrainsList()) {
                    Timestamp depTs = train.getDepartureTime();
                    Timestamp arrTs = train.getArrivalTime();
                    System.out.printf("  ID: %s, Num: %s, From: %s (%s) To: %s (%s), Class: %s, Price: %.2f, Seats: %d, Type: %s\n",
                            train.getId(), train.getTrainNumber(),
                            train.getDepartureStation().getName(), java.time.Instant.ofEpochSecond(depTs.getSeconds(), depTs.getNanos()),
                            train.getArrivalStation().getName(), java.time.Instant.ofEpochSecond(arrTs.getSeconds(), arrTs.getNanos()),
                            train.getServiceClass(), train.getPrice(), train.getAvailableSeats(), train.getTrainType());
                }
            } else {
                System.out.println("No trains found for your criteria.");
            }
        } catch (StatusRuntimeException e) {
            System.err.println("RPC failed: " + e.getStatus());
        }
    }

    public void purchaseTicket(String trainId, String serviceClass, int numTickets) {
        System.out.println("Attempting to purchase " + numTickets + " ticket(s) for train " + trainId + " in class " + serviceClass);
        PurchaseTicketRequest request = PurchaseTicketRequest.newBuilder()
                .setUserId(currentUserId)
                .setTrainId(trainId)
                .setNumberOfTickets(numTickets)
                .setServiceClass(serviceClass)
                .setPaymentMethodToken("sim_payment_tok_success") // Simulated token
                .build();
        try {
            PurchaseTicketResponse response = ticketServiceBlockingStub.purchaseTickets(request);
            System.out.println("Purchase " + (response.getSuccess() ? "successful" : "failed") + ": " + response.getMessage());
            if (response.getSuccess()) {
                for (Ticket ticket : response.getPurchasedTicketsList()) {
                    System.out.println("  Ticket ID: " + ticket.getId() + ", Seat: " + ticket.getSeatNumber() + ", Status: " + ticket.getStatus());
                    if(response.getPurchasedTicketsList().indexOf(ticket) == 0) {
                        subscribeToTripChanges(ticket.getId());
                    }
                }
            }
        } catch (StatusRuntimeException e) {
            System.err.println("RPC failed: " + e.getStatus());
        }
    }

    public void getMyTickets() {
        System.out.println("Fetching tickets for user: " + currentUserId);
        UserRequest request = UserRequest.newBuilder().setUserId(currentUserId).build();
        try {
            TicketListResponse response = ticketServiceBlockingStub.getMyTickets(request);
            if (response.getTicketsCount() > 0) {
                System.out.println("Your tickets:");
                for (Ticket ticket : response.getTicketsList()) {
                    System.out.printf("  ID: %s, Train No: %s, From: %s To: %s, Seat: %s, Status: %s\n",
                            ticket.getId(), ticket.getTrainDetails().getTrainNumber(),
                            ticket.getTrainDetails().getDepartureStation().getName(),
                            ticket.getTrainDetails().getArrivalStation().getName(),
                            ticket.getSeatNumber(), ticket.getStatus());
                }
            } else {
                System.out.println("No tickets found for user " + currentUserId);
            }
        } catch (StatusRuntimeException e) {
            System.err.println("RPC failed: " + e.getStatus());
        }
    }


    public void subscribeToTripChanges(String ticketId) {
        System.out.println("Subscribing to trip changes for ticket ID: " + ticketId);
        TripSubscriptionRequest request = TripSubscriptionRequest.newBuilder()
                .setUserId(currentUserId)
                .setTicketId(ticketId)
                .build();

        final CountDownLatch finishLatch = new CountDownLatch(1);

        notificationServiceAsyncStub.subscribeToTripChanges(request, new StreamObserver<TripChangeNotification>() {
            @Override
            public void onNext(TripChangeNotification notification) {
                System.out.println("\n[REAL-TIME UPDATE for Ticket " + notification.getTicketId() + "]: " + notification.getUpdateMessage());
                if (notification.hasNewDepartureTime() && notification.getNewDepartureTime().getSeconds() > 0) {
                    System.out.println("  New Departure: " + java.time.Instant.ofEpochSecond(notification.getNewDepartureTime().getSeconds()));
                }
                if (!notification.getNewPlatform().isEmpty()){
                    System.out.println("  New Platform: " + notification.getNewPlatform());
                }
                System.out.print("Enter command: ");
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Subscription failed: " + t.getMessage());
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Subscription stream completed by server for ticket " + ticketId);
                finishLatch.countDown();
            }
        });
        System.out.println("Subscription request sent. Waiting for updates for ticket " + ticketId + "...");
    }


    public static void main(String[] args) throws InterruptedException {
        TreniCalClient client = new TreniCalClient("localhost", 50051);
        Scanner scanner = new Scanner(System.in);

        try {
            while (true) {
                System.out.println("\n--- TreniCal Client Menu ---");
                System.out.println("1. Search Trains");
                System.out.println("2. Purchase Ticket");
                System.out.println("3. View My Tickets");
                // System.out.println("4. Subscribe to Train Updates (Manual - use after purchase)");
                System.out.println("0. Exit");
                System.out.print("Enter command: ");
                String command = scanner.nextLine();

                switch (command) {
                    case "1":
                        System.out.print("Enter departure station ID (e.g., RM001): "); String from = scanner.nextLine();
                        System.out.print("Enter arrival station ID (e.g., MI001): "); String to = scanner.nextLine();
                        System.out.print("Enter date (YYYY-MM-DD): "); String dateStr = scanner.nextLine();
                        LocalDate date = LocalDate.parse(dateStr);
                        client.searchTrains(from, to, date, "", "");
                        break;
                    case "2":
                        System.out.print("Enter Train ID to purchase: "); String trainId = scanner.nextLine();
                        System.out.print("Enter Service Class: "); String sClass = scanner.nextLine();
                        System.out.print("Enter number of tickets: "); int numT = Integer.parseInt(scanner.nextLine());
                        client.purchaseTicket(trainId, sClass, numT);
                        break;
                    case "3":
                        client.getMyTickets();
                        break;
                    // case "4":
                    //     System.out.print("Enter Ticket ID to subscribe for updates: "); String subTicketId = scanner.nextLine();
                    //     client.subscribeToTripChanges(subTicketId);
                    //     break;
                    case "0":
                        System.out.println("Exiting...");
                        return;
                    default:
                        System.out.println("Invalid command.");
                }
            }
        } finally {
            client.shutdown();
            scanner.close();
        }
    }

}


