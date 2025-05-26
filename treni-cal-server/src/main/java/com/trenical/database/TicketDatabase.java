package com.trenical.database;

import proto.Ticket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class TicketDatabase {
    private static TicketDatabase instance;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    private TicketDatabase() {}

    public static synchronized TicketDatabase getInstance() {
        if (instance == null) {
            instance = new TicketDatabase();
        }
        return instance;
    }

    public void saveTicket(Ticket ticket) {
        tickets.put(ticket.getId(), ticket);
    }

    public Ticket getTicketById(String ticketId) {
        return tickets.get(ticketId);
    }

    public List<Ticket> getTicketsByUserId(String userId) {
        return tickets.values().stream()
                .filter(ticket -> ticket.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    public List<Ticket> getTicketsForTrain(String trainId) {
        return tickets.values().stream()
                .filter(ticket -> ticket.getTrainDetails().getId().equals(trainId))
                .collect(Collectors.toList());
    }

    public List<Ticket> getTicketsForTrainAndClass(String trainId, String serviceClass) {
        return tickets.values().stream()
                .filter(ticket -> ticket.getTrainDetails().getId().equals(trainId) &&
                        ticket.getTrainDetails().getServiceClass().equals(serviceClass))
                .collect(Collectors.toList());
    }
}
