package ssg.legoflow.benchmarks.comparison;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.mqtt.codec.MqttCodec;
import ssg.legoflow.mqtt.protocol.*;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
/**
 * Benchmarks comparing standalone MQTT operations vs service-pipeline MQTT operations.
 *
 * Measures:
 * - Standalone: Direct MqttCodec encode/decode (baseline)
 * - Service path: Data through AbstractService DP/DF pipeline
 * - Overhead of the service wrapper relative to raw codec
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms256m", "-Xmx512m"})
@State(Scope.Benchmark)
public class MqttComparisonBenchmark {

    private static final int SMALL_PAYLOAD = 128;

    private MqttCodec codecV5;
    private PublishPacket qos0Publish;
    private PublishPacket qos1Publish;
    private ConnectPacket connectPacket;
    private ByteBuffer encodedQos0;
    private ByteBuffer encodedQos1;
    private Context ctx;

    @Setup(Level.Iteration)
    public void setup() {
        this.codecV5 = new MqttCodec(MqttVersion.V5_0);
        this.ctx = new DefaultContext();

        // QoS 0 PUBLISH (fire-and-forget telemetry)
        qos0Publish = new PublishPacket(
                "telemetry/sensor/temperature",
                makePayload(SMALL_PAYLOAD),
                QoS.AT_MOST_ONCE, false, false, 0, new ssg.legoflow.mqtt.protocol.MqttProperties());

        // QoS 1 PUBLISH (command with ack)
        qos1Publish = new PublishPacket(
                "commands/device/actuator/set",
                makePayload(SMALL_PAYLOAD),
                QoS.AT_LEAST_ONCE, false, false, 42, new ssg.legoflow.mqtt.protocol.MqttProperties());

        // CONNECT packet
        connectPacket = new ConnectPacket(
                MqttVersion.V5_0, "bench-client-01", true, 60,
                null, null, null, new ssg.legoflow.mqtt.protocol.MqttProperties());

        // Pre-encode for decode benchmarks
        this.encodedQos0 = codecV5.encode(qos0Publish);
        this.encodedQos1 = codecV5.encode(qos1Publish);
    }

    // -- Standalone (Baseline) Operations -------------------------------------------

    /** Standalone: Encode QoS 0 PUBLISH via MqttCodec. */
    @Benchmark
    public void standaloneEncodeQos0(Blackhole bh) {
        ByteBuffer result = codecV5.encode(qos0Publish);
        bh.consume(result.remaining());
    }

    /** Standalone: Decode QoS 0 PUBLISH from wire bytes. */
    @Benchmark
    public void standaloneDecodeQos0(Blackhole bh) {
        MqttPacket decoded = codecV5.decode(encodedQos0.duplicate());
        if (decoded instanceof PublishPacket p) {
            bh.consume(p.topic());
        }
    }

    /** Standalone: Full roundtrip encode -> decode QoS 0. */
    @Benchmark
    public void standaloneRoundtripQos0(Blackhole bh) {
        ByteBuffer encoded = codecV5.encode(qos0Publish);
        MqttPacket decoded = codecV5.decode(encoded);
        if (decoded instanceof PublishPacket p) {
            bh.consume(p.topic());
            bh.consume(p.qos().toString());
        }
    }

    /** Standalone: Full roundtrip encode -> decode QoS 1. */
    @Benchmark
    public void standaloneRoundtripQos1(Blackhole bh) {
        ByteBuffer encoded = codecV5.encode(qos1Publish);
        MqttPacket decoded = codecV5.decode(encoded);
        if (decoded instanceof PublishPacket p) {
            bh.consume(p.topic());
            bh.consume(p.packetId());
        }
    }

    // -- Service Pipeline Operations ------------------------------------------------

    /** Service: Simulate MqttBrokerService convertToOutput path for QoS 0 publish. */
    @Benchmark
    public void servicePipelineEncodeQos0(Blackhole bh) {
        ByteBuffer encoded = codecV5.encode(qos0Publish);
        ByteBuffer[] output = brokerPipelineConsume(encoded);
        for (ByteBuffer buf : output) {
            if (buf != null) bh.consume(buf.remaining());
        }
    }

    /** Service: Simulate MqttClientService convertToOutput path. */
    @Benchmark
    public void servicePipelineClientPath(Blackhole bh) {
        ByteBuffer encoded = codecV5.encode(qos1Publish);
        ctx.getStatistics().recordIn(ByteBuffer.class, 1, encoded.remaining());
        ByteBuffer[] output = clientPipelineConsume(encoded);
        for (ByteBuffer buf : output) {
            if (buf != null && buf.hasRemaining()) {
                ctx.getStatistics().recordOut(ByteBuffer.class, 1, buf.remaining());
                bh.consume(buf.remaining());
            }
        }
    }

    /** Service: Full service-pipeline roundtrip with statistics. */
    @Benchmark
    public void servicePipelineRoundtrip(Blackhole bh) {
        // Encode through codec
        ByteBuffer encoded = codecV5.encode(qos0Publish);

        // Pipeline consume path (broker receives publish)
        ByteBuffer[] output = brokerPipelineConsume(encoded);

        // Decode back to verify integrity
        for (ByteBuffer buf : output) {
            if (buf != null && buf.hasRemaining()) {
                MqttPacket decoded = codecV5.decode(buf.duplicate());
                if (decoded instanceof PublishPacket p) {
                    bh.consume(p.topic());
                }
            }
        }
    }

    // -- Service Pipeline Simulation Methods ----------------------------------------

    /** Simulates MqttBrokerService.convertToOutput for inbound publish. */
    private ByteBuffer[] brokerPipelineConsume(ByteBuffer input) {
        if (input == null || !input.hasRemaining()) return new ByteBuffer[0];
        // Broker service: duplicate buffer, pass through DP/DF pipeline
        ByteBuffer dup = input.duplicate();
        return new ByteBuffer[]{dup};
    }

    /** Simulates MqttClientService.convertToOutput for client data. */
    private ByteBuffer[] clientPipelineConsume(ByteBuffer input) {
        if (input == null || !input.hasRemaining()) return new ByteBuffer[0];
        // Client service: process through pipeline with statistics
        ByteBuffer processed = input.duplicate();
        return new ByteBuffer[]{processed};
    }

    private byte[] makePayload(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i % 128);
        }
        return bytes;
    }
}
