package ssg.legoflow.rpc.grpc.protobuf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FieldTagTest {

    @Test
    void testEncodeFieldOneVarint() {
        var tag = new FieldTag(1, WireType.VARINT);
        assertThat(tag.encode()).isEqualTo(0x08);
    }

    @Test
    void testEncodeFieldTwoLengthDelimited() {
        var tag = new FieldTag(2, WireType.LENGTH_DELIMITED);
        assertThat(tag.encode()).isEqualTo(0x12);
    }

    @Test
    void testEncodeFieldThreeFixed32() {
        var tag = new FieldTag(3, WireType.FIXED32);
        assertThat(tag.encode()).isEqualTo(0x1D);
    }

    @Test
    void testDecodeRoundTrip() {
        var original = new FieldTag(15, WireType.FIXED64);
        var decoded = FieldTag.decode(original.encode());
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testDecodeLargeFieldNumber() {
        var tag = new FieldTag(536870911, WireType.VARINT); // max field number
        var decoded = FieldTag.decode(tag.encode());
        assertThat(decoded.fieldNumber()).isEqualTo(536870911);
        assertThat(decoded.wireType()).isEqualTo(WireType.VARINT);
    }

    @Test
    void testInvalidFieldNumber() {
        assertThatThrownBy(() -> new FieldTag(0, WireType.VARINT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNegativeFieldNumber() {
        assertThatThrownBy(() -> new FieldTag(-1, WireType.VARINT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEncodeField16Varint() {
        // Field 16 requires 2-byte varint tag
        var tag = new FieldTag(16, WireType.VARINT);
        assertThat(tag.encode()).isEqualTo(128); // (16 << 3) | 0 = 128
    }
}
