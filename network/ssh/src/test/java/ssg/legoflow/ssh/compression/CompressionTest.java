package ssg.legoflow.ssh.compression;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CompressionTest {

    @Test
    void testNoneCompressionName() {
        assertThat(new NoneCompression().name()).isEqualTo("none");
    }

    @Test
    void testNoneCompressionPassThrough() {
        NoneCompression c = new NoneCompression();
        byte[] data = "hello world".getBytes();
        assertThat(c.compress(data)).isSameAs(data);
        assertThat(c.decompress(data)).isSameAs(data);
    }

    @Test
    void testNoneCompressionNotDelayed() {
        assertThat(new NoneCompression().isDelayed()).isFalse();
    }

    @Test
    void testZlibCompressionName() {
        assertThat(new ZlibCompression().name()).isEqualTo("zlib");
    }

    @Test
    void testZlibCompressionNotDelayed() {
        assertThat(new ZlibCompression().isDelayed()).isFalse();
    }

    @Test
    void testZlibCompressDecompress() {
        ZlibCompression c = new ZlibCompression();
        byte[] data = "Hello, this is a test message for zlib compression!".getBytes();
        byte[] compressed = c.compress(data);
        assertThat(compressed).isNotEmpty();
        byte[] decompressed = c.decompress(compressed);
        assertThat(decompressed).isEqualTo(data);
    }

    @Test
    void testZlibCompressLargeData() {
        ZlibCompression c = new ZlibCompression();
        byte[] data = new byte[10000];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 256);
        byte[] compressed = c.compress(data);
        assertThat(compressed.length).isLessThan(data.length);
        byte[] decompressed = c.decompress(compressed);
        assertThat(decompressed).isEqualTo(data);
    }

    @Test
    void testZlibOpenSshName() {
        assertThat(new ZlibOpenSshCompression().name()).isEqualTo("zlib@openssh.com");
    }

    @Test
    void testZlibOpenSshIsDelayed() {
        assertThat(new ZlibOpenSshCompression().isDelayed()).isTrue();
    }

    @Test
    void testZlibOpenSshPassThroughBeforeActivation() {
        ZlibOpenSshCompression c = new ZlibOpenSshCompression();
        byte[] data = "test data".getBytes();
        assertThat(c.compress(data)).isEqualTo(data);
        assertThat(c.decompress(data)).isEqualTo(data);
    }

    @Test
    void testZlibOpenSshCompressAfterActivation() {
        ZlibOpenSshCompression c = new ZlibOpenSshCompression();
        c.setActive(true);
        byte[] data = "Hello, this is delayed compression test!".getBytes();
        byte[] compressed = c.compress(data);
        assertThat(compressed).isNotEmpty();
        byte[] decompressed = c.decompress(compressed);
        assertThat(decompressed).isEqualTo(data);
    }

    @Test
    void testZlibOpenSshDeactivate() {
        ZlibOpenSshCompression c = new ZlibOpenSshCompression();
        c.setActive(true);
        c.setActive(false);
        byte[] data = "test".getBytes();
        assertThat(c.compress(data)).isEqualTo(data);
    }

    @Test
    void testZlibRepeatableData() {
        ZlibCompression c = new ZlibCompression();
        byte[] data = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes();
        byte[] compressed = c.compress(data);
        // Highly repetitive data should compress well
        assertThat(compressed.length).isLessThan(data.length);
    }
}
