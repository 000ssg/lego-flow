package ssg.legoflow.network.modbus.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionCodeTest {

    @Test
    void testCodes() {
        assertThat(FunctionCode.READ_COILS.code()).isEqualTo(0x01);
        assertThat(FunctionCode.READ_DISCRETE_INPUTS.code()).isEqualTo(0x02);
        assertThat(FunctionCode.READ_HOLDING_REGISTERS.code()).isEqualTo(0x03);
        assertThat(FunctionCode.READ_INPUT_REGISTERS.code()).isEqualTo(0x04);
        assertThat(FunctionCode.WRITE_SINGLE_COIL.code()).isEqualTo(0x05);
        assertThat(FunctionCode.WRITE_SINGLE_REGISTER.code()).isEqualTo(0x06);
        assertThat(FunctionCode.WRITE_MULTIPLE_COILS.code()).isEqualTo(0x0F);
        assertThat(FunctionCode.WRITE_MULTIPLE_REGISTERS.code()).isEqualTo(0x10);
        assertThat(FunctionCode.READ_WRITE_MULTIPLE_REGISTERS.code()).isEqualTo(0x17);
    }

    @ParameterizedTest
    @EnumSource(FunctionCode.class)
    void testRoundTrip(FunctionCode fc) {
        assertThat(FunctionCode.of(fc.code())).isEqualTo(fc);
    }

    @ParameterizedTest
    @EnumSource(FunctionCode.class)
    void testExceptionCode(FunctionCode fc) {
        assertThat(fc.exceptionCode()).isEqualTo(fc.code() | 0x80);
    }

    @Test
    void testIsException() {
        assertThat(FunctionCode.isException(0x01)).isFalse();
        assertThat(FunctionCode.isException(0x81)).isTrue();
        assertThat(FunctionCode.isException(0x90)).isTrue();
    }

    @Test
    void testFromException() {
        assertThat(FunctionCode.fromException(0x81)).isEqualTo(FunctionCode.READ_COILS);
        assertThat(FunctionCode.fromException(0x83)).isEqualTo(FunctionCode.READ_HOLDING_REGISTERS);
    }

    @Test
    void testUnknownCodeThrows() {
        assertThatThrownBy(() -> FunctionCode.of(0x99))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTotalCount() {
        assertThat(FunctionCode.values()).hasSize(9);
    }
}
