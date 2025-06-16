//package com.trenical.services;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.TestInstance;
//import org.junit.jupiter.api.io.TempDir;
//
//import io.grpc.Status;
//import io.grpc.stub.StreamObserver;
//import proto.*;
//
//import java.io.File;
//import java.sql.*;
//import java.util.UUID;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicReference;
//
//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//@DisplayName("S04 - Ticket Purchase Transactional Tests")
//class TicketPurchaseTest {
//
//    @TempDir
//    File tempDir;
//
//    private Connection testDbConnection;
//    private TicketServiceImpl ticketService;
//    private String dbPath;
//
//    // ID di test costanti per coerenza tra i test
//    private static final String TEST_USER_ID = "user-test-123";
//    private static final String TEST_TRAIN_ID = "TRIP01"; // Corrisponde a trip_id nella terminologia GTFS
//    private static final String TEST_DEPARTURE_STATION_ID = "83002"; // Milano
//    private static final String TEST_ARRIVAL_STATION_ID = "83001"; // Roma
//
//    @BeforeEach
//    void setUp() throws SQLException {
//        // Setup del database di test in un file temporaneo
//        dbPath = "jdbc:sqlite:" + new File(tempDir, "test_tickets.db").getAbsolutePath();
//        testDbConnection = DriverManager.getConnection(dbPath);
//        createTestTables();
//        insertTestData();
//
//        // Inizializza il servizio passando il percorso del DB di test
//        // Assumendo che esista un costruttore che accetta il path, altrimenti adattare.
//        ticketService = new TicketServiceImpl();
//    }
//
//    @AfterEach
//    void tearDown() throws SQLException {
//        if (testDbConnection != null && !testDbConnection.isClosed()) {
//            testDbConnection.close();
//        }
//    }
//
//    // Metodi per la creazione e popolazione del DB di test (invariati dalla tua versione)
//    private void createTestTables() throws SQLException {
//        // ... il tuo codice per creare le tabelle ...
//        String[] createStatements = {
//                "CREATE TABLE agencies (agency_id TEXT PRIMARY KEY, agency_name TEXT, agency_url TEXT, agency_timezone TEXT, agency_lang TEXT, agency_phone TEXT)",
//                "CREATE TABLE stations (stop_id TEXT PRIMARY KEY, stop_code TEXT, stop_name TEXT, stop_desc TEXT, stop_lat REAL, stop_lon REAL, zone_id TEXT, stop_url TEXT, location_type INTEGER, parent_station TEXT, stop_timezone TEXT, wheelchair_boarding INTEGER)",
//                "CREATE TABLE routes (route_id TEXT PRIMARY KEY, agency_id TEXT, route_short_name TEXT, route_long_name TEXT, route_desc TEXT, route_type INTEGER, route_url TEXT, route_color TEXT, route_text_color TEXT)",
//                "CREATE TABLE calendar (service_id TEXT PRIMARY KEY, monday INTEGER, tuesday INTEGER, wednesday INTEGER, thursday INTEGER, friday INTEGER, saturday INTEGER, sunday INTEGER, start_date TEXT, end_date TEXT)",
//                "CREATE TABLE trips (trip_id TEXT PRIMARY KEY, route_id TEXT, service_id TEXT, trip_headsign TEXT, trip_short_name TEXT, direction_id INTEGER, block_id TEXT, shape_id TEXT, wheelchair_accessible INTEGER, bikes_allowed INTEGER)",
//                "CREATE TABLE stop_times (trip_id TEXT, arrival_time TEXT, departure_time TEXT, stop_id TEXT, stop_sequence INTEGER, stop_headsign TEXT, pickup_type INTEGER, drop_off_type INTEGER, shape_dist_traveled REAL, PRIMARY KEY (trip_id, stop_sequence))",
//                "CREATE TABLE tickets (ticket_id TEXT PRIMARY KEY, user_id TEXT NOT NULL, trip_id TEXT NOT NULL, seat_number TEXT, purchase_date INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'CONFIRMED', service_class TEXT, price REAL, FOREIGN KEY (trip_id) REFERENCES trips(trip_id))"
//        };
//        try (Statement stmt = testDbConnection.createStatement()) {
//            for (String sql : createStatements) {
//                stmt.execute(sql);
//            }
//        }
//    }
//
//    private void insertTestData() throws SQLException {
//        // ... il tuo codice per inserire i dati ...
//        try (Statement stmt = testDbConnection.createStatement()) {
//            stmt.execute("INSERT INTO stations (stop_id, stop_name) VALUES ('" + TEST_DEPARTURE_STATION_ID + "', 'Milano Centrale')");
//            stmt.execute("INSERT INTO stations (stop_id, stop_name) VALUES ('" + TEST_ARRIVAL_STATION_ID + "', 'Roma Termini')");
//            stmt.execute("INSERT INTO routes (route_id, route_short_name, route_long_name) VALUES ('FR_AV01', 'Frecciarossa', 'Frecciarossa Milano-Roma')");
//            stmt.execute("INSERT INTO calendar VALUES ('DAILY', 1, 1, 1, 1, 1, 1, 1, '20240101', '20251231')");
//            stmt.execute("INSERT INTO trips (trip_id, route_id, service_id, trip_headsign, trip_short_name) VALUES ('" + TEST_TRAIN_ID + "', 'FR_AV01', 'DAILY', 'Roma Termini', '9651')");
//            stmt.execute("INSERT INTO stop_times VALUES ('" + TEST_TRAIN_ID + "', '08:00:00', '08:00:00', '" + TEST_DEPARTURE_STATION_ID + "', 1, null, 0, 0, 0)");
//            stmt.execute("INSERT INTO stop_times VALUES ('" + TEST_TRAIN_ID + "', '11:00:00', '11:00:00', '" + TEST_ARRIVAL_STATION_ID + "', 2, null, 0, 0, 0)");
//        }
//    }
//
//
//    @Test
//    @DisplayName("Test per l'acquisto di un biglietto con successo")
//    void testPurchaseTicketSuccessfully() throws InterruptedException, SQLException {
//        // ARRANGE
//        PurchaseTicketRequest request = PurchaseTicketRequest.newBuilder()
//                .setUserId(TEST_USER_ID)
//                .setTrainId(TEST_TRAIN_ID) // CORREZIONE: setTrainId invece di setTripId
//                .setNumberOfTickets(1)
//                .setServiceClass("Executive")
//                .setPaymentMethodToken("VALID_TOKEN")
//                .build();
//
//        final CountDownLatch latch = new CountDownLatch(1);
//        final AtomicReference<PurchaseTicketResponse> responseRef = new AtomicReference<>();
//        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
//
//        StreamObserver<PurchaseTicketResponse> observer = new StreamObserver<>() {
//            @Override
//            public void onNext(PurchaseTicketResponse response) {
//                responseRef.set(response);
//            }
//            @Override
//            public void onError(Throwable t) {
//                errorRef.set(t);
//                latch.countDown();
//            }
//            @Override
//            public void onCompleted() {
//                latch.countDown();
//            }
//        };
//
//        // ACT
//        ticketService.purchaseTickets(request, observer); // CORREZIONE: purchaseTickets invece di purchaseTicket
//        latch.await(5, TimeUnit.SECONDS);
//
//        // ASSERT
//        assertNull(errorRef.get(), "L'acquisto non doveva generare un errore");
//        PurchaseTicketResponse response = responseRef.get();
//        assertNotNull(response, "La risposta non dovrebbe essere nulla");
//        assertTrue(response.getSuccess(), "L'acquisto deve avere successo");
//        assertEquals(1, response.getPurchasedTicketsCount(), "Deve essere stato acquistato un solo biglietto");
//
//        // CORREZIONE: getPurchasedTickets(0) invece di getTicket()
//        Ticket purchasedTicket = response.getPurchasedTickets(0);
//        assertEquals("CONFIRMED", purchasedTicket.getStatus(), "Lo stato del biglietto deve essere 'CONFIRMED'");
//        assertNotNull(purchasedTicket.getId(), "Il biglietto deve avere un ID");
//
//        // Verifica diretta sul DB che il biglietto sia stato inserito
//        try (PreparedStatement stmt = testDbConnection.prepareStatement("SELECT COUNT(*) FROM tickets WHERE user_id = ? AND trip_id = ?")) {
//            stmt.setString(1, TEST_USER_ID);
//            stmt.setString(2, TEST_TRAIN_ID);
//            ResultSet rs = stmt.executeQuery();
//            assertTrue(rs.next());
//            assertEquals(1, rs.getInt(1), "Deve esserci esattamente un biglietto nel DB per questo utente e viaggio");
//        }
//    }
//
//    @Test
//    @DisplayName("Test per l'acquisto di un biglietto per un viaggio non valido")
//    void testPurchaseTicketWithInvalidTripId() throws InterruptedException {
//        // ARRANGE
//        String invalidTrainId = "TRIP_INVALIDO";
//        PurchaseTicketRequest request = PurchaseTicketRequest.newBuilder()
//                .setUserId(TEST_USER_ID)
//                .setTrainId(invalidTrainId)
//                .setNumberOfTickets(1)
//                .setServiceClass("Business")
//                .setPaymentMethodToken("VALID_TOKEN")
//                .build();
//
//        final CountDownLatch latch = new CountDownLatch(1);
//        final AtomicReference<PurchaseTicketResponse> responseRef = new AtomicReference<>();
//        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
//
//        StreamObserver<PurchaseTicketResponse> observer = new StreamObserver<>() {
//            @Override
//            public void onNext(PurchaseTicketResponse response) {
//                responseRef.set(response);
//            }
//            @Override
//            public void onError(Throwable t) {
//                errorRef.set(t);
//                latch.countDown();
//            }
//            @Override
//            public void onCompleted() {
//                latch.countDown();
//            }
//        };
//
//        // ACT
//        ticketService.purchaseTickets(request, observer); // CORREZIONE: purchaseTickets invece di purchaseTicket
//        latch.await(5, TimeUnit.SECONDS);
//
//        // ASSERT
//        assertNull(errorRef.get(), "onError non deve essere chiamato, la logica di fallimento è nel messaggio di risposta");
//        PurchaseTicketResponse response = responseRef.get();
//        assertNotNull(response);
//        assertFalse(response.getSuccess(), "L'acquisto deve fallire per un treno non valido");
//        assertTrue(response.getMessage().contains("Treno non trovato"), "Il messaggio di errore deve indicare che il treno non è stato trovato");
//    }
//}