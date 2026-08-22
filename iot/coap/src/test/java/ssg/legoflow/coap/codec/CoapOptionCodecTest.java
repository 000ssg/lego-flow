package ssg.legoflow.coap.codec;

import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.ContentFormat;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link CoapOptionCodec}.
 *
 * @since 0.1.0
 */
class CoapOptionCodecTest {

    @Test
    void testEncodeSingleOption() {
        var options = List.of(CoapOption.uriPath("sensors"));
        var buffer = CoapOptionCodec.encode(options);

        assertThat(buffer.remaining()).isGreaterThan(0);

        var decoded = CoapOptionCodec.decode(buffer);
        assertThat(decoded).hasSize(1);
        assertThat(decoded.getFirst().number()).isEqualTo(CoapOption.URI_PATH);
        assertThat(decoded.getFirst().asString()).isEqualTo("sensors");
    }

    @Test
    void testEncodeDecodeMultipleOptions() {
        var options = List.of(
                CoapOption.uriPath("sensors"),
                CoapOption.uriPath("temperature"),
                CoapOption.contentFormat(ContentFormat.TEXT_PLAIN.value())
        );

        var buffer = CoapOptionCodec.encode(options);
        var decoded = CoapOptionCodec.decode(buffer);

        assertThat(decoded).hasSize(3);
    }

    @Test
    void testDeltaEncoding() {
        // Options must be sorted by number and use delta encoding
        var options = List.of(
                CoapOption.uriPath("a"),          // number 11
                CoapOption.contentFormat(0),       // number 12, delta=1
                CoapOption.uriQuery("key=value")   // number 15, delta=3
        );

        var buffer = CoapOptionCodec.encode(options);
        var decoded = CoapOptionCodec.decode(buffer);

        assertThat(decoded).hasSize(3);
        assertThat(decoded.get(0).number()).isEqualTo(11);
        assertThat(decoded.get(1).number()).isEqualTo(12);
        assertThat(decoded.get(2).number()).isEqualTo(15);
    }

    @Test
    void testExtendedDelta13() {
        // Create an option with number > 12 that requires extended delta
        var options = List.of(
                CoapOption.maxAge(60)  // number 14
        );

        var buffer = CoapOptionCodec.encode(options);
        var decoded = CoapOptionCodec.decode(buffer);

        assertThat(decoded).hasSize(1);
        assertThat(decoded.getFirst().number()).isEqualTo(CoapOption.MAX_AGE);
        assertThat(decoded.getFirst().asInt()).isEqualTo(60);
    }

    @Test
    void testExtendedDelta14() {
        // Create options with large delta (>= 269)
        var options = List.of(
                new CoapOption(300, new byte[]{0x01})
        );

        var buffer = CoapOptionCodec.encode(options);
        var decoded = CoapOptionCodec.decode(buffer);

        assertThat(decoded).hasSize(1);
        assertThat(decoded.getFirst().number()).isEqualTo(300);
    }

    @Test
    void testOptionOrdering() {
        // Options given out of order should be sorted by number
        var options = new ArrayList<CoapOption>();
        options.add(CoapOption.uriQuery("key=value"));   // 15
        options.add(CoapOption.uriPath("sensors"));       // 11
        options.add(CoapOption.contentFormat(0));          // 12

        var buffer = CoapOptionCodec.encode(options);
        var decoded = CoapOptionCodec.decode(buffer);

        assertThat(decoded).hasSize(3);
        assertThat(decoded.get(0).number()).isEqualTo(11);
        assertThat(decoded.get(1).number()).isEqualTo(12);
        assertThat(decoded.get(2).number()).isEqualTo(15);
    }

    @Test
    void testEmptyOptions() {
        var buffer = CoapOptionCodec.encode(List.of());
        assertThat(buffer.remaining()).isZero();
    }

    @Test
    void testEncodeDecodeIfNoneMatch() {
        // If-None-Match has empty value
        var options = List.of(CoapOption.ifNoneMatch());

        var buffer = CoapOptionCodec.encode(options);
        var decoded = CoapOptionCodec.decode(buffer);

        assertThat(decoded).hasSize(1);
        assertThat(decoded.getFirst().number()).isEqualTo(CoapOption.IF_NONE_MATCH);
        assertThat(decoded.getFirst().value()).isEmpty();
    }
}
