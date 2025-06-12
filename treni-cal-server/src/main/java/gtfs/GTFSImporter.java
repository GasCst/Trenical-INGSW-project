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
import java.util.logging.Logger;
import java.util.logging.Level;

public class GTFSImporter {
    private static final Logger logger = Logger.getLogger(GTFSImporter.class.getName());

    // MODIFICA QUESTO PERCORSO per farlo corrispondere alla tua struttura
    private static final String GTFS_DIRECTORY_PATH = "treni-cal-server/src/main/java/gtfs";
    private static final String DB_PATH = "trenical.db";

    public static void main(String[] args) {
        logger.info("Starting GTFS Import Process...");

        // Check if GTFS directory exists
        File gtfsDir = new File(GTFS_DIRECTORY_PATH);
        if (!gtfsDir.exists() || !gtfsDir.isDirectory()) {
            logger.severe("GTFS directory not found: " + GTFS_DIRECTORY_PATH);
            System.err.println("ERROR: GTFS directory not found at: " + GTFS_DIRECTORY_PATH);
            return;
        }

        // List GTFS files
        String[] gtfsFiles = {
                "gtfs_agency.txt", "gtfs_calendar.txt", "gtfs_calendar_dates.txt",
                "gtfs_feed_info.txt", "gtfs_routes.txt", "gtfs_stops.txt",
                "gtfs_stop_times.txt", "gtfs_trips.txt"
        };

        int foundFiles = 0;
        logger.info("Found " + foundFiles + " GTFS files:");
        for (String fileName : gtfsFiles) {
            File file = new File(gtfsDir, fileName);
            if (file.exists()) {
                foundFiles++;
                logger.info("  - " + fileName);
            }
        }

        // Stabiliamo la connessione al database SQLite
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            if (conn != null) {
                logger.info("SQLite connection established. Database: " + DB_PATH);
                System.out.println("Connessione a SQLite stabilita. Verrà creato/aggiornato il file " + DB_PATH);

                // Enable optimizations
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = OFF");  // Disable for import
                    stmt.execute("PRAGMA journal_mode = WAL");
                    stmt.execute("PRAGMA synchronous = NORMAL");
                    stmt.execute("PRAGMA cache_size = -64000");
                }

                // 1. Create tables matching your GTFS structure
                createTables(conn);

                // 2. Import data in the correct order
                importAgencies(conn);
                importStops(conn);
                importRoutes(conn);
                importCalendar(conn);
                importCalendarDates(conn);
                importTrips(conn);
                importStopTimes(conn);

                // 3. Create indexes for performance
                createIndexes(conn);

                // 4. Analyze database
                analyzeDatabase(conn);

                System.out.println("✅ Importazione GTFS completata con successo!");
                logger.info("GTFS import completed successfully!");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error during import", e);
            System.err.println("❌ Errore SQL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createTables(Connection conn) throws SQLException {
        logger.info("Creating database tables...");

        String[] createTableStatements = {
                // Drop existing tables first
                "DROP TABLE IF EXISTS stop_times",
                "DROP TABLE IF EXISTS trips",
                "DROP TABLE IF EXISTS calendar_dates",
                "DROP TABLE IF EXISTS calendar",
                "DROP TABLE IF EXISTS routes",
                "DROP TABLE IF EXISTS stations",
                "DROP TABLE IF EXISTS agencies",

                // Agencies table - matches your 6 columns
                "CREATE TABLE agencies (" +
                        "agency_id TEXT PRIMARY KEY, " +
                        "agency_name TEXT NOT NULL, " +
                        "agency_url TEXT, " +
                        "agency_timezone TEXT NOT NULL, " +
                        "agency_lang TEXT, " +
                        "agency_phone TEXT" +
                        ")",

                // Stations table - matches your 12 columns
                "CREATE TABLE stations (" +
                        "stop_id TEXT PRIMARY KEY, " +
                        "stop_code TEXT, " +
                        "stop_name TEXT NOT NULL, " +
                        "stop_desc TEXT, " +
                        "stop_lat REAL, " +
                        "stop_lon REAL, " +
                        "zone_id TEXT, " +
                        "stop_url TEXT, " +
                        "location_type INTEGER DEFAULT 0, " +
                        "parent_station TEXT, " +
                        "stop_timezone TEXT, " +
                        "wheelchair_boarding INTEGER DEFAULT 0" +
                        ")",

                // Routes table - matches your 9 columns
                "CREATE TABLE routes (" +
                        "route_id TEXT PRIMARY KEY, " +
                        "agency_id TEXT, " +
                        "route_short_name TEXT, " +
                        "route_long_name TEXT, " +
                        "route_desc TEXT, " +
                        "route_type INTEGER NOT NULL, " +
                        "route_url TEXT, " +
                        "route_color TEXT DEFAULT 'FFFFFF', " +
                        "route_text_color TEXT DEFAULT '000000'" +
                        ")",

                // Calendar table - should work with your existing structure
                "CREATE TABLE calendar (" +
                        "service_id TEXT PRIMARY KEY, " +
                        "monday INTEGER NOT NULL CHECK (monday IN (0,1)), " +
                        "tuesday INTEGER NOT NULL CHECK (tuesday IN (0,1)), " +
                        "wednesday INTEGER NOT NULL CHECK (wednesday IN (0,1)), " +
                        "thursday INTEGER NOT NULL CHECK (thursday IN (0,1)), " +
                        "friday INTEGER NOT NULL CHECK (friday IN (0,1)), " +
                        "saturday INTEGER NOT NULL CHECK (saturday IN (0,1)), " +
                        "sunday INTEGER NOT NULL CHECK (sunday IN (0,1)), " +
                        "start_date TEXT NOT NULL, " +
                        "end_date TEXT NOT NULL" +
                        ")",

                // Calendar dates table
                "CREATE TABLE calendar_dates (" +
                        "service_id TEXT NOT NULL, " +
                        "date TEXT NOT NULL, " +
                        "exception_type INTEGER NOT NULL CHECK (exception_type IN (1,2)), " +
                        "PRIMARY KEY (service_id, date)" +
                        ")",

                // Trips table - simplified (no shapes reference)
                "CREATE TABLE trips (" +
                        "trip_id TEXT PRIMARY KEY, " +
                        "route_id TEXT NOT NULL, " +
                        "service_id TEXT NOT NULL, " +
                        "trip_headsign TEXT, " +
                        "trip_short_name TEXT, " +
                        "direction_id INTEGER CHECK (direction_id IN (0,1)), " +
                        "block_id TEXT, " +
                        "shape_id TEXT, " +
                        "wheelchair_accessible INTEGER DEFAULT 0, " +
                        "bikes_allowed INTEGER DEFAULT 0" +
                        ")",

                // Stop times table - matches your 9 columns
                "CREATE TABLE stop_times (" +
                        "trip_id TEXT NOT NULL, " +
                        "arrival_time TEXT NOT NULL, " +
                        "departure_time TEXT NOT NULL, " +
                        "stop_id TEXT NOT NULL, " +
                        "stop_sequence INTEGER NOT NULL, " +
                        "stop_headsign TEXT, " +
                        "pickup_type INTEGER DEFAULT 0, " +
                        "drop_off_type INTEGER DEFAULT 0, " +
                        "shape_dist_traveled REAL, " +
                        "PRIMARY KEY (trip_id, stop_sequence)" +
                        ")"
        };

        try (Statement stmt = conn.createStatement()) {
            for (String sql : createTableStatements) {
                stmt.execute(sql);
            }
            logger.info("Database tables created successfully");
            System.out.println("✅ Tabelle create (o già esistenti).");
        }
    }

    private static void importAgencies(Connection conn) {
        // Matches your 6 columns: agency_id,agency_name,agency_url,agency_timezone,agency_lang,agency_phone
        String sql = "INSERT OR REPLACE INTO agencies(agency_id, agency_name, agency_url, agency_timezone, agency_lang, agency_phone) VALUES(?,?,?,?,?,?)";
        importFromFile(conn, "gtfs_agency.txt", sql, 6, "agencies");
    }

    private static void importStops(Connection conn) {
        // Matches your 12 columns: stop_id,stop_code,stop_name,stop_desc,stop_lat,stop_lon,zone_id,stop_url,location_type,parent_station,stop_timezone,wheelchair_boarding
        String sql = "INSERT OR REPLACE INTO stations(stop_id, stop_code, stop_name, stop_desc, stop_lat, stop_lon, zone_id, stop_url, location_type, parent_station, stop_timezone, wheelchair_boarding) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, "gtfs_stops.txt", sql, 12, "stations");
    }

    private static void importRoutes(Connection conn) {
        // Matches your 9 columns: route_id,agency_id,route_short_name,route_long_name,route_desc,route_type,route_url,route_color,route_text_color
        String sql = "INSERT OR REPLACE INTO routes(route_id, agency_id, route_short_name, route_long_name, route_desc, route_type, route_url, route_color, route_text_color) VALUES(?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, "gtfs_routes.txt", sql, 9, "routes");
    }

    private static void importCalendar(Connection conn) {
        String sql = "INSERT OR REPLACE INTO calendar(service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday, start_date, end_date) VALUES(?,?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, "gtfs_calendar.txt", sql, 10, "calendar");
    }

    private static void importCalendarDates(Connection conn) {
        String sql = "INSERT OR REPLACE INTO calendar_dates(service_id, date, exception_type) VALUES(?,?,?)";
        importFromFile(conn, "gtfs_calendar_dates.txt", sql, 3, "calendar_dates");
    }

    private static void importTrips(Connection conn) {
        String sql = "INSERT OR REPLACE INTO trips(route_id, service_id, trip_id, trip_headsign, trip_short_name, direction_id, block_id, shape_id, wheelchair_accessible, bikes_allowed) VALUES(?,?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, "gtfs_trips.txt", sql, 10, "trips");
    }

    private static void importStopTimes(Connection conn) {
        // Matches your 9 columns: trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled
        String sql = "INSERT OR REPLACE INTO stop_times(trip_id, arrival_time, departure_time, stop_id, stop_sequence, stop_headsign, pickup_type, drop_off_type, shape_dist_traveled) VALUES(?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, "gtfs_stop_times.txt", sql, 9, "stop_times");
    }

    private static void importFromFile(Connection conn, String fileName, String sql, int expectedColumns, String tableName) {
        File file = new File(GTFS_DIRECTORY_PATH, fileName);
        if (!file.exists()) {
            logger.warning("GTFS file not found: " + fileName + " - Skipping import for " + tableName);
            System.out.println("⚠️  File " + fileName + " non trovato. Saltando importazione per " + tableName);
            return;
        }

        logger.info("Importing " + fileName + " into " + tableName);
        logger.info("Header for " + fileName + ": " + getFileHeader(file));
        System.out.println("📥 Importando " + fileName + " in tabella " + tableName + "...");

        String line = "";
        int batchSize = 1000;
        int count = 0;
        int errors = 0;

        try {
            conn.setAutoCommit(false);

            try (BufferedReader br = new BufferedReader(new FileReader(file));
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                String headerLine = br.readLine(); // Skip header row
                if (headerLine != null) {
                    System.out.println("   📋 Header: " + headerLine);
                }

                while ((line = br.readLine()) != null) {
                    try {
                        // Simple CSV parsing - split by comma
                        String[] values = line.split(",", -1);

                        if (values.length >= expectedColumns) {
                            for (int i = 0; i < expectedColumns; i++) {
                                String value = (i < values.length) ? values[i].trim() : "";

                                // Handle empty values
                                if (value.isEmpty() || value.equals("\"\"")) {
                                    pstmt.setString(i + 1, null);
                                } else {
                                    // Remove quotes if present
                                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                                        value = value.substring(1, value.length() - 1);
                                    }
                                    pstmt.setString(i + 1, value);
                                }
                            }
                            pstmt.addBatch();
                            count++;
                        } else {
                            logger.warning("Skipping line with insufficient columns (" + values.length + "/" + expectedColumns + "): " + line.substring(0, Math.min(100, line.length())));
                            errors++;
                        }

                        if (count % batchSize == 0) {
                            pstmt.executeBatch();
                            System.out.println("  💾 Inserite " + count + " righe in " + tableName);
                        }
                    } catch (Exception e) {
                        logger.warning("Error processing line in " + fileName + ": " + line + " - " + e.getMessage());
                        errors++;
                    }
                }

                pstmt.executeBatch();
            }

            conn.commit();
            logger.info("Import completed for " + tableName + ": " + count + " rows, " + errors + " errors");
            System.out.println("✅ Importazione di " + fileName + " completata. Righe: " + count + ", Errori: " + errors);

        } catch (IOException | SQLException e) {
            logger.log(Level.SEVERE, "Error importing " + fileName, e);
            System.err.println("❌ Errore durante l'importazione di " + fileName + ": " + e.getMessage());
            try {
                conn.rollback();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Error rolling back transaction", ex);
            }
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error resetting auto-commit", e);
            }
        }
    }

    private static String getFileHeader(File file) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            return br.readLine();
        } catch (IOException e) {
            return "Unable to read header";
        }
    }

    private static void createIndexes(Connection conn) throws SQLException {
        logger.info("Creating database indexes for performance...");

        String[] indexStatements = {
                // Essential indexes only
                "CREATE INDEX IF NOT EXISTS idx_stations_name ON stations(stop_name)",
                "CREATE INDEX IF NOT EXISTS idx_stations_code ON stations(stop_code)",
                "CREATE INDEX IF NOT EXISTS idx_routes_short_name ON routes(route_short_name)",
                "CREATE INDEX IF NOT EXISTS idx_trips_route ON trips(route_id)",
                "CREATE INDEX IF NOT EXISTS idx_trips_service ON trips(service_id)",
                "CREATE INDEX IF NOT EXISTS idx_trips_short_name ON trips(trip_short_name)",
                "CREATE INDEX IF NOT EXISTS idx_stop_times_trip ON stop_times(trip_id)",
                "CREATE INDEX IF NOT EXISTS idx_stop_times_stop ON stop_times(stop_id)",
                "CREATE INDEX IF NOT EXISTS idx_stop_times_sequence ON stop_times(trip_id, stop_sequence)"
        };

        try (Statement stmt = conn.createStatement()) {
            for (String sql : indexStatements) {
                stmt.execute(sql);
            }
            logger.info("Database indexes created successfully");
            System.out.println("✅ Indici del database creati.");
        }
    }

    private static void analyzeDatabase(Connection conn) throws SQLException {
        logger.info("Analyzing database...");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ANALYZE");

            String[] tables = {"agencies", "stations", "routes", "calendar", "trips", "stop_times"};
            System.out.println("\n📊 Statistiche Database:");

            for (String table : tables) {
                try {
                    var rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        System.out.println("   📋 " + table + ": " + count + " righe");
                        logger.info("Table " + table + ": " + count + " rows");
                    }
                } catch (SQLException e) {
                    logger.warning("Could not get count for table " + table + ": " + e.getMessage());
                }
            }

            System.out.println("✅ Analisi database completata.");
            logger.info("Database analysis completed");
        }
    }
}