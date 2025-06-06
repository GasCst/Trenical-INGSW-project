package gui;

import javafx.util.StringConverter;
import proto.*;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.List;

public class MainApp extends Application{

    private GrpcClientService grpcService;
    private TableView<TrainDisplay> trainTableView;
    private ObservableList<TrainDisplay> trainData = FXCollections.observableArrayList();
    private TextArea notificationArea;
    private TextField ticketIdForSubscriptionField;

    private ComboBox<Station> fromStationComboBox;
    private ComboBox<Station> toStationComboBox;
    private ObservableList<Station> availableStations = FXCollections.observableArrayList();


    public static void main(String[] args) {
        launch(args);
    }
    @Override
    public void start(Stage primaryStage) {
        grpcService = new GrpcClientService("localhost", 50051);
        BorderPane rootLayout = new BorderPane();





        initializeStations();

        // --- Pannello di ricerca ---
        GridPane searchGrid = new GridPane();
        searchGrid.setPadding(new Insets(10));
        searchGrid.setHgap(10);
        searchGrid.setVgap(5);

        Label fromLabel = new Label("Da:");
        fromStationComboBox = new ComboBox<>(availableStations);
        Label toLabel = new Label("A:");
        toStationComboBox = new ComboBox<>(availableStations);

        StringConverter<Station> stationConverter = new StringConverter<Station>() {
            @Override
            public String toString(Station station) {
                return station == null ? null : station.getName();
            }

            @Override
            public Station fromString(String string) {
                return null;
            }
        };





        fromStationComboBox.setConverter(stationConverter);
        toStationComboBox.setConverter(stationConverter);

        if (!availableStations.isEmpty()) {
            availableStations.stream().filter(s -> "Roma Termini".equals(s.getName())).findFirst().ifPresent(fromStationComboBox::setValue);
            availableStations.stream().filter(s -> "Milano Centrale".equals(s.getName())).findFirst().ifPresent(toStationComboBox::setValue);
            if(fromStationComboBox.getValue() == null) fromStationComboBox.getSelectionModel().selectFirst();
            if(toStationComboBox.getValue() == null) toStationComboBox.getSelectionModel().selectLast();
        }

        Label dateLabel = new Label("Data:");
        DatePicker datePicker = new DatePicker(LocalDate.now());
        Button searchButton = new Button("Cerca Treni");

        searchGrid.add(fromLabel, 0, 0); searchGrid.add(fromStationComboBox, 1, 0);
        searchGrid.add(toLabel, 0, 1); searchGrid.add(toStationComboBox, 1, 1);
        searchGrid.add(dateLabel, 0, 2); searchGrid.add(datePicker, 1, 2);
        searchGrid.add(searchButton, 1, 3);


        trainTableView = new TableView<>();
        setupTrainTableColumns();
        trainTableView.setItems(trainData);

        // --- Pannello acquisti ---
        HBox purchaseBox = new HBox(10);
        purchaseBox.setPadding(new Insets(10));
        Button purchaseButton = new Button("Purchase Selected");
        purchaseBox.getChildren().addAll(purchaseButton);


        // --- Area Notifiche ---
        VBox notificationBox = new VBox(5);
        notificationBox.setPadding(new Insets(10));
        Label notificationLabel = new Label("Real-time Notifications:");
        notificationArea = new TextArea();
        notificationArea.setEditable(false);
        notificationArea.setPrefHeight(100);

        HBox subscriptionBox = new HBox(10);
        ticketIdForSubscriptionField = new TextField();
        ticketIdForSubscriptionField.setPromptText("Enter Ticket ID to subscribe");
        Button subscribeButton = new Button("Subscribe to Updates");
        subscriptionBox.getChildren().addAll(new Label("Ticket ID:"), ticketIdForSubscriptionField, subscribeButton);
        notificationBox.getChildren().addAll(notificationLabel, subscriptionBox, notificationArea);



        VBox topControls = new VBox(searchGrid, trainTableView, purchaseBox);
        rootLayout.setCenter(topControls);
        rootLayout.setBottom(notificationBox);


        // --------  Handlers eventi  ------------

        searchButton.setOnAction(e -> {
            Station selectedFromStation = fromStationComboBox.getValue();
            Station selectedToStation = toStationComboBox.getValue();

            if (selectedFromStation == null || selectedToStation == null) {
                showAlert("Errore", "Seleziona sia la stazione di partenza che quella di arrivo.");
                return;
            }

            LocalDate ld = datePicker.getValue();
            TravelDate travelDate = TravelDate.newBuilder().setYear(ld.getYear()).setMonth(ld.getMonthValue()).setDay(ld.getDayOfMonth()).build();

            List<TrainDisplay> results = grpcService.searchTrains(selectedFromStation, selectedToStation, travelDate);
            trainData.setAll(results);
        });

        purchaseButton.setOnAction(e -> {
            TrainDisplay selectedTrain = trainTableView.getSelectionModel().getSelectedItem();
            if (selectedTrain != null) {
                // For simplicity, hardcoding service class and num tickets
                PurchaseTicketResponse response = grpcService.purchaseTicket(selectedTrain.getId(), selectedTrain.getServiceClass(), 1);
                showAlert("Purchase Status", response.getMessage());
                if (response.getSuccess() && response.getPurchasedTicketsCount() > 0) {
                    String purchasedTicketId = response.getPurchasedTickets(0).getId();
                    appendNotification("Purchased Ticket ID: " + purchasedTicketId + ". Consider subscribing to updates.");
                    ticketIdForSubscriptionField.setText(purchasedTicketId); // Pre-fill for easy subscription
                }
            } else {
                showAlert("Error", "Please select a train to purchase.");
            }
        });

        subscribeButton.setOnAction(e -> {
            String ticketId = ticketIdForSubscriptionField.getText();
            if (ticketId != null && !ticketId.isEmpty()) {
                grpcService.subscribeToTripChanges(ticketId,
                        notification -> Platform.runLater(() -> appendNotification("Update for " + ticketId + ": " + notification.getUpdateMessage())),
                        error -> Platform.runLater(() -> appendNotification("Subscription Error for " + ticketId + ": " + error.getMessage())),
                        () -> Platform.runLater(() -> appendNotification("Subscription ended for " + ticketId))
                );
                appendNotification("Subscribed to updates for ticket: " + ticketId);
            } else {
                showAlert("Error", "Please enter a Ticket ID to subscribe.");
            }
        });


        Scene scene = new Scene(rootLayout, 800, 700);
        String cssPath = getClass().getResource("styles.css").toExternalForm();
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        } else {
            System.err.println("Attenzione: Impossibile trovare styles.css");
        }
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> {
            try {
                grpcService.shutdown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private void initializeStations() {
        try {
            List<Station> stationsFromServer = grpcService.getAvailableStations();
            Platform.runLater(() -> {
                availableStations.setAll(stationsFromServer);

                if (!availableStations.isEmpty()) {
                    availableStations.stream().filter(s -> "Roma Termini".equals(s.getName())).findFirst().ifPresent(fromStationComboBox::setValue);
                    availableStations.stream().filter(s -> "Milano Centrale".equals(s.getName())).findFirst().ifPresent(toStationComboBox::setValue);
                    if(fromStationComboBox.getValue() == null && !availableStations.isEmpty()) fromStationComboBox.getSelectionModel().selectFirst();
                    if(toStationComboBox.getValue() == null && availableStations.size() > 1) toStationComboBox.getSelectionModel().select(1); else if (toStationComboBox.getValue() == null && !availableStations.isEmpty()) toStationComboBox.getSelectionModel().selectFirst();

                }
            });
        } catch (Exception e) {
            Platform.runLater(() -> {
                showAlert("Errore Caricamento Stazioni", "Impossibile caricare l'elenco delle stazioni dal server: " + e.getMessage());
            });
        }
    }

    private void setupTrainTableColumns() {
        TableColumn<TrainDisplay, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<TrainDisplay, String> numberCol = new TableColumn<>("Train No.");
        numberCol.setCellValueFactory(new PropertyValueFactory<>("trainNumber"));

        TableColumn<TrainDisplay, String> fromCol = new TableColumn<>("From");
        fromCol.setCellValueFactory(new PropertyValueFactory<>("departureStation"));

        TableColumn<TrainDisplay, String> toCol = new TableColumn<>("To");
        toCol.setCellValueFactory(new PropertyValueFactory<>("arrivalStation"));

        TableColumn<TrainDisplay, String> depTimeCol = new TableColumn<>("Departure");
        depTimeCol.setCellValueFactory(new PropertyValueFactory<>("departureTime"));

        TableColumn<TrainDisplay, String> arrTimeCol = new TableColumn<>("Arrival");
        arrTimeCol.setCellValueFactory(new PropertyValueFactory<>("arrivalTime"));

        TableColumn<TrainDisplay, String> classCol = new TableColumn<>("Class");
        classCol.setCellValueFactory(new PropertyValueFactory<>("serviceClass"));

        TableColumn<TrainDisplay, String> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));

        TableColumn<TrainDisplay, Integer> seatsCol = new TableColumn<>("Seats");
        seatsCol.setCellValueFactory(new PropertyValueFactory<>("availableSeats"));

        trainTableView.getColumns().addAll(idCol, numberCol, fromCol, toCol, depTimeCol, arrTimeCol, classCol, priceCol, seatsCol);
    }

    private void appendNotification(String message) {
        notificationArea.appendText(message + "\n");
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }






}
