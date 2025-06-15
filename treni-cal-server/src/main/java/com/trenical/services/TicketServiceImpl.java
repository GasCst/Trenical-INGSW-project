package com.trenical.services;

import io.grpc.stub.StreamObserver;
import proto.ModifyTicketRequest;
import proto.ModifyTicketResponse;
import proto.*;
import com.google.protobuf.Timestamp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class TicketServiceImpl extends TicketServiceGrpc.TicketServiceImplBase {

    private static final Logger logger = Logger.getLogger(TicketServiceImpl.class.getName());
    private static final String DB_PATH = "jdbc:sqlite:trenical.db";

    public TicketServiceImpl() {
        // Initialize tickets table if it doesn't exist
        createTicketsTableIfNotExists();
        logger.info("TicketServiceImpl initialized with REAL SQLite database");
    }

    private void createTicketsTableIfNotExists() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS tickets (
                ticket_id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                trip_id TEXT NOT NULL,
                seat_number TEXT,
                purchase_date INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'CONFIRMED',
                service_class TEXT,
                price REAL,
                FOREIGN KEY (trip_id) REFERENCES trips(trip_id)
            )
        """;

        try (Connection conn = DriverManager.getConnection(DB_PATH);
             Statement stmt = conn.createStatement()) {

            stmt.execute(createTableSQL);

            // Create index for performance
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_user ON tickets(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_trip ON tickets(trip_id)");

            logger.info("Tickets table created/verified successfully");

        } catch (SQLException e) {
            logger.severe("Error creating tickets table: " + e.getMessage());
        }
    }

    @Override
    public void purchaseTickets(PurchaseTicketRequest request, StreamObserver<PurchaseTicketResponse> responseObserver) {
        logger.info("Received PurchaseTickets request for user: " + request.getUserId() + " for train: " + request.getTrainId());

        PurchaseTicketResponse.Builder responseBuilder = PurchaseTicketResponse.newBuilder();

        try (Connection conn = DriverManager.getConnection(DB_PATH)) {

            // 1. Get REAL train info from GTFS
            Train trainToBook = getRealTrainFromGTFS(conn, request.getTrainId());

            if (trainToBook == null) {
                responseBuilder.setSuccess(false).setMessage("Treno non trovato nel sistema GTFS.");
                responseObserver.onNext(responseBuilder.build());
                responseObserver.onCompleted();
                return;
            }

            // 2. Check REAL available seats
            int soldTickets = getTicketCountForTrip(conn, request.getTrainId(), request.getServiceClass());
            int totalCapacity = getRealTrainCapacity(trainToBook.getServiceClass());
            int availableSeats = totalCapacity - soldTickets;

            logger.info("Train " + request.getTrainId() + " - Sold: " + soldTickets + ", Capacity: " + totalCapacity + ", Available: " + availableSeats);

            if (availableSeats < request.getNumberOfTickets()) {
                responseBuilder.setSuccess(false)
                        .setMessage("Posti insufficienti. Disponibili: " + availableSeats + ", Richiesti: " + request.getNumberOfTickets());
                responseObserver.onNext(responseBuilder.build());
                responseObserver.onCompleted();
                return;
            }

            // 3. Process payment (simulate real payment processing)
            boolean paymentSuccessful = processPayment(request.getPaymentMethodToken(), trainToBook.getPrice() * request.getNumberOfTickets());

            if (!paymentSuccessful) {
                responseBuilder.setSuccess(false).setMessage("Pagamento fallito. Verificare il metodo di pagamento.");
                responseObserver.onNext(responseBuilder.build());
                responseObserver.onCompleted();
                return;
            }

            // 4. Create and save REAL tickets
            List<Ticket> purchasedTicketsList = new ArrayList<>();

            for (int i = 0; i < request.getNumberOfTickets(); i++) {
                String ticketId = UUID.randomUUID().toString();
                String seatNumber = generateRealSeatNumber(request.getServiceClass(), soldTickets + i + 1);

                // Create ticket with REAL train data
                Ticket newTicket = Ticket.newBuilder()
                        .setId(ticketId)
                        .setUserId(request.getUserId())
                        .setTrainDetails(trainToBook.toBuilder()
                                .setServiceClass(request.getServiceClass())
                                .build())
                        .setSeatNumber(seatNumber)
                        .setPurchaseDate(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build())
                        .setStatus("CONFIRMED")
                        .build();

                // Save to database
                saveTicketToDatabase(conn, newTicket, request.getTrainId());
                purchasedTicketsList.add(newTicket);

                logger.info("Created ticket: " + ticketId + " for seat: " + seatNumber);
            }

            responseBuilder.setSuccess(true)
                    .addAllPurchasedTickets(purchasedTicketsList)
                    .setMessage("Acquisto completato con successo per " + request.getNumberOfTickets() + " biglietto/i.");

        } catch (SQLException e) {
            logger.severe("Database error during ticket purchase: " + e.getMessage());
            responseBuilder.setSuccess(false).setMessage("Errore del database durante l'acquisto.");
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void modifyTicket(ModifyTicketRequest request, StreamObserver<ModifyTicketResponse> responseObserver) {
        logger.info("Received ModifyTicket request for ticket ID: " + request.getTicketId());

        ModifyTicketResponse.Builder responseBuilder = ModifyTicketResponse.newBuilder();

        try (Connection conn = DriverManager.getConnection(DB_PATH)) {

            Ticket existingTicket = getTicketFromDatabase(conn, request.getTicketId());

            if (existingTicket == null || !existingTicket.getUserId().equals(request.getUserId())) {
                responseBuilder.setSuccess(false).setMessage("Biglietto non trovato o accesso negato.");
                responseObserver.onNext(responseBuilder.build());
                responseObserver.onCompleted();
                return;
            }

            double additionalCharge = 0.0;
            boolean modified = false;
            Ticket.Builder modifiedTicketBuilder = existingTicket.toBuilder();

            // Handle date change
            if (request.hasNewTravelDate()) {
                // In a real system, this would involve complex rebooking logic
                additionalCharge += 15.0; // Real change fee
                modified = true;
                logger.info("Date change requested for ticket: " + request.getTicketId());
            }

            // Handle service class change
            if (request.hasNewServiceClass() && !request.getNewServiceClass().equals(existingTicket.getTrainDetails().getServiceClass())) {
                double currentPrice = existingTicket.getTrainDetails().getPrice();
                double newPrice = calculatePriceForServiceClass(request.getNewServiceClass());
                additionalCharge += Math.max(0, newPrice - currentPrice);

                modifiedTicketBuilder.setTrainDetails(
                        existingTicket.getTrainDetails().toBuilder()
                                .setServiceClass(request.getNewServiceClass())
                                .setPrice(newPrice)
                                .build());
                modified = true;
                logger.info("Service class change requested for ticket: " + request.getTicketId());
            }

            if (modified) {
                if (additionalCharge > 0 && (request.getPaymentMethodTokenForDiff() == null || request.getPaymentMethodTokenForDiff().isEmpty())) {
                    responseBuilder.setSuccess(false)
                            .setMessage("Pagamento aggiuntivo richiesto: €" + String.format("%.2f", additionalCharge));
                } else {
                    // Process additional payment if needed
                    if (additionalCharge > 0) {
                        boolean paymentSuccessful = processPayment(request.getPaymentMethodTokenForDiff(), additionalCharge);
                        if (!paymentSuccessful) {
                            responseBuilder.setSuccess(false).setMessage("Pagamento aggiuntivo fallito.");
                            responseObserver.onNext(responseBuilder.build());
                            responseObserver.onCompleted();
                            return;
                        }
                    }

                    Ticket finalTicket = modifiedTicketBuilder.setStatus("MODIFIED").build();
                    updateTicketInDatabase(conn, finalTicket);

                    responseBuilder.setSuccess(true)
                            .setModifiedTicket(finalTicket)
                            .setNewPrice(additionalCharge)
                            .setMessage("Biglietto modificato con successo.");
                }
            } else {
                responseBuilder.setSuccess(false).setMessage("Nessuna modifica specificata.");
            }

        } catch (SQLException e) {
            logger.severe("Database error during ticket modification: " + e.getMessage());
            responseBuilder.setSuccess(false).setMessage("Errore del database durante la modifica.");
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getMyTickets(UserRequest request, StreamObserver<TicketListResponse> responseObserver) {
        logger.info("Received getMyTickets request for user: " + request.getUserId());

        try (Connection conn = DriverManager.getConnection(DB_PATH)) {
            List<Ticket> userTickets = getTicketsForUser(conn, request.getUserId());

            TicketListResponse response = TicketListResponse.newBuilder()
                    .addAllTickets(userTickets)
                    .build();

            logger.info("Returning " + userTickets.size() + " tickets for user: " + request.getUserId());
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (SQLException e) {
            logger.severe("Database error getting user tickets: " + e.getMessage());
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Errore del database")
                    .asRuntimeException());
        }
    }

    // Helper methods

    private Train getRealTrainFromGTFS(Connection conn, String tripId) throws SQLException {
        String sql = """
            SELECT 
                t.trip_id,
                t.trip_short_name,
                r.route_short_name,
                r.route_long_name,
                dep_st.stop_id AS dep_stop_id,
                dep_st.stop_name AS dep_station_name,
                arr_st.stop_id AS arr_stop_id,
                arr_st.stop_name AS arr_station_name,
                dep.departure_time,
                arr.arrival_time
            FROM trips t
            JOIN routes r ON t.route_id = r.route_id
            JOIN stop_times dep ON t.trip_id = dep.trip_id 
                AND dep.stop_sequence = (SELECT MIN(stop_sequence) FROM stop_times WHERE trip_id = t.trip_id)
            JOIN stop_times arr ON t.trip_id = arr.trip_id 
                AND arr.stop_sequence = (SELECT MAX(stop_sequence) FROM stop_times WHERE trip_id = t.trip_id)
            JOIN stations dep_st ON dep.stop_id = dep_st.stop_id
            JOIN stations arr_st ON arr.stop_id = arr_st.stop_id
            WHERE t.trip_id = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tripId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Station departureStation = Station.newBuilder()
                        .setId(rs.getString("dep_stop_id"))
                        .setName(rs.getString("dep_station_name"))
                        .build();

                Station arrivalStation = Station.newBuilder()
                        .setId(rs.getString("arr_stop_id"))
                        .setName(rs.getString("arr_station_name"))
                        .build();

                String routeShortName = rs.getString("route_short_name");
                String serviceClass = determineServiceClass(routeShortName);
                double price = calculateRealPrice(routeShortName);

                return Train.newBuilder()
                        .setId(tripId)
                        .setTrainNumber(rs.getString("trip_short_name"))
                        .setDepartureStation(departureStation)
                        .setArrivalStation(arrivalStation)
                        .setDepartureTime(convertTimeStringToTimestamp(rs.getString("departure_time")))
                        .setArrivalTime(convertTimeStringToTimestamp(rs.getString("arrival_time")))
                        .setServiceClass(serviceClass)
                        .setPrice(price)
                        .setAvailableSeats(getRealTrainCapacity(serviceClass))
                        .setTrainType(getTrainType(routeShortName))
                        .build();
            }
        }
        return null;
    }

    private int getTicketCountForTrip(Connection conn, String tripId, String serviceClass) throws SQLException {
        String sql = "SELECT COUNT(*) FROM tickets WHERE trip_id = ? AND service_class = ? AND status != 'CANCELLED'";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tripId);
            pstmt.setString(2, serviceClass);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private void saveTicketToDatabase(Connection conn, Ticket ticket, String tripId) throws SQLException {
        String sql = """
            INSERT INTO tickets (ticket_id, user_id, trip_id, seat_number, purchase_date, status, service_class, price)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticket.getId());
            pstmt.setString(2, ticket.getUserId());
            pstmt.setString(3, tripId);
            pstmt.setString(4, ticket.getSeatNumber());
            pstmt.setLong(5, ticket.getPurchaseDate().getSeconds());
            pstmt.setString(6, ticket.getStatus());
            pstmt.setString(7, ticket.getTrainDetails().getServiceClass());
            pstmt.setDouble(8, ticket.getTrainDetails().getPrice());

            pstmt.executeUpdate();
        }
    }

    private Ticket getTicketFromDatabase(Connection conn, String ticketId) throws SQLException {
        String sql = """
            SELECT t.*, tr.trip_short_name, tr.trip_headsign,
                   dep_st.stop_name AS dep_station_name,
                   arr_st.stop_name AS arr_station_name,
                   dep.departure_time, arr.arrival_time
            FROM tickets t
            JOIN trips tr ON t.trip_id = tr.trip_id
            JOIN stop_times dep ON tr.trip_id = dep.trip_id 
                AND dep.stop_sequence = (SELECT MIN(stop_sequence) FROM stop_times WHERE trip_id = tr.trip_id)
            JOIN stop_times arr ON tr.trip_id = arr.trip_id 
                AND arr.stop_sequence = (SELECT MAX(stop_sequence) FROM stop_times WHERE trip_id = tr.trip_id)
            JOIN stations dep_st ON dep.stop_id = dep_st.stop_id
            JOIN stations arr_st ON arr.stop_id = arr_st.stop_id
            WHERE t.ticket_id = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticketId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Station departureStation = Station.newBuilder()
                        .setName(rs.getString("dep_station_name"))
                        .build();

                Station arrivalStation = Station.newBuilder()
                        .setName(rs.getString("arr_station_name"))
                        .build();

                Train trainDetails = Train.newBuilder()
                        .setId(rs.getString("trip_id"))
                        .setTrainNumber(rs.getString("trip_short_name"))
                        .setDepartureStation(departureStation)
                        .setArrivalStation(arrivalStation)
                        .setDepartureTime(convertTimeStringToTimestamp(rs.getString("departure_time")))
                        .setArrivalTime(convertTimeStringToTimestamp(rs.getString("arrival_time")))
                        .setServiceClass(rs.getString("service_class"))
                        .setPrice(rs.getDouble("price"))
                        .build();

                return Ticket.newBuilder()
                        .setId(rs.getString("ticket_id"))
                        .setUserId(rs.getString("user_id"))
                        .setTrainDetails(trainDetails)
                        .setSeatNumber(rs.getString("seat_number"))
                        .setPurchaseDate(Timestamp.newBuilder().setSeconds(rs.getLong("purchase_date")).build())
                        .setStatus(rs.getString("status"))
                        .build();
            }
        }
        return null;
    }

    private void updateTicketInDatabase(Connection conn, Ticket ticket) throws SQLException {
        String sql = "UPDATE tickets SET status = ?, service_class = ?, price = ? WHERE ticket_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticket.getStatus());
            pstmt.setString(2, ticket.getTrainDetails().getServiceClass());
            pstmt.setDouble(3, ticket.getTrainDetails().getPrice());
            pstmt.setString(4, ticket.getId());

            pstmt.executeUpdate();
        }
    }

    private List<Ticket> getTicketsForUser(Connection conn, String userId) throws SQLException {
        List<Ticket> tickets = new ArrayList<>();

        String sql = """
            SELECT t.*, tr.trip_short_name, tr.trip_headsign,
                   dep_st.stop_name AS dep_station_name,
                   arr_st.stop_name AS arr_station_name,
                   dep.departure_time, arr.arrival_time
            FROM tickets t
            JOIN trips tr ON t.trip_id = tr.trip_id
            JOIN stop_times dep ON tr.trip_id = dep.trip_id 
                AND dep.stop_sequence = (SELECT MIN(stop_sequence) FROM stop_times WHERE trip_id = tr.trip_id)
            JOIN stop_times arr ON tr.trip_id = arr.trip_id 
                AND arr.stop_sequence = (SELECT MAX(stop_sequence) FROM stop_times WHERE trip_id = tr.trip_id)
            JOIN stations dep_st ON dep.stop_id = dep_st.stop_id
            JOIN stations arr_st ON arr.stop_id = arr_st.stop_id
            WHERE t.user_id = ?
            ORDER BY t.purchase_date DESC
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Station departureStation = Station.newBuilder()
                        .setName(rs.getString("dep_station_name"))
                        .build();

                Station arrivalStation = Station.newBuilder()
                        .setName(rs.getString("arr_station_name"))
                        .build();

                Train trainDetails = Train.newBuilder()
                        .setId(rs.getString("trip_id"))
                        .setTrainNumber(rs.getString("trip_short_name"))
                        .setDepartureStation(departureStation)
                        .setArrivalStation(arrivalStation)
                        .setDepartureTime(convertTimeStringToTimestamp(rs.getString("departure_time")))
                        .setArrivalTime(convertTimeStringToTimestamp(rs.getString("arrival_time")))
                        .setServiceClass(rs.getString("service_class"))
                        .setPrice(rs.getDouble("price"))
                        .build();

                Ticket ticket = Ticket.newBuilder()
                        .setId(rs.getString("ticket_id"))
                        .setUserId(rs.getString("user_id"))
                        .setTrainDetails(trainDetails)
                        .setSeatNumber(rs.getString("seat_number"))
                        .setPurchaseDate(Timestamp.newBuilder().setSeconds(rs.getLong("purchase_date")).build())
                        .setStatus(rs.getString("status"))
                        .build();

                tickets.add(ticket);
            }
        }

        return tickets;
    }

    // Utility methods

    private boolean processPayment(String paymentToken, double amount) {
        // Simulate real payment processing
        if (paymentToken == null || paymentToken.isEmpty()) {
            return false;
        }

        // In a real system, integrate with payment providers like Stripe, PayPal, etc.
        logger.info("Processing payment: €" + String.format("%.2f", amount) + " with token: " + paymentToken);

        // Simulate success (in reality, call payment API)
        return !paymentToken.equals("INVALID_TOKEN");
    }

    private String generateRealSeatNumber(String serviceClass, int seatIndex) {
        // Generate realistic seat numbers based on Italian train layout
        switch (serviceClass.toUpperCase()) {
            case "EXECUTIVE":
            case "BUSINESS":
                // Premium seats: 1-2A, 1-2B format
                int row = (seatIndex / 2) + 1;
                char seat = (seatIndex % 2 == 0) ? 'A' : 'B';
                return row + "" + seat;

            case "PREMIUM":
            case "SMART":
                // 4-seat rows: 1A, 1B, 1C, 1D
                int premiumRow = (seatIndex / 4) + 1;
                char[] premiumSeats = {'A', 'B', 'C', 'D'};
                char premiumSeat = premiumSeats[seatIndex % 4];
                return premiumRow + "" + premiumSeat;

            default:
                // Standard 6-seat rows
                int standardRow = (seatIndex / 6) + 1;
                char[] standardSeats = {'A', 'B', 'C', 'D', 'E', 'F'};
                char standardSeat = standardSeats[seatIndex % 6];
                return standardRow + "" + standardSeat;
        }
    }

    private String determineServiceClass(String routeShortName) {
        if (routeShortName == null) return "2ª Classe";

        switch (routeShortName.toUpperCase()) {
            case "FRECCIAROSSA": return "Executive";
            case "FRECCIARGENTO": return "Business";
            case "FRECCIABIANCA": return "Premium";
            case "ITALO": return "Smart";
            case "IC":
            case "ICN": return "1ª Classe";
            default: return "2ª Classe";
        }
    }

    private double calculateRealPrice(String routeShortName) {
        if (routeShortName == null) return 15.00;

        switch (routeShortName.toUpperCase()) {
            case "FRECCIAROSSA": return 89.90;
            case "FRECCIARGENTO": return 69.90;
            case "FRECCIABIANCA": return 59.90;
            case "ITALO": return 79.90;
            case "IC": return 39.50;
            case "ICN": return 45.00;
            default: return 8.50;
        }
    }

    private double calculatePriceForServiceClass(String serviceClass) {
        switch (serviceClass.toUpperCase()) {
            case "EXECUTIVE": return 89.90;
            case "BUSINESS": return 69.90;
            case "PREMIUM": return 59.90;
            case "SMART": return 79.90;
            case "1ª CLASSE": return 39.50;
            default: return 8.50;
        }
    }

    private int getRealTrainCapacity(String serviceClass) {
        switch (serviceClass.toUpperCase()) {
            case "EXECUTIVE": return 485; // ETR 1000
            case "BUSINESS": return 460; // ETR 600
            case "PREMIUM": return 380; // ETR 460
            case "SMART": return 450; // AGV Italo
            case "1ª CLASSE": return 250; // IC
            default: return 150; // Regional
        }
    }

    private String getTrainType(String routeShortName) {
        if (routeShortName == null) return "Regionale";

        switch (routeShortName.toUpperCase()) {
            case "FRECCIAROSSA":
            case "FRECCIARGENTO":
            case "FRECCIABIANCA":
            case "ITALO":
                return "Alta Velocità";
            case "IC":
            case "ICN":
                return "Intercity";
            default:
                return "Regionale";
        }
    }

    private Timestamp convertTimeStringToTimestamp(String timeStr) {
        try {
            // Convert GTFS time (HH:MM:SS) to timestamp for today
            String[] parts = timeStr.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);

            // Handle times >= 24:00:00 (next day)
            if (hours >= 24) {
                hours -= 24;
            }

            Instant now = Instant.now();
            // For simplicity, use current date with the GTFS time
            long epochSeconds = now.getEpochSecond() + (hours * 3600) + (minutes * 60) + seconds;

            return Timestamp.newBuilder()
                    .setSeconds(epochSeconds)
                    .setNanos(0)
                    .build();
        } catch (Exception e) {
            logger.warning("Error converting time string: " + timeStr);
            return Timestamp.newBuilder()
                    .setSeconds(Instant.now().getEpochSecond())
                    .build();
        }
    }
}