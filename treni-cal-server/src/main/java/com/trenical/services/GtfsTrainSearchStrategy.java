package com.trenical.services;

import com.google.protobuf.Timestamp;
import proto.SearchTrainRequest;
import proto.Station;
import proto.Train;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.*;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


public class GtfsTrainSearchStrategy implements TrainSearchStrategy {

    private static final String DB_PATH;

    static {

        URL resource = GtfsTrainSearchStrategy.class.getClassLoader().getResource("treni-cal-server/trenical.db");
        if (resource != null) {
            try {
                DB_PATH = "jdbc:sqlite:" + new File(resource.toURI()).getPath();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        } else {

            throw new RuntimeException("Database file not found in the classpath!");
        }
    }

    private static final Logger logger = Logger.getLogger(GtfsTrainSearchStrategy.class.getName());

    @Override
    public List<Train> searchTrains(SearchTrainRequest request) throws Exception {
        List<Train> foundTrains = new ArrayList<>();
        LocalTime currentTime = LocalTime.now();
        LocalDate currentDate = LocalDate.now();

        String sql = "SELECT DISTINCT " +
                "    t.trip_id, " +
                "    t.trip_short_name, " +
                "    r.route_id, " +
                "    r.route_short_name, " +
                "    r.route_long_name, " +
                "    r.route_desc, " +
                "    a.agency_name, " +
                "    dep_st.stop_id AS departure_stop_id, " +
                "    dep_st.stop_name AS departure_station_name, " +
                "    arr_st.stop_id AS arrival_stop_id, " +
                "    arr_st.stop_name AS arrival_station_name, " +
                "    dep.departure_time, " +
                "    arr.arrival_time, " +
                "    t.trip_headsign, " +
                "    t.service_id, " +
                "    c.monday, c.tuesday, c.wednesday, c.thursday, c.friday, c.saturday, c.sunday " +
                "FROM stop_times AS dep " +
                "JOIN stop_times AS arr ON dep.trip_id = arr.trip_id " +
                "JOIN trips AS t ON dep.trip_id = t.trip_id " +
                "JOIN routes AS r ON t.route_id = r.route_id " +
                "JOIN agencies AS a ON r.agency_id = a.agency_id " +
                "JOIN stations AS dep_st ON dep.stop_id = dep_st.stop_id " +
                "JOIN stations AS arr_st ON arr.stop_id = arr_st.stop_id " +
                "LEFT JOIN calendar AS c ON t.service_id = c.service_id " +
                "WHERE dep.stop_id = ? AND arr.stop_id = ? " +
                "AND dep.stop_sequence < arr.stop_sequence " +
                "ORDER BY dep.departure_time";




        try (Connection conn = DriverManager.getConnection(DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, request.getDepartureStation().getId());
            pstmt.setString(2, request.getArrivalStation().getId());
            ResultSet rs = pstmt.executeQuery();
            int totalFound = 0;
            int filtered = 0;

            while (rs.next()) {
                totalFound++;


                if (!isTrainOperatingToday(rs, currentDate)) {
                    continue;
                }

                String departureTimeStr = rs.getString("departure_time");
                LocalTime departureTime = parseGTFSTime(departureTimeStr);


                boolean includeThisTrain = false;
                if (departureTime.isAfter(currentTime)) {
                    includeThisTrain = true;
                } else {

                    Duration timeSinceDepart = Duration.between(departureTime, currentTime);
                    if (timeSinceDepart.toHours() <= 2) {
                        includeThisTrain = true;
                    }
                }

                if (!includeThisTrain) {
                    filtered++;
                    continue;
                }

                Station departureStation = Station.newBuilder()
                        .setId(rs.getString("departure_stop_id"))
                        .setName(rs.getString("departure_station_name"))
                        .build();

                Station arrivalStation = Station.newBuilder()
                        .setId(rs.getString("arrival_stop_id"))
                        .setName(rs.getString("arrival_station_name"))
                        .build();

                Timestamp departureTimestamp = convertTimeToTimestamp(departureTimeStr, currentDate);
                Timestamp arrivalTimestamp = convertTimeToTimestamp(rs.getString("arrival_time"), currentDate);


                String tripShortName = rs.getString("trip_short_name");
                String cleanTrainNumber = extractCleanTrainNumber(tripShortName);


                if (cleanTrainNumber == null) {
                    cleanTrainNumber = tripShortName != null ? tripShortName : "N/A";
                    System.out.println("Could not extract clean train number from: " + tripShortName +
                            ", using: " + cleanTrainNumber);
                }


                String routeShortName = rs.getString("route_short_name");
                String serviceClass = determineServiceClass(routeShortName, rs.getString("route_long_name"));
                double price = calculateRealPrice(routeShortName, rs.getString("route_desc"));


                int availableSeats = getRealAvailableSeats(routeShortName);

                Train train = Train.newBuilder()
                        .setId(rs.getString("trip_id"))
                        .setTrainNumber(cleanTrainNumber)
                        .setDepartureStation(departureStation)
                        .setArrivalStation(arrivalStation)
                        .setDepartureTime(departureTimestamp)
                        .setArrivalTime(arrivalTimestamp)
                        .setServiceClass(serviceClass)
                        .setPrice(price)
                        .setAvailableSeats(availableSeats)
                        .setTrainType(getTrainType(routeShortName))
                        .build();
                foundTrains.add(train);
            }
        }

        return foundTrains;
    }


    private boolean isTrainOperatingToday(ResultSet rs, LocalDate date) throws SQLException {
        DayOfWeek dayOfWeek = date.getDayOfWeek();

        switch (dayOfWeek) {
            case MONDAY: return rs.getInt("monday") == 1;
            case TUESDAY: return rs.getInt("tuesday") == 1;
            case WEDNESDAY: return rs.getInt("wednesday") == 1;
            case THURSDAY: return rs.getInt("thursday") == 1;
            case FRIDAY: return rs.getInt("friday") == 1;
            case SATURDAY: return rs.getInt("saturday") == 1;
            case SUNDAY: return rs.getInt("sunday") == 1;
            default: return false;
        }
    }

    private LocalTime parseGTFSTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            logger.warning("Empty time string, using current time");
            return LocalTime.now();
        }

