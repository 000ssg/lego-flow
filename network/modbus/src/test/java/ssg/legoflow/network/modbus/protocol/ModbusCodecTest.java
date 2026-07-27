package ssg.legoflow.network.modbus.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModbusCodecTest {

    @Test
    void testEncodeFrame() {
        var header = MbapHeader.request(1, 1, 5);
        byte[] pdu = ModbusCodec.buildReadHoldingRegistersRequest(0, 10);
        var frame = new ModbusFrame(header, pdu);

        byte[] encoded = ModbusCodec.encode(frame);
        assertThat(encoded.length).isEqualTo(MbapHeader.HEADER_SIZE + pdu.length);
    }

    @Test
    void testWriteReadRoundTrip() throws IOException {
        var header = MbapHeader.request(42, 1, 5);
        byte[] pdu = ModbusCodec.buildReadCoilsRequest(100, 8);
        var frame = new ModbusFrame(header, pdu);

        var baos = new ByteArrayOutputStream();
        ModbusCodec.write(frame, baos);

        var bais = new ByteArrayInputStream(baos.toByteArray());
        ModbusFrame decoded = ModbusCodec.read(bais);

        assertThat(decoded.header().transactionId()).isEqualTo(42);
        assertThat(decoded.functionCode()).isEqualTo(FunctionCode.READ_COILS.code());
    }

    @Test
    void testBuildReadCoilsRequest() {
        byte[] pdu = ModbusCodec.buildReadCoilsRequest(100, 8);
        assertThat(pdu[0]).isEqualTo((byte) 0x01);
        assertThat(pdu.length).isEqualTo(5);
    }

    @Test
    void testBuildReadDiscreteInputsRequest() {
        byte[] pdu = ModbusCodec.buildReadDiscreteInputsRequest(0, 16);
        assertThat(pdu[0]).isEqualTo((byte) 0x02);
    }

    @Test
    void testBuildReadHoldingRegistersRequest() {
        byte[] pdu = ModbusCodec.buildReadHoldingRegistersRequest(40000, 10);
        assertThat(pdu[0]).isEqualTo((byte) 0x03);
    }

    @Test
    void testBuildReadInputRegistersRequest() {
        byte[] pdu = ModbusCodec.buildReadInputRegistersRequest(30000, 5);
        assertThat(pdu[0]).isEqualTo((byte) 0x04);
    }

    @Test
    void testBuildWriteSingleCoilRequest() {
        byte[] on = ModbusCodec.buildWriteSingleCoilRequest(100, true);
        assertThat(on[0]).isEqualTo((byte) 0x05);
        assertThat(on[3]).isEqualTo((byte) 0xFF);
        assertThat(on[4]).isEqualTo((byte) 0x00);

        byte[] off = ModbusCodec.buildWriteSingleCoilRequest(100, false);
        assertThat(off[3]).isEqualTo((byte) 0x00);
        assertThat(off[4]).isEqualTo((byte) 0x00);
    }

    @Test
    void testBuildWriteSingleRegisterRequest() {
        byte[] pdu = ModbusCodec.buildWriteSingleRegisterRequest(1, 0x1234);
        assertThat(pdu[0]).isEqualTo((byte) 0x06);
        assertThat(pdu.length).isEqualTo(5);
    }

    @Test
    void testBuildWriteMultipleCoilsRequest() {
        boolean[] values = {true, false, true, true, false, false, true, false, true};
        byte[] pdu = ModbusCodec.buildWriteMultipleCoilsRequest(0, values);
        assertThat(pdu[0]).isEqualTo((byte) 0x0F);
    }

    @Test
    void testBuildWriteMultipleRegistersRequest() {
        int[] values = {100, 200, 300};
        byte[] pdu = ModbusCodec.buildWriteMultipleRegistersRequest(0, values);
        assertThat(pdu[0]).isEqualTo((byte) 0x10);
    }

    @Test
    void testBuildReadWriteMultipleRegistersRequest() {
        int[] writeValues = {1, 2};
        byte[] pdu = ModbusCodec.buildReadWriteMultipleRegistersRequest(0, 3, 10, writeValues);
        assertThat(pdu[0]).isEqualTo((byte) 0x17);
    }

    @Test
    void testBuildExceptionResponse() {
        byte[] pdu = ModbusCodec.buildExceptionResponse(
                FunctionCode.READ_COILS,
                ModbusException.ExceptionCode.ILLEGAL_DATA_ADDRESS);
        assertThat(pdu[0]).isEqualTo((byte) 0x81);
        assertThat(pdu[1]).isEqualTo((byte) 0x02);
    }

    @Test
    void testPackUnpackBooleans() {
        boolean[] original = {true, false, true, true, false, false, true, false, true, true};
        byte[] packed = ModbusCodec.packBooleans(original);
        boolean[] unpacked = ModbusCodec.unpackBooleans(packed, original.length);
        assertThat(unpacked).isEqualTo(original);
    }

    @Test
    void testPackUnpackSingleBit() {
        boolean[] single = {true};
        byte[] packed = ModbusCodec.packBooleans(single);
        assertThat(packed).hasSize(1);
        boolean[] unpacked = ModbusCodec.unpackBooleans(packed, 1);
        assertThat(unpacked[0]).isTrue();
    }

    @Test
    void testUnpackRegisters() {
        byte[] data = {0, 100, 1, 0, (byte) 0xFF, (byte) 0xFF};
        int[] regs = ModbusCodec.unpackRegisters(data);
        assertThat(regs).containsExactly(100, 256, 65535);
    }

    @Test
    void testReadQuantityValidation() {
        assertThatThrownBy(() -> ModbusCodec.buildReadCoilsRequest(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModbusCodec.buildReadCoilsRequest(0, 2001))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModbusCodec.buildReadHoldingRegistersRequest(0, 126))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testReadIncompleteHeaderThrows() {
        var bais = new ByteArrayInputStream(new byte[3]);
        assertThatThrownBy(() -> ModbusCodec.read(bais))
                .isInstanceOf(ModbusException.class);
    }
}
