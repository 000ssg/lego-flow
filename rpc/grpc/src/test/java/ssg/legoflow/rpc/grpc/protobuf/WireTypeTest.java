package ssg.legoflow.rpc.grpc.protobuf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

class WireTypeTest {

    @Test
    void testVarintValue() {
        assertThat(WireType.VARINT.value()).isEqualTo(0);
    }

    @Test
    void testFixed64Value() {
        assertThat(WireType.FIXED64.value()).isEqualTo(1);
    }

    @Test
    void testLengthDelimitedValue() {
        assertThat(WireType.LENGTH_DELIMITED.value()).isEqualTo(2);
    }

    @Test
    void testFixed32Value() {
        assertThat(WireType.FIXED32.value()).isEqualTo(5);
    }

    @ParameterizedTest
    @EnumSource(WireType.class)
    void testFromValueRoundTrip(WireType wireType) {
        assertThat(WireType.fromValue(wireType.value())).isEqualTo(wireType);
    }

    @Test
    void testFromValueInvalid() {
        assertThatThrownBy(() -> WireType.fromValue(6))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testFromValueNegative() {
        assertThatThrownBy(() -> WireType.fromValue(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testStartGroupValue() {
        assertThat(WireType.START_GROUP.value()).isEqualTo(3);
    }

    @Test
    void testEndGroupValue() {
        assertThat(WireType.END_GROUP.value()).isEqualTo(4);
    }
}
