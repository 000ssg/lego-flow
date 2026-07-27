package ssg.legoflow.media.rtsp.interleaved;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link InterleavedFrame}.
 */
class InterleavedFrameTest {

    @Test
    void testCreateFrame() {
        byte[] data = {0x01, 0x02, 0x03};
        var frame = new InterleavedFrame(0, data);
        assertThat(frame.channel()).isEqualTo(0);
        assertThat(frame.data()).isEqualTo(data);
    }

    @Test
    void testChannelRange() {
        assertThatThrownBy(() -> new InterleavedFrame(-1, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InterleavedFrame(256, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        // Valid edges
        assertThatCode(() -> new InterleavedFrame(0, new byte[0])).doesNotThrowAnyException();
        assertThatCode(() -> new InterleavedFrame(255, new byte[0])).doesNotThrowAnyException();
    }

    @Test
    void testDataIsCopied() {
        byte[] data = {0x01, 0x02};
        var frame = new InterleavedFrame(0, data);
        data[0] = 0x00;
        assertThat(frame.data()[0]).isEqualTo((byte) 0x01);
    }

    @Test
    void testDataReturnIsCopied() {
        byte[] data = {0x01, 0x02};
        var frame = new InterleavedFrame(0, data);
        frame.data()[0] = 0x00;
        assertThat(frame.data()[0]).isEqualTo((byte) 0x01);
    }

    @Test
    void testFrameSize() {
        byte[] data = new byte[100];
        var frame = new InterleavedFrame(0, data);
        assertThat(frame.frameSize()).isEqualTo(104); // 4 header + 100 data
    }

    @Test
    void testFrameSizeEmpty() {
        var frame = new InterleavedFrame(0, new byte[0]);
        assertThat(frame.frameSize()).isEqualTo(4);
    }

    @Test
    void testMagicByte() {
        assertThat(InterleavedFrame.MAGIC).isEqualTo((byte) '$');
    }

    @Test
    void testHeaderSize() {
        assertThat(InterleavedFrame.HEADER_SIZE).isEqualTo(4);
    }

    @Test
    void testNullDataThrows() {
        assertThatThrownBy(() -> new InterleavedFrame(0, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testToString() {
        var frame = new InterleavedFrame(2, new byte[50]);
        assertThat(frame.toString()).contains("channel=2").contains("length=50");
    }
}
