package ssg.legoflow.network.modbus.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataTypesTest {

    @Test
    void testCoilOn() {
        var coil = Coil.on(100);
        assertThat(coil.address()).isEqualTo(100);
        assertThat(coil.value()).isTrue();
    }

    @Test
    void testCoilOff() {
        var coil = Coil.off(200);
        assertThat(coil.address()).isEqualTo(200);
        assertThat(coil.value()).isFalse();
    }

    @Test
    void testCoilInvalidAddress() {
        assertThatThrownBy(() -> new Coil(-1, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coil(0x10000, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDiscreteInput() {
        var di = new DiscreteInput(0, true);
        assertThat(di.address()).isEqualTo(0);
        assertThat(di.value()).isTrue();
    }

    @Test
    void testDiscreteInputInvalidAddress() {
        assertThatThrownBy(() -> new DiscreteInput(-1, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testRegister() {
        var reg = Register.of(0, 42);
        assertThat(reg.address()).isEqualTo(0);
        assertThat(reg.value()).isEqualTo(42);
    }

    @Test
    void testRegisterMaxValue() {
        var reg = Register.of(0, 65535);
        assertThat(reg.value()).isEqualTo(65535);
    }

    @Test
    void testRegisterInvalidValue() {
        assertThatThrownBy(() -> Register.of(0, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Register.of(0, 65536))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInputRegister() {
        var ir = InputRegister.of(1000, 5000);
        assertThat(ir.address()).isEqualTo(1000);
        assertThat(ir.value()).isEqualTo(5000);
    }

    @Test
    void testInputRegisterInvalid() {
        assertThatThrownBy(() -> InputRegister.of(-1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InputRegister.of(0, 65536))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
