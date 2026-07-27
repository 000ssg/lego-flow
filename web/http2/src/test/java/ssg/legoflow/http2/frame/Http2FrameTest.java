package ssg.legoflow.http2.frame;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.*;

class Http2FrameTest {

    @Test
    void testDataFrameCreation() {
        var payload = ByteBuffer.wrap("hello".getBytes());
        var frame = Http2Frame.data(1, payload, false);

        assertThat(frame.type()).isEqualTo(Http2FrameType.DATA);
        assertThat(frame.streamId()).isEqualTo(1);
        assertThat(frame.hasFlag(Http2Flags.END_STREAM)).isFalse();
        assertThat(frame.payloadLength()).isEqualTo(5);
    }

    @Test
    void testDataFrameWithEndStream() {
        var frame = Http2Frame.data(1, ByteBuffer.wrap("data".getBytes()), true);

        assertThat(frame.hasFlag(Http2Flags.END_STREAM)).isTrue();
    }

    @Test
    void testHeadersFrameCreation() {
        var headerBlock = ByteBuffer.wrap(new byte[]{0x01, 0x02, 0x03});
        var frame = Http2Frame.headers(3, headerBlock, true, true);

        assertThat(frame.type()).isEqualTo(Http2FrameType.HEADERS);
        assertThat(frame.streamId()).isEqualTo(3);
        assertThat(frame.hasFlag(Http2Flags.END_STREAM)).isTrue();
        assertThat(frame.hasFlag(Http2Flags.END_HEADERS)).isTrue();
    }

    @Test
    void testPriorityFrame() {
        var frame = Http2Frame.priority(5, 3, 16, false);

        assertThat(frame.type()).isEqualTo(Http2FrameType.PRIORITY);
        assertThat(frame.streamId()).isEqualTo(5);
        assertThat(frame.payloadLength()).isEqualTo(5);
    }

    @Test
    void testPriorityFrameExclusive() {
        var frame = Http2Frame.priority(5, 3, 16, true);
        var payload = frame.payload();
        int dep = payload.getInt();

        assertThat(dep & 0x80000000).isNotZero();
        assertThat(dep & 0x7FFFFFFF).isEqualTo(3);
    }

    @Test
    void testRstStreamFrame() {
        var frame = Http2Frame.rstStream(1, Http2ErrorCode.CANCEL);

        assertThat(frame.type()).isEqualTo(Http2FrameType.RST_STREAM);
        assertThat(frame.streamId()).isEqualTo(1);
        var payload = frame.payload();
        assertThat(payload.getInt()).isEqualTo(Http2ErrorCode.CANCEL.code());
    }

    @Test
    void testSettingsFrame() {
        var settingsPayload = ByteBuffer.allocate(6);
        settingsPayload.putShort((short) 0x3);
        settingsPayload.putInt(100);
        settingsPayload.flip();
        var frame = Http2Frame.settings(settingsPayload);

        assertThat(frame.type()).isEqualTo(Http2FrameType.SETTINGS);
        assertThat(frame.streamId()).isEqualTo(0);
        assertThat(frame.hasFlag(Http2Flags.ACK)).isFalse();
    }

    @Test
    void testSettingsAckFrame() {
        var frame = Http2Frame.settingsAck();

        assertThat(frame.type()).isEqualTo(Http2FrameType.SETTINGS);
        assertThat(frame.hasFlag(Http2Flags.ACK)).isTrue();
        assertThat(frame.payloadLength()).isEqualTo(0);
    }

    @Test
    void testPingFrame() {
        var opaqueData = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        var frame = Http2Frame.ping(opaqueData);

        assertThat(frame.type()).isEqualTo(Http2FrameType.PING);
        assertThat(frame.hasFlag(Http2Flags.ACK)).isFalse();
        assertThat(frame.payloadLength()).isEqualTo(8);
    }

    @Test
    void testPingAckFrame() {
        var opaqueData = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        var frame = Http2Frame.pingAck(opaqueData);

        assertThat(frame.type()).isEqualTo(Http2FrameType.PING);
        assertThat(frame.hasFlag(Http2Flags.ACK)).isTrue();
    }

    @Test
    void testPingInvalidPayloadSize() {
        assertThatThrownBy(() -> Http2Frame.ping(ByteBuffer.wrap(new byte[7])))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGoawayFrame() {
        var debugData = ByteBuffer.wrap("error".getBytes());
        var frame = Http2Frame.goaway(5, Http2ErrorCode.NO_ERROR, debugData);

        assertThat(frame.type()).isEqualTo(Http2FrameType.GOAWAY);
        assertThat(frame.streamId()).isEqualTo(0);
        var payload = frame.payload();
        assertThat(payload.getInt() & 0x7FFFFFFF).isEqualTo(5);
        assertThat(payload.getInt()).isEqualTo(Http2ErrorCode.NO_ERROR.code());
    }

    @Test
    void testWindowUpdateFrame() {
        var frame = Http2Frame.windowUpdate(1, 32768);

        assertThat(frame.type()).isEqualTo(Http2FrameType.WINDOW_UPDATE);
        assertThat(frame.streamId()).isEqualTo(1);
        var payload = frame.payload();
        assertThat(payload.getInt() & 0x7FFFFFFF).isEqualTo(32768);
    }

    @Test
    void testWindowUpdateZeroIncrement() {
        assertThatThrownBy(() -> Http2Frame.windowUpdate(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testContinuationFrame() {
        var headerBlock = ByteBuffer.wrap(new byte[]{0x04, 0x05});
        var frame = Http2Frame.continuation(1, headerBlock, true);

        assertThat(frame.type()).isEqualTo(Http2FrameType.CONTINUATION);
        assertThat(frame.hasFlag(Http2Flags.END_HEADERS)).isTrue();
    }

    @Test
    void testPushPromiseFrame() {
        var headerBlock = ByteBuffer.wrap(new byte[]{0x01, 0x02});
        var frame = Http2Frame.pushPromise(1, 2, headerBlock);

        assertThat(frame.type()).isEqualTo(Http2FrameType.PUSH_PROMISE);
        assertThat(frame.streamId()).isEqualTo(1);
        var payload = frame.payload();
        assertThat(payload.getInt() & 0x7FFFFFFF).isEqualTo(2);
    }

    @Test
    void testEncodeDecodeRoundTrip() {
        var original = Http2Frame.data(1, ByteBuffer.wrap("test data".getBytes()), true);
        var encoded = original.encode();
        var decoded = Http2Frame.decode(encoded);

        assertThat(decoded.type()).isEqualTo(original.type());
        assertThat(decoded.flags()).isEqualTo(original.flags());
        assertThat(decoded.streamId()).isEqualTo(original.streamId());
        assertThat(decoded.payloadLength()).isEqualTo(original.payloadLength());
    }

    @Test
    void testFrameHeaderSize() {
        assertThat(Http2Frame.HEADER_SIZE).isEqualTo(9);
    }

    @Test
    void testStreamIdMasksReservedBit() {
        var frame = new Http2Frame(Http2FrameType.DATA, (byte) 0, 0xFFFFFFFF, ByteBuffer.allocate(0));
        assertThat(frame.streamId()).isEqualTo(0x7FFFFFFF);
    }

    @Test
    void testNullPayload() {
        var frame = new Http2Frame(Http2FrameType.SETTINGS, (byte) 0, 0, null);
        assertThat(frame.payloadLength()).isEqualTo(0);
    }
}
