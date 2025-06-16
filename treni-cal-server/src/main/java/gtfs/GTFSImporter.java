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
import java.sql.ResultSet;
import java.util.logging.Logger;
import java.util.logging.Level;

public class GTFSImporter {
    private static final Logger logger = Logger.getLogger(GTFSImporter.class.getName());


    private static final String GTFS_DIRECTORY_PATH = "treni-cal-server/src/main/java/gtfs";
    private static final String DB_PATH = "trenical.db";

    public static void main(String[] args) {
        logger.info("Starting GTFS Import Process...");


        File gtfsDir = new File(GTFS_DIRECTORY_PATH);
        if (!gtfsDir.exists() || !gtfsDir.isDirectory()) {

            String[] alternativePaths = {
                    "src/main/java/gtfs",
                    "gtfs",
                    "./gtfs",
                    "../gtfs"
            };

            boolean found = false;
            for (String altPath : alternativePaths) {
                File altDir = new File(altPath);
                if (altDir.exists() && altDir.isDirectory()) {
                    System.out.println("Found GTFS directory at: " + altPath);
                    gtfsDir = altDir;
                    found = true;
                    break;
                }
            }

            if (!found) {
                logger.severe("GTFS directory not found at any of the expected locations");
                System.err.println("ERROR: GTFS directory not found. Tried:");
                System.err.println("  - " + GTFS_DIRECTORY_PATH);
                for (String path : alternativePaths) {
                    System.err.println("  - " + path);
                }
                return;
            }
        }


        String[] gtfsFiles = {
                "gtfs_agency.txt", "gtfs_calendar.txt", "gtfs_calendar_dates.txt",
                "gtfs_feed_info.txt", "gtfs_routes.txt", "gtfs_stops.txt",
                "gtfs_stop_times.txt", "gtfs_trips.txt"
        };

        int foundFiles = 0;
        System.out.println("Checking for GTFS files in: " + gtfsDir.getAbsolutePath());
        for (String fileName : gtfsFiles) {
            File file = new File(gtfsDir, fileName);
            if (file.exists()) {
                foundFiles++;
                System.out.println("  ✓ " + fileName + " (" + file.length() + " bytes)");
            } else {
                System.out.println("  ✗ " + fileName + " (missing)");
            }
        }

        if (foundFiles == 0) {
            System.err.println("No GTFS files found! Please check the directory path.");
            return;
        }


        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            if (conn != null) {
                logger.info("SQLite connection established. Database: " + DB_PATH);
                System.out.println("Connected to SQLite database: " + DB_PATH);


                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = OFF");  // Disable for import
                    stmt.execute("PRAGMA journal_mode = WAL");
                    stmt.execute("PRAGMA synchronous = NORMAL");
                    stmt.execute("PRAGMA cache_size = -64000"); // 64MB cache
                    stmt.execute("PRAGMA temp_store = MEMORY");
                }


                if (isDatabaseAlreadyPopulated(conn)) {
                    System.out.println("Database already contains GTFS data.");
                    System.out.print("Do you want to recreate the database? (y/n): ");


                    boolean recreate = true;

                    if (!recreate) {
                        System.out.println("Skipping import. Using existing database.");
                        analyzeDatabase(conn);
                        return;
                    }
                }


                createTables(conn);


                System.out.println("\n📥 Starting GTFS data import...");
                importAgencies(conn, gtfsDir);
                importStops(conn, gtfsDir);
                importRoutes(conn, gtfsDir);
                importCalendar(conn, gtfsDir);
                importCalendarDates(conn, gtfsDir);
                importTrips(conn, gtfsDir);
                importStopTimes(conn, gtfsDir);


                createIndexes(conn);


                verifyImport(conn);
                analyzeDatabase(conn);

                System.out.println("\n✅ GTFS import completed successfully!");
                logger.info("GTFS import completed successfully!");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error during import", e);
            System.err.println("❌ SQL Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean isDatabaseAlreadyPopulated(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='stop_times'");
            if (rs.next()) {

                ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM stop_times");
                if (countRs.next()) {
                    return countRs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {

            return false;
        }
        return false;
    }

    private static void createTables(Connection conn) throws SQLException {
        logger.info("Creating database tables...");
        System.out.println("🔧 Creating database tables...");

        String[] createTableStatements = {

                "DROP TABLE IF EXISTS stop_times",
                "DROP TABLE IF EXISTS trips",
                "DROP TABLE IF EXISTS calendar_dates",
                "DROP TABLE IF EXISTS calendar",
                "DROP TABLE IF EXISTS routes",
                "DROP TABLE IF EXISTS stations",
                "DROP TABLE IF EXISTS agencies",


                """
                CREATE TABLE agencies (
                    agency_id TEXT PRIMARY KEY,
                    agency_name TEXT NOT NULL,
                    agency_url TEXT,
                    agency_timezone TEXT NOT NULL,
                    agency_lang TEXT,
                    agency_phone TEXT
                )""",


                """
                CREATE TABLE stations (
                    stop_id TEXT PRIMARY KEY,
                    stop_code TEXT,
                    stop_name TEXT NOT NULL,
                    stop_desc TEXT,
                    stop_lat REAL,
                    stop_lon REAL,
                    zone_id TEXT,
                    stop_url TEXT,
                    location_type INTEGER DEFAULT 0,
                    parent_station TEXT,
                    stop_timezone TEXT,
                    wheelchair_boarding INTEGER DEFAULT 0
                )""",


                """
                CREATE TABLE routes (
                    route_id TEXT PRIMARY KEY,
                    agency_id TEXT,
                    route_short_name TEXT,
                    route_long_name TEXT,
                    route_desc TEXT,
                    route_type INTEGER NOT NULL,
                    route_url TEXT,
                    route_color TEXT DEFAULT 'FFFFFF',
                    route_text_color TEXT DEFAULT '000000',
                    FOREIGN KEY (agency_id) REFERENCES agencies(agency_id)
                )""",


                """
                CREATE TABLE calendar (
                    service_id TEXT PRIMARY KEY,
                    monday INTEGER NOT NULL CHECK (monday IN (0,1)),
                    tuesday INTEGER NOT NULL CHECK (tuesday IN (0,1)),
                    wednesday INTEGER NOT NULL CHECK (wednesday IN (0,1)),
                    thursday INTEGER NOT NULL CHECK (thursday IN (0,1)),
                    friday INTEGER NOT NULL CHECK (friday IN (0,1)),
                    saturday INTEGER NOT NULL CHECK (saturday IN (0,1)),
                    sunday INTEGER NOT NULL CHECK (sunday IN (0,1)),
                    start_date TEXT NOT NULL,
                    end_date TEXT NOT NULL
                )""",


                """
                CREATE TABLE calendar_dates (
                    service_id TEXT NOT NULL,
                    date TEXT NOT NULL,
                    exception_type INTEGER NOT NULL CHECK (exception_type IN (1,2)),
                    PRIMARY KEY (service_id, date)
                )""",


                """
                CREATE TABLE trips (
                    trip_id TEXT PRIMARY KEY,
                    route_id TEXT NOT NULL,
                    service_id TEXT NOT NULL,
                    trip_headsign TEXT,
                    trip_short_name TEXT,
                    direction_id INTEGER CHECK (direction_id IN (0,1)),
                    block_id TEXT,
                    shape_id TEXT,
                    wheelchair_accessible INTEGER DEFAULT 0,
                    bikes_allowed INTEGER DEFAULT 0,
                    FOREIGN KEY (route_id) REFERENCES routes(route_id),
                    FOREIGN KEY (service_id) REFERENCES calendar(service_id)
                )""",


                """
                CREATE TABLE stop_times (
                    trip_id TEXT NOT NULL,
                    arrival_time TEXT NOT NULL,
                    departure_time TEXT NOT NULL,
                    stop_id TEXT NOT NULL,
                    stop_sequence INTEGER NOT NULL,
                    stop_headsign TEXT,
                    pickup_type INTEGER DEFAULT 0,
                    drop_off_type INTEGER DEFAULT 0,
                    shape_dist_traveled REAL,
                    PRIMARY KEY (trip_id, stop_sequence),
                    FOREIGN KEY (trip_id) REFERENCES trips(trip_id),
                    FOREIGN KEY (stop_id) REFERENCES stations(stop_id)
                )"""
        };

        try (Statement stmt = conn.createStatement()) {
            for (String sql : createTableStatements) {
                logger.fine("Executing: " + sql.substring(0, Math.min(sql.length(), 50)) + "...");
                stmt.execute(sql);
            }

            logger.info("Database tables created successfully");
            System.out.println("✅ Database tables created successfully");
        }
    }

    private static void importAgencies(Connection conn, File gtfsDir) {
        String sql = "INSERT OR REPLACE INTO agencies(agency_id, agency_name, agency_url, agency_timezone, agency_lang, agency_phone) VALUES(?,?,?,?,?,?)";
        importFromFile(conn, gtfsDir, "gtfs_agency.txt", sql, 6, "agencies");
    }

    private static void importStops(Connection conn, File gtfsDir) {
        String sql = "INSERT OR REPLACE INTO stations(stop_id, stop_code, stop_name, stop_desc, stop_lat, stop_lon, zone_id, stop_url, location_type, parent_station, stop_timezone, wheelchair_boarding) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, gtfsDir, "gtfs_stops.txt", sql, 12, "stations");
    }

    private static void importRoutes(Connection conn, File gtfsDir) {
        String sql = "INSERT OR REPLACE INTO routes(route_id, agency_id, route_short_name, route_long_name, route_desc, route_type, route_url, route_color, route_text_color) VALUES(?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, gtfsDir, "gtfs_routes.txt", sql, 9, "routes");
    }

    private static void importCalendar(Connection conn, File gtfsDir) {
        String sql = "INSERT OR REPLACE INTO calendar(service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday, start_date, end_date) VALUES(?,?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, gtfsDir, "gtfs_calendar.txt", sql, 10, "calendar");
    }

    private static void importCalendarDates(Connection conn, File gtfsDir) {
        String sql = "INSERT OR REPLACE INTO calendar_dates(service_id, date, exception_type) VALUES(?,?,?)";
        importFromFile(conn, gtfsDir, "gtfs_calendar_dates.txt", sql, 3, "calendar_dates");
    }

    private static void importTrips(Connection conn, File gtfsDir) {
        String sql = "INSERT OR REPLACE INTO trips(route_id, service_id, trip_id, trip_headsign, trip_short_name, direction_id, block_id, shape_id, wheelchair_accessible, bikes_allowed) VALUES(?,?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, gtfsDir, "gtfs_trips.txt", sql, 10, "trips");
    }

    private static void importStopTimes(Connection conn, File gtfsDir) {
        String sql = "INSERT OR REPLACE INTO stop_times(trip_id, arrival_time, departure_time, stop_id, stop_sequence, stop_headsign, pickup_type, drop_off_type, shape_dist_traveled) VALUES(?,?,?,?,?,?,?,?,?)";
        importFromFile(conn, gtfsDir, "gtfs_stop_times.txt", sql, 9, "stop_times");
    }

    private static void importFromFile(Connection conn, File gtfsDir, String fileName, String sql, int expectedColumns, String tableName) {
        File file = new File(gtfsDir, fileName);
        if (!file.exists()) {
            logger.warning("GTFS file not found: " + fileName + " - Skipping import for " + tableName);
            System.out.println("⚠️  File " + fileName + " not found. Skipping import for " + tableName);
            return;
        }

        logger.info("Importing " + fileName + " into " + tableName);
        System.out.println("📥 Importing " + fileName + " → " + tableName + "...");

        int count = 0;
        int errors = 0;
        int batchSize = 1000;

        try {
            conn.setAutoCommit(false);

            try (BufferedReader br = new BufferedReader(new FileReader(file));
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                String headerLine = br.readLine(); // Skip header
                if (headerLine != null) {
                    System.out.println("   📋 Header: " + headerLine);
                }

                String line;
                while ((line = br.readLine()) != null) {
                    try {

                        String[] values = parseCsvLine(line);

                        if (values.length >= expectedColumns) {
                            for (int i = 0; i < expectedColumns; i++) {
                                String value = (i < values.length) ? values[i].trim() : "";


                                if (value.isEmpty() || value.equals("\"\"")) {
                                    pstmt.setNull(i + 1, java.sql.Types.VARCHAR);
                                } else {

                                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                                        value = value.substring(1, value.length() - 1);
                                    }
                                    pstmt.setString(i + 1, value);
                                }
                            }
                            pstmt.addBatch();
                            count++;

                            if (count % batchSize == 0) {
                                pstmt.executeBatch();
                                System.out.print(".");
                                if (count % (batchSize * 10) == 0) {
                                    System.out.println(" " + count + " rows");
                                }
                            }
                        } else {
                            logger.warning("Skipping line with insufficient columns (" + values.length + "/" + expectedColumns + "): " + line.substring(0, Math.min(100, line.length())));
                            errors++;
                        }
                    } catch (Exception e) {
                        logger.warning("Error processing line: " + e.getMessage());
                        errors++;
                    }
                }


                pstmt.executeBatch();
            }

            conn.commit();
            logger.info("Import completed for " + tableName + ": " + count + " rows, " + errors + " errors");
            System.out.println("\n✅ " + fileName + " imported: " + count + " rows" + (errors > 0 ? ", " + errors + " errors" : ""));

        } catch (IOException | SQLException e) {
            logger.log(Level.SEVERE, "Error importing " + fileName, e);
            System.err.println("❌ Error importing " + fileName + ": " + e.getMessage());
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


    private static String[] parseCsvLine(String line) {
        if (line == null || line.isEmpty()) {
            return new String[0];
        }

        java.util.List<String> result = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {

                    currentField.append('"');
                    i++;
                } else {

                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {

                result.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }


        result.add(currentField.toString());

        return result.toArray(new String[0]);
    }

    private static void createIndexes(Connection conn) throws SQLException {
        logger.info("Creating database indexes...");
        System.out.println("🔗 Creating database indexes...");

        String[] indexStatements = {
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
            System.out.println("✅ Database indexes created");
        }
    }

    private static void verifyImport(Connection conn) throws SQLException {
        System.out.println("\n🔍 Verifying import...");


        String[] criticalTables = {"stations", "routes", "trips", "stop_times"};

        try (Statement stmt = conn.createStatement()) {
            for (String table : criticalTables) {
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count == 0) {
                        System.err.println("⚠️  WARNING: Table " + table + " is empty!");
                    } else {
                        System.out.println("✓ " + table + ": " + count + " rows");
                    }
                }
            }
        }
    }

    private static void analyzeDatabase(Connection conn) throws SQLException {
        logger.info("Analyzing database...");
        System.out.println("\n📊 Database Statistics:");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ANALYZE");

            String[] tables = {"agencies", "stations", "routes", "calendar", "trips", "stop_times"};

            for (String table : tables) {
                try {
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        System.out.println("   📋 " + String.format("%-12s", table) + ": " + String.format("%,d", count) + " rows");
                        logger.info("Table " + table + ": " + count + " rows");
                    }
                } catch (SQLException e) {
                    System.out.println("   ❌ " + table + ": " + e.getMessage());
                    logger.warning("Could not get count for table " + table + ": " + e.getMessage());
                }
            }


            File dbFile = new File(DB_PATH);
            if (dbFile.exists()) {
                long sizeInMB = dbFile.length() / (1024 * 1024);
                System.out.println("   💾 Database size: " + sizeInMB + " MB");
            }

            System.out.println("✅ Database analysis completed");
            logger.info("Database analysis completed");
        }
    }
}