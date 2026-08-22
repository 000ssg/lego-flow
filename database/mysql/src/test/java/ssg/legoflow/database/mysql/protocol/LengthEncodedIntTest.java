package ssg.legoflow.database.mysql.protocol;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link LengthEncodedInt}.
 */
class LengthEncodedIntTest {

    @Test
    void testOneByte_zero() {
        var encoded = LengthEncodedInt.encode(0);
        assertThat(encoded).hasSize(1);
        assertThat(LengthEncodedInt.decode(encoded)).isEqualTo(0);
    }

    @Test
    void testOneByte_max() {
        var encoded = LengthEncodedInt.encode(250);
        assertThat(encoded).hasSize(1);
        assertThat(LengthEncodedInt.decode(encoded)).isEqualTo(250);
    }

    @Test
    void testOneByte_one() {
        var encoded = LengthEncodedInt.encode(1);
        assertThat(encoded).hasSize(1);
        assertThat(LengthEncodedInt.decode(encoded)).isEqualTo(1);
    }

    @Test
    void testOneByte_127() {
        var encoded = LengthEncodedInt.encode(127);
        assertThat(encoded).hasSize(1);
        assertThat(LengthEncodedInt.decode(encoded)).isEqualTo(127);
    }

    @Test
    void testTwoByte_251() {
        var encoded = LengthEncodedInt.encode(251);
        assertThat(encoded).hasSize(3);
        assertThat(encoded[0] & 0xFF).isEqualTo(0xFC);
        assertThat(LengthEncodedInt.decode(encoded)).isEqualTo(251);
    }

    @Test
    void testTwoByte_65535() {
        var encoded = LengthEncodedInt.encode(65535);
        assertThat(encoded).hasSize(3);
        assertThat(LengthEncodedInt.decode(encoded)).isEqualTo(65535);
    }

    @Test
    void testTwoByte_500() {
        var encoded = LengthEncodedInt.encode(500);
        assertThat(encoded).hasSize(3);
        assertThat(LengthEncodedInt.decode(encoded)).isEqualTo(500);
    }

    @Test
    void testThreeByte_65536() {
        var encoded = LengthEncodedInt.encode(65536);
        assertThat(encoded).hasSize(4);
        assertThat(encoded[0] & 0xFF).isEqualTo(0xFD);
        assertThat(LengthEncodedInt.decode(encoded)).isEqualTo(65536);
    }

    @Test
    void testThreeByte_max() {
        var encoded = LengthEncodedInt.encode((1L << 24) - 1);
        assertThat(encoded).hasSize(4);
        assertThat(LengthEncodedInt.decode(encoded)).isEqualTo((1L << 24) - 1);
    }

    @Test
    void testEightByte() {
        var encoded = LengthEncodedInt.encode(1L << 24);
        assertThat(encoded).hasSize(9);
        assertThat(encoded[0] & 0xFF).isEqualTo(0xFE);
        assertThat(LengthEncodedInt.decode(encoded)).isEqualTo(1L << 24);
    }

    @Test
    void testEightByte_large() {
        long value = 1_000_000_000_000L;
        var encoded = LengthEncodedInt.encode(value);
        assertThat(encoded).hasSize(9);
        assertThat(LengthEncodedInt.decode(encoded)).isEqualTo(value);
    }

    @Test
    void testNull_marker() {
        var buf = ByteBuffer.allocate(1);
        buf.put((byte) 0xFB);
        buf.flip();
        assertThat(LengthEncodedInt.read(buf)).isEqualTo(-1);
    }

    @Test
    void testEncodedLength() {
        assertThat(LengthEncodedInt.encodedLength(0)).isEqualTo(1);
        assertThat(LengthEncodedInt.encodedLength(250)).isEqualTo(1);
        assertThat(LengthEncodedInt.encodedLength(251)).isEqualTo(3);
        assertThat(LengthEncodedInt.encodedLength(65535)).isEqualTo(3);
        assertThat(LengthEncodedInt.encodedLength(65536)).isEqualTo(4);
        assertThat(LengthEncodedInt.encodedLength((1L << 24) - 1)).isEqualTo(4);
        assertThat(LengthEncodedInt.encodedLength(1L << 24)).isEqualTo(9);
    }

    @Test
    void testBufferReadWrite() {
        var buf = ByteBuffer.allocate(30);
        LengthEncodedInt.write(buf, 42);
        LengthEncodedInt.write(buf, 300);
        LengthEncodedInt.write(buf, 100000);
        buf.flip();

        assertThat(LengthEncodedInt.read(buf)).isEqualTo(42);
        assertThat(LengthEncodedInt.read(buf)).isEqualTo(300);
        assertThat(LengthEncodedInt.read(buf)).isEqualTo(100000);
    }

    @Test
    void testNegative_throwsException() {
        assertThatThrownBy(() -> LengthEncodedInt.encode(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testRoundTrip_allBoundaries() {
        long[] values = {0, 1, 249, 250, 251, 252, 65534, 65535, 65536,
                (1L << 24) - 1, 1L << 24, Long.MAX_VALUE};
        for (long v : values) {
            assertThat(LengthEncodedInt.decode(LengthEncodedInt.encode(v)))
                    .as("Round-trip for %d", v).isEqualTo(v);
        }
    }
}
