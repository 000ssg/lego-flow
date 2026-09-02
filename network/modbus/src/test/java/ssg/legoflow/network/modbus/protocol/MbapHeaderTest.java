package ssg.legoflow.network.modbus.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class MbapHeaderTest {

    @Test
    void testCreate() {
        var header = new MbapHeader(1, 0, 6, 1);
        assertThat(header.transactionId()).isEqualTo(1);
        assertThat(header.protocolId()).isEqualTo(0);
        assertThat(header.length()).isEqualTo(6);
        assertThat(header.unitId()).isEqualTo(1);
    }

    @Test
    void testRequest() {
        var header = MbapHeader.request(42, 1, 5);
        assertThat(header.transactionId()).isEqualTo(42);
        assertThat(header.protocolId()).isEqualTo(0);
        assertThat(header.length()).isEqualTo(6); // pduLength + 1 for unitId
        assertThat(header.unitId()).isEqualTo(1);
    }

    @Test
    void testEncodeDecodeRoundTrip() {
        var original = new MbapHeader(1234, 0, 10, 255);
        byte[] encoded = original.encode();
        assertThat(encoded).hasSize(MbapHeader.HEADER_SIZE);

        var decoded = MbapHeader.decode(encoded);
        assertThat(decoded.transactionId()).isEqualTo(1234);
        assertThat(decoded.protocolId()).isEqualTo(0);
        assertThat(decoded.length()).isEqualTo(10);
        assertThat(decoded.unitId()).isEqualTo(255);
    }

    @Test
    void testEncodeBigValues() {
        var header = new MbapHeader(65535, 0, 65535, 255);
        byte[] encoded = header.encode();
        var decoded = MbapHeader.decode(encoded);
        assertThat(decoded.transactionId()).isEqualTo(65535);
        assertThat(decoded.length()).isEqualTo(65535);
        assertThat(decoded.unitId()).isEqualTo(255);
    }

    @Test
    void testDecodeShortDataThrows() {
        assertThatThrownBy(() -> MbapHeader.decode(new byte[6]))
                .isInstanceOf(ModbusException.class);
    }

    @Test
    void testDecodeInvalidProtocolThrows() {
        byte[] data = {0, 1, 0, 1, 0, 6, 1}; // protocol ID = 1
        assertThatThrownBy(() -> MbapHeader.decode(data))
                .isInstanceOf(ModbusException.class);
    }

    @Test
    void testInvalidTransactionId() {
        assertThatThrownBy(() -> new MbapHeader(-1, 0, 6, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MbapHeader(0x10000, 0, 6, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidProtocolId() {
        assertThatThrownBy(() -> new MbapHeader(1, 1, 6, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidUnitId() {
        assertThatThrownBy(() -> new MbapHeader(1, 0, 6, 256))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MbapHeader(1, 0, 6, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testHeaderSize() {
        assertThat(MbapHeader.HEADER_SIZE).isEqualTo(7);
    }

    @Test
    void testModbusProtocolId() {
        assertThat(MbapHeader.MODBUS_PROTOCOL_ID).isEqualTo(0);
    }
}
