package ssg.legoflow.rpc.grpc.demo;

import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.protobuf.*;
import ssg.legoflow.rpc.grpc.server.GrpcServer;

import java.util.zip.CRC32;

/**
 * Demo client-streaming service: upload chunks, respond with final checksum.
 *
 * UploadChunk: field 1 = data (bytes), field 2 = chunk_index (int32)
 * UploadResult: field 1 = total_bytes (int64), field 2 = checksum (int64), field 3 = chunk_count (int32)
 */
public class UploadService {

    public static final String SERVICE_NAME = "demo.Upload";

    public static final MessageDescriptor CHUNK_DESCRIPTOR =
            MessageDescriptor.builder("UploadChunk")
                    .addField(FieldDescriptor.scalar(1, "data", FieldDescriptor.Type.BYTES))
                    .addField(FieldDescriptor.scalar(2, "chunk_index", FieldDescriptor.Type.INT32))
                    .build();

    public static final MessageDescriptor RESULT_DESCRIPTOR =
            MessageDescriptor.builder("UploadResult")
                    .addField(FieldDescriptor.scalar(1, "total_bytes", FieldDescriptor.Type.INT64))
                    .addField(FieldDescriptor.scalar(2, "checksum", FieldDescriptor.Type.INT64))
                    .addField(FieldDescriptor.scalar(3, "chunk_count", FieldDescriptor.Type.INT32))
                    .build();

    public static final MethodDescriptor UPLOAD_METHOD =
            MethodDescriptor.clientStreaming(SERVICE_NAME, "Upload",
                    CHUNK_DESCRIPTOR, RESULT_DESCRIPTOR);

    public static ServiceDescriptor serviceDescriptor() {
        return ServiceDescriptor.builder(SERVICE_NAME)
                .addMethod(UPLOAD_METHOD)
                .build();
    }

    /**
     * Registers this service with the given server.
     */
    public static void register(GrpcServer server) {
        var registry = server.registry();
        registry.registerService(serviceDescriptor());

        registry.registerClientStreamingHandler(UPLOAD_METHOD.path(), (requests, metadata) -> {
            var crc = new CRC32();
            long totalBytes = 0;
            int chunkCount = requests.size();

            for (var chunk : requests) {
                byte[] data = chunk.getBytes(1);
                crc.update(data);
                totalBytes += data.length;
            }

            return new ProtoMessage()
                    .setVarint(1, totalBytes)
                    .setVarint(2, crc.getValue())
                    .setVarint(3, chunkCount);
        });
    }
}
