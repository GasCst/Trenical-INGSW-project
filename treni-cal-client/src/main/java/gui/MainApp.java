package gui;

import javafx.util.StringConverter;
import proto.*;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import proto.PurchaseTicketResponse;
import proto.Station;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainApp extends Application{

    private GrpcClientService grpcService;
    private TableView<TrainDisplay> trainTableView;
    private ObservableList<TrainDisplay> trainData = FXCollections.observableArrayList();
    private TextArea notificationArea;
    private TextField ticketIdForSubscriptionField;

    private ComboBox<Station> fromStationComboBox;
    private ComboBox<Station> toStationComboBox;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        grpcService = new GrpcClientService("localhost", 50051);
        primaryStage.setTitle("TreniCal Client");
        BorderPane rootLayout = new BorderPane();


        // --- Pannello di ricerca ---
        GridPane searchGrid = new GridPane();
        searchGrid.setPadding(new Insets(10));
        searchGrid.setHgap(10);
        searchGrid.setVgap(10);


        fromStationComboBox = createSearchableStationComboBox("Da:");
        toStationComboBox = createSearchableStationComboBox("A:");

        Button searchButton = new Button("Cerca Treni");
        searchButton.setDefaultButton(true);

        searchGrid.add(fromStationComboBox, 0, 0);
        searchGrid.add(toStationComboBox, 1, 0);
        searchGrid.add(searchButton, 2, 0);


        trainTableView = new TableView<>();
        setupTrainTableColumns();
        trainTableView.setItems(trainData);

        // Pannello Azioni
        HBox actionBox = createActionBox();
        actionBox.setPadding(new Insets(10));
        Button purchaseButton = new Button("Acquista Selezionato");
        ticketIdForSubscriptionField = new TextField();
        ticketIdForSubscriptionField.setPromptText("ID Biglietto");
        Button subscribeButton = new Button("Sottoscrivi Notifiche");
        actionBox.getChildren().addAll(purchaseButton, new Label("ID Biglietto:"), ticketIdForSubscriptionField, subscribeButton);

        // Area Notifiche
        VBox bottomBox = new VBox(5);
        bottomBox.setPadding(new Insets(10));
        notificationArea = new TextArea();
        notificationArea.setEditable(false);
        notificationArea.setPrefHeight(120);
        bottomBox.getChildren().addAll(new Label("Notifiche in Tempo Reale:"), notificationArea);

        VBox topControls = new VBox(10, searchGrid, trainTableView, actionBox);
        rootLayout.setCenter(topControls);
        rootLayout.setBottom(bottomBox);

        // Handlers Eventi
        searchButton.setOnAction(e -> handleSearchAction());
        purchaseButton.setOnAction(e -> handlePurchaseAction());
        subscribeButton.setOnAction(e -> handleSubscribeAction());

        // Setup Scena
        Scene scene = new Scene(rootLayout, 900, 750);
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

    private HBox createActionBox() {
        HBox actionBox = new HBox(10);
        actionBox.setPadding(new Insets(10));

        Button purchaseButton = new Button("Acquista Selezionato");

        // NUOVO PULSANTE per lo stato in tempo reale
        Button realTimeButton = new Button("Stato Real-time");

        actionBox.getChildren().addAll(purchaseButton, realTimeButton);

        purchaseButton.setOnAction(e -> handlePurchaseAction());

        // Handler per il nuovo pulsante
        realTimeButton.setOnAction(e -> {
            TrainDisplay selectedTrain = trainTableView.getSelectionModel().getSelectedItem();
            if (selectedTrain != null) {
                new Thread(() -> {
                    // CORREZIONE: Ora possiamo usare direttamente getTrainNumber(), perché contiene il numero corretto.
                    String trainNumber = selectedTrain.getTrainNumber();

                    String realTimeInfo = grpcService.getRealTimeTrainInfo(trainNumber);
                    Platform.runLater(() -> showAlert("Stato Treno", realTimeInfo));
                }).start();
            } else {
                showAlert("Errore", "Seleziona un treno per vederne lo stato.");
            }
        });

        return actionBox;
    }

    private void handleSearchAction() {
        Station selectedFromStation = fromStationComboBox.getValue();
        Station selectedToStation = toStationComboBox.getValue();

        if (selectedFromStation == null || selectedToStation == null) {
            showAlert("Errore", "Seleziona sia la stazione di partenza che quella di arrivo.");
            return;
        }
        if (selectedFromStation.getId().equals(selectedToStation.getId())) {
            showAlert("Errore", "La stazione di partenza e di arrivo non possono coincidere.");
            return;
        }
        List<TrainDisplay> results = grpcService.searchTrains(selectedFromStation, selectedToStation);
        trainData.setAll(results);
        trainTableView.sort();
    }

    private ComboBox<Station> createSearchableStationComboBox(String promptText) {
        ComboBox<Station> comboBox = new ComboBox<>();
        comboBox.setPromptText(promptText);
        comboBox.setEditable(true);

        comboBox.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) {
                comboBox.getItems().clear();
                return;
            }

            // Esegui la ricerca in un thread separato per non bloccare la GUI
            new Thread(() -> {
                // Chiama il nostro nuovo metodo di servizio
                List<Station> searchResult = grpcService.searchStations(newValue);

                // Aggiorna la UI sul thread di JavaFX
                Platform.runLater(() -> {
                    // Salva la stazione attualmente selezionata, se c'è
                    Station selected = comboBox.getSelectionModel().getSelectedItem();

                    // Aggiorna la lista dei suggerimenti
                    comboBox.getItems().setAll(searchResult);

                    // Se una stazione era già selezionata, prova a riselezionarla
                    if (selected != null) {
                        comboBox.getSelectionModel().select(selected);
                    }

                    // Mostra i risultati
                    if (!comboBox.isShowing()) {
                        comboBox.show();
                    }
                });
            }).start();
        });

        // Il converter è fondamentale per mostrare il nome della stazione
        comboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Station station) {
                return station == null ? "" : station.getName();
            }

            @Override
            public Station fromString(String string) {
                // Permette di selezionare un oggetto Station anche se l'utente ha solo digitato il testo
                return comboBox.getItems().stream()
                        .filter(s -> s.getName().equalsIgnoreCase(string))
                        .findFirst().orElse(null);
            }
        });

        return comboBox;
    }


    private void handlePurchaseAction() {
        TrainDisplay selectedTrain = trainTableView.getSelectionModel().getSelectedItem();
        if (selectedTrain != null) {
            if (selectedTrain.getDepartureInstant().isBefore(Instant.now())) {
                showAlert("Errore", "Non è possibile acquistare un biglietto per un treno già partito.");
                return;
            }
            PurchaseTicketResponse response = grpcService.purchaseTicket(selectedTrain.getId(), selectedTrain.getServiceClass(), 1);
            showAlert("Stato Acquisto", response.getMessage());
            if (response.getSuccess() && response.getPurchasedTicketsCount() > 0) {
                String purchasedTicketId = response.getPurchasedTickets(0).getId();
                appendNotification("Acquistato Biglietto ID: " + purchasedTicketId);
                ticketIdForSubscriptionField.setText(purchasedTicketId);
            }
        } else {
            showAlert("Errore", "Per favore, seleziona un treno da acquistare.");
        }
    }

    private void handleSubscribeAction() {
        String ticketId = ticketIdForSubscriptionField.getText();
        if (ticketId != null && !ticketId.isEmpty()) {
            grpcService.subscribeToTripChanges(ticketId,
                    notification -> Platform.runLater(() -> appendNotification("UPDATE per Treno: " + notification.getUpdateMessage())),
                    error -> Platform.runLater(() -> appendNotification("Errore Sottoscrizione: " + error.getMessage())),
                    () -> Platform.runLater(() -> appendNotification("Sottoscrizione terminata per " + ticketId))
            );
            appendNotification("Sottoscritto alle notifiche per il biglietto: " + ticketId);
        } else {
            showAlert("Errore", "Inserisci un ID Biglietto per sottoscrivere.");
        }
    }

    private void setupTrainTableColumns() {
        TableColumn<TrainDisplay, String> depTimeCol = new TableColumn<>("Partenza");
        depTimeCol.setCellValueFactory(new PropertyValueFactory<>("departureTime"));
        depTimeCol.setSortType(TableColumn.SortType.ASCENDING); // Default sort

        TableColumn<TrainDisplay, String> arrTimeCol = new TableColumn<>("Arrivo");
        arrTimeCol.setCellValueFactory(new PropertyValueFactory<>("arrivalTime"));

        TableColumn<TrainDisplay, String> fromCol = new TableColumn<>("Da");
        fromCol.setCellValueFactory(new PropertyValueFactory<>("departureStation"));

        TableColumn<TrainDisplay, String> toCol = new TableColumn<>("A");
        toCol.setCellValueFactory(new PropertyValueFactory<>("arrivalStation"));

        TableColumn<TrainDisplay, String> numberCol = new TableColumn<>("Treno N.");
        numberCol.setCellValueFactory(new PropertyValueFactory<>("trainNumber"));

        TableColumn<TrainDisplay, String> classCol = new TableColumn<>("Classe");
        classCol.setCellValueFactory(new PropertyValueFactory<>("serviceClass"));

        TableColumn<TrainDisplay, String> priceCol = new TableColumn<>("Prezzo");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));

        TableColumn<TrainDisplay, Integer> seatsCol = new TableColumn<>("Posti");
        seatsCol.setCellValueFactory(new PropertyValueFactory<>("availableSeats"));

        trainTableView.getColumns().setAll(depTimeCol, arrTimeCol, numberCol, classCol, priceCol, seatsCol);
        trainTableView.getSortOrder().add(depTimeCol);
    }

    private void appendNotification(String message) {
        notificationArea.appendText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " - " + message + "\n");
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}
