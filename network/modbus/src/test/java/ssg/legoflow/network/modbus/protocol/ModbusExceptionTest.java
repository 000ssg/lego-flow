package ssg.legoflow.network.modbus.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class ModbusExceptionTest {

    @Test
    void testMessageOnly() {
        var ex = new ModbusException("test");
        assertThat(ex.getMessage()).isEqualTo("test");
        assertThat(ex.exceptionCode()).isNull();
    }

    @Test
    void testWithExceptionCode() {
        var ex = new ModbusException(
                ModbusException.ExceptionCode.ILLEGAL_FUNCTION, "bad function");
        assertThat(ex.exceptionCode()).isEqualTo(ModbusException.ExceptionCode.ILLEGAL_FUNCTION);
    }

    @Test
    void testExceptionCodes() {
        assertThat(ModbusException.ExceptionCode.ILLEGAL_FUNCTION.code()).isEqualTo(1);
        assertThat(ModbusException.ExceptionCode.ILLEGAL_DATA_ADDRESS.code()).isEqualTo(2);
        assertThat(ModbusException.ExceptionCode.ILLEGAL_DATA_VALUE.code()).isEqualTo(3);
        assertThat(ModbusException.ExceptionCode.SERVER_DEVICE_FAILURE.code()).isEqualTo(4);
        assertThat(ModbusException.ExceptionCode.ACKNOWLEDGE.code()).isEqualTo(5);
        assertThat(ModbusException.ExceptionCode.SERVER_DEVICE_BUSY.code()).isEqualTo(6);
    }

    @Test
    void testExceptionCodeOf() {
        assertThat(ModbusException.ExceptionCode.of(1))
                .isEqualTo(ModbusException.ExceptionCode.ILLEGAL_FUNCTION);
        assertThat(ModbusException.ExceptionCode.of(6))
                .isEqualTo(ModbusException.ExceptionCode.SERVER_DEVICE_BUSY);
    }

    @Test
    void testExceptionCodeOfInvalid() {
        assertThatThrownBy(() -> ModbusException.ExceptionCode.of(99))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTotalExceptionCodes() {
        assertThat(ModbusException.ExceptionCode.values()).hasSize(6);
    }
}
