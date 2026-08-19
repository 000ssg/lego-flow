package ssg.legoflow.benchmarks.comparison;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DefaultContext;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
/**
 * Benchmarks comparing standalone CoAP operations vs service-pipeline CoAP operations.
 *
 * Measures:
 * - Standalone: Direct CoAP message construction/decoding (baseline)
 * - Service path: Data through CoapServerService/CoapClientService DP/DF pipeline
 * - Overhead of the service wrapper relative to raw protocol handling
 * - UDP-based transport characteristics (small datagrams, no persistent connection)
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms256m", "-Xmx512m"})
@State(Scope.Benchmark)
public class CoapComparisonBenchmark {

    private static final int SMALL_DATAGRAM = 64;
    private static final int MEDIUM_DATAGRAM = 512;
    private static final int LARGE_DATAGRAM = 1024;

    private Context ctx;
    private ByteBuffer smallCoapDatagram;
    private ByteBuffer mediumCoapDatagram;
    private ByteBuffer largeCoapDatagram;

    @Setup(Level.Iteration)
    public void setup() {
        this.ctx = new DefaultContext();
        // CoAP datagrams are typically smaller than TCP payloads due to UDP constraints
        this.smallCoapDatagram = buildCoapDatagram(SMALL_DATAGRAM);
        this.mediumCoapDatagram = buildCoapDatagram(MEDIUM_DATAGRAM);
        this.largeCoapDatagram = buildCoapDatagram(LARGE_DATAGRAM);
    }

    // -- Standalone (Baseline) Operations -------------------------------------------

    /** Standalone: Build a CoAP GET request datagram. */
    @Benchmark
    public void standaloneBuildGet(Blackhole bh) {
        byte[] datagram = buildCoapGetPayload();
        bh.consume(datagram.length);
    }

    /** Standalone: Parse CoAP message from bytes. */
    @Benchmark
    public void standaloneParseDatagram(Blackhole bh) {
        // Simulate parsing: extract version, type, token length, code, message ID
        byte[] data = new byte[smallCoapDatagram.remaining()];
        smallCoapDatagram.duplicate().get(data);
        if (data.length >= 4) {
            int version = (data[0] & 0xF0) >> 4;
            int type = (data[0] & 0x0C) >> 2;
            int code = ((data[0] & 0x01) << 8) | (data[1] & 0xFF);
            bh.consume(version);
            bh.consume(type);
            bh.consume(code);
        }
    }

    /** Standalone: Full datagram roundtrip (build -> parse). */
    @Benchmark
    public void standaloneDatagramRoundtrip(Blackhole bh) {
        byte[] built = buildCoapGetPayload();
        if (built.length >= 4) {
            int code = ((built[0] & 0x01) << 8) | (built[1] & 0xFF);
            bh.consume(code);
        }
    }

    // -- Service Pipeline Operations ------------------------------------------------

    /** Service: Simulate CoapServerService convertToOutput for inbound datagram. */
    @Benchmark
    public void servicePipelineConsumeSmall(Blackhole bh) {
        ctx.getStatistics().recordIn(ByteBuffer.class, 1, smallCoapDatagram.remaining());
        ByteBuffer[] output = coapServerConsume(smallCoapDatagram);
        for (ByteBuffer buf : output) {
            if (buf != null && buf.hasRemaining()) {
                ctx.getStatistics().recordOut(ByteBuffer.class, 1, buf.remaining());
                bh.consume(buf.remaining());
            }
        }
    }

    /** Service: Simulate CoapClientService convertToOutput for response. */
    @Benchmark
    public void servicePipelineConsumeMedium(Blackhole bh) {
        ByteBuffer[] output = coapClientConsume(mediumCoapDatagram);
        for (ByteBuffer buf : output) {
            if (buf != null && buf.hasRemaining()) {
                bh.consume(buf.remaining());
            }
        }
    }

    /** Service: Full service-pipeline roundtrip with statistics. */
    @Benchmark
    public void servicePipelineRoundtrip(Blackhole bh) {
        // Client sends GET through pipeline
        ByteBuffer[] clientOutput = coapClientConsume(smallCoapDatagram);

        // Server receives through pipeline
        for (ByteBuffer buf : clientOutput) {
            if (buf != null && buf.hasRemaining()) {
                ctx.getStatistics().recordIn(ByteBuffer.class, 1, buf.remaining());
                ByteBuffer[] serverOutput = coapServerConsume(buf.duplicate());
                for (ByteBuffer resp : serverOutput) {
                    if (resp != null && resp.hasRemaining()) {
                        ctx.getStatistics().recordOut(ByteBuffer.class, 1, resp.remaining());
                        bh.consume(resp.remaining());
                    }
                }
            }
        }
    }

    /** Service: Multi-datagram batch processing (simulates concurrent UDP arrives). */
    @Benchmark
    public void servicePipelineBatchDatagrams(Blackhole bh) {
        ByteBuffer[] datagrams = new ByteBuffer[]{
                smallCoapDatagram, mediumCoapDatagram, smallCoapDatagram
        };
        long totalBytes = 0;
        for (ByteBuffer datagram : datagrams) {
            ctx.getStatistics().recordIn(ByteBuffer.class, 1, datagram.remaining());
            ByteBuffer[] output = coapServerConsume(datagram);
            for (ByteBuffer buf : output) {
                if (buf != null && buf.hasRemaining()) {
                    totalBytes += buf.remaining();
                }
            }
        }
        bh.consume(totalBytes);
    }

    // -- Service Pipeline Simulation Methods ----------------------------------------

    /** Simulates CoapServerService.convertToOutput for server-side CoAP data. */
    private ByteBuffer[] coapServerConsume(ByteBuffer input) {
        if (input == null || !input.hasRemaining()) return new ByteBuffer[0];
        // Server service: process datagram through DP/DF pipeline
        ByteBuffer dup = input.duplicate();
        return new ByteBuffer[]{dup};
    }

    /** Simulates CoapClientService.convertToOutput for client-side CoAP data. */
    private ByteBuffer[] coapClientConsume(ByteBuffer input) {
        if (input == null || !input.hasRemaining()) return new ByteBuffer[0];
        // Client service: process through pipeline
        ByteBuffer dup = input.duplicate();
        return new ByteBuffer[]{dup};
    }

    /** Build a CoAP datagram with the specified size. */
    private static ByteBuffer buildCoapDatagram(int totalSize) {
        byte[] bytes = new byte[totalSize];
        // CoAP header: 4 bytes minimum (Ver/Tok/Type, Code, Message ID)
        bytes[0] = (byte) (0x40 | 0x01); // Ver=1, T=0, TKL=0, Type=CON(0)
        bytes[1] = (byte) 0x01;          // Code: GET
        bytes[2] = 0x00;                 // Message ID high byte
        bytes[3] = 0x01;                 // Message ID low byte
        // Fill remaining with payload
        for (int i = 4; i < totalSize; i++) {
            bytes[i] = (byte) (i % 128);
        }
        return ByteBuffer.wrap(bytes);
    }

    /** Build a CoAP GET request payload. */
    private static byte[] buildCoapGetPayload() {
        // Token URI: /temp with token 0x1A2B3C4D
        byte[] header = new byte[] {
                (byte) 0x59,   // Ver=1, T=0, TKL=4, Type=CON(0)
                0x01,          // Code: GET
                (byte) 0xAB, (byte) 0xCD,    // Message ID
                0x1A, 0x2B, 0x3C, 0x4D,  // Token
                (byte) 0x01,   // URI-Path option (type 15 = 0 + 11 = 15)
                't', 'e', 'm', 'p'       // URI path
        };
        return header;
    }
}
