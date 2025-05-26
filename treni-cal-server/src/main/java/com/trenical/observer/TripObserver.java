package com.trenical.observer;


import io.grpc.stub.StreamObserver;
import proto.TripChangeNotification;

// Questo è l' 'Observer'
public class TripObserver {
    private final StreamObserver<TripChangeNotification> clientStreamObserver;
    private final String ticketId;

    public TripObserver(StreamObserver<TripChangeNotification> clientStreamObserver, String ticketId) {
        this.clientStreamObserver = clientStreamObserver;
        this.ticketId = ticketId;
    }

    public void sendUpdate(TripChangeNotification notification) {
        if (notification.getTicketId().equals(this.ticketId) || notification.getTicketId().equals("*")) { // "*" può essere una wildcard usata per esempio se si vogliono fare aggiornamenti generici
            try {
                clientStreamObserver.onNext(notification);
            } catch (Exception e) {
                System.err.println("Error sending update to client for ticket " + ticketId + ": " + e.getMessage());
                NotificationEngine.getInstance().removeObserverForTicket(this.ticketId, this);
            }
        }
    }

    public String getTicketId() {
        return ticketId;
    }


    public void completeSubscription() {
        try {
            clientStreamObserver.onCompleted();
        } catch (Exception e) {
            System.err.println("Error completing client stream for ticket " + ticketId + ": " + e.getMessage());
        }
    }
    public void handleError(Throwable t) {
        try {
            clientStreamObserver.onError(t);
        } catch (Exception e) {
            System.err.println("Error sending error to client for ticket " + ticketId + ": " + e.getMessage());
        }
    }


}
