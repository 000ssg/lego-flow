package ssg.legoflow.network.common.ber;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link BerLength} encoding and decoding.
 */
class BerLengthTest {

    // ── Short form ──

    @Test
    void testEncodeShortFormZero() {
        ByteBuffer buf = ByteBuffer.allocate(1);
        BerLength.encode(0, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0);
    }

    @Test
    void testEncodeShortForm127() {
        ByteBuffer buf = ByteBuffer.allocate(1);
        BerLength.encode(127, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(127);
    }

    @Test
    void testDecodeShortForm() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{42});
        assertThat(BerLength.decode(buf)).isEqualTo(42);
    }

    // ── Long form ──

    @Test
    void testEncodeLongForm128() {
        ByteBuffer buf = ByteBuffer.allocate(3);
        BerLength.encode(128, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0x81); // 1 byte follows
        assertThat(buf.get() & 0xFF).isEqualTo(128);
    }

    @Test
    void testEncodeLongForm255() {
        ByteBuffer buf = ByteBuffer.allocate(3);
        BerLength.encode(255, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0x81);
        assertThat(buf.get() & 0xFF).isEqualTo(255);
    }

    @Test
    void testEncodeLongForm256() {
        ByteBuffer buf = ByteBuffer.allocate(4);
        BerLength.encode(256, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0x82); // 2 bytes follow
        assertThat(buf.get() & 0xFF).isEqualTo(1);
        assertThat(buf.get() & 0xFF).isEqualTo(0);
    }

    @Test
    void testEncodeLongForm65535() {
        ByteBuffer buf = ByteBuffer.allocate(4);
        BerLength.encode(65535, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0x82);
        assertThat(buf.get() & 0xFF).isEqualTo(0xFF);
        assertThat(buf.get() & 0xFF).isEqualTo(0xFF);
    }

    @Test
    void testEncodeLongForm65536() {
        ByteBuffer buf = ByteBuffer.allocate(5);
        BerLength.encode(65536, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0x83); // 3 bytes follow
        assertThat(buf.get() & 0xFF).isEqualTo(1);
        assertThat(buf.get() & 0xFF).isEqualTo(0);
        assertThat(buf.get() & 0xFF).isEqualTo(0);
    }

    @Test
    void testRoundTripShortForm() {
        for (int i = 0; i <= 127; i++) {
            ByteBuffer buf = ByteBuffer.allocate(5);
            BerLength.encode(i, buf);
            buf.flip();
            assertThat(BerLength.decode(buf)).isEqualTo(i);
        }
    }

    @Test
    void testRoundTripLongForm() {
        int[] values = {128, 255, 256, 1000, 65535, 65536, 100000, 16777215, 16777216};
        for (int v : values) {
            ByteBuffer buf = ByteBuffer.allocate(6);
            BerLength.encode(v, buf);
            buf.flip();
            assertThat(BerLength.decode(buf)).isEqualTo(v);
        }
    }

    // ── Indefinite form ──

    @Test
    void testDecodeIndefiniteLength() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{(byte) 0x80});
        assertThat(BerLength.decode(buf)).isEqualTo(BerLength.INDEFINITE);
    }

    @Test
    void testEncodeIndefiniteLength() {
        ByteBuffer buf = ByteBuffer.allocate(1);
        BerLength.encodeIndefinite(buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0x80);
    }

    @Test
    void testEncodeEndOfContents() {
        ByteBuffer buf = ByteBuffer.allocate(2);
        BerLength.encodeEndOfContents(buf);
        buf.flip();
        assertThat(buf.get()).isEqualTo((byte) 0x00);
        assertThat(buf.get()).isEqualTo((byte) 0x00);
    }

    // ── Encoded length calculation ──

    @Test
    void testEncodedLength() {
        assertThat(BerLength.encodedLength(0)).isEqualTo(1);
        assertThat(BerLength.encodedLength(127)).isEqualTo(1);
        assertThat(BerLength.encodedLength(128)).isEqualTo(2);
        assertThat(BerLength.encodedLength(255)).isEqualTo(2);
        assertThat(BerLength.encodedLength(256)).isEqualTo(3);
        assertThat(BerLength.encodedLength(65535)).isEqualTo(3);
        assertThat(BerLength.encodedLength(65536)).isEqualTo(4);
    }

    // ── Error cases ──

    @Test
    void testEncodeNegativeLengthThrows() {
        ByteBuffer buf = ByteBuffer.allocate(5);
        assertThatThrownBy(() -> BerLength.encode(-1, buf))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDecodeEmptyBufferThrows() {
        ByteBuffer buf = ByteBuffer.allocate(0);
        assertThatThrownBy(() -> BerLength.decode(buf))
                .isInstanceOf(BerDecodingException.class);
    }

    @Test
    void testDecodeTruncatedLongFormThrows() {
        // Says 2 bytes follow but only 1 available
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{(byte) 0x82, 0x01});
        assertThatThrownBy(() -> BerLength.decode(buf))
                .isInstanceOf(BerDecodingException.class);
    }
}
