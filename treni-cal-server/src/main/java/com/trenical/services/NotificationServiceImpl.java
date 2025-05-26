package com.trenical.services;

import io.grpc.stub.StreamObserver;
import proto.*;
import com.trenical.database.TicketDatabase;
import com.trenical.database.TrainDatabase;
import com.trenical.observer.TripObserver;
import com.trenical.observer.NotificationEngine;


public class NotificationServiceImpl extends NotificationServiceGrpc.NotificationServiceImplBase{

    private final NotificationEngine notificationEngine = NotificationEngine.getInstance();
    private final TicketDatabase ticketDatabase = TicketDatabase.getInstance();

    @Override
    public void subscribeToTripChanges(TripSubscriptionRequest request, StreamObserver<TripChangeNotification> responseObserver) {
        System.out.println("[Server] User " + request.getUserId() + " subscribed to updates for ticket " + request.getTicketId());

        Ticket ticket = ticketDatabase.getTicketById(request.getTicketId());
        if (ticket == null || !ticket.getUserId().equals(request.getUserId())) {
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription("Ticket not found or not owned by user.")
                    .asRuntimeException());
            return;
        }

        TripObserver tripObserver = new TripObserver(responseObserver, request.getTicketId());
        notificationEngine.addObserver(ticket.getTrainDetails().getId(), tripObserver);
    }
}
