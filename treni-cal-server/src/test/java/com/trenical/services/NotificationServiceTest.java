/*
package com.trenical.services;

import com.trenical.observer.NotificationEngine;
import com.trenical.observer.TripObserver;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.StreamRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import proto.Ticket;
import proto.Train;
import proto.TripChangeNotification;
import proto.TripSubscriptionRequest;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("S06 - Test del Servizio di Notifiche con Observer Pattern")
class NotificationServiceTest {

    @Mock
    private NotificationEngine mockNotificationEngine;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private static final String TEST_TRIP_ID = "TRIP-NOTIFY-01";
    private static final String TEST_USER_ID = "user-notify-123";

    @Test
    @DisplayName("Il client dovrebbe sottoscriversi e ricevere una notifica di ritardo")
    void shouldSubscribeAndReceiveDelayNotification() throws Exception {
        // ARRANGE
        // 1. Prepara la richiesta di sottoscrizione del client
        TripSubscriptionRequest request = TripSubscriptionRequest.newBuilder()
                .setTripId(TEST_TRIP_ID)
                .setUserId(TEST_USER_ID)
                .build();

        // 2. StreamRecorder agisce come il nostro client gRPC, registrando tutte le risposte
        StreamRecorder<TripChangeNotification> responseObserver = StreamRecorder.create();

        // 3. ArgumentCaptor per "catturare" l'oggetto TripObserver che viene creato
        //    e passato al NotificationEngine. Questo è il cuore del test dell'Observer.
        ArgumentCaptor<TripObserver> observerCaptor = ArgumentCaptor.forClass(TripObserver.class);

        // ACT
        // 1. Il client chiama il metodo di sottoscrizione sul server
        notificationService.subscribeToTripUpdates(request, responseObserver);

        // 2. Verifica che il servizio abbia registrato il nostro observer nel motore di notifiche
        verify(mockNotificationEngine).subscribe(eq(TEST_TRIP_ID), observerCaptor.capture());

        // 3. Ora simuliamo un evento! Un'altra parte del sistema notifica un ritardo.
        //    Creiamo una notifica di esempio.
        TripChangeNotification testNotification = TripChangeNotification.newBuilder()
                .setTripId(TEST_TRIP_ID)
                .setUpdateMessage("Il treno è in ritardo di 15 minuti.")
                .setNewStatus("IN RITARDO")
                .build();

        // 4. Usiamo l'observer che abbiamo catturato per inviare la notifica.
        //    Questo simula il NotificationEngine che notifica i suoi iscritti.
        TripObserver capturedObserver = observerCaptor.getValue();
        capturedObserver.onUpdate(testNotification);

        // ASSERT
        // 1. Attendi che la notifica arrivi al nostro StreamRecorder.
        if (!responseObserver.awaitCompletion(5, TimeUnit.SECONDS)) {
            fail("La chiamata gRPC non è stata completata entro il timeout.");
        }

        // 2. Verifica che non ci siano stati errori durante lo stream
        assertNull(responseObserver.getError(), "Lo stream non dovrebbe avere errori");

        // 3. Controlla che abbiamo ricevuto esattamente UNA notifica
        List<TripChangeNotification> receivedNotifications = responseObserver.getValues();
        assertEquals(1, receivedNotifications.size(), "Dovrebbe essere ricevuta una sola notifica");

        // 4. Verifica che la notifica ricevuta sia esattamente quella che abbiamo inviato
        TripChangeNotification received = receivedNotifications.get(0);
        assertEquals(TEST_TRIP_ID, received.getTripId());
        assertEquals("Il treno è in ritardo di 15 minuti.", received.getUpdateMessage());
        assertEquals("IN RITARDO", received.getNewStatus());
    }

    @Test
    @DisplayName("Il client dovrebbe disiscriversi correttamente alla chiusura dello stream")
    void shouldUnsubscribeOnStreamCompletion() {
        // ARRANGE
        TripSubscriptionRequest request = TripSubscriptionRequest.newBuilder()
                .setTripId(TEST_TRIP_ID)
                .setUserId(TEST_USER_ID)
                .build();
        StreamRecorder<TripChangeNotification> responseObserver = StreamRecorder.create();
        ArgumentCaptor<TripObserver> observerCaptor = ArgumentCaptor.forClass(TripObserver.class);

        // ACT
        // 1. Sottoscrivi il client
        notificationService.subscribeToTripUpdates(request, responseObserver);

        // 2. Simula la chiusura della connessione da parte del client (onCompleted)
        responseObserver.onCompleted();

        // ASSERT
        // Verifica che il metodo 'unsubscribe' sia stato chiamato sul NotificationEngine,
        // passando l'observer corretto. Questo assicura che non ci siano "memory leak"
        // di observer sul server.
        verify(mockNotificationEngine).subscribe(eq(TEST_TRIP_ID), observerCaptor.capture());
        verify(mockNotificationEngine).unsubscribe(eq(TEST_TRIP_ID), eq(observerCaptor.getValue()));
    }
}



*/
