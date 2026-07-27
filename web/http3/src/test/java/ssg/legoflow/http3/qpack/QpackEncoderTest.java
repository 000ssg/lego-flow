package ssg.legoflow.http3.qpack;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class QpackEncoderTest {

    @Test
    void testEncodeStaticTableHit() {
        // Given
        var encoder = new QpackEncoder();
        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "GET")
        );

        // When
        var encoded = encoder.encode(headers);

        // Then: should produce output (static table index 17 for :method GET)
        assertThat(encoded.remaining()).isGreaterThan(0);
    }

    @Test
    void testEncodeMultipleHeaders() {
        // Given
        var encoder = new QpackEncoder();
        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "GET"),
                new AbstractMap.SimpleEntry<>(":path", "/"),
                new AbstractMap.SimpleEntry<>(":scheme", "https"),
                new AbstractMap.SimpleEntry<>(":status", "200")
        );

        // When
        var encoded = encoder.encode(headers);

        // Then
        assertThat(encoded.remaining()).isGreaterThan(0);
    }

    @Test
    void testEncodeLiteralHeader() {
        // Given
        var encoder = new QpackEncoder();
        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>("x-custom-header", "custom-value")
        );

        // When
        var encoded = encoder.encode(headers);

        // Then: should produce output for literal encoding
        assertThat(encoded.remaining()).isGreaterThan(0);
    }

    @Test
    void testEncodeNameReference() {
        // Given: header with name in static table but custom value
        var encoder = new QpackEncoder();
        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>("content-type", "application/xml")
        );

        // When
        var encoded = encoder.encode(headers);

        // Then
        assertThat(encoded.remaining()).isGreaterThan(0);
    }

    @Test
    void testEncodeHeaderList() {
        // Given
        var encoder = new QpackEncoder();

        // When
        var encoded = encoder.encodeHeaderList(
                ":method", "GET",
                ":path", "/",
                ":scheme", "https"
        );

        // Then
        assertThat(encoded.remaining()).isGreaterThan(0);
    }

    @Test
    void testEncodeHeaderListOddPairsThrows() {
        // Given
        var encoder = new QpackEncoder();

        // When/Then
        assertThatThrownBy(() -> encoder.encodeHeaderList(":method"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEncodeWithHuffmanDisabled() {
        // Given
        var encoder = new QpackEncoder();
        encoder.setUseHuffman(false);
        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>("x-test", "value")
        );

        // When
        var encoded = encoder.encode(headers);

        // Then
        assertThat(encoded.remaining()).isGreaterThan(0);
    }

    @Test
    void testEncoderDynamicTable() {
        // Given
        var encoder = new QpackEncoder();

        // When/Then
        assertThat(encoder.getDynamicTable()).isNotNull();
    }

    @Test
    void testSetMaxTableCapacity() {
        // Given
        var encoder = new QpackEncoder(8192);

        // When
        encoder.setMaxTableCapacity(2048);

        // Then
        assertThat(encoder.getDynamicTable().maxCapacity()).isEqualTo(2048);
    }

    // ==================== Dynamic Table Encoder Tests ====================

    @Test
    void testUseDynamicTableFlag() {
        // Given
        var encoder = new QpackEncoder();

        // When/Then
        assertThat(encoder.isUseDynamicTable()).isFalse();
        encoder.setUseDynamicTable(true);
        assertThat(encoder.isUseDynamicTable()).isTrue();
    }

    @Test
    void testEncodeWithDynamicTableInsertion() {
        // Given
        var encoder = new QpackEncoder(4096);
        encoder.setUseDynamicTable(true);

        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>("x-custom", "first-value")
        );

        // When
        var encoded = encoder.encode(headers);

        // Then: header inserted into dynamic table
        assertThat(encoded.remaining()).isGreaterThan(0);
        assertThat(encoder.getDynamicTable().size()).isEqualTo(1);
        assertThat(encoder.getDynamicTable().getEntry(0).name()).isEqualTo("x-custom");
        assertThat(encoder.getDynamicTable().getEntry(0).value()).isEqualTo("first-value");
    }

    @Test
    void testEncodeWithDynamicTableReuse() {
        // Given
        var encoder = new QpackEncoder(4096);
        encoder.setUseDynamicTable(true);
        var decoder = new QpackDecoder(4096);

        // First encode inserts into dynamic table
        var headers1 = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>("x-custom", "value-1")
        );
        var encoded1 = encoder.encode(headers1);

        // Sync decoder's dynamic table
        decoder.getDynamicTable().insert("x-custom", "value-1");

        // Second encode should reference dynamic table for same name
        var headers2 = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>("x-custom", "value-2")
        );
        var encoded2 = encoder.encode(headers2);

        // Then: dynamic table should have both entries
        assertThat(encoder.getDynamicTable().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testEncodeInsertWithStaticNameReference() {
        // Given
        var encoder = new QpackEncoder(4096);

        // When: insert with static name reference (index 0 = :authority)
        var instruction = encoder.encodeInsertWithStaticNameReference(0, "example.com");

        // Then
        assertThat(instruction.remaining()).isGreaterThan(0);
        assertThat(encoder.getDynamicTable().size()).isEqualTo(1);
        assertThat(encoder.getDynamicTable().getEntry(0).name()).isEqualTo(":authority");
        assertThat(encoder.getDynamicTable().getEntry(0).value()).isEqualTo("example.com");
    }

    @Test
    void testEncodeInsertWithDynamicNameReference() {
        // Given
        var encoder = new QpackEncoder(4096);
        encoder.getDynamicTable().insert("x-custom", "old-value");

        // When
        var instruction = encoder.encodeInsertWithDynamicNameReference(0, "new-value");

        // Then
        assertThat(instruction.remaining()).isGreaterThan(0);
        assertThat(encoder.getDynamicTable().size()).isEqualTo(2);
        assertThat(encoder.getDynamicTable().getEntry(0).name()).isEqualTo("x-custom");
        assertThat(encoder.getDynamicTable().getEntry(0).value()).isEqualTo("new-value");
    }

    @Test
    void testEncodeInsertWithLiteralName() {
        // Given
        var encoder = new QpackEncoder(4096);

        // When
        var instruction = encoder.encodeInsertWithLiteralName("x-brand-new", "fresh-value");

        // Then
        assertThat(instruction.remaining()).isGreaterThan(0);
        assertThat(encoder.getDynamicTable().size()).isEqualTo(1);
        assertThat(encoder.getDynamicTable().getEntry(0).name()).isEqualTo("x-brand-new");
    }

    @Test
    void testEncodeDuplicate() {
        // Given
        var encoder = new QpackEncoder(4096);
        encoder.getDynamicTable().insert("header-a", "value-a");
        encoder.getDynamicTable().insert("header-b", "value-b");

        // When: duplicate entry at index 1 (header-a)
        var instruction = encoder.encodeDuplicate(1);

        // Then
        assertThat(instruction.remaining()).isGreaterThan(0);
        assertThat(encoder.getDynamicTable().size()).isEqualTo(3);
        assertThat(encoder.getDynamicTable().getEntry(0).name()).isEqualTo("header-a");
    }

    @Test
    void testEncodeSetDynamicTableCapacity() {
        // Given
        var encoder = new QpackEncoder(4096);

        // When
        var instruction = encoder.encodeSetDynamicTableCapacity(2048);

        // Then
        assertThat(instruction.remaining()).isGreaterThan(0);
        assertThat(encoder.getDynamicTable().maxCapacity()).isEqualTo(2048);
    }

    @Test
    void testDrainEncoderInstructions() {
        // Given
        var encoder = new QpackEncoder(4096);
        encoder.setUseDynamicTable(true);

        // When: encode header that triggers dynamic table insertion
        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>("x-custom", "some-value")
        );
        encoder.encode(headers);

        // Then: encoder instructions should be available
        var instructions = encoder.drainEncoderInstructions();
        assertThat(instructions.remaining()).isGreaterThan(0);

        // Subsequent drain should be empty
        var empty = encoder.drainEncoderInstructions();
        assertThat(empty.remaining()).isEqualTo(0);
    }

    @Test
    void testRoundtripWithDynamicTable() {
        // Given: encoder with dynamic table enabled, and matching decoder
        var encoder = new QpackEncoder(4096);
        encoder.setUseDynamicTable(true);
        var decoder = new QpackDecoder(4096);

        // When: encode headers with static table names + custom values
        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "GET"),
                new AbstractMap.SimpleEntry<>(":path", "/api/data"),
                new AbstractMap.SimpleEntry<>(":scheme", "https")
        );
        var encoded = encoder.encode(headers);

        // Sync dynamic tables by processing encoder instructions on decoder side
        var instructions = encoder.drainEncoderInstructions();
        if (instructions.hasRemaining()) {
            decoder.processEncoderInstructions(instructions);
        }

        // Then: decode should produce the original headers
        var decoded = decoder.decode(encoded);
        assertThat(decoded).hasSize(3);
        assertThat(decoded.get(0).getKey()).isEqualTo(":method");
        assertThat(decoded.get(0).getValue()).isEqualTo("GET");
    }
}
