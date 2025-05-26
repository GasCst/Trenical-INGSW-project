package com.trenical.observer;

import proto.TripChangeNotification;
import com.google.protobuf.Timestamp;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationEngine {
    private static NotificationEngine instance;
    private final Map<String, List<TripObserver>> trainObservers = new ConcurrentHashMap<>();

    private NotificationEngine() {}


    public static synchronized NotificationEngine getInstance() {
        if (instance == null) {
            instance = new NotificationEngine();
        }
        return instance;
    }


    public void addObserver(String trainId, TripObserver observer) {
        trainObservers.computeIfAbsent(trainId, k -> new CopyOnWriteArrayList<>()).add(observer);
        System.out.println("[NotificationEngine] Added observer for ticket " + observer.getTicketId() + " on train " + trainId);
    }

    public void removeObserver(String trainId, TripObserver observer) {
        List<TripObserver> observers = trainObservers.get(trainId);
        if (observers != null) {
            observers.remove(observer);
            System.out.println("[NotificationEngine] Removed observer for ticket " + observer.getTicketId() + " on train " + trainId);
        }
    }

    public void removeObserverForTicket(String ticketId, TripObserver observerToRemove) {
        trainObservers.forEach((trainId, observers) -> {
            observers.removeIf(obs -> obs.getTicketId().equals(ticketId) && obs.equals(observerToRemove));
        });
        System.out.println("[NotificationEngine] Attempted removal of observer for ticket " + ticketId);
    }



    public void notifyTripChange(String trainId, String ticketIdForUpdate, String message, Timestamp newTime, String newPlatform) {
        List<TripObserver> observers = trainObservers.get(trainId);
        if (observers != null && !observers.isEmpty()) {
            TripChangeNotification notification = TripChangeNotification.newBuilder()
                    .setTicketId(ticketIdForUpdate)
                    .setUpdateMessage(message)
                    .setNewDepartureTime(newTime == null ? Timestamp.newBuilder().setSeconds(0).build() : newTime)
                    .setNewPlatform(newPlatform == null ? "" : newPlatform)
                    .build();

            System.out.println("[NotificationEngine] Notifying " + observers.size() + " observers for train " + trainId + " about: " + message);
            for (TripObserver observer : observers) {
                if(observer.getTicketId().equals(ticketIdForUpdate)) {
                    observer.sendUpdate(notification);
                }
            }
        } else {
            System.out.println("[NotificationEngine] No observers for train " + trainId + " to notify about: " + message);
        }
    }


    public void simulateTrainDelay(String trainIdAffected, String ticketIdOnTrain, String delayMessage) {
        System.out.println("[NotificationEngine] Simulating delay for train " + trainIdAffected + ": " + delayMessage);
        Timestamp newDeparture = Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() + 7200).build();
        String platform = "TBD";
        notifyTripChange(trainIdAffected, ticketIdOnTrain, delayMessage, newDeparture, platform);
    }


}
