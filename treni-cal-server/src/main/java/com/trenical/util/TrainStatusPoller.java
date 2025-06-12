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

/**
 * TrainStatusPoller - Monitora solo treni REALI dal database GTFS
 * Non usa più dati mock, interroga solo viaggiatreno per treni veri
 */
public class TrainStatusPoller implements Runnable {

    private static final Logger logger = Logger.getLogger(TrainStatusPoller.class.getName());

    private final RubyViaggiatrenoClient rubyClient;
    private final NotificationEngine notificationEngine;
    private static final String DB_PATH = "jdbc:sqlite:trenical.db";

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
            // Get all REAL trip IDs that have active subscriptions
            Set<String> subscribedTripIds = notificationEngine.getSubscribedTrainIds();

            if (subscribedTripIds.isEmpty()) {
                System.out.println("[Poller] No active subscriptions to poll.");
                return;
            }

            logger.info("Polling " + subscribedTripIds.size() + " real trips with active subscriptions");

            // For each subscribed trip, get the real train number and check status
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

    /**
     * Poll status for a real train from GTFS data
     */
    private void pollRealTrainStatus(Connection conn, String tripId) {
        try {
            // Get real train number from GTFS database
            String realTrainNumber = getRealTrainNumberFromTrip(conn, tripId);

            if (realTrainNumber == null) {
                logger.warning("Could not find real train number for trip: " + tripId);
                return;
            }

            // Check if this is a valid train number (3-5 digits)
            if (!realTrainNumber.matches("\\d{3,5}")) {
                logger.warning("Invalid train number format: " + realTrainNumber + " for trip: " + tripId);
                return;
            }

            // Query Viaggiatreno API for REAL train status
            TrainStatusResponse status = rubyClient.getTrainStatus(realTrainNumber);

            if (status == null) {
                logger.warning("No response from Viaggiatreno for train: " + realTrainNumber);
                return;
            }

            // Track real train numbers we're monitoring
            realTrainNumbers.add(realTrainNumber);

            // Check if status has changed for this real train
            boolean statusChanged = notificationEngine.hasStatusChanged(realTrainNumber, status);

            if (statusChanged) {
                logger.info("Status change detected for REAL train " + realTrainNumber + ": " + status.getTrainStatusDescription());
                System.out.println("[Poller] Real train " + realTrainNumber + " status changed: " + status.getTrainStatusDescription());

                // Notify all observers of this real trip
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

    /**
     * Extract real train number from GTFS trip data
     */
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

                // Extract clean train number using the same logic as TrainServiceImpl
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

    /**
     * Extract clean train number for Viaggiatreno API
     * Same logic as TrainServiceImpl to ensure consistency
     */
    private String extractCleanTrainNumber(String tripShortName) {
        if (tripShortName == null || tripShortName.trim().isEmpty()) {
            return null;
        }

        String trimmed = tripShortName.trim();

        // Extract pure numbers for Viaggiatreno API compatibility
        // Examples: "FR 9001" -> "9001", "IC 501" -> "501", "NTV 9701" -> "9701"
        String[] parts = trimmed.split("\\s+");
        for (String part : parts) {
            // Must be 3-5 digits for valid train number
            if (part.matches("\\d{3,5}")) {
                return part;
            }
        }

        // If no pure number found, extract numbers from the string
        String numbers = trimmed.replaceAll("[^0-9]", "");
        if (numbers.length() >= 3 && numbers.length() <= 5) {
            return numbers;
        }

        // Try alternative patterns for edge cases
        // Pattern: Letters followed by numbers (e.g., "FR9001", "IC501")
        if (trimmed.matches("[A-Z]{1,3}\\d{3,5}")) {
            String extracted = trimmed.replaceAll("^[A-Z]+", "");
            if (extracted.length() >= 3 && extracted.length() <= 5) {
                return extracted;
            }
        }

        return null;
    }

    /**
     * Get statistics about real trains being monitored
     */
    public void logPollingStatistics() {
        logger.info("Polling Statistics:");
        logger.info("  - Poll cycles completed: " + pollCount);
        logger.info("  - Real train numbers monitored: " + realTrainNumbers.size());
        logger.info("  - Active subscriptions: " + notificationEngine.getSubscribedTrainIds().size());

        if (!realTrainNumbers.isEmpty()) {
            logger.info("  - Real trains being tracked: " + String.join(", ", realTrainNumbers));
        }
    }

    /**
     * Check if we're monitoring any real trains
     */
    public boolean hasActiveRealTrains() {
        return !realTrainNumbers.isEmpty();
    }

    /**
     * Get set of real train numbers currently being monitored
     */
    public Set<String> getMonitoredRealTrains() {
        return new HashSet<>(realTrainNumbers);
    }

    /**
     * Validate that all monitored trains are real train numbers
     */
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