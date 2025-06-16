//package com.trenical.services;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.*;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import com.trenical.rubyViaggiatreno.RubyViaggiatrenoClient;
//import io.grpc.stub.StreamObserver;
//import proto.TrainInfoRequest;
//import proto.TrainRealTimeUpdate;
//import ruby_viaggiatreno_microservizio.TrainStatusResponse;
//
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicReference;
//import java.sql.*;
//
//@ExtendWith(MockitoExtension.class)
//@DisplayName("S03 - Real-time Train Info Tests with Viaggiatreno Integration")
//class RealTimeTrainInfoTest {
//
//    @Mock
//    private RubyViaggiatrenoClient mockRubyClient;
//
//    @Mock
//    private StreamObserver<TrainRealTimeUpdate> mockResponseObserver;
//
//    private TrainServiceImpl trainService;
//
//    @BeforeEach
//    void setUp() {
//        trainService = new TrainServiceImpl(mockRubyClient);
//    }
//
//    @Test
//    @DisplayName("Should return real-time info for valid train")
//    void testGetTrainRealTimeInfo_ValidTrain_ReturnsStatus() throws InterruptedException {
//        // Arrange
//        String trainId = "FR9001_1";
//
//        TrainStatusResponse mockResponse = TrainStatusResponse.newBuilder()
//                .setTrainNumber("9001")
//                .setFound(true)
//                .setTrainStatusDescription("In orario")
//                .setDelayMinutes(0)
//                .setLastDetectedStation("Roma Termini")
//                .setTrainCategory("Frecciarossa")
//                .setOriginStation("Milano Centrale")
//                .setDestinationStation("Napoli Centrale")
//                .build();
//
//        when(mockRubyClient.getTrainStatus("9001")).thenReturn(mockResponse);
//
//        TrainInfoRequest request = TrainInfoRequest.newBuilder()
//                .setTrainId(trainId)
//                .build();
//
//        AtomicReference<TrainRealTimeUpdate> updateRef = new AtomicReference<>();
//        CountDownLatch latch = new CountDownLatch(1);
//
//        StreamObserver<TrainRealTimeUpdate> responseObserver = new StreamObserver<TrainRealTimeUpdate>() {
//            @Override
//            public void onNext(TrainRealTimeUpdate update) {
//                updateRef.set(update);
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
//                latch.countDown();
//            }
//        };
//
//        // Act
//        trainService.getTrainRealTimeInfo(request, responseObserver);
//
//        // Assert
//        assertTrue(latch.await(5, TimeUnit.SECONDS));
//        assertNotNull(updateRef.get());
//
//        TrainRealTimeUpdate update = updateRef.get();
//        assertEquals(trainId, update.getTrainId());
//        assertTrue(update.getStatusUpdate().contains("9001"));
//        assertTrue(update.getStatusUpdate().contains("In orario"));
//    }
//
//    @Test
//    @DisplayName("Should handle delayed train information")
//    void testGetTrainRealTimeInfo_DelayedTrain_ReturnsDelayInfo() throws InterruptedException {
//        // Arrange
//        String trainId = "IC501_1";
//
//        TrainStatusResponse mockResponse = TrainStatusResponse.newBuilder()
//                .setTrainNumber("501")
//                .setFound(true)
//                .setTrainStatusDescription("In ritardo")
//                .setDelayMinutes(15)
//                .setLastDetectedStation("Bologna Centrale")
//                .setTrainCategory("Intercity")
//                .setOriginStation("Milano Centrale")
//                .setDestinationStation("Roma Termini")
//                .build();
//
//        when(mockRubyClient.getTrainStatus("501")).thenReturn(mockResponse);
//
//        TrainInfoRequest request = TrainInfoRequest.newBuilder()
//                .setTrainId(trainId)
//                .build();
//
//        AtomicReference<TrainRealTimeUpdate> updateRef = new AtomicReference<>();
//        CountDownLatch latch = new CountDownLatch(1);
//
//        StreamObserver<TrainRealTimeUpdate> responseObserver = new StreamObserver<TrainRealTimeUpdate>() {
//            @Override
//            public void onNext(TrainRealTimeUpdate update) {
//                updateRef.set(update);
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
//                latch.countDown();
//            }
//        };
//
//        // Act
//        trainService.getTrainRealTimeInfo(request, responseObserver);
//
//        // Assert
//        assertTrue(latch.await(5, TimeUnit.SECONDS));
//        assertNotNull(updateRef.get());
//
//        TrainRealTimeUpdate update = updateRef.get();
//        assertTrue(update.getStatusUpdate().contains("15 min"));
//        assertTrue(update.getStatusUpdate().contains("Bologna Centrale"));
//    }
//
//    @Test
//    @DisplayName("Should handle train not found scenario")
//    void testGetTrainRealTimeInfo_TrainNotFound_ReturnsError() throws InterruptedException {
//        // Arrange
//        String trainId = "INVALID_TRAIN";
//
//        TrainStatusResponse mockResponse = TrainStatusResponse.newBuilder()
//                .setTrainNumber("INVALID")
//                .setFound(false)
//                .setErrorMessage("Train not found")
//                .build();
//
//        when(mockRubyClient.getTrainStatus("INVALID")).thenReturn(mockResponse);
//
//        TrainInfoRequest request = TrainInfoRequest.newBuilder()
//                .setTrainId(trainId)
//                .build();
//
//        AtomicReference<Throwable> errorRef = new AtomicReference<>();
//        CountDownLatch latch = new CountDownLatch(1);
//
//        StreamObserver<TrainRealTimeUpdate> responseObserver = new StreamObserver<TrainRealTimeUpdate>() {
//            @Override
//            public void onNext(TrainRealTimeUpdate update) {
//                fail("Should not receive update for invalid train");
//            }
//
//            @Override
//            public void onError(Throwable t) {
//                errorRef.set(t);
//                latch.countDown();
//            }
//
//            @Override
//            public void onCompleted() {
//                fail("Should not complete for invalid train");
//                latch.countDown();
//            }
//        };
//
//        // Act
//        trainService.getTrainRealTimeInfo(request, responseObserver);
//
//        // Assert
//        assertTrue(latch.await(5, TimeUnit.SECONDS));
//        assertNotNull(errorRef.get());
//    }
//
//    @Test
//    @DisplayName("Should handle Viaggiatreno service unavailable")
//    void testGetTrainRealTimeInfo_ServiceUnavailable_HandlesGracefully() throws InterruptedException {
//        // Arrange
//        String trainId = "FR9001_1";
//
//        when(mockRubyClient.getTrainStatus("9001"))
//                .thenThrow(new RuntimeException("Viaggiatreno service unavailable"));
//
//        TrainInfoRequest request = TrainInfoRequest.newBuilder()
//                .setTrainId(trainId)
//                .build();
//
//        AtomicReference<Throwable> errorRef = new AtomicReference<>();
//        CountDownLatch latch = new CountDownLatch(1);
//
//        StreamObserver<TrainRealTimeUpdate> responseObserver = new StreamObserver<TrainRealTimeUpdate>() {
//            @Override
//            public void onNext(TrainRealTimeUpdate update) {
//                fail("Should not receive update when service is unavailable");
//            }
//
//            @Override
//            public void onError(Throwable t) {
//                errorRef.set(t);
//                latch.countDown();
//            }
//
//            @Override
//            public void onCompleted() {
//                fail("Should not complete when service is unavailable");
//                latch.countDown();
//            }
//        };
//
//        // Act
//        trainService.getTrainRealTimeInfo(request, responseObserver);
//
//        // Assert
//        assertTrue(latch.await(5, TimeUnit.SECONDS));
//        assertNotNull(errorRef.get());
//    }
//
//    @Test
//    @DisplayName("Should validate train number extraction from trip ID")
//    void testGetTrainRealTimeInfo_TrainNumberExtraction_WorksCorrectly() throws InterruptedException {
//        // Test different trip ID formats and ensure correct train number extraction
//
//        String[][] testCases = {
//                {"FR9001_1", "9001"},
//                {"IC501_1", "501"},
//                {"REG_4001", "4001"},
//                {"NTV8975_1", "8975"}
//        };
//
//        for (String[] testCase : testCases) {
//            String tripId = testCase[0];
//            String expectedTrainNumber = testCase[1];
//
//            TrainStatusResponse mockResponse = TrainStatusResponse.newBuilder()
//                    .setTrainNumber(expectedTrainNumber)
//                    .setFound(true)
//                    .setTrainStatusDescription("In orario")
//                    .build();
//
//            when(mockRubyClient.getTrainStatus(expectedTrainNumber)).thenReturn(mockResponse);
//
//            TrainInfoRequest request = TrainInfoRequest.newBuilder()
//                    .setTrainId(tripId)
//                    .build();
//
//            CountDownLatch latch = new CountDownLatch(1);
//            AtomicReference<String> actualTrainNumber = new AtomicReference<>();
//
//            StreamObserver<TrainRealTimeUpdate> responseObserver = new StreamObserver<TrainRealTimeUpdate>() {
//                @Override
//                public void onNext(TrainRealTimeUpdate update) {
//                    // Extract train number from status update
//                    String status = update.getStatusUpdate();
//                    if (status.contains(expectedTrainNumber)) {
//                        actualTrainNumber.set(expectedTrainNumber);
//                    }
//                }
//
//                @Override
//                public void onError(Throwable t) {
//                    latch.countDown();
//                }
//
//                @Override
//                public void onCompleted() {
//                    latch.countDown();
//                }
//            };
//
//            // Act
//            trainService.getTrainRealTimeInfo(request, responseObserver);
//
//            // Assert
//            assertTrue(latch.await(2, TimeUnit.SECONDS));
//            assertEquals(expectedTrainNumber, actualTrainNumber.get(),
//                    "Train number extraction failed for trip ID: " + tripId);
//        }
//    }
//
//    @Test
//    @DisplayName("Should handle HTML parsing robustness")
//    void testGetTrainRealTimeInfo_HTMLParsing_IsRobust() throws InterruptedException {
//        // Test HTML parsing with various response formats
//
//        TrainStatusResponse[] testResponses = {
//                // Normal response
//                TrainStatusResponse.newBuilder()
//                        .setTrainNumber("9001")
//                        .setFound(true)
//                        .setTrainStatusDescription("Arrivato alle 14:25")
//                        .setDelayMinutes(5)
//                        .setLastDetectedStation("Roma Termini")
//                        .build(),
//
//                // Response with special characters
//                TrainStatusResponse.newBuilder()
//                        .setTrainNumber("501")
//                        .setFound(true)
//                        .setTrainStatusDescription("Partito da Città/Sant'Antonio con 10' di ritardo")
//                        .setDelayMinutes(10)
//                        .setLastDetectedStation("Città/Sant'Antonio")
//                        .build(),
//
//                // Response with cancellation
//                TrainStatusResponse.newBuilder()
//                        .setTrainNumber("1501")
//                        .setFound(true)
//                        .setTrainStatusDescription("Treno cancellato")
//                        .setDelayMinutes(0)
//                        .setLastDetectedStation("N/A")
//                        .build()
//        };
//
//        for (int i = 0; i < testResponses.length; i++) {
//            TrainStatusResponse response = testResponses[i];
//            String trainNumber = response.getTrainNumber();
//
//            when(mockRubyClient.getTrainStatus(trainNumber)).thenReturn(response);
//
//            TrainInfoRequest request = TrainInfoRequest.newBuilder()
//                    .setTrainId("TEST_" + trainNumber + "_1")
//                    .build();
//
//            CountDownLatch latch = new CountDownLatch(1);
//            AtomicReference<TrainRealTimeUpdate> updateRef = new AtomicReference<>();
//
//            StreamObserver<TrainRealTimeUpdate> responseObserver = new StreamObserver<TrainRealTimeUpdate>() {
//                @Override
//                public void onNext(TrainRealTimeUpdate update) {
//                    updateRef.set(update);
//                }
//
//                @Override
//                public void onError(Throwable t) {
//                    latch.countDown();
//                }
//
//                @Override
//                public void onCompleted() {
//                    latch.countDown();
//                }
//            };
//
//            // Act
//            trainService.getTrainRealTimeInfo(request, responseObserver);
//
//            // Assert
//            assertTrue(latch.await(2, TimeUnit.SECONDS),
//                    "Request " + i + " should complete");
//
//            if (response.getFound()) {
//                assertNotNull(updateRef.get(),
//                        "Should receive update for test case " + i);
//                assertTrue(updateRef.get().getStatusUpdate().contains(trainNumber),
//                        "Update should contain train number for test case " + i);
//            }
//        }
//    }
//
//    @Test
//    @DisplayName("Should measure real-time response performance")
//    void testGetTrainRealTimeInfo_Performance_MeetsRequirements() throws InterruptedException {
//        // Arrange
//        String trainId = "FR9001_1";
//
//        TrainStatusResponse mockResponse = TrainStatusResponse.newBuilder()
//                .setTrainNumber("9001")
//                .setFound(true)
//                .setTrainStatusDescription("In orario")
//                .setDelayMinutes(0)
//                .setLastDetectedStation("Roma Termini")
//                .build();
//
//        when(mockRubyClient.getTrainStatus("9001")).thenReturn(mockResponse);
//
//        TrainInfoRequest request = TrainInfoRequest.newBuilder()
//                .setTrainId(trainId)
//                .build();
//
//        CountDownLatch latch = new CountDownLatch(1);
//
//        StreamObserver<TrainRealTimeUpdate> responseObserver = new StreamObserver<TrainRealTimeUpdate>() {
//            @Override
//            public void onNext(TrainRealTimeUpdate update) {}
//
//            @Override
//            public void onError(Throwable t) {
//                latch.countDown();
//            }
//
//            @Override
//            public void onCompleted() {
//                latch.countDown();
//            }
//        };
//
//        // Act & Assert
//        long startTime = System.nanoTime();
//        trainService.getTrainRealTimeInfo(request, responseObserver);
//        assertTrue(latch.await(5, TimeUnit.SECONDS));
//        long endTime = System.nanoTime();
//
//        long durationMs = (endTime - startTime) / 1_000_000;
//        assertTrue(durationMs < 2000,
//                "Real-time info should be retrieved within 2 seconds, took: " + durationMs + "ms");
//    }
//
//    @Test
//    @DisplayName("Should handle concurrent real-time requests")
//    void testGetTrainRealTimeInfo_ConcurrentRequests_HandledProperly() throws InterruptedException {
//        // Arrange
//        int numberOfRequests = 20;
//        CountDownLatch latch = new CountDownLatch(numberOfRequests);
//
//        TrainStatusResponse mockResponse = TrainStatusResponse.newBuilder()
//                .setTrainNumber("9001")
//                .setFound(true)
//                .setTrainStatusDescription("In orario")
//                .build();
//
//        when(mockRubyClient.getTrainStatus("9001")).thenReturn(mockResponse);
//
//        // Act
//        for (int i = 0; i < numberOfRequests; i++) {
//            new Thread(() -> {
//                TrainInfoRequest request = TrainInfoRequest.newBuilder()
//                        .setTrainId("FR9001_1")
//                        .build();
//
//                StreamObserver<TrainRealTimeUpdate> responseObserver = new StreamObserver<TrainRealTimeUpdate>() {
//                    @Override
//                    public void onNext(TrainRealTimeUpdate update) {}
//
//                    @Override
//                    public void onError(Throwable t) {
//                        latch.countDown();
//                    }
//
//                    @Override
//                    public void onCompleted() {
//                        latch.countDown();
//                    }
//                };
//
//                trainService.getTrainRealTimeInfo(request, responseObserver);
//            }).start();
//        }
//
//        // Assert
//        assertTrue(latch.await(10, TimeUnit.SECONDS),
//                "All concurrent real-time requests should complete within 10 seconds");
//    }
//
//    @Test
//    @DisplayName("Should validate status message formatting")
//    void testGetTrainRealTimeInfo_StatusFormatting_IsConsistent() throws InterruptedException {
//        // Arrange
//        String trainId = "IC501_1";
//
//        TrainStatusResponse mockResponse = TrainStatusResponse.newBuilder()
//                .setTrainNumber("501")
//                .setFound(true)
//                .setTrainStatusDescription("Partito con 5 minuti di ritardo")
//                .setDelayMinutes(5)
//                .setLastDetectedStation("Milano Centrale")
//                .setTrainCategory("Intercity")
//                .build();
//
//        when(mockRubyClient.getTrainStatus("501")).thenReturn(mockResponse);
//
//        TrainInfoRequest request = TrainInfoRequest.newBuilder()
//                .setTrainId(trainId)
//                .build();
//
//        AtomicReference<TrainRealTimeUpdate> updateRef = new AtomicReference<>();
//        CountDownLatch latch = new CountDownLatch(1);
//
//        StreamObserver<TrainRealTimeUpdate> responseObserver = new StreamObserver<TrainRealTimeUpdate>() {
//            @Override
//            public void onNext(TrainRealTimeUpdate update) {
//                updateRef.set(update);
//            }
//
//            @Override
//            public void onError(Throwable t) {
//                latch.countDown();
//            }
//
//            @Override
//            public void onCompleted() {
//                latch.countDown();
//            }
//        };
//
//        // Act
//        trainService.getTrainRealTimeInfo(request, responseObserver);
//
//        // Assert
//        assertTrue(latch.await(5, TimeUnit.SECONDS));
//        assertNotNull(updateRef.get());
//
//        String statusUpdate = updateRef.get().getStatusUpdate();
//
//        // Verify formatting contains all required elements
//        assertTrue(statusUpdate.contains("501"), "Should contain train number");
//        assertTrue(statusUpdate.contains("5 min"), "Should contain delay information");
//        assertTrue(statusUpdate.contains("Milano Centrale"), "Should contain last detected station");
//
//        // Verify consistent format (Treno X: Status. Ritardo: Y min. Ultima rilevazione: Z)
//        assertTrue(statusUpdate.matches(".*Treno \\d+:.*Ritardo: \\d+ min.*Ultima rilevazione:.*"),
//                "Status format should be consistent");
//    }
//}
//
//
//
