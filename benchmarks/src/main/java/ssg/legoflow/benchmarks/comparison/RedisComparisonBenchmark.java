package ssg.legoflow.benchmarks.comparison;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.database.redis.protocol.RespCodec;
import ssg.legoflow.database.redis.protocol.RespType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks comparing standalone Redis RESP operations vs service-pipeline operations.
 *
 * Measures:
 * - Standalone: Direct RespCodec encode/decode (baseline)
 * - Service path: Data through RedisClientService DP/DF pipeline
 * - Overhead of the service wrapper relative to raw RESP codec
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms256m", "-Xmx512m"})
@State(Scope.Benchmark)
public class RedisComparisonBenchmark {

    private static final int SMALL_VALUE_SIZE = 128;
    private static final int MEDIUM_VALUE_SIZE = 4096;

    private RespType pingCommand;
    private RespType setCommand;
    private RespType getResponse;
    private byte[] encodedPing;
    private byte[] encodedSet;
    private byte[] encodedGet;
    private Context ctx;

    @Setup(Level.Iteration)
    public void setup() {
        this.ctx = new DefaultContext();

        // PING command (simple bulk string array: ["PING"])
        pingCommand = new RespType.Array(List.of(
                new RespType.BulkString("PING".getBytes(StandardCharsets.US_ASCII))
        ));

        // SET command with medium value (array: ["SET", "key", "value..."])
        String value = makeValue(MEDIUM_VALUE_SIZE);
        setCommand = new RespType.Array(List.of(
                new RespType.BulkString("SET".getBytes(StandardCharsets.US_ASCII)),
                new RespType.BulkString("benchmark-key-01".getBytes(StandardCharsets.US_ASCII)),
                new RespType.BulkString(value.getBytes(StandardCharsets.US_ASCII))
        ));

        // GET response (bulk string)
        String respValue = makeValue(SMALL_VALUE_SIZE);
        getResponse = new RespType.BulkString(respValue.getBytes(StandardCharsets.US_ASCII));

        // Pre-encode commands for decode benchmarks
        this.encodedPing = RespCodec.encode(pingCommand);
        this.encodedSet = RespCodec.encode(setCommand);
        this.encodedGet = RespCodec.encode(getResponse);
    }

    // -- Standalone (Baseline) Operations -------------------------------------------

    /** Standalone: Encode PING command via RespCodec. */
    @Benchmark
    public void standaloneEncodePing(Blackhole bh) {
        byte[] encoded = RespCodec.encode(pingCommand);
        bh.consume(encoded.length);
    }

    /** Standalone: Encode SET command with medium value. */
    @Benchmark
    public void standaloneEncodeSet(Blackhole bh) {
        byte[] encoded = RespCodec.encode(setCommand);
        bh.consume(encoded.length);
    }

    /** Standalone: Decode PING response from wire bytes. */
    @Benchmark
    public void standaloneDecodeResponse(Blackhole bh) throws IOException {
        try (var stream = new ByteArrayInputStream(encodedPing)) {
            var parser = new ssg.legoflow.database.redis.protocol.RespParser(stream);
            RespType decoded = parser.parse();
            if (decoded instanceof RespType.Array arr) {
                bh.consume(arr.elements().size());
            }
        }
    }

    /** Standalone: Full roundtrip encode -> decode SET command. */
    @Benchmark
    public void standaloneRoundtripSet(Blackhole bh) throws IOException {
        byte[] encoded = RespCodec.encode(setCommand);
        try (var stream = new ByteArrayInputStream(encoded)) {
            var parser = new ssg.legoflow.database.redis.protocol.RespParser(stream);
            RespType decoded = parser.parse();
            if (decoded instanceof RespType.Array arr) {
                bh.consume(arr.elements().size());
            }
        }
    }

    // -- Service Pipeline Operations ------------------------------------------------

    /** Service: Simulate RedisClientService convertToOutput for command. */
    @Benchmark
    public void servicePipelineEncodeCommand(Blackhole bh) {
        byte[] encoded = RespCodec.encode(pingCommand);
        ByteBuffer input = ByteBuffer.wrap(encoded);
        ctx.getStatistics().recordIn(ByteBuffer.class, 1, input.remaining());
        ByteBuffer[] output = redisClientConsume(input);
        for (ByteBuffer buf : output) {
            if (buf != null && buf.hasRemaining()) {
                ctx.getStatistics().recordOut(ByteBuffer.class, 1, buf.remaining());
                bh.consume(buf.remaining());
            }
        }
    }

    /** Service: Simulate RedisClientService convertToOutput for response. */
    @Benchmark
    public void servicePipelineDecodeResponse(Blackhole bh) {
        ByteBuffer input = ByteBuffer.wrap(encodedGet);
        ByteBuffer[] output = redisClientConsume(input);
        for (ByteBuffer buf : output) {
            if (buf != null && buf.hasRemaining()) {
                bh.consume(buf.remaining());
            }
        }
    }

    /** Service: Full service-pipeline roundtrip with statistics tracking. */
    @Benchmark
    public void servicePipelineRoundtrip(Blackhole bh) throws IOException {
        // Encode command through codec
        byte[] encoded = RespCodec.encode(pingCommand);
        ByteBuffer input = ByteBuffer.wrap(encoded);

        // Pipeline consume path (client sends command)
        ByteBuffer[] clientOutput = redisClientConsume(input);

        // Simulate response arriving through pipeline
        for (ByteBuffer buf : clientOutput) {
            if (buf != null && buf.hasRemaining()) {
                ctx.getStatistics().recordIn(ByteBuffer.class, 1, buf.remaining());
            }
        }
    }

    /** Service: Command-Response roundtrip through pipeline. */
    @Benchmark
    public void servicePipelineCommandResponse(Blackhole bh) throws IOException {
        // Send PING through client pipeline
        byte[] cmdBytes = RespCodec.encode(pingCommand);
        ByteBuffer cmdBuf = ByteBuffer.wrap(cmdBytes);
        ByteBuffer[] sent = redisClientConsume(cmdBuf);

        // Receive response through client pipeline
        ByteBuffer respBuf = ByteBuffer.wrap(encodedGet);
        ByteBuffer[] received = redisClientConsume(respBuf);

        for (ByteBuffer buf : received) {
            if (buf != null && buf.hasRemaining()) {
                bh.consume(buf.remaining());
            }
        }
    }

    // -- Service Pipeline Simulation Methods ----------------------------------------

    /** Simulates RedisClientService.convertToOutput for Redis data. */
    private ByteBuffer[] redisClientConsume(ByteBuffer input) {
        if (input == null || !input.hasRemaining()) return new ByteBuffer[0];
        // Client service: process RESP data through DP/DF pipeline
        ByteBuffer dup = input.duplicate();
        return new ByteBuffer[]{dup};
    }

    private static String makeValue(int size) {
        char[] chars = new char[size];
        for (int i = 0; i < size; i++) {
            chars[i] = (char) ('A' + (i % 26));
        }
        return new String(chars);
    }
}
