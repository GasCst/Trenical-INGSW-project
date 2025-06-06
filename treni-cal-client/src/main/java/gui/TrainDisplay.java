package gui;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class TrainDisplay {
    private final SimpleStringProperty id;
    private final SimpleStringProperty trainNumber;
    private final SimpleStringProperty departureStation;
    private final SimpleStringProperty arrivalStation;
    private final SimpleStringProperty departureTime;
    private final SimpleStringProperty arrivalTime;
    private final SimpleStringProperty serviceClass;
    private final SimpleStringProperty price;
    private final SimpleIntegerProperty availableSeats;

    public TrainDisplay(String id, String trainNumber, String departureStation, String arrivalStation,
                        String departureTime, String arrivalTime, String serviceClass, double price, int availableSeats) {
        this.id = new SimpleStringProperty(id);
        this.trainNumber = new SimpleStringProperty(trainNumber);
        this.departureStation = new SimpleStringProperty(departureStation);
        this.arrivalStation = new SimpleStringProperty(arrivalStation);
        this.departureTime = new SimpleStringProperty(departureTime);
        this.arrivalTime = new SimpleStringProperty(arrivalTime);
        this.serviceClass = new SimpleStringProperty(serviceClass);
        this.price = new SimpleStringProperty(String.format("%.2f", price));
        this.availableSeats = new SimpleIntegerProperty(availableSeats);
    }


    // Getter methods for JavaFX properties (e.g., idProperty(), getTrainNumber(), etc.)
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
