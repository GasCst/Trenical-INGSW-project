package com.trenical;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import proto.*;  // Ensure this is pointing to the correct generated class
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TreniCalServer {

    public static void main(String[] args) throws IOException, InterruptedException {
        Server server = ServerBuilder
                .forPort(50051)
                .addService(new TreniCalServiceImpl())  // Adds the service implementation
                .build()
                .start();

        System.out.println("Server gRPC avviato su 50051");
        server.awaitTermination();
    }
}

class TreniCalServiceImpl extends TreniCalGrpc.TreniCalImplBase {

    @Override
    public void search(proto.SearchRequest req, StreamObserver<SearchResponse> responseObserver) {
        // Mock data for demonstration
        List<proto.Ticket> tickets = new ArrayList<>();

        // Creating sample tickets - replace this with actual search logic
        tickets.add(proto.Ticket.newBuilder()
                .setTicketId("1")
                .setDestination("Milan")
                .setDepartureTime("2025-05-24T10:00:00")
                .setPrice(29)
                .build());

        tickets.add(proto.Ticket.newBuilder()
                .setTicketId("2")
                .setDestination("Rome")
                .setDepartureTime("2025-05-24T12:00:00")
                .setPrice(34)
                .build());

        // Sending response
        proto.SearchResponse response = proto.SearchResponse.newBuilder()
                .addAllTickets(tickets)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
