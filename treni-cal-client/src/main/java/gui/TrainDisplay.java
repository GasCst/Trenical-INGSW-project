package gui;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TrainDisplay {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private final SimpleStringProperty id;
    private final SimpleStringProperty trainNumber;
    private final SimpleStringProperty departureStation;
    private final SimpleStringProperty arrivalStation;
    private final SimpleStringProperty departureTime;
    private final SimpleStringProperty arrivalTime;
    private final SimpleStringProperty serviceClass;
    private final SimpleStringProperty price;
    private final SimpleIntegerProperty availableSeats;
    private final Instant departureInstant;

    public TrainDisplay(String id, String trainNumber, String departureStation, String arrivalStation,
                        Instant departureInstant, Instant arrivalInstant, String serviceClass, double price, int availableSeats) {
        this.id = new SimpleStringProperty(id);
        this.trainNumber = new SimpleStringProperty(trainNumber);
        this.departureStation = new SimpleStringProperty(departureStation);
        this.arrivalStation = new SimpleStringProperty(arrivalStation);
        this.departureInstant = departureInstant;
        this.departureTime = new SimpleStringProperty(TIME_FORMATTER.format(departureInstant));
        this.arrivalTime = new SimpleStringProperty(TIME_FORMATTER.format(arrivalInstant));
        this.serviceClass = new SimpleStringProperty(serviceClass);
        this.price = new SimpleStringProperty(String.format("%.2f", price));
        this.availableSeats = new SimpleIntegerProperty(availableSeats);
    }

    public Instant getDepartureInstant() {
        return departureInstant;
    }



    public String getId() { return id.get(); }
    public SimpleStringProperty idProperty() { return id; }
    public String getTrainNumber() { return trainNumber.get(); }
    public SimpleStringProperty trainNumberProperty() { return trainNumber; }
    public String getDepartureStation() { return departureStation.get(); }
    public SimpleStringProperty departureStationProperty() { return departureStation; }
    public String getArrivalStation() { return arrivalStation.get(); }
    public SimpleStringProperty arrivalStationProperty() { return arrivalStation; }
    public String getDepartureTime() { return departureTime.get(); }
    public SimpleStringProperty departureTimeProperty() { return departureTime; }
    public String getArrivalTime() { return arrivalTime.get(); }
    public SimpleStringProperty arrivalTimeProperty() { return arrivalTime; }
    public String getServiceClass() { return serviceClass.get(); }
    public SimpleStringProperty serviceClassProperty() { return serviceClass; }
    public String getPrice() { return price.get(); }
    public SimpleStringProperty priceProperty() { return price; }
    public int getAvailableSeats() { return availableSeats.get(); }
    public SimpleIntegerProperty availableSeatsProperty() { return availableSeats; }


}
