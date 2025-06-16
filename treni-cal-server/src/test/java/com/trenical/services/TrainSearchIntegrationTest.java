//package com.trenical.services;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.TestInstance;
//import org.junit.jupiter.api.io.TempDir;
//
//import com.trenical.rubyViaggiatreno.RubyViaggiatrenoClient;
//import io.grpc.stub.StreamObserver;
//import proto.*;
//
//import java.io.File;
//import java.sql.*;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicReference;
//
//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//@DisplayName("S02 - Train Search Integration Tests with Strategy Pattern")
//class TrainSearchIntegrationTest {
//
//    @TempDir
//    File tempDir;
//
//    private Connection testDbConnection;
//    private TrainServiceImpl trainService;
//    private RubyViaggiatrenoClient mockRubyClient;
//
//    @BeforeEach
//    void setUp() throws SQLException {
//        // Setup test database
//        String testDbPath = new File(tempDir, "test_trenical.db").getAbsolutePath();
//        testDbConnection = DriverManager.getConnection("jdbc:sqlite:" + testDbPath);
//
//        createTestTables();
//        insertTestData();
//
//        // Mock Ruby client (not needed for train search, only for real-time info)
//        mockRubyClient = new RubyViaggiatrenoClient("localhost", 50052);
//        trainService = new TrainServiceImpl(mockRubyClient);
//    }
//
//    private void createTestTables() throws SQLException {
//        String[] createStatements = {
//                "CREATE TABLE agencies (agency_id TEXT PRIMARY KEY, agency_name TEXT, agency_url TEXT, agency_timezone TEXT, agency_lang TEXT, agency_phone TEXT)",
//                "CREATE TABLE stations (stop_id TEXT PRIMARY KEY, stop_code TEXT, stop_name TEXT, stop_desc TEXT, stop_lat REAL, stop_lon REAL, zone_id TEXT, stop_url TEXT, location_type INTEGER, parent_station TEXT, stop_timezone TEXT, wheelchair_boarding INTEGER)",
//                "CREATE TABLE routes (route_id TEXT PRIMARY KEY, agency_id TEXT, route_short_name TEXT, route_long_name TEXT, route_desc TEXT, route_type INTEGER, route_url TEXT, route_color TEXT, route_text_color TEXT)",
//                "CREATE TABLE calendar (service_id TEXT PRIMARY KEY, monday INTEGER, tuesday INTEGER, wednesday INTEGER, thursday INTEGER, friday INTEGER, saturday INTEGER, sunday INTEGER, start_date TEXT, end_date TEXT)",
//                "CREATE TABLE trips (trip_id TEXT PRIMARY KEY, route_id TEXT, service_id TEXT, trip_headsign TEXT, trip_short_name TEXT, direction_id INTEGER, block_id TEXT, shape_id TEXT, wheelchair_accessible INTEGER, bikes_allowed INTEGER)",
//                "CREATE TABLE stop_times (trip_id TEXT, arrival_time TEXT, departure_time TEXT, stop_id TEXT, stop_sequence INTEGER, stop_headsign TEXT, pickup_type INTEGER, drop_off_type INTEGER, shape_dist_traveled REAL, PRIMARY KEY (trip_id, stop_sequence))"
//        };
//
//        try (Statement stmt = testDbConnection.createStatement()) {
//            for (String sql : createStatements) {
//                stmt.execute(sql);
//            }
//        }
//    }
//
//    private void insertTestData() throws SQLException {
//        // Insert test agencies
//        try (PreparedStatement stmt = testDbConnection.prepareStatement(
//                "INSERT INTO agencies VALUES (?, ?, ?, ?, ?, ?)")) {
//
//            stmt.setString(1, "TRENITALIA");
//            stmt.setString(2, "Trenitalia S.p.A.");
//            stmt.setString(3, "https://www.trenitalia.com");
//            stmt.setString(4, "Europe/Rome");
//            stmt.setString(5, "it");
//            stmt.setString(6, "892021");
//            stmt.executeUpdate();
//        }
//
//        // Insert test stations
//        String[][] stations = {
//                {"83001", "RMT", "Roma Termini", "Stazione Centrale di Roma", "41.9009", "12.5026", "RM", "", "1", "", "Europe/Rome", "1"},
//                {"83002", "MIC", "Milano Centrale", "Stazione Centrale di Milano", "45.4862", "9.2048", "MI", "", "1", "", "Europe/Rome", "1"},
//                {"83003", "SMN", "Firenze S.M.N.", "Stazione Santa Maria Novella", "43.7761", "11.2474", "FI", "", "1", "", "Europe/Rome", "1"}
//        };
//
//        try (PreparedStatement stmt = testDbConnection.prepareStatement(
//                "INSERT INTO stations VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
//
//            for (String[] station : stations) {
//                for (int i = 0; i < station.length; i++) {
//                    stmt.setString(i + 1, station[i]);
//                }
//                stmt.executeUpdate();
//            }
//        }
//
//        // Insert test routes
//        try (PreparedStatement stmt = testDbConnection.prepareStatement(
//                "INSERT INTO routes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
//
//            stmt.setString(1, "FR_AV01");
//            stmt.setString(2, "TRENITALIA");
//            stmt.setString(3, "Frecciarossa");
//            stmt.setString(4, "Milano - Roma - Napoli");
//            stmt.setString(5, "Servizio alta velocità");
//            stmt.setInt(6, 1);
//            stmt.setString(7, "https://www.trenitalia.com");
//            stmt.setString(8, "E60012");
//            stmt.setString(9, "FFFFFF");
//            stmt.executeUpdate();
//        }
//
//        // Insert test calendar
//        try (PreparedStatement stmt = testDbConnection.prepareStatement(
//                "INSERT INTO calendar VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
//
//            stmt.setString(1, "DAILY");
//            stmt.setInt(2, 1); // monday
//            stmt.setInt(3, 1); // tuesday
//            stmt.setInt(4, 1); // wednesday
//            stmt.setInt(5, 1); // thursday
//            stmt.setInt(6, 1); // friday
//            stmt.setInt(7, 1); // saturday
//            stmt.setInt(8, 1); // sunday
//            stmt.setString(9, "20250101");
//            stmt.setString(10, "20251231");
//            stmt.executeUpdate();
//        }
//
//        // Insert test trips
//        try (PreparedStatement stmt = testDbConnection.prepareStatement(
//                "INSERT INTO trips VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
//
//            stmt.setString(1, "FR9001_1");
//            stmt.setString(2, "FR_AV01");
//            stmt.setString(3, "DAILY");
//            stmt.setString(4, "Milano Centrale");
//            stmt.setString(5, "FR 9001");
//            stmt.setInt(6, 0);
//            stmt.setString(7, "B001");
//            stmt.setString(8, "SH001");
//            stmt.setInt(9, 1);
//            stmt.setInt(10, 0);
//            stmt.executeUpdate();
//        }
//
//        // Insert test stop_times
//        String[][] stopTimes = {
//                {"FR9001_1", "06:00:00", "06:00:00", "83001", "1", "Milano Centrale", "0", "0", "0"},
//                {"FR9001_1", "09:00:00", "09:02:00", "83003", "2", "Milano Centrale", "0", "0", "273"},
//                {"FR9001_1", "11:30:00", "11:30:00", "83002", "3", "", "0", "0", "516"}
//        };
//
//        try (PreparedStatement stmt = testDbConnection.prepareStatement(
//                "INSERT INTO stop_times VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
//
//            for (String[] stopTime : stopTimes) {
//                for (int i = 0; i < stopTime.length; i++) {
//                    stmt.setString(i + 1, stopTime[i]);
//                }
//                stmt.executeUpdate();
//            }
//        }
//    }
//
//    @Test
//    @DisplayName("Should find direct trains between stations")
//    void testSearchTrains_DirectRoute_ReturnsResults() throws InterruptedException {
//        // Arrange
//        Station fromStation = Station.newBuilder()
//                .setId("83001")
//                .setName("Roma Termini")
//                .build();
//
//        Station toStation = Station.newBuilder()
//                .setId("83002")
//                .setName("Milano Centrale")
//                .build();
//
//        SearchTrainRequest request = SearchTrainRequest.newBuilder()
//                .setDepartureStation(fromStation)
//                .setArrivalStation(toStation)
//                .build();
//
//        AtomicReference<SearchTrainResponse> responseRef = new AtomicReference<>();
//        CountDownLatch latch = new CountDownLatch(1);
//
//        StreamObserver<SearchTrainResponse> responseObserver = new StreamObserver<SearchTrainResponse>() {
//            @Override
//            public void onNext(SearchTrainResponse response) {
//                responseRef.set(response); // Set the response to the reference
//            }
//
//            @Override
//            public void onError(Throwable t) {
//                fail("Should not receive error: " + t.getMessage());
//                latch.countDown();
//            }
//
//            @Override
//            public void onCompleted() {
//                latch.countDown(); // Count down when the response is complete
//            }
//        };
//
//        // Act
//        long startTime = System.nanoTime();
//        trainService.searchTrains(request, responseObserver);
//        assertTrue(latch.await(5, TimeUnit.SECONDS));
//        long endTime = System.nanoTime();
//
//        long durationMs = (endTime - startTime) / 1_000_000;
//        assertTrue(durationMs < 85, "Search should complete within 85ms target, took: " + durationMs + "ms");
//    }
//
//    @Test
//    @DisplayName("Should handle concurrent search requests")
//    void testSearchTrains_ConcurrentRequests_HandledProperly() throws InterruptedException {
//        // Arrange
//        int numberOfRequests = 50;
//        CountDownLatch latch = new CountDownLatch(numberOfRequests);
//
//        Station fromStation = Station.newBuilder()
//                .setId("83001")
//                .setName("Roma Termini")
//                .build();
//
//        Station toStation = Station.newBuilder()
//                .setId("83002")
//                .setName("Milano Centrale")
//                .build();
//
//        // Act
//        for (int i = 0; i < numberOfRequests; i++) {
//            new Thread(() -> {
//                SearchTrainRequest request = SearchTrainRequest.newBuilder()
//                        .setDepartureStation(fromStation)
//                        .setArrivalStation(toStation)
//                        .build();
//
//                StreamObserver<SearchTrainResponse> responseObserver = new StreamObserver<SearchTrainResponse>() {
//                    @Override
//                    public void onNext(SearchTrainResponse response) {}
//
//                    @Override
//                    public void onError(Throwable t) {
//                        latch.countDown(); // Count down on error
//                    }
//
//                    @Override
//                    public void onCompleted() {
//                        latch.countDown(); // Count down on completion
//                    }
//                };
//
//                trainService.searchTrains(request, responseObserver);
//            }).start();
//        }
//
//        // Assert
//        assertTrue(latch.await(10, TimeUnit.SECONDS),
//                "All concurrent requests should complete within 10 seconds");
//    }
//
//    // Add other test cases in the same pattern...
//}
