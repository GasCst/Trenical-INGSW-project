package gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import proto.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainApp extends Application {

    private GrpcClientService grpcService;
    private TableView<TrainDisplay> trainTableView;
    private ObservableList<TrainDisplay> trainData = FXCollections.observableArrayList();
    private TextArea notificationArea;
    private TextField ticketIdForSubscriptionField;
    private ComboBox<Station> fromStationComboBox;
    private ComboBox<Station> toStationComboBox;
    private DatePicker datePicker;
    private Spinner<Integer> timeHourSpinner;
    private Spinner<Integer> timeMinuteSpinner;
    private ComboBox<String> trainTypeFilter;
    private Label statusLabel;
    private Button searchButton;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        grpcService = new GrpcClientService("localhost", 50051);
        primaryStage.setTitle("TreniCal - Sistema Gestione Treni");

        // Main layout
        BorderPane rootLayout = new BorderPane();
        rootLayout.setStyle("-fx-background-color: #f8f9fa;");

        // Create header
        VBox header = createHeader();
        rootLayout.setTop(header);

        // Create search panel
        VBox searchPanel = createSearchPanel();

        // Create results panel
        VBox resultsPanel = createResultsPanel();

        // Create action panel
        VBox actionPanel = createActionPanel();

        // Create notification panel
        VBox notificationPanel = createNotificationPanel();

        // Main content area
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.getChildren().addAll(searchPanel, resultsPanel, actionPanel);

        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #f8f9fa; -fx-background-color: #f8f9fa;");

        rootLayout.setCenter(scrollPane);
        rootLayout.setBottom(notificationPanel);

        // Setup scene
        Scene scene = new Scene(rootLayout, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Initialize status
        updateStatus("Sistema pronto. Seleziona stazioni per cercare treni.");

        primaryStage.setOnCloseRequest(event -> {
            try {
                grpcService.shutdown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private VBox createHeader() {
        VBox header = new VBox();
        header.setStyle("-fx-background-color: linear-gradient(to right, #2c3e50, #3498db); -fx-padding: 20;");
        header.setAlignment(Pos.CENTER);

        Label titleLabel = new Label("🚂 TreniCal");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.WHITE);

        Label subtitleLabel = new Label("Sistema Distribuito per la Gestione di Biglietti e Servizi Ferroviari");
        subtitleLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
        subtitleLabel.setTextFill(Color.LIGHTGRAY);

        header.getChildren().addAll(titleLabel, subtitleLabel);
        return header;
    }

    private VBox createSearchPanel() {
        VBox searchPanel = new VBox(15);
        searchPanel.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);");

        Label searchTitle = new Label("🔍 Ricerca Treni");
        searchTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        searchTitle.setTextFill(Color.valueOf("#2c3e50"));

        // Station selection
        GridPane stationGrid = new GridPane();
        stationGrid.setHgap(15);
        stationGrid.setVgap(10);
        stationGrid.setAlignment(Pos.CENTER_LEFT);

        Label fromLabel = new Label("Stazione di partenza:");
        fromLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        fromStationComboBox = createSearchableStationComboBox("Es. Roma Termini");
        fromStationComboBox.setPrefWidth(250);

        Label toLabel = new Label("Stazione di arrivo:");
        toLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        toStationComboBox = createSearchableStationComboBox("Es. Milano Centrale");
        toStationComboBox.setPrefWidth(250);

        stationGrid.add(fromLabel, 0, 0);
        stationGrid.add(fromStationComboBox, 0, 1);
        stationGrid.add(toLabel, 1, 0);
        stationGrid.add(toStationComboBox, 1, 1);

        // Date and time selection
        GridPane dateTimeGrid = new GridPane();
        dateTimeGrid.setHgap(15);
        dateTimeGrid.setVgap(10);
        dateTimeGrid.setAlignment(Pos.CENTER_LEFT);

        Label dateLabel = new Label("Data:");
        dateLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        datePicker = new DatePicker();
        datePicker.setValue(LocalDate.now());
        datePicker.setPrefWidth(150);

        Label timeLabel = new Label("Orario preferito:");
        timeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        HBox timeBox = new HBox(5);
        timeHourSpinner = new Spinner<>(0, 23, LocalTime.now().getHour());
        timeHourSpinner.setPrefWidth(70);
        timeMinuteSpinner = new Spinner<>(0, 59, LocalTime.now().getMinute(), 15);
        timeMinuteSpinner.setPrefWidth(70);

        Label colonLabel = new Label(":");
        colonLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        timeBox.getChildren().addAll(timeHourSpinner, colonLabel, timeMinuteSpinner);
        timeBox.setAlignment(Pos.CENTER_LEFT);

        Label filterLabel = new Label("Tipo treno:");
        filterLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        trainTypeFilter = new ComboBox<>();
        trainTypeFilter.getItems().addAll("Tutti", "Alta Velocità", "Intercity", "Regionale");
        trainTypeFilter.setValue("Tutti");
        trainTypeFilter.setPrefWidth(150);

        dateTimeGrid.add(dateLabel, 0, 0);
        dateTimeGrid.add(datePicker, 0, 1);
        dateTimeGrid.add(timeLabel, 1, 0);
        dateTimeGrid.add(timeBox, 1, 1);
        dateTimeGrid.add(filterLabel, 2, 0);
        dateTimeGrid.add(trainTypeFilter, 2, 1);

        // Search buttons
        HBox searchButtonsBox = createSearchButtonsBox();

        // Status label
        statusLabel = new Label("Sistema pronto");
        statusLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 11));
        statusLabel.setTextFill(Color.GRAY);

        searchPanel.getChildren().addAll(searchTitle, stationGrid, dateTimeGrid, searchButtonsBox, statusLabel);
        return searchPanel;
    }

    private HBox createSearchButtonsBox() {
        // Search button
        searchButton = new Button("🔍 Cerca Treni");
        searchButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 5;");
        searchButton.setOnAction(e -> handleSearchAction());
        searchButton.setPrefWidth(150);

        // New search button for resetting the form
        Button newSearchButton = new Button("🔄 Nuova Ricerca");
        newSearchButton.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 5;");
        newSearchButton.setOnAction(e -> resetSearchForm());
        newSearchButton.setPrefWidth(150);

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox buttonBox = new HBox(10);
        buttonBox.getChildren().addAll(searchButton, newSearchButton, spacer);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        return buttonBox;
    }

    private VBox createResultsPanel() {
        VBox resultsPanel = new VBox(10);
        resultsPanel.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);");

        Label resultsTitle = new Label("🚄 Risultati Ricerca");
        resultsTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        resultsTitle.setTextFill(Color.valueOf("#2c3e50"));

        trainTableView = new TableView<>();
        setupTrainTableColumns();
        trainTableView.setItems(trainData);
        trainTableView.setPrefHeight(300);
        trainTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Custom row factory for styling departed trains
        trainTableView.setRowFactory(tv -> {
            TableRow<TrainDisplay> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null) {
                    if (newItem.getDepartureInstant().isBefore(Instant.now())) {
                        row.setStyle("-fx-background-color: #f8f9fa; -fx-text-fill: #6c757d;");
                    } else {
                        row.setStyle("");
                    }
                }
            });
            return row;
        });

        resultsPanel.getChildren().addAll(resultsTitle, trainTableView);
        return resultsPanel;
    }

    private VBox createActionPanel() {
        VBox actionPanel = new VBox(15);
        actionPanel.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);");

        Label actionTitle = new Label("🎫 Azioni");
        actionTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        actionTitle.setTextFill(Color.valueOf("#2c3e50"));

        // Action buttons
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        Button purchaseButton = new Button("💳 Acquista Biglietto");
        purchaseButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 15; -fx-background-radius: 5;");
        purchaseButton.setOnAction(e -> handlePurchaseAction());

        Button realTimeButton = new Button("📡 Stato Real-time");
        realTimeButton.setStyle("-fx-background-color: #fd7e14; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 15; -fx-background-radius: 5;");
        realTimeButton.setOnAction(e -> handleRealTimeAction());

        Button refreshButton = new Button("🔄 Aggiorna");
        refreshButton.setStyle("-fx-background-color: #6f42c1; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 15; -fx-background-radius: 5;");
        refreshButton.setOnAction(e -> handleSearchAction());

        buttonBox.getChildren().addAll(purchaseButton, realTimeButton, refreshButton);

        // Subscription section
        HBox subscriptionBox = new HBox(10);
        subscriptionBox.setAlignment(Pos.CENTER_LEFT);

        Label subscriptionLabel = new Label("ID Biglietto per notifiche:");
        subscriptionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        ticketIdForSubscriptionField = new TextField();
        ticketIdForSubscriptionField.setPromptText("Inserisci ID biglietto...");
        ticketIdForSubscriptionField.setPrefWidth(200);

        Button subscribeButton = new Button("🔔 Sottoscrivi Notifiche");
        subscribeButton.setStyle("-fx-background-color: #17a2b8; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 15; -fx-background-radius: 5;");
        subscribeButton.setOnAction(e -> handleSubscribeAction());

        subscriptionBox.getChildren().addAll(subscriptionLabel, ticketIdForSubscriptionField, subscribeButton);

        actionPanel.getChildren().addAll(actionTitle, buttonBox, subscriptionBox);
        return actionPanel;
    }

    private VBox createNotificationPanel() {
        VBox notificationPanel = new VBox(10);
        notificationPanel.setStyle("-fx-background-color: #2c3e50; -fx-padding: 15;");

        Label notificationTitle = new Label("📢 Notifiche in Tempo Reale");
        notificationTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        notificationTitle.setTextFill(Color.WHITE);

        notificationArea = new TextArea();
        notificationArea.setEditable(false);
        notificationArea.setPrefHeight(120);
        notificationArea.setStyle("-fx-control-inner-background: #34495e; -fx-text-fill: #ecf0f1; -fx-font-family: 'Courier New'; -fx-font-size: 11px;");
        notificationArea.setWrapText(true);

        notificationPanel.getChildren().addAll(notificationTitle, notificationArea);
        return notificationPanel;
    }

    private ComboBox<Station> createSearchableStationComboBox(String promptText) {
        ComboBox<Station> comboBox = new ComboBox<>();
        comboBox.setPromptText(promptText);
        comboBox.setEditable(true);

        // Add clear button functionality
        comboBox.getEditor().setOnMouseClicked(e -> {
            if (e.getClickCount() == 3) { // Triple-click to clear
                clearComboBoxSelection(comboBox);
            }
        });

        // Store the last selected station to prevent clearing
        Station[] lastSelectedStation = new Station[1];
        boolean[] isUpdatingFromSelection = new boolean[1]; // Prevent recursive updates

        comboBox.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            if (isUpdatingFromSelection[0]) return; // Skip if we're updating from selection

            if (newValue == null || newValue.length() < 2) {
                // Only clear items if no station is selected
                if (comboBox.getSelectionModel().getSelectedItem() == null) {
                    comboBox.getItems().clear();
                    lastSelectedStation[0] = null;
                }
                return;
            }

            // Check if user is trying to clear or change the field
            Station currentSelection = comboBox.getSelectionModel().getSelectedItem();
            if (currentSelection != null) {
                String currentDisplayName = extractStationDisplayName(currentSelection);

                // If user deleted part of the station name, clear the selection
                if (newValue.length() < currentDisplayName.length() &&
                        !currentDisplayName.toLowerCase().startsWith(newValue.toLowerCase())) {
                    comboBox.getSelectionModel().clearSelection();
                    lastSelectedStation[0] = null;
                }

                // If the text exactly matches current selection, don't search
                if (newValue.equals(currentDisplayName) ||
                        newValue.equals(currentSelection.getName())) {
                    return;
                }
            }

            // Extract clean search query
            String cleanQuery = extractCleanStationName(newValue);

            // Perform search in background
            new Thread(() -> {
                try {
                    List<Station> searchResult = grpcService.searchStations(cleanQuery);
                    Platform.runLater(() -> {
                        // Check if the text field still contains our search query
                        if (cleanQuery.equals(extractCleanStationName(comboBox.getEditor().getText()))) {
                            comboBox.getItems().setAll(searchResult);

                            if (!searchResult.isEmpty() && !comboBox.isShowing()) {
                                comboBox.show();
                            }
                        }
                    });
                } catch (Exception ex) {
                    System.err.println("Error searching stations: " + ex.getMessage());
                }
            }).start();
        });

        // Handle selection changes
        comboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldStation, newStation) -> {
            if (newStation != null && !isUpdatingFromSelection[0]) {
                lastSelectedStation[0] = newStation;
                isUpdatingFromSelection[0] = true;

                Platform.runLater(() -> {
                    String displayName = extractStationDisplayName(newStation);
                    comboBox.getEditor().setText(displayName);
                    comboBox.hide(); // Hide dropdown after selection
                    isUpdatingFromSelection[0] = false;
                });
            }
        });

        // Handle focus events
        comboBox.getEditor().focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                // When gaining focus, select all text for easy replacement
                Platform.runLater(() -> {
                    comboBox.getEditor().selectAll();
                });
            }
        });

        // Custom string converter
        comboBox.setConverter(new StringConverter<Station>() {
            @Override
            public String toString(Station station) {
                if (station == null) return "";
                return extractStationDisplayName(station);
            }

            @Override
            public Station fromString(String string) {
                if (string == null || string.trim().isEmpty()) {
                    return null;
                }

                // First, try to find exact match in current items
                for (Station station : comboBox.getItems()) {
                    if (station.getName().equals(string) ||
                            extractStationDisplayName(station).equals(string) ||
                            extractCleanStationName(station.getName()).equalsIgnoreCase(extractCleanStationName(string))) {
                        return station;
                    }
                }

                // Check last selected station
                if (lastSelectedStation[0] != null) {
                    String lastStationDisplay = extractStationDisplayName(lastSelectedStation[0]);
                    String cleanLast = extractCleanStationName(lastStationDisplay);
                    String cleanInput = extractCleanStationName(string);

                    if (cleanLast.equalsIgnoreCase(cleanInput)) {
                        return lastSelectedStation[0];
                    }
                }

                return null;
            }
        });

        return comboBox;
    }

    private void clearComboBoxSelection(ComboBox<Station> comboBox) {
        Platform.runLater(() -> {
            comboBox.getSelectionModel().clearSelection();
            comboBox.getEditor().clear();
            comboBox.getItems().clear();
            comboBox.hide();
        });
    }

    private void resetSearchForm() {
        clearComboBoxSelection(fromStationComboBox);
        clearComboBoxSelection(toStationComboBox);
        datePicker.setValue(LocalDate.now());
        timeHourSpinner.getValueFactory().setValue(LocalTime.now().getHour());
        timeMinuteSpinner.getValueFactory().setValue(0);
        trainTypeFilter.setValue("Tutti");
        trainData.clear();
        updateStatus("Sistema pronto per una nuova ricerca.");
    }

    /**
     * Extract clean station name for display in the text field
     */
    private String extractStationDisplayName(Station station) {
        if (station == null || station.getName() == null) {
            return "";
        }

        String name = station.getName();

        // Remove emoji and extra formatting for display
        name = name.replaceAll(" 🚉", "")
                .replaceAll(" 🚏", "");

        // Extract just the main station name (before parentheses)
        int parenIndex = name.indexOf(" (");
        if (parenIndex > 0) {
            return name.substring(0, parenIndex);
        }

        return name;
    }

    /**
     * Extract clean station name for searching
     */
    private String extractCleanStationName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }

        // Remove all formatting: emojis, parentheses content, extra spaces
        String clean = name.replaceAll(" 🚉", "")
                .replaceAll(" 🚏", "")
                .replaceAll("\\s*\\([^)]*\\)", "")
                .trim();

        return clean;
    }

    private void handleSearchAction() {
        Station from = fromStationComboBox.getValue();
        Station to = toStationComboBox.getValue();

        if (from == null || to == null) {
            showAlert("Errore di Validazione", "Seleziona sia la stazione di partenza che di arrivo.", Alert.AlertType.WARNING);
            return;
        }
        if (from.getId().equals(to.getId())) {
            showAlert("Errore di Validazione", "Le stazioni di partenza e arrivo non possono coincidere.", Alert.AlertType.WARNING);
            return;
        }

        searchButton.setDisable(true);
        searchButton.setText("Ricerca...");
        updateStatus("🔍 Ricerca treni in corso per " + from.getName() + " → " + to.getName());

        new Thread(() -> {
            try {
                List<TrainDisplay> results = grpcService.searchTrains(from, to);
                Platform.runLater(() -> {
                    trainData.setAll(results);
                    trainTableView.sort();
                    if (results.isEmpty()) {
                        updateStatus("❌ Nessun treno diretto trovato.");
                        showAlert("Nessun Risultato", "Nessun treno diretto trovato per la tratta selezionata.", Alert.AlertType.INFORMATION);
                    } else {
                        updateStatus("✅ Trovati " + results.size() + " treni.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    updateStatus("❌ Errore durante la ricerca.");
                    showAlert("Errore di Rete", "Impossibile completare la ricerca: " + e.getMessage(), Alert.AlertType.ERROR);
                });
            } finally {
                Platform.runLater(() -> {
                    searchButton.setDisable(false);
                    searchButton.setText("🔍 Cerca Treni");
                });
            }
        }).start();
    }

    private void handlePurchaseAction() {
        TrainDisplay selectedTrain = trainTableView.getSelectionModel().getSelectedItem();
        if (selectedTrain == null) {
            showAlert("Selezione Richiesta", "Seleziona un treno dalla tabella per procedere con l'acquisto.", Alert.AlertType.WARNING);
            return;
        }
        if (selectedTrain.getDepartureInstant().isBefore(Instant.now())) {
            showAlert("Treno Partito", "Non è possibile acquistare un biglietto per un treno già partito.", Alert.AlertType.ERROR);
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Conferma Acquisto");
        confirmAlert.setHeaderText("Stai per acquistare un biglietto per il treno " + selectedTrain.getTrainNumber());
        confirmAlert.setContentText(String.format("Da: %s\nA: %s\nPartenza: %s\nPrezzo: €%s\n\nProcedere con l'acquisto?",
                selectedTrain.getDepartureStation(), selectedTrain.getArrivalStation(), selectedTrain.getDepartureTime(), selectedTrain.getPrice()));

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    PurchaseTicketResponse purchaseResponse = grpcService.purchaseTicket(selectedTrain.getId(), selectedTrain.getServiceClass(), 1);
                    Platform.runLater(() -> {
                        if (purchaseResponse.getSuccess()) {
                            String ticketId = purchaseResponse.getPurchasedTickets(0).getId();
                            showAlert("Acquisto Riuscito", "Biglietto acquistato con successo!\nID Biglietto: " + ticketId, Alert.AlertType.INFORMATION);
                            ticketIdForSubscriptionField.setText(ticketId);
                            appendNotification("💳 Biglietto acquistato: " + ticketId);
                        } else {
                            showAlert("Acquisto Fallito", purchaseResponse.getMessage(), Alert.AlertType.ERROR);
                        }
                    });
                }).start();
            }
        });
    }

    private void handleRealTimeAction() {
        TrainDisplay selectedTrain = trainTableView.getSelectionModel().getSelectedItem();
        if (selectedTrain == null) {
            showAlert("Selezione Richiesta", "Seleziona un treno per vederne lo stato in tempo reale.", Alert.AlertType.WARNING);
            return;
        }

        updateStatus("📡 Recupero stato per treno " + selectedTrain.getTrainNumber() + "...");
        new Thread(() -> {
            String realTimeInfo = grpcService.getRealTimeTrainInfo(selectedTrain.getId());
            Platform.runLater(() -> {
                showAlert("Stato Treno " + selectedTrain.getTrainNumber(), realTimeInfo, Alert.AlertType.INFORMATION);
                updateStatus("✅ Stato recuperato.");
                appendNotification("📡 Stato richiesto per " + selectedTrain.getTrainNumber());
            });
        }).start();
    }

    private void handleSubscribeAction() {
        String ticketId = ticketIdForSubscriptionField.getText().trim();

        if (ticketId.isEmpty()) {
            showAlert("ID Richiesto", "⚠️ Inserisci un ID biglietto per sottoscrivere alle notifiche.", Alert.AlertType.WARNING);
            return;
        }

        updateStatus("🔔 Sottoscrizione notifiche...");

        grpcService.subscribeToTripChanges(
                ticketId,
                notification -> Platform.runLater(() -> {
                    appendNotification("🔔 AGGIORNAMENTO VIAGGIO: " + notification.getUpdateMessage());
                    updateStatus("🔔 Notifica ricevuta per biglietto " + ticketId);
                }),
                error -> Platform.runLater(() -> {
                    appendNotification("❌ Errore sottoscrizione: " + error.getMessage());
                    updateStatus("❌ Errore sottoscrizione notifiche");
                }),
                () -> Platform.runLater(() -> {
                    appendNotification("ℹ️ Sottoscrizione terminata per biglietto " + ticketId);
                    updateStatus("ℹ️ Sottoscrizione terminata");
                })
        );

        appendNotification("🔔 Sottoscritto alle notifiche per biglietto: " + ticketId);
        updateStatus("✅ Sottoscrizione attiva per biglietto " + ticketId);
    }

    private void setupTrainTableColumns() {
        // Time columns
        TableColumn<TrainDisplay, String> depTimeCol = new TableColumn<>("🕐 Partenza");
        depTimeCol.setCellValueFactory(new PropertyValueFactory<>("departureTime"));
        depTimeCol.setSortType(TableColumn.SortType.ASCENDING);
        depTimeCol.setPrefWidth(100);

        TableColumn<TrainDisplay, String> arrTimeCol = new TableColumn<>("🕑 Arrivo");
        arrTimeCol.setCellValueFactory(new PropertyValueFactory<>("arrivalTime"));
        arrTimeCol.setPrefWidth(100);

        // Station columns
        TableColumn<TrainDisplay, String> fromCol = new TableColumn<>("🚉 Da");
        fromCol.setCellValueFactory(new PropertyValueFactory<>("departureStation"));
        fromCol.setPrefWidth(150);

        TableColumn<TrainDisplay, String> toCol = new TableColumn<>("🚉 A");
        toCol.setCellValueFactory(new PropertyValueFactory<>("arrivalStation"));
        toCol.setPrefWidth(150);

        // Train details columns
        TableColumn<TrainDisplay, String> numberCol = new TableColumn<>("🚄 Treno");
        numberCol.setCellValueFactory(new PropertyValueFactory<>("trainNumber"));
        numberCol.setPrefWidth(80);

        TableColumn<TrainDisplay, String> classCol = new TableColumn<>("🎫 Tipo");
        classCol.setCellValueFactory(new PropertyValueFactory<>("serviceClass"));
        classCol.setPrefWidth(120);

        TableColumn<TrainDisplay, String> priceCol = new TableColumn<>("💰 Prezzo");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        priceCol.setPrefWidth(80);
        priceCol.setCellFactory(column -> new TableCell<TrainDisplay, String>() {
            @Override
            protected void updateItem(String price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText("€" + price);
                }
            }
        });

        TableColumn<TrainDisplay, Integer> seatsCol = new TableColumn<>("💺 Posti");
        seatsCol.setCellValueFactory(new PropertyValueFactory<>("availableSeats"));
        seatsCol.setPrefWidth(80);

        // Status column
        TableColumn<TrainDisplay, String> statusCol = new TableColumn<>("📊 Stato");
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(column -> new TableCell<TrainDisplay, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setStyle("");
                } else {
                    TrainDisplay train = getTableView().getItems().get(getIndex());
                    if (train.getDepartureInstant().isBefore(Instant.now())) {
                        setText("🔴 Partito");
                        setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                    } else {
                        setText("🟢 In orario");
                        setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                    }
                }
            }
        });

        trainTableView.getColumns().setAll(depTimeCol, arrTimeCol, fromCol, toCol, numberCol, classCol, priceCol, seatsCol, statusCol);
        trainTableView.getSortOrder().add(depTimeCol);

        // Style the table
        trainTableView.setStyle("-fx-background-color: white;");
    }

    private void updateStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    private void appendNotification(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String formattedMessage = String.format("[%s] %s%n", timestamp, message);
        notificationArea.appendText(formattedMessage);
        notificationArea.setScrollTop(Double.MAX_VALUE);
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setStyle("-fx-font-family: Arial;");
        alert.showAndWait();
    }

}
