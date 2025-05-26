package com.trenical.services;

import io.grpc.stub.StreamObserver;
import com.trenical.database.TicketDatabase;
import com.trenical.database.TrainDatabase;
import proto.*;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class TicketServiceImpl extends TicketServiceGrpc.TicketServiceImplBase {
    private final TicketDatabase ticketDatabase = TicketDatabase.getInstance();
    private final TrainDatabase trainDatabase = TrainDatabase.getInstance();

    @Override
    public void purchaseTickets(PurchaseTicketRequest request, StreamObserver<PurchaseTicketResponse> responseObserver) {
        System.out.println("[Server] Received PurchaseTickets request for user: "
                + request.getUserId() + " for train: " + request.getTrainId());
        PurchaseTicketResponse.Builder responseBuilder = PurchaseTicketResponse.newBuilder();

        Train trainToBook = trainDatabase.getTrainById(request.getTrainId());
        if (trainToBook == null) {
            responseBuilder.setSuccess(false).setMessage("Train not found.");
        } else {
            // DISPonibilità dei posti sempre simulata per ora...
            int availableSeats = trainDatabase.getAvailableSeats(request.getTrainId(), request.getServiceClass());
            if (availableSeats >= request.getNumberOfTickets()) {
                // Processo di pagamento anch'esso simulato.
                boolean paymentSuccessful = !request.getPaymentMethodToken().isEmpty();

                if (paymentSuccessful) {
                    List<Ticket> purchasedTicketsList = new ArrayList<>();
                    for (int i = 0; i < request.getNumberOfTickets(); i++) {
                        String ticketId = UUID.randomUUID().toString();
                        String seatNumber = request.getServiceClass().substring(0,1) + (ticketDatabase.getTicketsForTrain(request.getTrainId()).size() + i + 1);

                        Ticket newTicket = Ticket.newBuilder()
                                .setId(ticketId)
                                .setUserId(request.getUserId())
                                .setTrainDetails(trainToBook.toBuilder().setServiceClass(request.getServiceClass()).buildPartial()) // Adjust price based on class if needed
                                .setSeatNumber(seatNumber)
                                .setPurchaseDate(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build())
                                .setStatus("CONFIRMED")
                                .build();
                        ticketDatabase.saveTicket(newTicket);
                        purchasedTicketsList.add(newTicket);
                    }
                    responseBuilder.setSuccess(true)
                            .addAllPurchasedTickets(purchasedTicketsList)
                            .setMessage("Purchase successful for " + request.getNumberOfTickets() + " ticket(s).");
                } else {
                    responseBuilder.setSuccess(false).setMessage("Payment failed.");
                }
            } else {
                responseBuilder.setSuccess(false).setMessage("Not enough available seats in class " + request.getServiceClass() + ".");
            }
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }


    @Override
    public void modifyTicket(ModifyTicketRequest request, StreamObserver<ModifyTicketResponse> responseObserver) {
        System.out.println("[Server] Received ModifyTicket request for ticket ID: " + request.getTicketId());
        ModifyTicketResponse.Builder responseBuilder = ModifyTicketResponse.newBuilder();
        Ticket existingTicket = ticketDatabase.getTicketById(request.getTicketId());

        if (existingTicket == null || !existingTicket.getUserId().equals(request.getUserId())) {
            responseBuilder.setSuccess(false).setMessage("Ticket not found or access denied.");
        } else {
            // Logica di modifica biglietto semplicata, da implementare:
            // 1) Controllo della disponibilità per nuove date/ classi
            // 2) verifica di una eventuale differenza di costo del biglietto, ed oppure una penality da pagare per il cambiamento
            // 3) AGGIORNAMENTO DEL BIGLIETTO
            Ticket.Builder modifiedTicketBuilder = existingTicket.toBuilder();
            boolean modified = false;
            double additionalCharge = 0.0;

            if (request.hasNewTravelDate()) {
                // si dovrebbe Cambiare la data , e ricontrollare la disponibilità del treno
                // per il momento aggiorno soltanto la data del bigliettop
                Train currentTrain = existingTicket.getTrainDetails();
                modifiedTicketBuilder.setTrainDetails(currentTrain.toBuilder()
                        .setDepartureTime(Timestamp.newBuilder()
                                .setSeconds(Instant.now().getEpochSecond() + 86400)
                                .build())
                        .setArrivalTime(Timestamp.newBuilder()
                                .setSeconds(Instant.now().getEpochSecond() + 86400 + 3600)
                                .build())
                );
                modified = true;
                additionalCharge += 10.0; // differenza di prezzo simulata.
            }
            if (request.hasNewServiceClass() && !request.getNewServiceClass().equals(existingTicket.getTrainDetails().getServiceClass())) {
                modifiedTicketBuilder.setTrainDetails(existingTicket.getTrainDetails().toBuilder().setServiceClass(request.getNewServiceClass()));
                // XXXXX qua da aggiustare la logica dei prezzi
                modified = true;
                additionalCharge += 15.0; // sempre simulata
            }

            if (modified) {
                // XXX qua pure simulo il pagamento della differenza di prezzo.
                if (additionalCharge > 0 && request.getPaymentMethodTokenForDiff().isEmpty()) {
                    responseBuilder.setSuccess(false).setMessage("Additional payment required for modification.");
                } else {
                    Ticket finalTicket = modifiedTicketBuilder.setStatus("MODIFIED").build();
                    ticketDatabase.saveTicket(finalTicket); // Update ticket
                    responseBuilder.setSuccess(true)
                            .setModifiedTicket(finalTicket)
                            .setAdditionalCharge(additionalCharge)
                            .setMessage("Ticket modified successfully.");
                }
            } else {
                responseBuilder.setSuccess(false).setMessage("No changes specified or modification not allowed.");
            }
        }
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }


    @Override
    public void getMyTickets(UserRequest request, StreamObserver<TicketListResponse> responseObserver) {
        List<Ticket> userTickets = ticketDatabase.getTicketsByUserId(request.getUserId());
        TicketListResponse response = TicketListResponse.newBuilder()
                .addAllTickets(userTickets)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }





}
