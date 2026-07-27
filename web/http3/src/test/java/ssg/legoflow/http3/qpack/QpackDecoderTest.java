package ssg.legoflow.http3.qpack;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class QpackDecoderTest {

    @Test
    void testDecodeStaticTableIndexed() {
        // Given: encode with encoder, decode with decoder
        var encoder = new QpackEncoder();
        var decoder = new QpackDecoder();
        var original = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "GET")
        );

        // When
        var encoded = encoder.encode(original);
        var decoded = decoder.decode(encoded);

        // Then
        assertThat(decoded).hasSize(1);
        assertThat(decoded.get(0).getKey()).isEqualTo(":method");
        assertThat(decoded.get(0).getValue()).isEqualTo("GET");
    }

    @Test
    void testDecodeMultipleHeaders() {
        // Given
        var encoder = new QpackEncoder();
        var decoder = new QpackDecoder();
        var original = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "GET"),
                new AbstractMap.SimpleEntry<>(":path", "/"),
                new AbstractMap.SimpleEntry<>(":scheme", "https"),
                new AbstractMap.SimpleEntry<>(":status", "200")
        );

        // When
        var encoded = encoder.encode(original);
        var decoded = decoder.decode(encoded);

        // Then
        assertThat(decoded).hasSize(4);
        assertThat(decoded.get(0).getKey()).isEqualTo(":method");
        assertThat(decoded.get(0).getValue()).isEqualTo("GET");
        assertThat(decoded.get(1).getKey()).isEqualTo(":path");
        assertThat(decoded.get(1).getValue()).isEqualTo("/");
        assertThat(decoded.get(2).getKey()).isEqualTo(":scheme");
        assertThat(decoded.get(2).getValue()).isEqualTo("https");
        assertThat(decoded.get(3).getKey()).isEqualTo(":status");
        assertThat(decoded.get(3).getValue()).isEqualTo("200");
    }

    @Test
    void testDecodeLiteralHeader() {
        // Given
        var encoder = new QpackEncoder();
        var decoder = new QpackDecoder();
        var original = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>("x-custom", "my-value")
        );

        // When
        var encoded = encoder.encode(original);
        var decoded = decoder.decode(encoded);

        // Then
        assertThat(decoded).hasSize(1);
        assertThat(decoded.get(0).getKey()).isEqualTo("x-custom");
        assertThat(decoded.get(0).getValue()).isEqualTo("my-value");
    }

    @Test
    void testDecodeNameReference() {
        // Given: name from static table, custom value
        var encoder = new QpackEncoder();
        var decoder = new QpackDecoder();
        var original = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>("content-type", "application/xml")
        );

        // When
        var encoded = encoder.encode(original);
        var decoded = decoder.decode(encoded);

        // Then
        assertThat(decoded).hasSize(1);
        assertThat(decoded.get(0).getKey()).isEqualTo("content-type");
        assertThat(decoded.get(0).getValue()).isEqualTo("application/xml");
    }

    @Test
    void testRoundtripRequestHeaders() {
        // Given: typical HTTP/3 request headers
        var encoder = new QpackEncoder();
        var decoder = new QpackDecoder();
        var original = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "POST"),
                new AbstractMap.SimpleEntry<>(":path", "/api/data"),
                new AbstractMap.SimpleEntry<>(":scheme", "https"),
                new AbstractMap.SimpleEntry<>(":authority", "example.com"),
                new AbstractMap.SimpleEntry<>("content-type", "application/json"),
                new AbstractMap.SimpleEntry<>("accept", "*/*")
        );

        // When
        var encoded = encoder.encode(original);
        var decoded = decoder.decode(encoded);

        // Then
        assertThat(decoded).hasSize(6);
        for (int i = 0; i < original.size(); i++) {
            assertThat(decoded.get(i).getKey()).isEqualTo(original.get(i).getKey());
            assertThat(decoded.get(i).getValue()).isEqualTo(original.get(i).getValue());
        }
    }

    @Test
    void testRoundtripResponseHeaders() {
        // Given: typical HTTP/3 response headers
        var encoder = new QpackEncoder();
        var decoder = new QpackDecoder();
        var original = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":status", "200"),
                new AbstractMap.SimpleEntry<>("content-type", "text/html; charset=utf-8"),
                new AbstractMap.SimpleEntry<>("cache-control", "no-cache")
        );

        // When
        var encoded = encoder.encode(original);
        var decoded = decoder.decode(encoded);

        // Then
        assertThat(decoded).hasSize(3);
        for (int i = 0; i < original.size(); i++) {
            assertThat(decoded.get(i).getKey()).isEqualTo(original.get(i).getKey());
            assertThat(decoded.get(i).getValue()).isEqualTo(original.get(i).getValue());
        }
    }

    @Test
    void testDecoderDynamicTable() {
        // Given
        var decoder = new QpackDecoder();

        // When/Then
        assertThat(decoder.getDynamicTable()).isNotNull();
    }

    @Test
    void testSetMaxTableCapacity() {
        // Given
        var decoder = new QpackDecoder(8192);

        // When
        decoder.setMaxTableCapacity(2048);

        // Then
        assertThat(decoder.getDynamicTable().maxCapacity()).isEqualTo(2048);
    }

    // ==================== Encoder Instruction Processing Tests ====================

    @Test
    void testProcessEncoderInstructionInsertWithStaticNameRef() {
        // Given
        var encoder = new QpackEncoder(4096);
        var decoder = new QpackDecoder(4096);

        // When: generate an insert instruction and process it on the decoder
        var instruction = encoder.encodeInsertWithStaticNameReference(0, "example.com");
        decoder.processEncoderInstructions(instruction);

        // Then: decoder's dynamic table should have the entry
        assertThat(decoder.getDynamicTable().size()).isEqualTo(1);
        assertThat(decoder.getDynamicTable().getEntry(0).name()).isEqualTo(":authority");
        assertThat(decoder.getDynamicTable().getEntry(0).value()).isEqualTo("example.com");
    }

    @Test
    void testProcessEncoderInstructionInsertWithLiteralName() {
        // Given
        var encoder = new QpackEncoder(4096);
        var decoder = new QpackDecoder(4096);

        // When
        var instruction = encoder.encodeInsertWithLiteralName("x-custom", "my-value");
        decoder.processEncoderInstructions(instruction);

        // Then
        assertThat(decoder.getDynamicTable().size()).isEqualTo(1);
        assertThat(decoder.getDynamicTable().getEntry(0).name()).isEqualTo("x-custom");
        assertThat(decoder.getDynamicTable().getEntry(0).value()).isEqualTo("my-value");
    }

    @Test
    void testProcessEncoderInstructionSetCapacity() {
        // Given
        var encoder = new QpackEncoder(4096);
        var decoder = new QpackDecoder(4096);

        // When
        var instruction = encoder.encodeSetDynamicTableCapacity(2048);
        decoder.processEncoderInstructions(instruction);

        // Then
        assertThat(decoder.getDynamicTable().maxCapacity()).isEqualTo(2048);
    }

    // ==================== Decoder Instruction Generation Tests ====================

    @Test
    void testEncodeSectionAcknowledgment() {
        // Given
        var decoder = new QpackDecoder(4096);

        // When
        var instruction = decoder.encodeSectionAcknowledgment(4L);

        // Then
        assertThat(instruction.remaining()).isGreaterThan(0);
        assertThat(decoder.getDynamicTable().isStreamAcknowledged(4L)).isTrue();
    }

    @Test
    void testEncodeStreamCancellation() {
        // Given
        var decoder = new QpackDecoder(4096);
        decoder.getDynamicTable().addStreamReference(4L);

        // When
        var instruction = decoder.encodeStreamCancellation(4L);

        // Then
        assertThat(instruction.remaining()).isGreaterThan(0);
        assertThat(decoder.getDynamicTable().getStreamReferenceCount(4L)).isEqualTo(0);
    }

    @Test
    void testEncodeInsertCountIncrement() {
        // Given
        var decoder = new QpackDecoder(4096);
        decoder.getDynamicTable().insert("h1", "v1");
        decoder.getDynamicTable().insert("h2", "v2");

        // When
        var instruction = decoder.encodeInsertCountIncrement(2);

        // Then
        assertThat(instruction.remaining()).isGreaterThan(0);
        assertThat(decoder.getDynamicTable().getKnownReceivedCount()).isEqualTo(2);
    }

    @Test
    void testDrainDecoderInstructions() {
        // Given
        var decoder = new QpackDecoder(4096);

        // When: no instructions accumulated
        var empty = decoder.drainDecoderInstructions();
        assertThat(empty.remaining()).isEqualTo(0);
    }
}
