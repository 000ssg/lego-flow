package ssg.legoflow.benchmarks.messaging;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ssg.legoflow.mqtt.codec.MqttCodec;
import ssg.legoflow.mqtt.protocol.*;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
/**
 * Benchmarks for MQTT packet encoding and decoding latency.
 *
 * Measures the cost of encoding/decoding MQTT CONNECT, PUBLISH (QoS 0/1/2),
 * SUBSCRIBE, and SUBACK packets through {@link MqttCodec}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms256m", "-Xmx512m"})
@State(Scope.Benchmark)
public class MqttLatencyBenchmark {

    private static final int SMALL_PAYLOAD  = 128;
    private static final int MEDIUM_PAYLOAD = 1024;
    private static final int LARGE_PAYLOAD  = 8192;

    private MqttCodec codecV5;
    private MqttCodec codecV3;
    private PublishPacket smallPublishQos0;
    private PublishPacket smallPublishQos1;
    private PublishPacket mediumPublishQos2;
    private PublishPacket largePublishQos1;
    private ConnectPacket connectPacket;

    @Setup(Level.Iteration)
    public void setup() {
        this.codecV5 = new MqttCodec(MqttVersion.V5_0);
        this.codecV3 = new MqttCodec(MqttVersion.V3_1_1);

        // Small PUBLISH QoS 0 (fire-and-forget telemetry)
        smallPublishQos0 = new PublishPacket(
                "telemetry/sensor/temperature",
                makePayload(SMALL_PAYLOAD),
                QoS.AT_MOST_ONCE, false, false, 0, new ssg.legoflow.mqtt.protocol.MqttProperties());

        // Small PUBLISH QoS 1 (command with ack)
        smallPublishQos1 = new PublishPacket(
                "commands/device/actuator/set",
                makePayload(SMALL_PAYLOAD),
                QoS.AT_LEAST_ONCE, false, false, 42, new ssg.legoflow.mqtt.protocol.MqttProperties());

        // Medium PUBLISH QoS 2 (exactly-once financial events)
        mediumPublishQos2 = new PublishPacket(
                "events/finance/trade",
                makePayload(MEDIUM_PAYLOAD),
                QoS.EXACTLY_ONCE, false, false, 100, new ssg.legoflow.mqtt.protocol.MqttProperties());

        // Large PUBLISH QoS 1 (image chunk)
        largePublishQos1 = new PublishPacket(
                "images/camera/stream",
                makePayload(LARGE_PAYLOAD),
                QoS.AT_LEAST_ONCE, false, false, 200, new ssg.legoflow.mqtt.protocol.MqttProperties());

        // CONNECT packet
        connectPacket = new ConnectPacket(
                MqttVersion.V5_0, "bench-client-01", true, 60,
                "user", "pass", null, new ssg.legoflow.mqtt.protocol.MqttProperties());
    }

    /**
     * Encode a small QoS 0 PUBLISH packet.
     */
    @Benchmark
    public void encodeSmallQos0(Blackhole bh) {
        bh.consume(codecV5.encode(smallPublishQos0));
    }

    /**
     * Encode a small QoS 1 PUBLISH packet.
     */
    @Benchmark
    public void encodeSmallQos1(Blackhole bh) {
        bh.consume(codecV5.encode(smallPublishQos1));
    }

    /**
     * Encode a medium QoS 2 PUBLISH packet.
     */
    @Benchmark
    public void encodeMediumQos2(Blackhole bh) {
        bh.consume(codecV5.encode(mediumPublishQos2));
    }

    /**
     * Encode a large QoS 1 PUBLISH packet (8 KB payload).
     */
    @Benchmark
    public void encodeLargeQos1(Blackhole bh) {
        bh.consume(codecV5.encode(largePublishQos1));
    }

    /**
     * Encode a CONNECT packet.
     */
    @Benchmark
    public void encodeConnect(Blackhole bh) {
        bh.consume(codecV5.encode(connectPacket));
    }

    /**
     * Roundtrip: encode then decode a small QoS 0 PUBLISH.
     */
    @Benchmark
    public void roundtripSmallPublish(Blackhole bh) {
        ByteBuffer encoded = codecV5.encode(smallPublishQos0);
        MqttPacket decoded = codecV5.decode(encoded);
        if (decoded instanceof PublishPacket p) {
            bh.consume(p.topic());
            bh.consume(p.qos());
        }
    }

    /**
     * Roundtrip: encode then decode a large QoS 1 PUBLISH.
     */
    @Benchmark
    public void roundtripLargePublish(Blackhole bh) {
        ByteBuffer encoded = codecV5.encode(largePublishQos1);
        MqttPacket decoded = codecV5.decode(encoded);
        if (decoded instanceof PublishPacket p) {
            bh.consume(p.topic());
            bh.consume(p.qos());
        }
    }

    private byte[] makePayload(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i % 128);
        }
        return bytes;
    }
}
