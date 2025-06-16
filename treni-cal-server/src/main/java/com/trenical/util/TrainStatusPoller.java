package com.trenical.util;

import com.trenical.rubyViaggiatreno.RubyViaggiatrenoClient;
import com.trenical.observer.NotificationEngine;
import ruby_viaggiatreno_microservizio.TrainStatusResponse;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;


public class TrainStatusPoller implements Runnable {

    private static final Logger logger = Logger.getLogger(TrainStatusPoller.class.getName());

    private final RubyViaggiatrenoClient rubyClient;
    private final NotificationEngine notificationEngine;
    private static final String DB_PATH = "jdbc:sqlite:../../trenical.db";

    private int pollCount = 0;
    private final Set<String> realTrainNumbers = new HashSet<>();

    public TrainStatusPoller(RubyViaggiatrenoClient rubyClient, NotificationEngine notificationEngine) {
        this.rubyClient = rubyClient;
        this.notificationEngine = notificationEngine;
        logger.info("TrainStatusPoller initialized - will monitor ONLY REAL trains from GTFS data");
    }

    @Override
    public void run() {
        pollCount++;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.nnnnnnn"));
        System.out.println("[Poller] Running status check at " + timestamp);

        try {

            Set<String> subscribedTripIds = notificationEngine.getSubscribedTrainIds();

            if (subscribedTripIds.isEmpty()) {
                System.out.println("[Poller] No active subscriptions to poll.");
                return;
            }

            logger.info("Polling " + subscribedTripIds.size() + " real trips with active subscriptions");


            try (Connection conn = DriverManager.getConnection(DB_PATH)) {
                for (String tripId : subscribedTripIds) {
                    pollRealTrainStatus(conn, tripId);
                }
            }

        } catch (Exception e) {
            logger.severe("Error during polling cycle " + pollCount + ": " + e.getMessage());
            System.err.println("[Poller] Error during status check: " + e.getMessage());
        }
    }


    private void pollRealTrainStatus(Connection conn, String tripId) {
        try {

            String realTrainNumber = getRealTrainNumberFromTrip(conn, tripId);

            if (realTrainNumber == null) {
                logger.warning("Could not find real train number for trip: " + tripId);
                return;
            }


            if (!realTrainNumber.matches("\\d{3,5}")) {
                logger.warning("Invalid train number format: " + realTrainNumber + " for trip: " + tripId);
                return;
            }


            TrainStatusResponse status = rubyClient.getTrainStatus(realTrainNumber);

            if (status == null) {
                logger.warning("No response from Viaggiatreno for train: " + realTrainNumber);
                return;
            }


            realTrainNumbers.add(realTrainNumber);


            boolean statusChanged = notificationEngine.hasStatusChanged(realTrainNumber, status);

            if (statusChanged) {
                logger.info("Status change detected for REAL train " + realTrainNumber + ": " + status.getTrainStatusDescription());
                System.out.println("[Poller] Real train " + realTrainNumber + " status changed: " + status.getTrainStatusDescription());


                notificationEngine.updateAndNotifyObservers(tripId, status);
            } else {
                logger.fine("No status change for train " + realTrainNumber);
            }

        } catch (SQLException e) {
            logger.severe("Database error polling trip " + tripId + ": " + e.getMessage());
        } catch (Exception e) {
            logger.warning("Error polling train status for trip " + tripId + ": " + e.getMessage());
        }
    }


    private String getRealTrainNumberFromTrip(Connection conn, String tripId) throws SQLException {
        String sql = """
            SELECT 
                t.trip_short_name,
                r.route_short_name,
                r.route_long_name
            FROM trips t
            JOIN routes r ON t.route_id = r.route_id
            WHERE t.trip_id = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tripId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String tripShortName = rs.getString("trip_short_name");
                String routeShortName = rs.getString("route_short_name");

                logger.fine("Found trip: " + tripId + " -> " + tripShortName + " on route: " + routeShortName);


                String cleanTrainNumber = extractCleanTrainNumber(tripShortName);

                if (cleanTrainNumber != null) {
                    logger.fine("Extracted train number: " + cleanTrainNumber + " from trip: " + tripId);
                    return cleanTrainNumber;
                } else {
                    logger.warning("Could not extract valid train number from: " + tripShortName);
                    return null;
                }
            }
        }

        logger.warning("Trip not found in database: " + tripId);
        return null;
    }


    private String extractCleanTrainNumber(String tripShortName) {
        if (tripShortName == null || tripShortName.trim().isEmpty()) {
            return null;
        }

        String trimmed = tripShortName.trim();


        String[] parts = trimmed.split("\\s+");
        for (String part : parts) {

            if (part.matches("\\d{3,5}")) {
                return part;
            }
        }


        String numbers = trimmed.replaceAll("[^0-9]", "");
        if (numbers.length() >= 3 && numbers.length() <= 5) {
            return numbers;
        }


        if (trimmed.matches("[A-Z]{1,3}\\d{3,5}")) {
            String extracted = trimmed.replaceAll("^[A-Z]+", "");
            if (extracted.length() >= 3 && extracted.length() <= 5) {
                return extracted;
            }
        }

        return null;
    }


    public void logPollingStatistics() {
        logger.info("Polling Statistics:");
        logger.info("  - Poll cycles completed: " + pollCount);
        logger.info("  - Real train numbers monitored: " + realTrainNumbers.size());
        logger.info("  - Active subscriptions: " + notificationEngine.getSubscribedTrainIds().size());

        if (!realTrainNumbers.isEmpty()) {
            logger.info("  - Real trains being tracked: " + String.join(", ", realTrainNumbers));
        }
    }


    public boolean hasActiveRealTrains() {
        return !realTrainNumbers.isEmpty();
    }


    public Set<String> getMonitoredRealTrains() {
        return new HashSet<>(realTrainNumbers);
    }


    public boolean validateRealTrainNumbers() {
        for (String trainNumber : realTrainNumbers) {
            if (!trainNumber.matches("\\d{3,5}")) {
                logger.warning("Invalid real train number detected: " + trainNumber);
                return false;
            }
        }
        return true;
    }
}