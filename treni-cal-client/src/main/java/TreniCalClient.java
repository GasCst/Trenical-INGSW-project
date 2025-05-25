import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import proto.*;

public class TreniCalClient {
    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051)
                .usePlaintext()
                .build();
        TreniCalGrpc.TreniCalBlockingStub stub = TreniCalGrpc.newBlockingStub(channel);

        SearchRequest request = SearchRequest.newBuilder().setFrom("Roma").setTo("Milano").build();
        SearchResponse response = stub.search(request);

        response.getTicketsList().forEach(ticket -> System.out.println(ticket));
        channel.shutdown();
    }
}
