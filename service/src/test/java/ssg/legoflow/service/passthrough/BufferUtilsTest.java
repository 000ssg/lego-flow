package ssg.legoflow.service.passthrough;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BufferUtils}.
 */
class BufferUtilsTest {

    @Test
    void testDumpHex() {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[]{0x48, 0x65, 0x6c, 0x6c, 0x6f});
        String hex = BufferUtils.dumpHex(buffer);
        assertThat(hex).isEqualTo("48 65 6c 6c 6f");
    }

    @Test
    void testDumpHexEmptyBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(0);
        assertThat(BufferUtils.dumpHex(buffer)).isEmpty();
    }

    @Test
    void testDumpHexSingleByte() {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[]{(byte) 0xFF});
        assertThat(BufferUtils.dumpHex(buffer)).isEqualTo("ff");
    }

    @Test
    void testDumpHexDoesNotModifyBuffer() {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[]{0x01, 0x02, 0x03});
        BufferUtils.dumpHex(buffer);
        assertThat(buffer.position()).isEqualTo(0);
        assertThat(buffer.remaining()).isEqualTo(3);
    }

    @Test
    void testDumpHexNullThrows() {
        assertThatThrownBy(() -> BufferUtils.dumpHex(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIndexOfFound() {
        byte[] data = "Hello World".getBytes();
        byte[] pattern = "World".getBytes();
        assertThat(BufferUtils.indexOf(data, pattern, 0)).isEqualTo(6);
    }

    @Test
    void testIndexOfNotFound() {
        byte[] data = "Hello World".getBytes();
        byte[] pattern = "xyz".getBytes();
        assertThat(BufferUtils.indexOf(data, pattern, 0)).isEqualTo(-1);
    }

    @Test
    void testIndexOfWithOffset() {
        byte[] data = "abcabc".getBytes();
        byte[] pattern = "abc".getBytes();
        assertThat(BufferUtils.indexOf(data, pattern, 0)).isEqualTo(0);
        assertThat(BufferUtils.indexOf(data, pattern, 1)).isEqualTo(3);
    }

    @Test
    void testIndexOfAtBoundary() {
        byte[] data = "abc".getBytes();
        byte[] pattern = "abc".getBytes();
        assertThat(BufferUtils.indexOf(data, pattern, 0)).isEqualTo(0);
    }

    @Test
    void testIndexOfPatternLongerThanData() {
        byte[] data = "ab".getBytes();
        byte[] pattern = "abcdef".getBytes();
        assertThat(BufferUtils.indexOf(data, pattern, 0)).isEqualTo(-1);
    }

    @Test
    void testIndexOfEmptyPattern() {
        byte[] data = "abc".getBytes();
        byte[] pattern = new byte[0];
        assertThat(BufferUtils.indexOf(data, pattern, 0)).isEqualTo(0);
        assertThat(BufferUtils.indexOf(data, pattern, 2)).isEqualTo(2);
    }

    @Test
    void testIndexOfNullDataThrows() {
        assertThatThrownBy(() -> BufferUtils.indexOf(null, new byte[]{1}, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIndexOfNullPatternThrows() {
        assertThatThrownBy(() -> BufferUtils.indexOf(new byte[]{1}, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIndexOfNegativeOffsetThrows() {
        assertThatThrownBy(() -> BufferUtils.indexOf(new byte[]{1}, new byte[]{1}, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
