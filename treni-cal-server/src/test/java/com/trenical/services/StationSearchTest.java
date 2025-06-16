//package com.trenical.services;
//
//import com.trenical.rubyViaggiatreno.RubyViaggiatrenoClient;
//import io.grpc.stub.StreamObserver;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import proto.SearchStationRequest;
//import proto.Station;
//import proto.StationListResponse;
//
//import java.util.concurrent.atomic.AtomicReference;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class StationSearchTest {
//
//    @Mock
//    private RubyViaggiatrenoClient rubyClient; // 1. Mock della dipendenza
//
//    @InjectMocks
//    private TrainServiceImpl trainService; // 2. Iniezione automatica del mock nel servizio
//
//    @Test
//    void testSearchStationsSuccessfully() throws Exception {
//        // ARRANGE
//        // Prepara la richiesta e la risposta simulata dal client Ruby
//        String query = "Paola";
//        SearchStationRequest request = SearchStationRequest.newBuilder().setSearchQuery(query).build();
//
//        ruby_viaggiatreno_microservizio.Station rubyStation = ruby_viaggiatreno_microservizio.Station.newBuilder()
//                .setId("S01717")
//                .setName("Paola")
//                .build();
//        ruby_viaggiatreno_microservizio.StationListResponse rubyResponse = ruby_viaggiatreno_microservizio.StationListResponse.newBuilder()
//                .addStations(rubyStation)
//                .build();
//
//        // Configura il mock per restituire la risposta simulata quando viene chiamato
//        when(rubyClient.searchStations(query)).thenReturn(rubyResponse);
//
//        // Prepara un contenitore per la risposta che riceveremo dal servizio
//        final AtomicReference<StationListResponse> actualResponse = new AtomicReference<>();
//        final AtomicReference<Throwable> error = new AtomicReference<>();
//        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
//
//
//        StreamObserver<StationListResponse> responseObserver = new StreamObserver<StationListResponse>() {
//            @Override
//            public void onNext(StationListResponse value) {
//                actualResponse.set(value);
//            }
//
//            @Override
//            public void onError(Throwable t) {
//                error.set(t);
//                latch.countDown();
//            }
//
//            @Override
//            public void onCompleted() {
//                latch.countDown();
//            }
//        };
//
//        // ACT
//        // Chiama il metodo `searchStations` che vogliamo testare
//        trainService.searchStations(request, responseObserver);
//
//        latch.await(1, java.util.concurrent.TimeUnit.SECONDS);
//
//
//        // ASSERT
//        // Verifica che tutto sia andato come previsto
//        assertNull(error.get(), "Non dovrebbe esserci stato un errore");
//        assertNotNull(actualResponse.get(), "La risposta non dovrebbe essere nulla");
//        assertEquals(1, actualResponse.get().getStationsCount(), "Dovrebbe esserci una stazione nella lista");
//
//        Station returnedStation = actualResponse.get().getStations(0);
//        assertEquals("S01717", returnedStation.getId());
//        assertEquals("Paola", returnedStation.getName());
//
//        // Verifica che il metodo sul client mock sia stato chiamato
//        verify(rubyClient).searchStations(query);
//    }
//
//    @Test
//    void testSearchStationsThrowsException() throws Exception {
//        // ARRANGE
//        String query = "Stazione Inesistente";
//        SearchStationRequest request = SearchStationRequest.newBuilder().setSearchQuery(query).build();
//        RuntimeException simulatedException = new RuntimeException("Errore dal servizio Ruby");
//
//        // Configura il mock per lanciare un'eccezione
//        when(rubyClient.searchStations(anyString())).thenThrow(simulatedException);
//
//        final AtomicReference<Throwable> actualError = new AtomicReference<>();
//        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
//
//
//        StreamObserver<StationListResponse> responseObserver = new StreamObserver<StationListResponse>() {
//            @Override
//            public void onNext(StationListResponse value) {
//                fail("onNext non avrebbe dovuto essere chiamato");
//            }
//
//            @Override
//            public void onError(Throwable t) {
//                actualError.set(t);
//                latch.countDown();
//            }
//
//            @Override
//            public void onCompleted() {
//                fail("onCompleted non avrebbe dovuto essere chiamato");
//            }
//        };
//
//        // ACT
//        trainService.searchStations(request, responseObserver);
//
//        latch.await(1, java.util.concurrent.TimeUnit.SECONDS);
//
//
//        // ASSERT
//        assertNotNull(actualError.get(), "Un errore avrebbe dovuto essere catturato");
//        assertTrue(actualError.get().getMessage().contains("Errore ricerca stazioni: " + simulatedException.getMessage()));
//    }
//}