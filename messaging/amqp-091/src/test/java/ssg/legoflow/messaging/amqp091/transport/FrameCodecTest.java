package ssg.legoflow.messaging.amqp091.transport;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.amqp091.common.Amqp091Constants;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for AMQP 0-9-1 frame codec encoding and decoding.
 * Wire format: TYPE(1) + CHAN(2) + SIZE(4) + PAYLOAD(N) + END(1)
 * Heartbeat: TYPE(1) + END(1)
 */
class FrameCodecTest {

    @Test
    void testHeartbeatEncodeDecode() {
        ByteBuffer encoded = Amqp091FrameCodec.encodeHeartbeat();
        assertThat(encoded.remaining()).isEqualTo(2);
        assertThat(encoded.get(0)).isEqualTo(Amqp091Constants.FRAME_METHOD);
        assertThat(encoded.get(1)).isEqualTo(Amqp091Constants.FRAME_END);

        // Rewind before decoding
        encoded.rewind();
        Amqp091Frame decoded = Amqp091FrameCodec.decode(encoded);
        assertThat(decoded).isNotNull();
        assertThat(decoded.type()).isEqualTo(Amqp091Constants.FRAME_METHOD);
        assertThat(decoded.payloadSize()).isEqualTo(0);
    }

    @Test
    void testMethodFrameEncodeDecode() {
        byte[] payload = "hello".getBytes();
        ByteBuffer encoded = Amqp091FrameCodec.encodeMethod(20, payload);

        // Wire format: TYPE(1) + CHAN(2) + SIZE(4) + PAYLOAD(5) + END(1) = 13
        assertThat(encoded.remaining()).isEqualTo(13);
        assertThat(encoded.get(0)).isEqualTo(Amqp091Constants.FRAME_METHOD);
        assertThat(encoded.getShort(1)).isEqualTo((short) 0);
        assertThat(encoded.getInt(3)).isEqualTo(5);

        // Payload at offset 7
        byte[] readPayload = new byte[5];
        encoded.get(7, readPayload);
        assertThat(readPayload).isEqualTo(payload);
        assertThat(encoded.get(12)).isEqualTo(Amqp091Constants.FRAME_END);

        // Rewind before decode
        encoded.rewind();
        Amqp091Frame decoded = Amqp091FrameCodec.decode(encoded);
        assertThat(decoded).isNotNull();
        assertThat(decoded.type()).isEqualTo(Amqp091Constants.FRAME_METHOD);
        assertThat(decoded.payloadSize()).isEqualTo(5);
    }

    @Test
    void testGenericFrameEncodeDecode() {
        byte[] payload = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        ByteBuffer encoded = Amqp091FrameCodec.encodeFrame(5, Amqp091Constants.FRAME_HEADER, 8, payload);

        assertThat(encoded.remaining()).isEqualTo(16);
        assertThat(encoded.get(0)).isEqualTo(Amqp091Constants.FRAME_HEADER);
        assertThat(encoded.getShort(1)).isEqualTo((short) 5);
        assertThat(encoded.getInt(3)).isEqualTo(8);
        assertThat(encoded.get(15)).isEqualTo(Amqp091Constants.FRAME_END);

        encoded.rewind();
        Amqp091Frame decoded = Amqp091FrameCodec.decode(encoded);
        assertThat(decoded).isNotNull();
        assertThat(decoded.type()).isEqualTo(Amqp091Constants.FRAME_HEADER);
        assertThat(decoded.channel()).isEqualTo(5);
        assertThat(decoded.payloadSize()).isEqualTo(8);
    }

    @Test
    void testBodyFrameRoundTrip() {
        byte[] bodyChunk = new byte[1024];
        for (int i = 0; i < bodyChunk.length; i++) bodyChunk[i] = (byte) (i & 0xFF);

        ByteBuffer encoded = Amqp091FrameCodec.encodeFrame(3, Amqp091Constants.FRAME_BODY, bodyChunk.length, bodyChunk);
        assertThat(encoded.remaining()).isEqualTo(8 + bodyChunk.length);

        encoded.rewind();
        Amqp091Frame decoded = Amqp091FrameCodec.decode(encoded);
        assertThat(decoded).isNotNull();
        assertThat(decoded.type()).isEqualTo(Amqp091Constants.FRAME_BODY);
        assertThat(decoded.channel()).isEqualTo(3);
        assertThat(decoded.payloadSize()).isEqualTo(1024);
    }

    @Test
    void testDecodeInsufficientData() {
        ByteBuffer tooSmall = ByteBuffer.allocate(4);
        tooSmall.put((byte) Amqp091Constants.FRAME_METHOD);
        tooSmall.flip();
        assertThat(Amqp091FrameCodec.decode(tooSmall)).isNull();
    }

    @Test
    void testDecodeMissingEndOctet() {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) Amqp091Constants.FRAME_METHOD);
        buf.putShort((short) 0);
        buf.putInt(0);
        buf.put((byte) 0xFF); // wrong end
        buf.flip();
        assertThatThrownBy(() -> Amqp091FrameCodec.decode(buf))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing frame end octet");
    }

    @Test
    void testHeartbeatWireFormatIs2Bytes() {
        ByteBuffer buf = Amqp091FrameCodec.encodeHeartbeat();
        assertThat(buf.limit()).isEqualTo(2);
        buf.rewind();
        Amqp091Frame frame = Amqp091FrameCodec.decode(buf);
        assertThat(frame).isNotNull();
        assertThat(frame.payloadSize()).isEqualTo(0);
    }

    @Test
    void testEmptyPayloadMethodFrame() {
        ByteBuffer encoded = Amqp091FrameCodec.encodeMethod(30, new byte[0]);
        assertThat(encoded.remaining()).isEqualTo(8);
        assertThat(encoded.get(0)).isEqualTo(Amqp091Constants.FRAME_METHOD);
        assertThat(encoded.getShort(1)).isEqualTo((short) 0);
        assertThat(encoded.getInt(3)).isEqualTo(0);
        assertThat(encoded.get(7)).isEqualTo(Amqp091Constants.FRAME_END);

        encoded.rewind();
        Amqp091Frame decoded = Amqp091FrameCodec.decode(encoded);
        assertThat(decoded).isNotNull();
        assertThat(decoded.payloadSize()).isEqualTo(0);
    }

    @Test
    void testDecodePartialPayload() {
        ByteBuffer buf = ByteBuffer.allocate(16);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) Amqp091Constants.FRAME_METHOD);
        buf.putShort((short) 1);
        buf.putInt(100); // claims 100 bytes but only 2 available
        buf.put(new byte[2]);
        buf.flip();

        // Should return null because not enough data
        assertThat(Amqp091FrameCodec.decode(buf)).isNull();
    }

    @Test
    void testLargePayloadRoundTrip() {
        byte[] large = new byte[65536];
        for (int i = 0; i < large.length; i++) large[i] = (byte) (i & 0xFF);

        ByteBuffer encoded = Amqp091FrameCodec.encodeFrame(99, Amqp091Constants.FRAME_BODY, large.length, large);
        assertThat(encoded.remaining()).isEqualTo(8 + large.length);

        encoded.rewind();
        Amqp091Frame decoded = Amqp091FrameCodec.decode(encoded);
        assertThat(decoded).isNotNull();
        assertThat(decoded.channel()).isEqualTo(99);
        assertThat(decoded.payloadSize()).isEqualTo(65536);
        assertThat(decoded.payload().array()).isEqualTo(large);
    }
}
