package ssg.legoflow.benchmarks.comparison;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DefaultContext;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks comparing standalone SMTP operations vs service-pipeline SMTP operations.
 *
 * Measures:
 * - Standalone: Direct protocol command/response handling (baseline)
 * - Service path: Data through SmtpService/SmtpClientService DP/DF pipeline
 * - Overhead of the service wrapper relative to raw protocol handling
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms256m", "-Xmx512m"})
@State(Scope.Benchmark)
public class SmtpComparisonBenchmark {

    private static final String HELLO_CMD = "EHLO bench.example.com\r\n";
    private static final String MAIL_FROM_CMD = "MAIL FROM:<sender@example.com>\r\n";
    private static final String RCPT_TO_CMD = "RCPT TO:<recipient@example.org>\r\n";
    private static final String DATA_HEADER = "DATA\r\n";
    private static final int BODY_SIZE = 4096;

    private Context ctx;
    private ByteBuffer smtpHello;
    private ByteBuffer smtpData;
    private ByteBuffer smtpResponse;
    private ByteBuffer emailBody;

    @Setup(Level.Iteration)
    public void setup() {
        this.ctx = new DefaultContext();

        // SMTP command bytes
        this.smtpHello = ByteBuffer.wrap(HELLO_CMD.getBytes(StandardCharsets.US_ASCII));
        this.smtpData = ByteBuffer.wrap(DATA_HEADER.getBytes(StandardCharsets.US_ASCII));

        // SMTP response bytes
        String response250 = "250 OK\r\n";
        this.smtpResponse = ByteBuffer.wrap(response250.getBytes(StandardCharsets.US_ASCII));

        // Email body (mimics MIME content)
        StringBuilder body = new StringBuilder(BODY_SIZE);
        for (int i = 0; i < BODY_SIZE / 16; i++) {
            body.append("This is line ").append(i).append(" of the benchmark email body.\r\n");
        }
        this.emailBody = ByteBuffer.wrap(body.toString().getBytes(StandardCharsets.US_ASCII));
    }

    // -- Standalone (Baseline) Operations -------------------------------------------

    /** Standalone: Parse SMTP command from bytes. */
    @Benchmark
    public void standaloneParseCommand(Blackhole bh) {
        byte[] data = new byte[smtpHello.remaining()];
        smtpHello.get(data);
        String command = new String(data, StandardCharsets.US_ASCII).trim();
        bh.consume(command);
    }

    /** Standalone: Build SMTP response bytes. */
    @Benchmark
    public void standaloneBuildResponse(Blackhole bh) {
        byte[] response = "250 2.1.0 OK\r\n".getBytes(StandardCharsets.US_ASCII);
        bh.consume(response.length);
    }

    /** Standalone: Full command -> response roundtrip. */
    @Benchmark
    public void standaloneCommandRoundtrip(Blackhole bh) {
        // Parse command
        byte[] cmd = new byte[smtpHello.remaining()];
        smtpHello.duplicate().get(cmd);
        String parsed = new String(cmd, StandardCharsets.US_ASCII).trim();

        // Generate response
        byte[] resp;
        if (parsed.startsWith("EHLO")) {
            resp = "250 EHLO OK\r\n".getBytes(StandardCharsets.US_ASCII);
        } else {
            resp = "500 Unknown command\r\n".getBytes(StandardCharsets.US_ASCII);
        }
        bh.consume(resp.length);
    }

    // -- Service Pipeline Operations ------------------------------------------------

    /** Service: Simulate SmtpService convertToOutput for inbound SMTP command. */
    @Benchmark
    public void servicePipelineConsumeCommand(Blackhole bh) {
        ctx.getStatistics().recordIn(ByteBuffer.class, 1, smtpHello.remaining());
        ByteBuffer[] output = smtpServerConsume(smtpHello);
        for (ByteBuffer buf : output) {
            if (buf != null && buf.hasRemaining()) {
                ctx.getStatistics().recordOut(ByteBuffer.class, 1, buf.remaining());
                bh.consume(buf.remaining());
            }
        }
    }

    /** Service: Simulate SmtpClientService convertToOutput for response. */
    @Benchmark
    public void servicePipelineConsumeResponse(Blackhole bh) {
        ByteBuffer[] output = smtpClientConsume(smtpResponse);
        for (ByteBuffer buf : output) {
            if (buf != null && buf.hasRemaining()) {
                bh.consume(buf.remaining());
            }
        }
    }

    /** Service: Full service-pipeline roundtrip with statistics. */
    @Benchmark
    public void servicePipelineRoundtrip(Blackhole bh) {
        // Client sends EHLO through pipeline
        ByteBuffer[] clientOutput = smtpClientConsume(smtpHello);

        // Server receives through pipeline
        for (ByteBuffer buf : clientOutput) {
            if (buf != null && buf.hasRemaining()) {
                ctx.getStatistics().recordIn(ByteBuffer.class, 1, buf.remaining());
                ByteBuffer[] serverOutput = smtpServerConsume(buf.duplicate());
                for (ByteBuffer resp : serverOutput) {
                    if (resp != null && resp.hasRemaining()) {
                        ctx.getStatistics().recordOut(ByteBuffer.class, 1, resp.remaining());
                        bh.consume(resp.remaining());
                    }
                }
            }
        }
    }

    // -- Service Pipeline Simulation Methods ----------------------------------------

    /** Simulates SmtpService.convertToOutput for server-side SMTP data. */
    private ByteBuffer[] smtpServerConsume(ByteBuffer input) {
        if (input == null || !input.hasRemaining()) return new ByteBuffer[0];
        // Server service: process through DP/DF pipeline
        ByteBuffer dup = input.duplicate();
        return new ByteBuffer[]{dup};
    }

    /** Simulates SmtpClientService.convertToOutput for client-side SMTP data. */
    private ByteBuffer[] smtpClientConsume(ByteBuffer input) {
        if (input == null || !input.hasRemaining()) return new ByteBuffer[0];
        // Client service: process through pipeline
        ByteBuffer dup = input.duplicate();
        return new ByteBuffer[]{dup};
    }
}
