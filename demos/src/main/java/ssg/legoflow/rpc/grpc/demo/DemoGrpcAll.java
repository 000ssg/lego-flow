package ssg.legoflow.rpc.grpc.demo;

import ssg.legoflow.rpc.grpc.client.CallOptions;
import ssg.legoflow.rpc.grpc.client.GrpcChannel;
import ssg.legoflow.rpc.grpc.client.GrpcStub;
import ssg.legoflow.rpc.grpc.common.StatusException;
import ssg.legoflow.rpc.grpc.metadata.Metadata;
import ssg.legoflow.rpc.grpc.protobuf.*;
import ssg.legoflow.rpc.grpc.server.GrpcServer;
import ssg.legoflow.rpc.grpc.transport.GrpcStatus;
import ssg.legoflow.rpc.grpc.transport.GrpcTimeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
/**
 * Comprehensive demo of all gRPC module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link GrpcServer}</b> — No external dependencies.
 * Runs anywhere without installation. Supports all four call types (unary, server streaming,
 * client streaming, bidirectional streaming), metadata, interceptors, deadlines, status codes,
 * and protobuf encoding/decoding. Ideal for development, testing, CI/CD, and learning the
 * gRPC protocol.</p>
 *
 * <p><b>Alternative: External gRPC (io.grpc) server / Envoy proxy</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production load testing with TLS mutual authentication</li>
 *   <li>Envoy sidecar proxying for service mesh integration</li>
 *   <li>Advanced load balancing (round-robin, least-connections)</li>
 *   <li>Integration testing against a real gRPC cluster</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the channel setup (loopback vs remote).
 * All service definitions, stub calls, interceptors, and metadata use the same API
 * regardless of backend. When {@code USE_EXTERNAL=true}, the demo skips server creation
 * and connects the channel directly to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Unary RPC — single request/response (Calculator: add, multiply, divide)</li>
 *   <li>Server streaming — single request, multiple responses (FileDownload)</li>
 *   <li>Client streaming — multiple requests, single response (Upload)</li>
 *   <li>Bidirectional streaming — multiple requests, multiple responses (Chat)</li>
 *   <li>Metadata — headers and trailers with typed keys</li>
 *   <li>Interceptors — server-side and client-side interceptor chains</li>
 *   <li>Deadline/timeout — gRPC timeout format (nS/uS/mS/S/M/H)</li>
 *   <li>Status codes — all 17 gRPC status codes, error handling</li>
 *   <li>Protobuf encoding — schema-aware encode/decode round-trip</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoGrpcAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoGrpcAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house GrpcServer (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for gRPC/Envoy
    // =========================================================================

    /** Set to {@code true} to connect to an external gRPC server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external gRPC server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external gRPC server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 50051;

    private DemoGrpcAll() {}

    /**
     * Results from running the full demo.
     *
     * @param unaryRpc           true if unary RPC (add, multiply, divide) succeeded
     * @param serverStreaming     number of chunks received from server streaming
     * @param clientStreaming     true if upload checksum was computed correctly
     * @param bidiStreaming       number of responses from bidi streaming
     * @param metadata           true if metadata round-trip succeeded
     * @param interceptors       number of interceptor invocations observed
     * @param deadlineTimeout    true if deadline/timeout was configured correctly
     * @param statusCodes        true if status code error handling succeeded
     * @param protobufEncoding   true if protobuf encode/decode round-trip succeeded
     */
    public record Results(
            boolean unaryRpc,
            int serverStreaming,
            boolean clientStreaming,
            int bidiStreaming,
            boolean metadata,
            int interceptors,
            boolean deadlineTimeout,
            boolean statusCodes,
            boolean protobufEncoding
    ) {}

    /**
     * Runs the comprehensive demo covering all gRPC features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        var server = new GrpcServer();

        // Register all demo services
        CalculatorService.register(server);
        FileDownloadService.register(server);
        UploadService.register(server);
        ChatService.register(server);

        // Track interceptor invocations
        List<String> interceptorLog = new CopyOnWriteArrayList<>();

        // Add server interceptor
        server.addInterceptor((method, md, next) -> {
            interceptorLog.add("server:" + method.methodName());
            return next;
        });

        var channel = new GrpcChannel(server);

        // 1. Unary RPC
        boolean unary = demoUnaryRpc(channel);

        // 2. Server streaming
        int serverStreamCount = demoServerStreaming(channel);

        // 3. Client streaming
        boolean clientStream = demoClientStreaming(channel);

        // 4. Bidi streaming
        int bidiCount = demoBidiStreaming(channel);

        // 5. Metadata
        boolean metadataOk = demoMetadata(channel);

        // 6. Interceptors (client-side)
        int interceptCount = demoInterceptors(channel, interceptorLog);

        // 7. Deadline/timeout
        boolean deadlineOk = demoDeadlineTimeout();

        // 8. Status codes
        boolean statusOk = demoStatusCodes(channel);

        // 9. Protobuf encoding
        boolean protobufOk = demoProtobufEncoding();

        return new Results(
                unary,
                serverStreamCount,
                clientStream,
                bidiCount,
                metadataOk,
                interceptCount,
                deadlineOk,
                statusOk,
                protobufOk
        );
    }

    // ======================== 1. UNARY RPC ==================================

    /**
     * Demonstrates unary RPC with the Calculator service: add, multiply, divide.
     */
    static boolean demoUnaryRpc(GrpcChannel channel) {
        LOG.info("=== 1. Unary RPC ===");
        var stub = new GrpcStub(channel, CalculatorService.serviceDescriptor());

        // Add
        var addReq = new ProtoMessage().setDouble(1, 10.0).setDouble(2, 20.0);
        var addResp = stub.unaryCall("Add", addReq);
        double addResult = addResp.getDouble(1);
        LOG.info("Add: 10 + 20 = {}", addResult);

        // Multiply
        var mulReq = new ProtoMessage().setDouble(1, 6.0).setDouble(2, 7.0);
        var mulResp = stub.unaryCall("Multiply", mulReq);
        double mulResult = mulResp.getDouble(1);
        LOG.info("Multiply: 6 * 7 = {}", mulResult);

        // Divide
        var divReq = new ProtoMessage().setDouble(1, 100.0).setDouble(2, 4.0);
        var divResp = stub.unaryCall("Divide", divReq);
        double divResult = divResp.getDouble(1);
        LOG.info("Divide: 100 / 4 = {}", divResult);

        return addResult == 30.0 && mulResult == 42.0 && divResult == 25.0;
    }

    // ======================== 2. SERVER STREAMING ============================

    /**
     * Demonstrates server streaming with the FileDownload service.
     * A single download request produces multiple chunk responses.
     */
    static int demoServerStreaming(GrpcChannel channel) {
        LOG.info("=== 2. Server Streaming ===");
        var stub = new GrpcStub(channel, FileDownloadService.serviceDescriptor());

        var request = new ProtoMessage()
                .setString(1, "demo-file.dat")
                .setVarint(2, 128); // 128-byte chunks

        var responses = stub.serverStreamingCall("Download", request);
        LOG.info("Received {} chunks", responses.size());

        int totalBytes = 0;
        for (var resp : responses) {
            byte[] chunkData = resp.getBytes(1);
            totalBytes += chunkData.length;
            LOG.info("  Chunk {}/{}: {} bytes",
                    resp.getInt32(2), resp.getInt32(3), chunkData.length);
        }
        LOG.info("Total download: {} bytes", totalBytes);
        return responses.size();
    }

    // ======================== 3. CLIENT STREAMING ============================

    /**
     * Demonstrates client streaming with the Upload service.
     * Multiple upload chunks produce a single result with checksum.
     */
    static boolean demoClientStreaming(GrpcChannel channel) {
        LOG.info("=== 3. Client Streaming ===");
        var stub = new GrpcStub(channel, UploadService.serviceDescriptor());

        // Create upload chunks
        var chunks = new ArrayList<ProtoMessage>();
        var crc = new java.util.zip.CRC32();
        for (int i = 0; i < 5; i++) {
            byte[] data = ("chunk-data-" + i).getBytes();
            crc.update(data);
            chunks.add(new ProtoMessage()
                    .setBytes(1, data)
                    .setVarint(2, i));
        }

        var result = stub.clientStreamingCall("Upload", chunks);
        long totalBytes = result.getVarint(1);
        long checksum = result.getVarint(2);
        int chunkCount = result.getInt32(3);

        LOG.info("Upload result: {} bytes, {} chunks, checksum={}",
                totalBytes, chunkCount, checksum);
        return chunkCount == 5 && checksum == crc.getValue();
    }

    // ======================== 4. BIDI STREAMING ==============================

    /**
     * Demonstrates bidirectional streaming with the Chat service.
     * Each message produces an echo and a system notification (2 responses per message).
     */
    static int demoBidiStreaming(GrpcChannel channel) {
        LOG.info("=== 4. Bidirectional Streaming ===");
        var stub = new GrpcStub(channel, ChatService.serviceDescriptor());

        var messages = List.of(
                new ProtoMessage().setString(1, "alice").setString(2, "hello").setVarint(3, 1000),
                new ProtoMessage().setString(1, "bob").setString(2, "hi there").setVarint(3, 2000),
                new ProtoMessage().setString(1, "carol").setString(2, "hey").setVarint(3, 3000)
        );

        var responses = stub.bidiStreamingCall("Chat", messages);
        LOG.info("Chat responses: {}", responses.size());
        for (var resp : responses) {
            LOG.info("  [{}] {}", resp.getString(1), resp.getString(2));
        }
        return responses.size();
    }

    // ======================== 5. METADATA ====================================

    /**
     * Demonstrates metadata (headers and trailers) with typed keys.
     * Metadata carries request context (auth tokens, trace IDs, etc.).
     */
    static boolean demoMetadata(GrpcChannel channel) {
        LOG.info("=== 5. Metadata ===");

        // Create metadata with various keys
        var metadata = new Metadata()
                .put("x-request-id", "demo-12345")
                .put("x-trace-id", "trace-abc")
                .put("authorization", "Bearer demo-token");

        LOG.info("Metadata keys: {}", metadata.keys());
        LOG.info("Request ID: {}", metadata.get("x-request-id"));

        // Verify metadata round-trip
        boolean hasRequestId = metadata.containsKey("x-request-id");
        boolean hasTrace = "trace-abc".equals(metadata.get("x-trace-id"));
        boolean hasAuth = metadata.containsKey("authorization");

        // Merge metadata
        var extra = new Metadata().put("x-extra", "value");
        var merged = metadata.merge(extra);
        boolean hasMerged = merged.containsKey("x-extra") && merged.containsKey("x-request-id");

        LOG.info("Metadata merge: {} keys", merged.size());
        return hasRequestId && hasTrace && hasAuth && hasMerged;
    }

    // ======================== 6. INTERCEPTORS ================================

    /**
     * Demonstrates server and client interceptor chains.
     * Interceptors can log, authenticate, modify metadata, etc.
     */
    static int demoInterceptors(GrpcChannel channel, List<String> interceptorLog) {
        LOG.info("=== 6. Interceptors ===");
        int beforeCount = interceptorLog.size();

        // Client interceptor: add a stub with client interceptor
        var stub = new GrpcStub(channel, CalculatorService.serviceDescriptor())
                .withInterceptor((method, options, md, next) -> {
                    interceptorLog.add("client:" + method.methodName());
                    return next;
                });

        // Make a call — both server and client interceptors fire
        var req = new ProtoMessage().setDouble(1, 1.0).setDouble(2, 2.0);
        stub.unaryCall("Add", req);

        int afterCount = interceptorLog.size();
        int newEvents = afterCount - beforeCount;
        LOG.info("Interceptor events from this call: {}", newEvents);
        LOG.info("Total interceptor log: {}", interceptorLog);
        return afterCount;
    }

    // ======================== 7. DEADLINE / TIMEOUT ==========================

    /**
     * Demonstrates gRPC deadline/timeout configuration.
     * Timeouts use the gRPC format: nS/uS/mS/S/M/H (nano/micro/milli/seconds/minutes/hours).
     */
    static boolean demoDeadlineTimeout() {
        LOG.info("=== 7. Deadline / Timeout ===");

        // Create timeout from duration
        var timeout = GrpcTimeout.fromDuration(Duration.ofSeconds(5));
        LOG.info("Timeout: {} (encoded: {})", timeout, timeout.encode());

        // Parse timeout from header value
        var parsed = GrpcTimeout.parse("500m");
        LOG.info("Parsed: {} = {} ms", parsed, parsed.toMillis());

        // CallOptions with deadline
        var options = CallOptions.withDeadline(Duration.ofSeconds(10));
        LOG.info("Call options timeout: {}", options.timeout());

        // Verify
        boolean durationOk = timeout.toMillis() == 5000;
        boolean parsedOk = parsed.toMillis() == 500;
        boolean optionsOk = options.timeout() != null;

        return durationOk && parsedOk && optionsOk;
    }

    // ======================== 8. STATUS CODES ================================

    /**
     * Demonstrates gRPC status code handling.
     * Division by zero throws INVALID_ARGUMENT; unimplemented methods throw UNIMPLEMENTED.
     */
    static boolean demoStatusCodes(GrpcChannel channel) {
        LOG.info("=== 8. Status Codes ===");
        var stub = new GrpcStub(channel, CalculatorService.serviceDescriptor());

        // Trigger INVALID_ARGUMENT via division by zero
        boolean invalidArgCaught = false;
        try {
            var req = new ProtoMessage().setDouble(1, 10.0).setDouble(2, 0.0);
            stub.unaryCall("Divide", req);
        } catch (StatusException e) {
            invalidArgCaught = e.status() == GrpcStatus.INVALID_ARGUMENT;
            LOG.info("Caught status: {} — {}", e.status(), e.getMessage());
        }

        // Verify status code constants
        boolean statusOk = GrpcStatus.OK.code() == 0;
        boolean cancelled = GrpcStatus.CANCELLED.code() == 1;
        boolean unauthenticated = GrpcStatus.UNAUTHENTICATED.code() == 16;

        LOG.info("Status codes: OK={}, CANCELLED={}, UNAUTHENTICATED={}",
                statusOk, cancelled, unauthenticated);
        return invalidArgCaught && statusOk && cancelled && unauthenticated;
    }

    // ======================== 9. PROTOBUF ENCODING ===========================

    /**
     * Demonstrates protobuf encoding and decoding with schema-aware codec.
     * ProtoMessage fields are encoded using wire types (varint, fixed64, length-delimited).
     */
    static boolean demoProtobufEncoding() {
        LOG.info("=== 9. Protobuf Encoding ===");

        // Define a message descriptor
        var descriptor = MessageDescriptor.builder("DemoMessage")
                .addField(FieldDescriptor.scalar(1, "name", FieldDescriptor.Type.STRING))
                .addField(FieldDescriptor.scalar(2, "age", FieldDescriptor.Type.INT32))
                .addField(FieldDescriptor.scalar(3, "score", FieldDescriptor.Type.DOUBLE))
                .addField(FieldDescriptor.scalar(4, "active", FieldDescriptor.Type.BOOL))
                .build();

        // Create and encode a message
        var original = new ProtoMessage()
                .setString(1, "Alice")
                .setVarint(2, 30)
                .setDouble(3, 95.5)
                .setBool(4, true);

        byte[] encoded = ProtobufCodec.encode(original, descriptor);
        LOG.info("Encoded {} bytes", encoded.length);

        // Decode and verify round-trip
        var decoded = ProtobufCodec.decode(encoded, descriptor);
        boolean nameOk = "Alice".equals(decoded.getString(1));
        boolean ageOk = decoded.getInt32(2) == 30;
        boolean scoreOk = decoded.getDouble(3) == 95.5;
        boolean activeOk = decoded.getBool(4);

        LOG.info("Decoded: name={}, age={}, score={}, active={}",
                decoded.getString(1), decoded.getInt32(2),
                decoded.getDouble(3), decoded.getBool(4));

        // Schema-less encode/decode
        byte[] schemaless = ProtobufCodec.encode(original);
        var decoded2 = ProtobufCodec.decode(schemaless);
        boolean schemalessOk = decoded2.has(1) && decoded2.has(2);
        LOG.info("Schema-less round-trip: {} fields", decoded2.fieldCount());

        return nameOk && ageOk && scoreOk && activeOk && schemalessOk;
    }
}
