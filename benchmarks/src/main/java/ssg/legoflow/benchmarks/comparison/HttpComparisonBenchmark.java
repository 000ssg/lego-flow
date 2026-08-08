package ssg.legoflow.benchmarks.comparison;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.core.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks comparing standalone HTTP operations vs service-pipeline HTTP operations.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms256m", "-Xmx512m"})
@State(Scope.Benchmark)
public class HttpComparisonBenchmark {

    private static final int SMALL_BODY = 256;
    private static final int MEDIUM_BODY = 4096;

    private HttpProtocolCodec codec;
    private HttpRequest smallGetRequest;
    private HttpResponse jsonResponse;
    private ByteBuffer encodedRequest;
    private ByteBuffer encodedResponse;
    private Context ctx;

    @Setup(Level.Iteration)
    public void setup() {
        this.codec = new HttpProtocolCodec();
        this.ctx = new DefaultContext();

        // Small GET request
        smallGetRequest = HttpRequest.of(HttpMethod.GET, "/api/v1/users?id=42&fields=name,email");
        smallGetRequest.getHeaders().set("Host", "api.example.com");
        smallGetRequest.getHeaders().set("Accept", "application/json");
        smallGetRequest.getHeaders().set("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test");

        // JSON response
        jsonResponse = HttpResponse.of(HttpStatus.OK, makePayload(SMALL_BODY));
        jsonResponse.getHeaders().set("Content-Type", "application/json");
        jsonResponse.getHeaders().set("X-Trace-Id", "trace-" + System.nanoTime());

        // Pre-encode for parsing benchmarks
        this.encodedRequest = codec.serializeRequest(smallGetRequest);
        this.encodedResponse = codec.serializeResponse(jsonResponse);
    }

    // -- Standalone (Baseline) Operations -------------------------------------------

    /** Standalone: Serialize a small GET request via HttpProtocolCodec. */
    @Benchmark
    public void standaloneSerializeGet(Blackhole bh) {
        ByteBuffer result = codec.serializeRequest(smallGetRequest);
        bh.consume(result.remaining());
    }

    /** Standalone: Parse an HTTP request from wire bytes. */
    @Benchmark
    public void standaloneParseRequest(Blackhole bh) {
        HttpRequest parsed = codec.parseRequest(encodedRequest.duplicate());
        bh.consume(parsed.getMethod().toString());
        bh.consume(parsed.getUri());
    }

    /** Standalone: Full roundtrip serialize -> parse request. */
    @Benchmark
    public void standaloneRoundtripRequest(Blackhole bh) {
        ByteBuffer serialized = codec.serializeRequest(smallGetRequest);
        serialized.rewind();
        HttpRequest parsed = codec.parseRequest(serialized);
        bh.consume(parsed.getMethod());
        bh.consume(parsed.getHeaders().get("authorization"));
    }

    /** Standalone: Full roundtrip serialize -> parse response. */
    @Benchmark
    public void standaloneRoundtripResponse(Blackhole bh) {
        ByteBuffer serialized = codec.serializeResponse(jsonResponse);
        serialized.rewind();
        HttpResponse parsed = codec.parseResponse(serialized);
        bh.consume(parsed.getStatus());
        bh.consume(parsed.getBodyAsString().length());
    }

    // -- Service Pipeline Operations ------------------------------------------------

    /** Service: Simulate DP/DF consume path for HTTP request. */
    @Benchmark
    public void servicePipelineSerializeRequest(Blackhole bh) {
        ByteBuffer serialized = codec.serializeRequest(smallGetRequest);
        serialized.rewind();
        ByteBuffer[] output = pipelineConvertToOutput(serialized);
        for (ByteBuffer buf : output) {
            if (buf != null) bh.consume(buf.remaining());
        }
    }

    /** Service: Simulate DP/DF consume path for HTTP response. */
    @Benchmark
    public void servicePipelineSerializeResponse(Blackhole bh) {
        ByteBuffer serialized = codec.serializeResponse(jsonResponse);
        serialized.rewind();
        ByteBuffer[] output = pipelineConvertToOutput(serialized);
        for (ByteBuffer buf : output) {
            if (buf != null) bh.consume(buf.remaining());
        }
    }

    /** Service: Full service-pipeline roundtrip with statistics tracking. */
    @Benchmark
    public void servicePipelineRoundtrip(Blackhole bh) {
        // Record inbound statistics
        ctx.getStatistics().recordIn(ByteBuffer.class, 1, encodedRequest.remaining());

        // Serialize through codec
        ByteBuffer serialized = codec.serializeResponse(jsonResponse);
        serialized.rewind();

        // Pipeline process
        ByteBuffer[] output = pipelineConsumePath(serialized);

        // Record outbound statistics and consume
        for (ByteBuffer buf : output) {
            if (buf != null && buf.hasRemaining()) {
                ctx.getStatistics().recordOut(ByteBuffer.class, 1, buf.remaining());
                bh.consume(buf.remaining());
            }
        }
    }

    // -- Service Pipeline Simulation Methods ----------------------------------------

    /** Simulates AbstractService.convertToOutput for HTTP data. */
    private ByteBuffer[] pipelineConvertToOutput(ByteBuffer input) {
        if (input == null || !input.hasRemaining()) return new ByteBuffer[0];
        ByteBuffer dup = input.duplicate();
        return new ByteBuffer[]{dup};
    }

    /** Simulates full DP/DF consume path. */
    private ByteBuffer[] pipelineConsumePath(ByteBuffer input) {
        if (input == null || !input.hasRemaining()) return new ByteBuffer[0];
        ByteBuffer converted = input.duplicate();
        return new ByteBuffer[]{converted};
    }

    private String makePayload(int size) {
        char[] chars = new char[size];
        for (int i = 0; i < size; i++) {
            chars[i] = (char) ('A' + (i % 26));
        }
        return new String(chars);
    }
}
