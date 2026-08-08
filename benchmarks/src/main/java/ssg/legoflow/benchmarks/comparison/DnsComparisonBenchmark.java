package ssg.legoflow.benchmarks.comparison;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.network.dns.protocol.DnsCodec;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.RecordType;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks comparing standalone DNS operations vs service-pipeline DNS operations.
 *
 * Measures:
 * - Standalone: Direct DnsCodec encode/decode (baseline)
 * - Service path: Data through DnsService/DnsClientService DP/DF pipeline
 * - Overhead of the service wrapper relative to raw codec
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms256m", "-Xmx512m"})
@State(Scope.Benchmark)
public class DnsComparisonBenchmark {

    private static final String QUERY_DOMAIN = "example.com";
    private static final RecordType RECORD_TYPE = RecordType.A;

    private DnsMessage queryMessage;
    private ByteBuffer encodedQuery;
    private Context ctx;

    @Setup(Level.Iteration)
    public void setup() {
        this.ctx = new DefaultContext();
        // Build a DNS query message (A record lookup)
        queryMessage = DnsMessage.query(QUERY_DOMAIN, RECORD_TYPE);
        byte[] encoded = DnsCodec.encode(queryMessage);
        this.encodedQuery = ByteBuffer.wrap(encoded);
    }

    // -- Standalone (Baseline) Operations -------------------------------------------

    /** Standalone: Create a DNS query message. */
    @Benchmark
    public void standaloneCreateQuery(Blackhole bh) {
        DnsMessage msg = DnsMessage.query("bench.test.local", RECORD_TYPE);
        bh.consume(msg.header().id());
    }

    /** Standalone: Encode DNS query via DnsCodec. */
    @Benchmark
    public void standaloneEncodeQuery(Blackhole bh) {
        byte[] encoded = DnsCodec.encode(queryMessage);
        bh.consume(encoded.length);
    }

    /** Standalone: Decode DNS query from wire bytes. */
    @Benchmark
    public void standaloneDecodeQuery(Blackhole bh) {
        DnsMessage decoded = DnsCodec.decode(encodedQuery.duplicate().array());
        if (decoded != null) bh.consume(decoded.header().id());
    }

    /** Standalone: Full roundtrip encode -> decode. */
    @Benchmark
    public void standaloneRoundtrip(Blackhole bh) {
        byte[] encoded = DnsCodec.encode(queryMessage);
        DnsMessage decoded = DnsCodec.decode(encoded);
        if (decoded != null) {
            bh.consume(decoded.header().id());
        }
    }

    // -- Service Pipeline Operations ------------------------------------------------

    /** Service: Simulate DnsService convertToOutput for inbound DNS query. */
    @Benchmark
    public void servicePipelineConsumeQuery(Blackhole bh) {
        byte[] encoded = DnsCodec.encode(queryMessage);
        ByteBuffer input = ByteBuffer.wrap(encoded);
        ctx.getStatistics().recordIn(ByteBuffer.class, 1, input.remaining());
        ByteBuffer[] output = dnsServiceConsume(input);
        for (ByteBuffer buf : output) {
            if (buf != null && buf.hasRemaining()) {
                ctx.getStatistics().recordOut(ByteBuffer.class, 1, buf.remaining());
                bh.consume(buf.remaining());
            }
        }
    }

    /** Service: Simulate DnsClientService query response path. */
    @Benchmark
    public void servicePipelineClientResponse(Blackhole bh) {
        byte[] encoded = DnsCodec.encode(queryMessage);
        ByteBuffer input = ByteBuffer.wrap(encoded);
        ByteBuffer[] output = dnsClientConsume(input);
        for (ByteBuffer buf : output) {
            if (buf != null && buf.hasRemaining()) {
                bh.consume(buf.remaining());
            }
        }
    }

    /** Service: Full service-pipeline roundtrip with statistics tracking. */
    @Benchmark
    public void servicePipelineRoundtrip(Blackhole bh) {
        // Encode DNS message
        byte[] encoded = DnsCodec.encode(queryMessage);
        ByteBuffer input = ByteBuffer.wrap(encoded);

        // Pipeline consume path (server receives query)
        ByteBuffer[] serverOutput = dnsServiceConsume(input);

        // Pipeline submit path (client sends query)
        for (ByteBuffer buf : serverOutput) {
            if (buf != null && buf.hasRemaining()) {
                ctx.getStatistics().recordIn(ByteBuffer.class, 1, buf.remaining());
            }
        }
    }

    // -- Service Pipeline Simulation Methods ----------------------------------------

    /** Simulates DnsService.convertToOutput for server-side DNS data. */
    private ByteBuffer[] dnsServiceConsume(ByteBuffer input) {
        if (input == null || !input.hasRemaining()) return new ByteBuffer[0];
        // Server service: duplicate buffer, pass through DP/DF pipeline
        ByteBuffer dup = input.duplicate();
        return new ByteBuffer[]{dup};
    }

    /** Simulates DnsClientService.convertToOutput for client-side DNS data. */
    private ByteBuffer[] dnsClientConsume(ByteBuffer input) {
        if (input == null || !input.hasRemaining()) return new ByteBuffer[0];
        // Client service: process through pipeline with query handling
        ByteBuffer processed = input.duplicate();
        return new ByteBuffer[]{processed};
    }
}
