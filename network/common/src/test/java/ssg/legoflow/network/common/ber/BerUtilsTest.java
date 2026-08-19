package ssg.legoflow.network.common.ber;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.asn1.Asn1Tag;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link BerUtils}.
 */
class BerUtilsTest {

    @Test
    void testPeekTag() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{0x02, 0x01, 0x05});
        Asn1Tag tag = BerUtils.peekTag(buf);
        assertThat(tag).isEqualTo(Asn1Tag.INTEGER);
        assertThat(buf.position()).isEqualTo(0); // position unchanged
    }

    @Test
    void testIsEndOfContentsTrue() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{0x00, 0x00});
        assertThat(BerUtils.isEndOfContents(buf)).isTrue();
        assertThat(buf.position()).isEqualTo(0);
    }

    @Test
    void testIsEndOfContentsFalse() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{0x01, 0x00});
        assertThat(BerUtils.isEndOfContents(buf)).isFalse();
    }

    @Test
    void testIsEndOfContentsInsufficientData() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{0x00});
        assertThat(BerUtils.isEndOfContents(buf)).isFalse();
    }

    @Test
    void testConsumeEndOfContents() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{0x00, 0x00, 0x01});
        BerUtils.consumeEndOfContents(buf);
        assertThat(buf.position()).isEqualTo(2);
    }

    @Test
    void testConsumeEndOfContentsInvalid() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{0x01, 0x00});
        assertThatThrownBy(() -> BerUtils.consumeEndOfContents(buf))
                .isInstanceOf(BerDecodingException.class);
    }

    @Test
    void testBase128RoundTrip() {
        int[] values = {0, 1, 127, 128, 16383, 16384, 2097151};
        for (int v : values) {
            ByteBuffer buf = ByteBuffer.allocate(5);
            BerUtils.encodeBase128(v, buf);
            buf.flip();
            assertThat(BerUtils.decodeBase128(buf)).isEqualTo(v);
        }
    }

    @Test
    void testBase128Length() {
        assertThat(BerUtils.base128Length(0)).isEqualTo(1);
        assertThat(BerUtils.base128Length(127)).isEqualTo(1);
        assertThat(BerUtils.base128Length(128)).isEqualTo(2);
        assertThat(BerUtils.base128Length(16383)).isEqualTo(2);
        assertThat(BerUtils.base128Length(16384)).isEqualTo(3);
    }

    @Test
    void testTlvSize() {
        // BOOLEAN: tag(1) + length(1) + value(1) = 3
        assertThat(BerUtils.tlvSize(Asn1Tag.BOOLEAN, 1)).isEqualTo(3);
        // SEQUENCE with 200 bytes content: tag(1) + length(2, long form) + 200 = 203
        assertThat(BerUtils.tlvSize(Asn1Tag.SEQUENCE, 200)).isEqualTo(203);
    }

    @Test
    void testEncodeBase128Negative() {
        ByteBuffer buf = ByteBuffer.allocate(5);
        assertThatThrownBy(() -> BerUtils.encodeBase128(-1, buf))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
