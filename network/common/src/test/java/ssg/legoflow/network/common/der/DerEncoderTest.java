package ssg.legoflow.network.common.der;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.asn1.*;
import ssg.legoflow.network.common.ber.BerDecoder;
import ssg.legoflow.network.common.ber.BerEncoder;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link DerEncoder}.
 */
class DerEncoderTest {

    @Test
    void testDerBooleanTrue() {
        ByteBuffer encoded = DerEncoder.encode(Asn1Boolean.TRUE);
        assertThat(encoded.remaining()).isEqualTo(3);
        assertThat(encoded.get() & 0xFF).isEqualTo(0x01);
        assertThat(encoded.get() & 0xFF).isEqualTo(0x01);
        assertThat(encoded.get() & 0xFF).isEqualTo(0xFF);
    }

    @Test
    void testDerBooleanFalse() {
        ByteBuffer encoded = DerEncoder.encode(Asn1Boolean.FALSE);
        assertThat(encoded.remaining()).isEqualTo(3);
        assertThat(encoded.get() & 0xFF).isEqualTo(0x01);
        assertThat(encoded.get() & 0xFF).isEqualTo(0x01);
        assertThat(encoded.get() & 0xFF).isEqualTo(0x00);
    }

    @Test
    void testDerSetSortedByTag() {
        // SET with elements in non-canonical order
        var set = Asn1Set.of(
                Asn1OctetString.of("test"),   // tag 0x04
                Asn1Boolean.TRUE,              // tag 0x01
                Asn1Integer.of(42)             // tag 0x02
        );

        ByteBuffer derEncoded = DerEncoder.encode(set);
        Asn1Type decoded = BerDecoder.decode(derEncoded);

        assertThat(decoded).isInstanceOf(Asn1Set.class);
        var decodedSet = (Asn1Set) decoded;
        assertThat(decodedSet.elements()).hasSize(3);
        // DER should sort by tag: BOOLEAN(0x01), INTEGER(0x02), OCTET STRING(0x04)
        assertThat(decodedSet.elements().get(0)).isInstanceOf(Asn1Boolean.class);
        assertThat(decodedSet.elements().get(1)).isInstanceOf(Asn1Integer.class);
        assertThat(decodedSet.elements().get(2)).isInstanceOf(Asn1OctetString.class);
    }

    @Test
    void testDerSetAlreadySorted() {
        var set = Asn1Set.of(
                Asn1Boolean.TRUE,
                Asn1Integer.of(1),
                Asn1OctetString.of("sorted")
        );
        ByteBuffer derEncoded = DerEncoder.encode(set);
        Asn1Type decoded = BerDecoder.decode(derEncoded);
        assertThat(decoded).isInstanceOf(Asn1Set.class);
        var decodedSet = (Asn1Set) decoded;
        assertThat(decodedSet.elements().get(0)).isInstanceOf(Asn1Boolean.class);
        assertThat(decodedSet.elements().get(1)).isInstanceOf(Asn1Integer.class);
        assertThat(decodedSet.elements().get(2)).isInstanceOf(Asn1OctetString.class);
    }

    @Test
    void testDerDefiniteLengthOnly() {
        // DER should use definite length; verify no 0x80 length byte
        var seq = Asn1Sequence.of(Asn1Integer.of(1), Asn1Boolean.TRUE);
        ByteBuffer encoded = DerEncoder.encode(seq);
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        assertThat(bytes[0] & 0xFF).isEqualTo(0x30); // SEQUENCE tag
        // Length byte should NOT be 0x80 (indefinite)
        assertThat(bytes[1] & 0xFF).isNotEqualTo(0x80);
    }

    @Test
    void testDerNestedSetSorted() {
        var innerSet = Asn1Set.of(
                Asn1OctetString.of("z"),
                Asn1Integer.of(1)
        );
        var outerSeq = Asn1Sequence.of(innerSet);
        ByteBuffer encoded = DerEncoder.encode(outerSeq);
        Asn1Type decoded = BerDecoder.decode(encoded);
        var seq = (Asn1Sequence) decoded;
        var set = (Asn1Set) seq.elements().getFirst();
        // Inner set should be sorted: INTEGER(0x02) before OCTET STRING(0x04)
        assertThat(set.elements().get(0)).isInstanceOf(Asn1Integer.class);
        assertThat(set.elements().get(1)).isInstanceOf(Asn1OctetString.class);
    }

    @Test
    void testDerSequencePreservesOrder() {
        // SEQUENCE preserves insertion order even in DER
        var seq = Asn1Sequence.of(
                Asn1OctetString.of("first"),
                Asn1Integer.of(1),
                Asn1Boolean.TRUE
        );
        ByteBuffer encoded = DerEncoder.encode(seq);
        Asn1Type decoded = BerDecoder.decode(encoded);
        var decodedSeq = (Asn1Sequence) decoded;
        assertThat(decodedSeq.elements().get(0)).isInstanceOf(Asn1OctetString.class);
        assertThat(decodedSeq.elements().get(1)).isInstanceOf(Asn1Integer.class);
        assertThat(decodedSeq.elements().get(2)).isInstanceOf(Asn1Boolean.class);
    }

    @Test
    void testDerIntegerRoundTrip() {
        var original = Asn1Integer.of(12345);
        ByteBuffer encoded = DerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testDerContextSpecificExplicit() {
        var original = Asn1ContextSpecific.explicit(0, Asn1Integer.of(7));
        ByteBuffer encoded = DerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isInstanceOf(Asn1ContextSpecific.class);
        var ctx = (Asn1ContextSpecific) decoded;
        assertThat(ctx.value()).isEqualTo(Asn1Integer.of(7));
    }

    @Test
    void testDerNullRoundTrip() {
        ByteBuffer encoded = DerEncoder.encode(Asn1Null.INSTANCE);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isInstanceOf(Asn1Null.class);
    }

    @Test
    void testDerEncodeTo() {
        var type = Asn1Integer.of(42);
        int size = BerEncoder.encodedSize(type);
        ByteBuffer buf = ByteBuffer.allocate(size);
        DerEncoder.encodeTo(type, buf);
        buf.flip();
        Asn1Type decoded = BerDecoder.decode(buf);
        assertThat(decoded).isEqualTo(type);
    }
}
