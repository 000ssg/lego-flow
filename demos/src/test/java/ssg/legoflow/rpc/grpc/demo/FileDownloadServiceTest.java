package ssg.legoflow.rpc.grpc.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.grpc.client.GrpcChannel;
import ssg.legoflow.rpc.grpc.client.GrpcStub;
import ssg.legoflow.rpc.grpc.protobuf.ProtoMessage;
import ssg.legoflow.rpc.grpc.server.GrpcServer;
import static org.assertj.core.api.Assertions.*;
class FileDownloadServiceTest {

    private GrpcStub stub;

    @BeforeEach
    void setUp() {
        var server = new GrpcServer();
        FileDownloadService.register(server);
        var channel = new GrpcChannel(server);
        stub = new GrpcStub(channel, FileDownloadService.serviceDescriptor());
    }

    @Test
    void testDownloadSingleChunk() {
        var request = new ProtoMessage()
                .setString(1, "test.txt")
                .setVarint(2, 2048);

        var responses = stub.serverStreamingCall("Download", request);
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getInt32(2)).isEqualTo(0); // chunk_index
        assertThat(responses.getFirst().getInt32(3)).isEqualTo(1); // total_chunks
    }

    @Test
    void testDownloadMultipleChunks() {
        var request = new ProtoMessage()
                .setString(1, "data.bin")
                .setVarint(2, 100);

        var responses = stub.serverStreamingCall("Download", request);
        assertThat(responses.size()).isGreaterThan(1);

        // Verify chunk indices are sequential
        for (int i = 0; i < responses.size(); i++) {
            assertThat(responses.get(i).getInt32(2)).isEqualTo(i);
        }

        // All chunks report same total
        int totalChunks = responses.getFirst().getInt32(3);
        assertThat(responses).allSatisfy(r ->
                assertThat(r.getInt32(3)).isEqualTo(totalChunks));
    }

    @Test
    void testDownloadChunkDataNotEmpty() {
        var request = new ProtoMessage()
                .setString(1, "file.txt")
                .setVarint(2, 256);

        var responses = stub.serverStreamingCall("Download", request);
        for (var response : responses) {
            byte[] chunkData = response.getBytes(1);
            assertThat(chunkData.length).isGreaterThan(0);
        }
    }

    @Test
    void testDownloadTotalDataSize() {
        var request = new ProtoMessage()
                .setString(1, "test.txt")
                .setVarint(2, 200);

        var responses = stub.serverStreamingCall("Download", request);
        int totalBytes = responses.stream()
                .mapToInt(r -> r.getBytes(1).length)
                .sum();
        assertThat(totalBytes).isEqualTo(1024);
    }

    @Test
    void testServiceDescriptor() {
        var desc = FileDownloadService.serviceDescriptor();
        assertThat(desc.fullName()).isEqualTo("demo.FileDownload");
        assertThat(desc.methods()).hasSize(1);
        assertThat(desc.method("Download")).isNotNull();
    }

    @Test
    void testGenerateFileContent() {
        byte[] content = FileDownloadService.generateFileContent("abc", 10);
        assertThat(content).hasSize(10);
        assertThat(content[0]).isEqualTo((byte) 'a');
        assertThat(content[1]).isEqualTo((byte) 'b');
        assertThat(content[2]).isEqualTo((byte) 'c');
        assertThat(content[3]).isEqualTo((byte) 'a');
    }
}
