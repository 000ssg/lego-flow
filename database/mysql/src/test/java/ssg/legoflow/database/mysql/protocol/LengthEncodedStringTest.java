package ssg.legoflow.database.mysql.protocol;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link LengthEncodedString}.
 */
class LengthEncodedStringTest {

    @Test
    void testReadWrite_simple() {
        var buf = ByteBuffer.allocate(64);
        LengthEncodedString.write(buf, "hello");
        buf.flip();
        assertThat(LengthEncodedString.read(buf)).isEqualTo("hello");
    }

    @Test
    void testReadWrite_empty() {
        var buf = ByteBuffer.allocate(64);
        LengthEncodedString.write(buf, "");
        buf.flip();
        assertThat(LengthEncodedString.read(buf)).isEqualTo("");
    }

    @Test
    void testReadWrite_null() {
        var buf = ByteBuffer.allocate(64);
        LengthEncodedString.write(buf, null);
        buf.flip();
        assertThat(LengthEncodedString.read(buf)).isNull();
    }

    @Test
    void testReadWrite_unicode() {
        var buf = ByteBuffer.allocate(64);
        LengthEncodedString.write(buf, "éèê");
        buf.flip();
        assertThat(LengthEncodedString.read(buf)).isEqualTo("éèê");
    }

    @Test
    void testReadWrite_bytes() {
        var buf = ByteBuffer.allocate(64);
        byte[] data = {1, 2, 3, 4, 5};
        LengthEncodedString.writeBytes(buf, data);
        buf.flip();
        assertThat(LengthEncodedString.readBytes(buf)).isEqualTo(data);
    }

    @Test
    void testReadWrite_bytes_null() {
        var buf = ByteBuffer.allocate(64);
        LengthEncodedString.writeBytes(buf, null);
        buf.flip();
        assertThat(LengthEncodedString.readBytes(buf)).isNull();
    }

    @Test
    void testNullTerminated_simple() {
        var buf = ByteBuffer.allocate(64);
        LengthEncodedString.writeNullTerminated(buf, "test");
        buf.flip();
        assertThat(LengthEncodedString.readNullTerminated(buf)).isEqualTo("test");
    }

    @Test
    void testNullTerminated_empty() {
        var buf = ByteBuffer.allocate(64);
        LengthEncodedString.writeNullTerminated(buf, "");
        buf.flip();
        assertThat(LengthEncodedString.readNullTerminated(buf)).isEqualTo("");
    }

    @Test
    void testFixedLength() {
        var buf = ByteBuffer.wrap("HELLO WORLD".getBytes());
        assertThat(LengthEncodedString.readFixedLength(buf, 5)).isEqualTo("HELLO");
    }

    @Test
    void testRestOfPacket() {
        var buf = ByteBuffer.wrap("remaining data".getBytes());
        assertThat(LengthEncodedString.readRestOfPacket(buf)).isEqualTo("remaining data");
    }

    @Test
    void testRestOfPacketBytes() {
        byte[] data = {10, 20, 30};
        var buf = ByteBuffer.wrap(data);
        assertThat(LengthEncodedString.readRestOfPacketBytes(buf)).isEqualTo(data);
    }

    @Test
    void testMultipleStrings() {
        var buf = ByteBuffer.allocate(256);
        LengthEncodedString.write(buf, "first");
        LengthEncodedString.write(buf, "second");
        LengthEncodedString.write(buf, "third");
        buf.flip();

        assertThat(LengthEncodedString.read(buf)).isEqualTo("first");
        assertThat(LengthEncodedString.read(buf)).isEqualTo("second");
        assertThat(LengthEncodedString.read(buf)).isEqualTo("third");
    }

    @Test
    void testLongString() {
        String longStr = "x".repeat(300);
        var buf = ByteBuffer.allocate(512);
        LengthEncodedString.write(buf, longStr);
        buf.flip();
        assertThat(LengthEncodedString.read(buf)).isEqualTo(longStr);
    }
}
