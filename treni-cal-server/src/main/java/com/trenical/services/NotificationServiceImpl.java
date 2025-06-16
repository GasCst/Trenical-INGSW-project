package com.trenical.services;

import io.grpc.stub.StreamObserver;
import proto.*;
import proto.Treni.*;
import com.trenical.observer.TripObserver;
import com.trenical.observer.NotificationEngine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

public class NotificationServiceImpl extends NotificationServiceGrpc.NotificationServiceImplBase {

    private static final Logger logger = Logger.getLogger(NotificationServiceImpl.class.getName());
    private final NotificationEngine notificationEngine = NotificationEngine.getInstance();
    private static final String DB_PATH = "jdbc:sqlite:../../trenical.db";

    public NotificationServiceImpl() {
        logger.info("NotificationServiceImpl initialized with REAL data only");
    }

    @Override
    public void subscribeToTripChanges(TripSubscriptionRequest request, StreamObserver<TripChangeNotification> responseObserver) {
        logger.info("User " + request.getUserId() + " subscribed to updates for ticket " + request.getTicketId());

        try (Connection conn = DriverManager.getConnection(DB_PATH)) {


            Ticket ticket = getRealTicketFromDatabase(conn, request.getTicketId(), request.getUserId());

            if (ticket == null) {
                logger.warning("Ticket not found or access denied for ticket: " + request.getTicketId() + ", user: " + request.getUserId());
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Biglietto non trovato o accesso negato.")
                        .asRuntimeException());
                return;
            }


            String realTripId = ticket.getTrainDetails().getId();
            logger.info("Creating subscription for real trip: " + realTripId);


            TripObserver tripObserver = new TripObserver(responseObserver, request.getTicketId());
            notificationEngine.addObserver(realTripId, tripObserver);

            logger.info("Successfully created subscription for ticket " + request.getTicketId() + " on real trip " + realTripId);

        } catch (SQLException e) {
            logger.severe("Database error during subscription: " + e.getMessage());
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Errore del database durante la sottoscrizione.")
                    .asRuntimeException());
        } catch (Exception e) {
            logger.severe("Unexpected error during subscription: " + e.getMessage());
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Errore imprevisto durante la sottoscrizione.")
                    .asRuntimeException());
        }
    }


    private Ticket getRealTicketFromDatabase(Connection conn, String ticketId, String userId) throws SQLException {
        String sql = """
            SELECT 
                t.ticket_id,
                t.user_id,
                t.trip_id,
                t.seat_number,
                t.purchase_date,
                t.status,
                t.service_class,
                t.price,
                tr.trip_short_name,
                tr.trip_headsign,
                r.route_short_name,
                r.route_long_name,
                dep_st.stop_id AS dep_stop_id,
                dep_st.stop_name AS dep_station_name,
                arr_st.stop_id AS arr_stop_id,
                arr_st.stop_name AS arr_station_name,
                dep.departure_time,
                arr.arrival_time
            FROM tickets t
            JOIN trips tr ON t.trip_id = tr.trip_id
            JOIN routes r ON tr.route_id = r.route_id
            JOIN stop_times dep ON tr.trip_id = dep.trip_id 
                AND dep.stop_sequence = (SELECT MIN(stop_sequence) FROM stop_times WHERE trip_id = tr.trip_id)
            JOIN stop_times arr ON tr.trip_id = arr.trip_id 
                AND arr.stop_sequence = (SELECT MAX(stop_sequence) FROM stop_times WHERE trip_id = tr.trip_id)
            JOIN stations dep_st ON dep.stop_id = dep_st.stop_id
            JOIN stations arr_st ON arr.stop_id = arr_st.stop_id
            WHERE t.ticket_id = ? AND t.user_id = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticketId);
            pstmt.setString(2, userId);

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


                Train trainDetails = Train.newBuilder()
                        .setId(rs.getString("trip_id"))
                        .setTrainNumber(rs.getString("trip_short_name"))
                        .setDepartureStation(departureStation)
                        .setArrivalStation(arrivalStation)
                        .setDepartureTime(convertTimeStringToTimestamp(rs.getString("departure_time")))
                        .setArrivalTime(convertTimeStringToTimestamp(rs.getString("arrival_time")))
                        .setServiceClass(rs.getString("service_class"))
                        .setPrice(rs.getDouble("price"))
                        .setTrainType(determineTrainType(rs.getString("route_short_name")))
                        .build();


                Ticket realTicket = Ticket.newBuilder()
                        .setId(rs.getString("ticket_id"))
                        .setUserId(rs.getString("user_id"))
                        .setTrainDetails(trainDetails)
                        .setSeatNumber(rs.getString("seat_number"))
                        .setPurchaseDate(com.google.protobuf.Timestamp.newBuilder()
                                .setSeconds(rs.getLong("purchase_date"))
                                .build())
                        .setStatus(rs.getString("status"))
                        .build();

                logger.info("Found real ticket: " + ticketId + " for trip: " + rs.getString("trip_id") +
                        " (" + rs.getString("trip_short_name") + ") from " +
                        rs.getString("dep_station_name") + " to " + rs.getString("arr_station_name"));

                return realTicket;
            }
        }

        return null;
    }


    private String determineTrainType(String routeShortName) {
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
            case "REG":
            case "FL1": case "FL2": case "FL3": case "FL4": case "FL5": case "FL6": case "FL7": case "FL8":
            case "S1": case "S2": case "S3": case "S4": case "S5": case "S6": case "S7": case "S8":
            case "S9": case "S10": case "S11": case "S12": case "S13":
            case "SFM1": case "SFM2": case "SFM3": case "SFM4":
                return "Regionale";
            default:
                return "Regionale";
        }
    }


    private com.google.protobuf.Timestamp convertTimeStringToTimestamp(String timeStr) {
        try {

            String[] parts = timeStr.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);


            if (hours >= 24) {
                hours -= 24;
            }


            java.time.Instant now = java.time.Instant.now();
            long epochSeconds = now.getEpochSecond() + (hours * 3600) + (minutes * 60) + seconds;

            return com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(epochSeconds)
                    .setNanos(0)
                    .build();
        } catch (Exception e) {
            logger.warning("Error converting time string: " + timeStr + " - " + e.getMessage());
            return com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(java.time.Instant.now().getEpochSecond())
                    .build();
        }
    }
}