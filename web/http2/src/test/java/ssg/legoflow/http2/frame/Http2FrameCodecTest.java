package ssg.legoflow.http2.frame;

import ssg.legoflow.blocks.DefaultContext;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;
class Http2FrameCodecTest {

    @Test
    void testDecodeCompleteFrame() {
        var codec = new Http2FrameCodec(Http2FrameCodec.Mode.DECODE);
        var ctx = new DefaultContext();

        var frame = Http2Frame.data(1, ByteBuffer.wrap("hello".getBytes()), true);
        var encoded = frame.encode();

        var results = codec.filter(ctx, encoded);

        assertThat(results).hasSize(1);
        var decoded = Http2Frame.decode(results[0]);
        assertThat(decoded.type()).isEqualTo(Http2FrameType.DATA);
        assertThat(decoded.streamId()).isEqualTo(1);
    }

    @Test
    void testDecodeMultipleFrames() {
        var codec = new Http2FrameCodec(Http2FrameCodec.Mode.DECODE);
        var ctx = new DefaultContext();

        var frame1 = Http2Frame.headers(1, ByteBuffer.wrap(new byte[]{0x01}), false, true);
        var frame2 = Http2Frame.data(1, ByteBuffer.wrap("data".getBytes()), true);

        var combined = ByteBuffer.allocate(frame1.encode().remaining() + frame2.encode().remaining());
        combined.put(frame1.encode());
        combined.put(frame2.encode());
        combined.flip();

        var results = codec.filter(ctx, combined);

        assertThat(results).hasSize(2);
    }

    @Test
    void testDecodePartialFrame() {
        var codec = new Http2FrameCodec(Http2FrameCodec.Mode.DECODE);
        var ctx = new DefaultContext();

        var frame = Http2Frame.data(1, ByteBuffer.wrap("hello world".getBytes()), true);
        var encoded = frame.encode();

        var part1 = ByteBuffer.allocate(5);
        for (int i = 0; i < 5; i++) part1.put(encoded.get());
        part1.flip();

        var part2 = ByteBuffer.allocate(encoded.remaining());
        part2.put(encoded);
        part2.flip();

        var results1 = codec.filter(ctx, part1);
        assertThat(results1).isEmpty();
        assertThat(codec.hasBufferedData()).isTrue();

        var results2 = codec.filter(ctx, part2);
        assertThat(results2).hasSize(1);
    }

    @Test
    void testEncodeMode() {
        var codec = new Http2FrameCodec(Http2FrameCodec.Mode.ENCODE);
        var ctx = new DefaultContext();

        var frame = Http2Frame.data(1, ByteBuffer.wrap("test".getBytes()), false);
        var encoded = frame.encode();

        var results = codec.filter(ctx, encoded);
        assertThat(results).hasSize(1);
    }

    @Test
    void testDecodeFrameHelper() {
        var codec = new Http2FrameCodec(Http2FrameCodec.Mode.DECODE);
        var frame = Http2Frame.settings(ByteBuffer.allocate(0));
        var encoded = frame.encode();

        var decoded = codec.decodeFrame(encoded);
        assertThat(decoded.type()).isEqualTo(Http2FrameType.SETTINGS);
    }

    @Test
    void testEncodeFrameHelper() {
        var codec = new Http2FrameCodec(Http2FrameCodec.Mode.ENCODE);
        var frame = Http2Frame.ping(ByteBuffer.wrap(new byte[8]));

        var encoded = codec.encodeFrame(frame);
        assertThat(encoded.remaining()).isEqualTo(Http2Frame.HEADER_SIZE + 8);
    }

    @Test
    void testDecodeAllFrameTypes() {
        var codec = new Http2FrameCodec(Http2FrameCodec.Mode.DECODE);
        var ctx = new DefaultContext();

        var frames = new Http2Frame[] {
            Http2Frame.data(1, ByteBuffer.wrap("x".getBytes()), false),
            Http2Frame.headers(3, ByteBuffer.wrap(new byte[]{0x01}), false, true),
            Http2Frame.priority(5, 0, 16, false),
            Http2Frame.rstStream(1, Http2ErrorCode.NO_ERROR),
            Http2Frame.settings(ByteBuffer.allocate(0)),
            Http2Frame.ping(ByteBuffer.wrap(new byte[8])),
            Http2Frame.goaway(0, Http2ErrorCode.NO_ERROR, null),
            Http2Frame.windowUpdate(0, 1024),
            Http2Frame.continuation(3, ByteBuffer.wrap(new byte[]{0x02}), true)
        };

        for (var frame : frames) {
            var encoded = frame.encode();
            var results = codec.filter(ctx, encoded);
            assertThat(results).hasSize(1);
            var decoded = Http2Frame.decode(results[0]);
            assertThat(decoded.type()).isEqualTo(frame.type());
        }
    }
}
