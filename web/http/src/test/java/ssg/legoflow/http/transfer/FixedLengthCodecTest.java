package ssg.legoflow.http.transfer;

import ssg.legoflow.blocks.DefaultContext;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class FixedLengthCodecTest {

    @Test
    void testFilterPassesDataWithinLimit() {
        var codec = new FixedLengthCodec(100);
        var ctx = new DefaultContext();
        var data = ByteBuffer.wrap("Hello".getBytes(StandardCharsets.UTF_8));

        ByteBuffer[] result = codec.filter(ctx, data);

        assertThat(result).hasSize(1);
        assertThat(result[0].remaining()).isEqualTo(5);
    }

    @Test
    void testFilterPassesDataAtExactLimit() {
        var content = "12345";
        var codec = new FixedLengthCodec(5);
        var ctx = new DefaultContext();
        var data = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));

        ByteBuffer[] result = codec.filter(ctx, data);

        assertThat(result).hasSize(1);
    }

    @Test
    void testFilterRejectsDataExceedingLimit() {
        var codec = new FixedLengthCodec(3);
        var ctx = new DefaultContext();
        var data = ByteBuffer.wrap("Hello World".getBytes(StandardCharsets.UTF_8));

        ByteBuffer[] result = codec.filter(ctx, data);

        assertThat(result).isEmpty();
    }

    @Test
    void testFilterMultipleBuffersWithinLimit() {
        var codec = new FixedLengthCodec(20);
        var ctx = new DefaultContext();
        var buf1 = ByteBuffer.wrap("abc".getBytes(StandardCharsets.UTF_8));
        var buf2 = ByteBuffer.wrap("def".getBytes(StandardCharsets.UTF_8));

        ByteBuffer[] result = codec.filter(ctx, buf1, buf2);

        assertThat(result).hasSize(2);
    }

    @Test
    void testFilterMultipleBuffersExceedingLimit() {
        var codec = new FixedLengthCodec(5);
        var ctx = new DefaultContext();
        var buf1 = ByteBuffer.wrap("abc".getBytes(StandardCharsets.UTF_8));
        var buf2 = ByteBuffer.wrap("defgh".getBytes(StandardCharsets.UTF_8));

        ByteBuffer[] result = codec.filter(ctx, buf1, buf2);

        assertThat(result).isEmpty();
    }

    @Test
    void testGetExpectedLength() {
        var codec = new FixedLengthCodec(42);

        assertThat(codec.getExpectedLength()).isEqualTo(42);
    }

    @Test
    void testNegativeLengthAllowsAnySize() {
        var codec = new FixedLengthCodec(-1);
        var ctx = new DefaultContext();
        var data = ByteBuffer.wrap("any length data".getBytes(StandardCharsets.UTF_8));

        ByteBuffer[] result = codec.filter(ctx, data);

        assertThat(result).hasSize(1);
    }

    @Test
    void testFilterEmptyData() {
        var codec = new FixedLengthCodec(10);
        var ctx = new DefaultContext();

        ByteBuffer[] result = codec.filter(ctx);

        assertThat(result).isEmpty();
    }
}
