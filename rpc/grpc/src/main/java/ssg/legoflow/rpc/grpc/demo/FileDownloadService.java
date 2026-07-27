package ssg.legoflow.rpc.grpc.demo;

import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.common.StatusException;
import ssg.legoflow.rpc.grpc.protobuf.*;
import ssg.legoflow.rpc.grpc.server.GrpcServer;
import ssg.legoflow.rpc.grpc.transport.GrpcStatus;

import java.util.Arrays;

/**
 * Demo server-streaming service: chunked file download.
 *
 * DownloadRequest:  field 1 = filename (string), field 2 = chunk_size (int32)
 * DownloadResponse: field 1 = chunk_data (bytes), field 2 = chunk_index (int32), field 3 = total_chunks (int32)
 */
public class FileDownloadService {

    public static final String SERVICE_NAME = "demo.FileDownload";

    public static final MessageDescriptor REQUEST_DESCRIPTOR =
            MessageDescriptor.builder("DownloadRequest")
                    .addField(FieldDescriptor.scalar(1, "filename", FieldDescriptor.Type.STRING))
                    .addField(FieldDescriptor.scalar(2, "chunk_size", FieldDescriptor.Type.INT32))
                    .build();

    public static final MessageDescriptor RESPONSE_DESCRIPTOR =
            MessageDescriptor.builder("DownloadResponse")
                    .addField(FieldDescriptor.scalar(1, "chunk_data", FieldDescriptor.Type.BYTES))
                    .addField(FieldDescriptor.scalar(2, "chunk_index", FieldDescriptor.Type.INT32))
                    .addField(FieldDescriptor.scalar(3, "total_chunks", FieldDescriptor.Type.INT32))
                    .build();

    public static final MethodDescriptor DOWNLOAD_METHOD =
            MethodDescriptor.serverStreaming(SERVICE_NAME, "Download",
                    REQUEST_DESCRIPTOR, RESPONSE_DESCRIPTOR);

    public static ServiceDescriptor serviceDescriptor() {
        return ServiceDescriptor.builder(SERVICE_NAME)
                .addMethod(DOWNLOAD_METHOD)
                .build();
    }

    /**
     * Registers this service with the given server.
     * Simulates file content with repeated bytes based on the filename length.
     */
    public static void register(GrpcServer server) {
        var registry = server.registry();
        registry.registerService(serviceDescriptor());

        registry.registerServerStreamingHandler(DOWNLOAD_METHOD.path(), (request, metadata, responseStream) -> {
            String filename = request.getString(1);
            int chunkSize = request.getInt32(2);

            if (chunkSize <= 0) {
                throw new StatusException(GrpcStatus.INVALID_ARGUMENT, "chunk_size must be positive");
            }

            // Simulate file content: filename repeated to create ~1KB of data
            byte[] fileContent = generateFileContent(filename, 1024);
            int totalChunks = (int) Math.ceil((double) fileContent.length / chunkSize);

            for (int i = 0; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, fileContent.length);
                byte[] chunk = Arrays.copyOfRange(fileContent, start, end);

                var response = new ProtoMessage()
                        .setBytes(1, chunk)
                        .setVarint(2, i)
                        .setVarint(3, totalChunks);
                responseStream.accept(response);
            }
        });
    }

    static byte[] generateFileContent(String filename, int size) {
        byte[] content = new byte[size];
        byte[] nameBytes = filename.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int i = 0; i < size; i++) {
            content[i] = nameBytes[i % nameBytes.length];
        }
        return content;
    }
}
