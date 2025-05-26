package com.trenical.database;

import proto.Station;
import proto.Train;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TrainDatabase {
    private static TrainDatabase instance;
    private final Map<String, Train> trains = new ConcurrentHashMap<>();
    private final TicketDatabase ticketDatabase = TicketDatabase.getInstance();


    private TrainDatabase() {

        Station rome = Station.newBuilder().setId("RM001").setName("Roma Termini").build();
        Station milan = Station.newBuilder().setId("MI001").setName("Milano Centrale").build();
        Station naples = Station.newBuilder().setId("NA001").setName("Napoli Centrale").build();

        trains.put("TR001", Train.newBuilder()
                .setId("TR001").setTrainNumber("FR 9600")
                .setDepartureStation(rome).setArrivalStation(milan)
                .setDepartureTime(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() + 3600 * 2).build())
                .setArrivalTime(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() + 3600 * 5).build())
                .setServiceClass("Standard").setPrice(50.00).setAvailableSeats(100).setTrainType("High-Speed")
                .build());
        trains.put("TR002", Train.newBuilder()
                .setId("TR002").setTrainNumber("IC 650")
                .setDepartureStation(rome).setArrivalStation(naples)
                .setDepartureTime(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() + 3600 * 3).build())
                .setArrivalTime(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() + 3600 * 5).build())
                .setServiceClass("Business").setPrice(35.00).setAvailableSeats(50).setTrainType("Intercity")
                .build());
        trains.put("TR003", Train.newBuilder()
                .setId("TR003").setTrainNumber("FR 9602")
                .setDepartureStation(rome).setArrivalStation(milan)
                .setDepartureTime(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() + 86400 + 3600 * 4).build())
                .setArrivalTime(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() + 86400 + 3600 * 7).build())
                .setServiceClass("Standard").setPrice(55.00).setAvailableSeats(120).setTrainType("High-Speed")
                .build());
    }

    public static synchronized TrainDatabase getInstance() {
        if (instance == null) {
            instance = new TrainDatabase();
        }
        return instance;
    }

    public List<Train> getAllTrains() {
        return new ArrayList<>(trains.values());
    }

    public Train getTrainById(String trainId) {
        return trains.get(trainId);
    }

    public int getAvailableSeats(String trainId, String serviceClass) {
        Train train = trains.get(trainId);
        if (train == null || !train.getServiceClass().equals(serviceClass)) {
            return 0;
        }

        long soldTickets = ticketDatabase.getTicketsForTrainAndClass(trainId, serviceClass).size();
        int totalSeatsForClass = 100; // Example: assume 100 seats for any class this train offers
        if(train != null && train.getId().equals("TR001") && serviceClass.equals("Standard")) totalSeatsForClass = 100;
        if(train != null && train.getId().equals("TR002") && serviceClass.equals("Business")) totalSeatsForClass = 50;
        if(train != null && train.getId().equals("TR003") && serviceClass.equals("Standard")) totalSeatsForClass = 120;


        return Math.max(0, totalSeatsForClass - (int)soldTickets);
    }

    public void updateTrainStatus(String trainId, String status, String platform, Timestamp newArrivalTime) {
        Train train = trains.get(trainId);
        if (train != null) {
            Train updatedTrain = train.toBuilder()
                    .setArrivalTime(newArrivalTime)
                    .build();
            System.out.println("[TrainDatabase] Status update for " + trainId + ": " + status + ", Platform: " + platform);
        }
    }
}
