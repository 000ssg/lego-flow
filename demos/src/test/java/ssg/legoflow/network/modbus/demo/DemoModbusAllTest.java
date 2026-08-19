package ssg.legoflow.network.modbus.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive Modbus demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code ModbusServer}. To test against
 * an external Modbus TCP simulator or PLC, set {@code DemoModbusAll.USE_EXTERNAL = true}
 * and configure host/port before running.</p>
 *
 * @since 0.1.0
 */
class DemoModbusAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoModbusAll.runAll();

        assertThat(results.readCoils())
                .as("Read coils (FC 01) returns correct values")
                .isEqualTo(8);

        assertThat(results.readDiscreteInputs())
                .as("Read discrete inputs (FC 02) returns correct values")
                .isEqualTo(4);

        assertThat(results.readHoldingRegisters())
                .as("Read holding registers (FC 03) returns correct values")
                .isEqualTo(5);

        assertThat(results.readInputRegisters())
                .as("Read input registers (FC 04) returns correct values")
                .isEqualTo(4);

        assertThat(results.writeSingleValues())
                .as("Write single coil (FC 05) and register (FC 06) succeed")
                .isTrue();

        assertThat(results.writeMultipleValues())
                .as("Write multiple coils (FC 15) and registers (FC 16) succeed")
                .isTrue();

        assertThat(results.readWriteRegisters())
                .as("Read/write multiple registers (FC 23) succeeds")
                .isTrue();

        assertThat(results.deviceMemory())
                .as("DeviceMemory four-table operations all succeed")
                .isTrue();

        assertThat(results.frameEncoding())
                .as("MBAP header and frame construction are correct")
                .isTrue();

        assertThat(results.functionCodesAndTypes())
                .as("All 9 function codes and data types validate correctly")
                .isTrue();
    }
}
