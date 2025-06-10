package com.trenical.util;

import com.trenical.observer.NotificationEngine;
import com.trenical.rubyViaggiatreno.RubyViaggiatrenoClient;
import com.google.protobuf.Timestamp;
import ruby_viaggiatreno_microservizio.TrainStatusResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

public class TrainStatusPoller implements Runnable {
    private final RubyViaggiatrenoClient rubyClient;
    private final NotificationEngine notificationEngine;

    public TrainStatusPoller(RubyViaggiatrenoClient rubyClient, NotificationEngine notificationEngine) {
        this.rubyClient = rubyClient;
        this.notificationEngine = notificationEngine;
    }

    @Override
    public void run() {
        System.out.println("[Poller] Running status check at " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME));
        Set<String> subscribedTrainIds = notificationEngine.getSubscribedTrainIds();

        if (subscribedTrainIds.isEmpty()) {
            System.out.println("[Poller] No active subscriptions to poll.");
            return;
        }

        System.out.println("[Poller] Checking status for subscribed trains: " + subscribedTrainIds);
        for (String trainId : subscribedTrainIds) {
            String trainNumber = trainId;

            TrainStatusResponse newStatus = rubyClient.getTrainStatus(trainNumber);
            if (newStatus.getFound()) {
                if (notificationEngine.hasStatusChanged(trainNumber, newStatus)) {
                    System.out.println("[Poller] DETECTED STATUS CHANGE for train " + trainNumber);
                    notificationEngine.updateAndNotifyObservers(trainNumber, newStatus);
                } else {
                    System.out.println("[Poller] No status change for train " + trainNumber);
                }
            }
        }
    }
}