        try {
            String[] parts = timeStr.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);


            if (hours >= 24) {
                hours -= 24;
            }

            return LocalTime.of(hours, minutes, seconds);
        } catch (Exception e) {
            logger.warning("Error parsing time: " + timeStr + " - " + e.getMessage());
            return LocalTime.now();
        }
    }


    private Timestamp convertTimeToTimestamp(String timeStr, LocalDate date) {
        try {
            LocalTime time = parseGTFSTime(timeStr);
            LocalDateTime dateTime = LocalDateTime.of(date, time);
            ZonedDateTime zdt = ZonedDateTime.of(dateTime, ZoneId.of("Europe/Rome"));

            return Timestamp.newBuilder()
                    .setSeconds(zdt.toEpochSecond())
                    .setNanos(zdt.getNano())
                    .build();
        } catch (Exception e) {
            logger.warning("Error parsing time: " + timeStr + " - " + e.getMessage());
            Instant now = Instant.now();
            return Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();
        }
    }


    private String extractCleanTrainNumber(String tripShortName) {
        if (tripShortName == null || tripShortName.trim().isEmpty()) {
            return null;
        }

        String trimmed = tripShortName.trim();
        logger.fine("Extracting train number from: '" + trimmed + "'");


        String[] parts = trimmed.split("\\s+");
        for (String part : parts) {

            if (part.matches("\\d{3,5}")) {
                logger.fine("Extracted train number: " + part + " from: " + trimmed);
                return part;
            }
        }


        String numbers = trimmed.replaceAll("[^0-9]", "");
        if (numbers.length() >= 3 && numbers.length() <= 5) {
            logger.fine("Extracted train number from cleanup: " + numbers + " from: " + trimmed);
            return numbers;
        }


        if (trimmed.matches("[A-Z]{1,3}\\d{3,5}")) {
            String extracted = trimmed.replaceAll("^[A-Z]+", "");
            if (extracted.length() >= 3 && extracted.length() <= 5) {
                logger.fine("Extracted from pattern: " + extracted + " from: " + trimmed);
                return extracted;
            }
        }

        logger.warning("Could not extract valid train number from: '" + trimmed + "'");
        return null;
    }


    private String determineServiceClass(String routeShortName, String routeLongName) {
        if (routeShortName == null) return "Standard";


        switch (routeShortName.toUpperCase()) {
            case "FRECCIAROSSA":
                return "Executive";
            case "FRECCIARGENTO":
                return "Business";
            case "FRECCIABIANCA":
                return "Premium";
            case "ITALO":
                return "Smart";
            case "IC":
            case "ICN":
                return "1ª Classe";
            default:
                return "2ª Classe";
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



    private double calculateRealPrice(String routeShortName, String routeDesc) {
        if (routeShortName == null) return 15.00;


        switch (routeShortName.toUpperCase()) {
            case "FRECCIAROSSA":
                return 89.90;
            case "FRECCIARGENTO":
                return 69.90;
            case "FRECCIABIANCA":
                return 59.90;
            case "ITALO":
                return 79.90;
            case "IC":
                return 39.50;
            case "ICN":
                return 45.00;
            case "REG":
            case "FL1": case "FL2": case "FL3": case "FL4": case "FL5": case "FL6": case "FL7": case "FL8":
            case "S1": case "S2": case "S3": case "S4": case "S5": case "S6": case "S7": case "S8":
            case "S9": case "S10": case "S11": case "S12": case "S13":
            case "SFM1": case "SFM2": case "SFM3": case "SFM4":
                return 8.50;
            default:
                return 20.00;
        }
    }


    private int getRealAvailableSeats(String routeShortName) {
        if (routeShortName == null) return 100;


        switch (routeShortName.toUpperCase()) {
            case "FRECCIAROSSA":
                return 485;
            case "FRECCIARGENTO":
                return 460;
            case "FRECCIABIANCA":
                return 380;
            case "ITALO":
                return 450;
            case "IC":
                return 250;
            case "ICN":
                return 200;
            default:
                return 150;
        }
    }

}

