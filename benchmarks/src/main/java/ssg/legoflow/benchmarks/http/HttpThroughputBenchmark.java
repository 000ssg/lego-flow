package ssg.legoflow.benchmarks.http;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ssg.legoflow.http.core.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for HTTP request/response serialization and deserialization throughput.
 *
 * Measures the cost of serializing HTTP requests/responses through {@link HttpProtocolCodec}
 * and parsing them back — representing the core protocol I/O path.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms256m", "-Xmx512m"})
@State(Scope.Benchmark)
public class HttpThroughputBenchmark {

    private static final int SMALL_BODY_SIZE   = 256;
    private static final int MEDIUM_BODY_SIZE  = 4096;
    private static final int LARGE_BODY_SIZE   = 65536;

    private HttpProtocolCodec codec;
    private HttpRequest smallRequest;
    private HttpRequest mediumRequest;
    private HttpRequest largeRequest;
    private HttpResponse smallResponse;
    private HttpResponse largeResponse;

    @Setup(Level.Iteration)
    public void setup() {
        this.codec = new HttpProtocolCodec();

        // Small request/response
        smallRequest = HttpRequest.of(HttpMethod.GET, "/api/v1/users?id=42&fields=name,email");
        smallRequest.getHeaders().set("Host", "api.example.com");
        smallRequest.getHeaders().set("Accept", "application/json");
        smallRequest.getHeaders().set("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test");

        smallResponse = HttpResponse.of(HttpStatus.OK, makePayload(SMALL_BODY_SIZE));
        smallResponse.getHeaders().set("Content-Type", "application/json");
        smallResponse.getHeaders().set("X-Request-Id", "req-" + System.currentTimeMillis());

        // Medium request/response with body
        mediumRequest = HttpRequest.of(HttpMethod.POST, "/api/v1/events");
        mediumRequest.getHeaders().set("Host", "events.example.com");
        mediumRequest.getHeaders().set("Content-Type", "application/json");
        mediumRequest.setBody(ByteBuffer.wrap(makePayload(MEDIUM_BODY_SIZE).getBytes(StandardCharsets.UTF_8)));

        // Large response
        largeResponse = HttpResponse.of(HttpStatus.OK, makePayload(LARGE_BODY_SIZE));
        largeResponse.getHeaders().set("Content-Type", "application/octet-stream");
    }

    /**
     * Serialize a small GET request.
     */
    @Benchmark
    public void serializeSmallRequest(Blackhole bh) {
        bh.consume(codec.serializeRequest(smallRequest));
    }

    /**
     * Serialize a medium POST request with body.
     */
    @Benchmark
    public void serializeMediumRequest(Blackhole bh) {
        bh.consume(codec.serializeRequest(mediumRequest));
    }

    /**
     * Serialize a small response.
     */
    @Benchmark
    public void serializeSmallResponse(Blackhole bh) {
        bh.consume(codec.serializeResponse(smallResponse));
    }

    /**
     * Serialize a large response (64 KB body).
     */
    @Benchmark
    public void serializeLargeResponse(Blackhole bh) {
        bh.consume(codec.serializeResponse(largeResponse));
    }

    /**
     * Roundtrip: serialize then parse request.
     */
    @Benchmark
    public void roundtripSmallRequest(Blackhole bh) {
        ByteBuffer serialized = codec.serializeRequest(smallRequest);
        serialized.rewind();
        HttpRequest parsed = codec.parseRequest(serialized);
        bh.consume(parsed.getMethod());
        bh.consume(parsed.getUri());
    }

    /**
     * Roundtrip: serialize then parse response.
     */
    @Benchmark
    public void roundtripSmallResponse(Blackhole bh) {
        ByteBuffer serialized = codec.serializeResponse(smallResponse);
        serialized.rewind();
        HttpResponse parsed = codec.parseResponse(serialized);
        bh.consume(parsed.getStatus());
    }

    private String makePayload(int size) {
        char[] chars = new char[size];
        for (int i = 0; i < size; i++) {
            chars[i] = (char) ('a' + (i % 26));
        }
        return new String(chars);
    }
}
