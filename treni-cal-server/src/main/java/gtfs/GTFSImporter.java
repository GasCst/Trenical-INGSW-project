package gtfs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class GTFSImporter {

    // MODIFICA QUESTO PERCORSO per farlo corrispondere alla tua struttura
    // Deve puntare alla cartella che contiene i tuoi file .txt
    private static final String GTFS_DIRECTORY_PATH = "treni-cal-server/src/main/java/gtfs";

    // Questo sarà il nome del file del database che verrà creato
    private static final String DB_PATH = "trenical.db";

    public static void main(String[] args) {
        // Stabiliamo la connessione al database SQLite
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            if (conn != null) {
                System.out.println("Connessione a SQLite stabilita. Verrà creato il file " + DB_PATH);

                // 1. Creiamo le tabelle
                createTables(conn);

                importStops(conn);
                importAgencies(conn);
                importRoutes(conn);
                importCalendar(conn);
                importCalendarDates(conn);
                importTrips(conn);
                importStopTimes(conn);

                System.out.println("Importazione completata con successo!");
            }
        } catch (SQLException e) {
            System.err.println("Errore SQL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createTables(Connection conn) throws SQLException {
        String[] createTableStatements = {
                // Stations table (from gtfs_stops.txt)
                "CREATE TABLE IF NOT EXISTS stations (" +
                        "stop_id TEXT PRIMARY KEY, " +
                        "stop_code TEXT, " +
                        "stop_name TEXT NOT NULL, " +
                        "stop_desc TEXT, " +
                        "stop_lat REAL, " +
                        "stop_lon REAL, " +
                        "zone_id TEXT, " +
                        "stop_url TEXT, " +
                        "location_type INTEGER, " +
                        "parent_station TEXT, " +
                        "stop_timezone TEXT, " +
                        "wheelchair_boarding INTEGER);",

                // Agencies table (from gtfs_agency.txt)
                "CREATE TABLE IF NOT EXISTS agencies (" +
                        "agency_id TEXT PRIMARY KEY, " +
                        "agency_name TEXT NOT NULL, " +
                        "agency_url TEXT, " +
                        "agency_timezone TEXT, " +
                        "agency_lang TEXT, " +
                        "agency_phone TEXT);",

                // Routes table (from gtfs_routes.txt)
                "CREATE TABLE IF NOT EXISTS routes (" +
                        "route_id TEXT PRIMARY KEY, " +
                        "agency_id TEXT, " +
                        "route_short_name TEXT, " +
                        "route_long_name TEXT, " +
                        "route_desc TEXT, " +
                        "route_type INTEGER, " +
                        "route_url TEXT, " +
                        "route_color TEXT, " +
                        "route_text_color TEXT, " +
                        "FOREIGN KEY(agency_id) REFERENCES agencies(agency_id));",

                // Calendar table (from gtfs_calendar.txt)
                "CREATE TABLE IF NOT EXISTS calendar (" +
                        "service_id TEXT PRIMARY KEY, " +
                        "monday INTEGER, " +
                        "tuesday INTEGER, " +
                        "wednesday INTEGER, " +
                        "thursday INTEGER, " +
                        "friday INTEGER, " +
                        "saturday INTEGER, " +
                        "sunday INTEGER, " +
                        "start_date TEXT, " +
                        "end_date TEXT);",

                // Calendar dates table (from gtfs_calendar_dates.txt)
                "CREATE TABLE IF NOT EXISTS calendar_dates (" +
                        "service_id TEXT, " +
                        "date TEXT, " +
                        "exception_type INTEGER, " +
                        "PRIMARY KEY (service_id, date));",

                // Trips table (from gtfs_trips.txt)
                "CREATE TABLE IF NOT EXISTS trips (" +
                        "trip_id TEXT PRIMARY KEY, " +
                        "route_id TEXT, " +
                        "service_id TEXT, " +
                        "trip_headsign TEXT, " +
                        "trip_short_name TEXT, " +
                        "direction_id INTEGER, " +
                        "block_id TEXT, " +
                        "shape_id TEXT, " +
                        "wheelchair_accessible INTEGER, " +
                        "bikes_allowed INTEGER, " +
                        "FOREIGN KEY(route_id) REFERENCES routes(route_id), " +
                        "FOREIGN KEY(service_id) REFERENCES calendar(service_id));",

                // Stop times table (from gtfs_stop_times.txt)
                "CREATE TABLE IF NOT EXISTS stop_times (" +
                        "trip_id TEXT, " +
                        "arrival_time TEXT, " +
                        "departure_time TEXT, " +
                        "stop_id TEXT, " +
                        "stop_sequence INTEGER, " +
                        "stop_headsign TEXT, " +
                        "pickup_type INTEGER, " +
                        "drop_off_type INTEGER, " +
                        "shape_dist_traveled REAL, " +
                        "PRIMARY KEY (trip_id, stop_sequence), " +
                        "FOREIGN KEY(trip_id) REFERENCES trips(trip_id), " +
                        "FOREIGN KEY(stop_id) REFERENCES stations(stop_id));"
        };

        try (Statement stmt = conn.createStatement()) {
            for (String sql : createTableStatements) {
                stmt.execute(sql);
            }
            System.out.println("Tabelle create (o già esistenti).");
        }
    }

    private static void importStops(Connection conn) {
        String sql = "INSERT INTO stations(stop_id, stop_code, stop_name, stop_desc, stop_lat, stop_lon, zone_id, stop_url, location_type, parent_station, stop_timezone, wheelchair_boarding) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, "gtfs_stops.txt", sql, 12);
    }

    private static void importAgencies(Connection conn) {
        String sql = "INSERT INTO agencies(agency_id, agency_name, agency_url, agency_timezone, agency_lang, agency_phone) VALUES(?,?,?,?,?,?)";
        importFromFile(conn, "gtfs_agency.txt", sql, 6);
    }

    private static void importRoutes(Connection conn) {
        String sql = "INSERT INTO routes(route_id, agency_id, route_short_name, route_long_name, route_desc, route_type, route_url, route_color, route_text_color) VALUES(?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, "gtfs_routes.txt", sql, 9);
    }

    private static void importCalendar(Connection conn) {
        String sql = "INSERT INTO calendar(service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday, start_date, end_date) VALUES(?,?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, "gtfs_calendar.txt", sql, 10);
    }

    private static void importCalendarDates(Connection conn) {
        String sql = "INSERT INTO calendar_dates(service_id, date, exception_type) VALUES(?,?,?)";
        importFromFile(conn, "gtfs_calendar_dates.txt", sql, 3);
    }

    private static void importTrips(Connection conn) {
        String sql = "INSERT INTO trips(route_id, service_id, trip_id, trip_headsign, trip_short_name, direction_id, block_id, shape_id, wheelchair_accessible, bikes_allowed) VALUES(?,?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, "gtfs_trips.txt", sql, 10);
    }

    private static void importStopTimes(Connection conn) {
        String sql = "INSERT INTO stop_times(trip_id, arrival_time, departure_time, stop_id, stop_sequence, stop_headsign, pickup_type, drop_off_type, shape_dist_traveled) VALUES(?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, "gtfs_stop_times.txt", sql, 9);
    }

    private static void importFromFile(Connection conn, String fileName, String sql, int columnCount) {
        File file = new File(GTFS_DIRECTORY_PATH, fileName);
        if (!file.exists()) {
            System.err.println("ATTENZIONE: Il file " + fileName + " non è stato trovato nel percorso specificato. Salto l'importazione.");
            return;
        }

        String line = "";
        int batchSize = 1000;
        int count = 0;

        try {
            conn.setAutoCommit(false);

            try (BufferedReader br = new BufferedReader(new FileReader(file));
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                br.readLine(); // Skip header row

                while ((line = br.readLine()) != null) {
                    String[] values = line.split(",", -1);
                    if (values.length >= columnCount) {
                        for (int i = 0; i < columnCount; i++) {
                            String value = values[i].trim();
                            // Handle empty values for numeric columns
                            if (value.isEmpty()) {
                                pstmt.setString(i + 1, null);
                            } else {
                                pstmt.setString(i + 1, value);
                            }
                        }
                        pstmt.addBatch();
                        count++;
                    }
                    if (count % batchSize == 0) {
                        pstmt.executeBatch();
                        System.out.println("Inserite " + count + " righe in " + fileName.split("\\.")[0]);
                    }
                }
                pstmt.executeBatch();
            }

            conn.commit();
            System.out.println("Importazione di " + fileName + " completata. Totale righe: " + count);

        } catch (IOException | SQLException e) {
            System.err.println("Errore durante l'importazione di " + fileName + " alla riga: " + line);
            e.printStackTrace();
            try {
                conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}