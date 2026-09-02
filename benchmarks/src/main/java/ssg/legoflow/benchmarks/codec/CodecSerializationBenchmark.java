package ssg.legoflow.benchmarks.codec;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ssg.legoflow.database.redis.protocol.RespCodec;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.http.core.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
/**
 * Benchmarks for protocol serialization and deserialization speed.
 *
 * Measures the cost of encoding/decoding data through the various protocol codecs:
 * <ul>
 *   <li>HTTP protocol codec (requests/responses with headers)</li>
 *   <li>Redis RESP2 codec (bulk strings, arrays)</li>
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms256m", "-Xmx512m"})
@State(Scope.Benchmark)
public class CodecSerializationBenchmark {

    private static final int SMALL_SIZE  = 128;
    private static final int MEDIUM_SIZE = 4096;

    private HttpProtocolCodec httpCodec;
    private HttpRequest jsonRequest;
    private HttpResponse jsonResponse;
    private RespType respBulkString;
    private RespType respArray;
    private byte[] respEncodedBulk;
    private byte[] respEncodedArray;

    @Setup(Level.Iteration)
    public void setup() {
        this.httpCodec = new HttpProtocolCodec();

        // JSON request with body
        jsonRequest = HttpRequest.of(HttpMethod.POST, "/api/v1/records");
        jsonRequest.getHeaders().set("Host", "data.example.com");
        jsonRequest.getHeaders().set("Content-Type", "application/json");
        jsonRequest.getHeaders().set("Authorization", "Bearer token-abc123");
        String jsonBody = "{\"name\":\"test\",\"value\":42,\"tags\":[\"a\",\"b\",\"c\"]}";
        jsonRequest.setBody(ByteBuffer.wrap(jsonBody.getBytes(StandardCharsets.UTF_8)));

        // JSON response
        jsonResponse = HttpResponse.of(HttpStatus.OK, makePayload(MEDIUM_SIZE));
        jsonResponse.getHeaders().set("Content-Type", "application/json");
        jsonResponse.getHeaders().set("X-Trace-Id", "trace-" + System.currentTimeMillis());

        // RESP bulk string (takes byte[])
        respBulkString = new RespType.BulkString(makePayload(SMALL_SIZE).getBytes(StandardCharsets.UTF_8));

        // RESP array of bulk strings
        List<RespType> arrayElements = List.of(
                new RespType.BulkString("field1".getBytes(StandardCharsets.UTF_8)),
                new RespType.BulkString("value1".getBytes(StandardCharsets.UTF_8)),
                new RespType.BulkString("field2".getBytes(StandardCharsets.UTF_8)),
                new RespType.BulkString("value2".getBytes(StandardCharsets.UTF_8))
        );
        respArray = new RespType.Array(arrayElements);

        // Pre-encode RESP values for decode benchmarks
        respEncodedBulk = RespCodec.encode(respBulkString);
        respEncodedArray = RespCodec.encode(respArray);
    }

    // -- HTTP Codec ------------------------------------------------------------------

    /**
     * Serialize an HTTP request with JSON body.
     */
    @Benchmark
    public void httpSerializeRequest(Blackhole bh) {
        bh.consume(httpCodec.serializeRequest(jsonRequest));
    }

    /**
     * Serialize an HTTP response.
     */
    @Benchmark
    public void httpSerializeResponse(Blackhole bh) {
        bh.consume(httpCodec.serializeResponse(jsonResponse));
    }

    /**
     * Parse an HTTP request from bytes.
     */
    @Benchmark
    public void httpParseRequest(Blackhole bh) {
        ByteBuffer bytes = httpCodec.serializeRequest(jsonRequest);
        bytes.rewind();
        HttpRequest parsed = httpCodec.parseRequest(bytes);
        bh.consume(parsed.getMethod());
        bh.consume(parsed.getHeaders().get("content-type"));
    }

    /**
     * Parse an HTTP response from bytes.
     */
    @Benchmark
    public void httpParseResponse(Blackhole bh) {
        ByteBuffer bytes = httpCodec.serializeResponse(jsonResponse);
        bytes.rewind();
        HttpResponse parsed = httpCodec.parseResponse(bytes);
        bh.consume(parsed.getStatus());
        bh.consume(parsed.getBodyAsString().length());
    }

    // -- RESP Codec ------------------------------------------------------------------

    /**
     * Encode a RESP bulk string (128 bytes).
     */
    @Benchmark
    public void respEncodeBulkString(Blackhole bh) {
        bh.consume(RespCodec.encode(respBulkString));
    }

    /**
     * Encode a RESP array with 4 elements.
     */
    @Benchmark
    public void respEncodeArray(Blackhole bh) {
        bh.consume(RespCodec.encode(respArray));
    }

    /**
     * Decode a RESP bulk string from wire bytes.
     */
    @Benchmark
    public void respDecodeBulkString(Blackhole bh) throws IOException {
        try (var stream = new ByteArrayInputStream(respEncodedBulk)) {
            var parser = new ssg.legoflow.database.redis.protocol.RespParser(stream);
            RespType decoded = parser.parse();
            if (decoded instanceof RespType.BulkString bs) {
                bh.consume(bs.value());
            }
        }
    }

    /**
     * Decode a RESP array from wire bytes.
     */
    @Benchmark
    public void respDecodeArray(Blackhole bh) throws IOException {
        try (var stream = new ByteArrayInputStream(respEncodedArray)) {
            var parser = new ssg.legoflow.database.redis.protocol.RespParser(stream);
            RespType decoded = parser.parse();
            if (decoded instanceof RespType.Array arr) {
                bh.consume(arr.elements().size());
            }
        }
    }

    // -- Roundtrip comparisons -------------------------------------------------------

    /**
     * Full HTTP roundtrip: serialize + parse request.
     */
    @Benchmark
    public void httpRoundtripRequest(Blackhole bh) {
        ByteBuffer bytes = httpCodec.serializeRequest(jsonRequest);
        bytes.rewind();
        HttpRequest parsed = httpCodec.parseRequest(bytes);
        bh.consume(parsed.getMethod());
        bh.consume(parsed.getBodyAsString().length());
    }

    /**
     * Full RESP roundtrip: encode + decode bulk string.
     */
    @Benchmark
    public void respRoundtripBulkString(Blackhole bh) throws IOException {
        byte[] encoded = RespCodec.encode(respBulkString);
        try (var stream = new ByteArrayInputStream(encoded)) {
            var parser = new ssg.legoflow.database.redis.protocol.RespParser(stream);
            RespType decoded = parser.parse();
            if (decoded instanceof RespType.BulkString bs) {
                bh.consume(bs.value());
            }
        }
    }

    private String makePayload(int size) {
        char[] chars = new char[size];
        for (int i = 0; i < size; i++) {
            chars[i] = (char) ('A' + (i % 26));
        }
        return new String(chars);
    }
}
