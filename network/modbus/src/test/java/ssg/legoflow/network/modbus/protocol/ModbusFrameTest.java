package ssg.legoflow.network.modbus.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModbusFrameTest {

    @Test
    void testCreate() {
        var header = MbapHeader.request(1, 1, 5);
        var frame = new ModbusFrame(header, new byte[]{0x03, 0, 0, 0, 10});
        assertThat(frame.functionCode()).isEqualTo(0x03);
        assertThat(frame.isException()).isFalse();
    }

    @Test
    void testExceptionFrame() {
        var header = MbapHeader.request(1, 1, 2);
        var frame = new ModbusFrame(header, new byte[]{(byte) 0x83, 0x02});
        assertThat(frame.isException()).isTrue();
        assertThat(frame.functionCode()).isEqualTo(0x83);
    }

    @Test
    void testData() {
        var header = MbapHeader.request(1, 1, 5);
        var frame = new ModbusFrame(header, new byte[]{0x03, 0x04, 0, 100, 0, (byte) 200});
        byte[] data = frame.data();
        assertThat(data.length).isEqualTo(5);
        assertThat(data[0]).isEqualTo((byte) 0x04);
    }

    @Test
    void testNullHeaderThrows() {
        assertThatThrownBy(() -> new ModbusFrame(null, new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNullPduThrows() {
        var header = MbapHeader.request(1, 1, 5);
        assertThatThrownBy(() -> new ModbusFrame(header, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEmptyPduThrows() {
        var header = MbapHeader.request(1, 1, 0);
        assertThatThrownBy(() -> new ModbusFrame(header, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDefensiveCopy() {
        var header = MbapHeader.request(1, 1, 2);
        byte[] original = {0x01, 0x02};
        var frame = new ModbusFrame(header, original);
        original[0] = 0;
        assertThat(frame.pdu()[0]).isEqualTo((byte) 0x01);
    }

    @Test
    void testEquality() {
        var header = MbapHeader.request(1, 1, 2);
        var frame1 = new ModbusFrame(header, new byte[]{0x01, 0x02});
        var frame2 = new ModbusFrame(header, new byte[]{0x01, 0x02});
        assertThat(frame1).isEqualTo(frame2);
        assertThat(frame1.hashCode()).isEqualTo(frame2.hashCode());
    }
}
