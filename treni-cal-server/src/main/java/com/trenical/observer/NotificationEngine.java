package com.trenical.observer;

import ruby_viaggiatreno_microservizio.TrainStatusResponse;
import proto.TripChangeNotification;
import com.google.protobuf.Timestamp;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationEngine {
    private static NotificationEngine instance;
    private final Map<String, List<TripObserver>> trainObservers = new ConcurrentHashMap<>();
    private final Map<String, TrainStatusResponse> lastKnownStatuses = new ConcurrentHashMap<>();

    private NotificationEngine() {}

    public static synchronized NotificationEngine getInstance() {
        if (instance == null) {
            instance = new NotificationEngine();
        }
        return instance;
    }

    public void addObserver(String trainId, TripObserver observer) {
        trainObservers.computeIfAbsent(trainId, k -> new CopyOnWriteArrayList<>()).add(observer);
        System.out.println("[NotificationEngine] Aggiunto osservatore per il biglietto " + observer.getTicketId() + " sul treno " + trainId);
    }

    public void removeObserverForTicket(String ticketId, TripObserver observerToRemove) {
        trainObservers.forEach((trainId, observers) -> {
            observers.removeIf(obs -> obs.getTicketId().equals(ticketId) && obs.equals(observerToRemove));
        });
        System.out.println("[NotificationEngine] Tentata rimozione dell'osservatore per il biglietto " + ticketId);
    }


    public Set<String> getSubscribedTrainIds() {
        return trainObservers.keySet();
    }

    public boolean hasStatusChanged(String trainNumber, TrainStatusResponse newStatus) {
        TrainStatusResponse lastStatus = lastKnownStatuses.get(trainNumber);
        if (lastStatus == null) {
            return true; // Se è la prima volta che vediamo questo treno, è un cambiamento.
        }
        // Confronta i campi chiave per determinare se è avvenuto un cambiamento degno di notifica.
        return !(Objects.equals(lastStatus.getTrainStatusDescription(), newStatus.getTrainStatusDescription()) &&
                lastStatus.getDelayMinutes() == newStatus.getDelayMinutes() &&
                Objects.equals(lastStatus.getLastDetectedStation(), newStatus.getLastDetectedStation()));
    }


    public void updateAndNotifyObservers(String trainNumber, TrainStatusResponse newStatus) {
        // Aggiorna la cache con il nuovo stato
        lastKnownStatuses.put(trainNumber, newStatus);

        List<TripObserver> observers = trainObservers.get(trainNumber);
        if (observers == null || observers.isEmpty()) {
            return;
        }

        String message = String.format("Treno %s: %s. Ritardo: %d min. Ultima rilevazione: %s",
                newStatus.getTrainNumber(),
                newStatus.getTrainStatusDescription(),
                newStatus.getDelayMinutes(),
                newStatus.getLastDetectedStation());

        System.out.println("[NotificationEngine] Notificando " + observers.size() + " osservatori per il treno " + trainNumber);

        for (TripObserver observer : observers) {
            TripChangeNotification notification = TripChangeNotification.newBuilder()
                    .setTicketId(observer.getTicketId())
                    .setUpdateMessage(message)
                    .build();
            observer.sendUpdate(notification);
        }
    }
}









