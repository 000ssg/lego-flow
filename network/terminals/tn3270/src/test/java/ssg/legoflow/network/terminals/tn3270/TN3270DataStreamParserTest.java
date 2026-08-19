package ssg.legoflow.network.terminals.tn3270;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.terminals.tn3270.TN3270DataStreamParser.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the 3270 data stream parser.
 */
class TN3270DataStreamParserTest {

    private final TN3270DataStreamParser parser = new TN3270DataStreamParser();

    @Test
    void testParseNullThrows() {
        assertThatThrownBy(() -> parser.parse((byte[]) null))
            .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void testParseEmptyData() {
        List<DataStreamRecord> records = parser.parse(new byte[0]);
        assertThat(records).hasSize(1);
        assertThat(records.get(0)).isInstanceOf(KeyboardDataRecord.class);
    }

    @Test
    void testParseKeyboardDataOnly() {
        byte[] data = new byte[32];
        for (int i = 0; i < 32; i++) data[i] = (byte) (i + 1);
        List<DataStreamRecord> records = parser.parse(data);
        assertThat(records).hasSize(1);
        KeyboardDataRecord kb = (KeyboardDataRecord) records.get(0);
        assertThat(kb.data()).hasSize(32);
        for (int i = 0; i < 32; i++) {
            assertThat(kb.data()[i]).isEqualTo((byte) (i + 1));
        }
    }

    @Test
    void testParsePartialKeyboardData() {
        byte[] data = new byte[16];
        for (int i = 0; i < 16; i++) data[i] = (byte) (i + 1);
        List<DataStreamRecord> records = parser.parse(data);
        assertThat(records).hasSize(1);
        KeyboardDataRecord kb = (KeyboardDataRecord) records.get(0);
        assertThat(kb.data()).hasSize(16);
    }

    @Test
    void testParseKeyboardPlusData() {
        byte[] data = new byte[32 + 6];
        // Fill keyboard area
        for (int i = 0; i < 32; i++) data[i] = (byte) (i + 1);
        // Field: length includes 2 attr bytes + 3 data bytes = 5
        data[32] = 0x05; // length = 5 (2 attrs + 3 data)
        data[33] = 0x00; // primary attr (normal)
        data[34] = 0x00; // secondary attr (normal)
        data[35] = 'A';
        data[36] = 'B';
        data[37] = 'C';

        List<DataStreamRecord> records = parser.parse(data);
        assertThat(records).hasSize(2);
        assertThat(records.get(0)).isInstanceOf(KeyboardDataRecord.class);
        assertThat(records.get(1)).isInstanceOf(FieldDataRecord.class);

        FieldDataRecord field = (FieldDataRecord) records.get(1);
        assertThat(field.data()).isEqualTo(new byte[]{'A', 'B', 'C'});
        assertThat(field.attr()).isEqualTo(TN3270FieldAttr.NORMAL);
    }

    @Test
    void testParseFieldWithAttributes() {
        byte[] data = new byte[32 + 7];
        // Keyboard area
        for (int i = 0; i < 32; i++) data[i] = 0;
        // Field: length=6 (2 attrs + 4 data), primary=0x02 (bold), secondary=0x01
        data[32] = 0x06; // length = 6 (2 attrs + 4 data)
        data[33] = 0x02; // primary: bold
        data[34] = 0x01; // secondary: normal
        data[35] = 'X';
        data[36] = 'Y';
        data[37] = 'Z';
        data[38] = 'W';

        List<DataStreamRecord> records = parser.parse(data);
        assertThat(records).hasSize(2);

        FieldDataRecord field = (FieldDataRecord) records.get(1);
        assertThat(field.data()).hasSize(4);
        assertThat(field.data()[0]).isEqualTo((byte) 'X');
        assertThat(field.attr().isBold()).isTrue();
    }

    @Test
    void testParseControlFunctionPPI() {
        byte[] data = new byte[32 + 1];
        data[32] = (byte) 0x80; // PPI

        List<DataStreamRecord> records = parser.parse(data);
        assertThat(records).hasSize(2);
        assertThat(records.get(1)).isSameAs(ControlRecord.PPI);
    }

    @Test
    void testParseControlFunctionRTS() {
        byte[] data = new byte[32 + 1];
        data[32] = (byte) 0x81; // RTS

        List<DataStreamRecord> records = parser.parse(data);
        assertThat(records).hasSize(2);
        assertThat(records.get(1)).isSameAs(ControlRecord.RTS);
    }

    @Test
    void testParseControlFunctionTSS() {
        byte[] data = new byte[32 + 1];
        data[32] = (byte) 0x82; // TSS

        List<DataStreamRecord> records = parser.parse(data);
        assertThat(records).hasSize(2);
        assertThat(records.get(1)).isSameAs(ControlRecord.TSS);
    }

    @Test
    void testParseControlFunctionECD() {
        byte[] data = new byte[32 + 1];
        data[32] = (byte) 0x83; // ECD

        List<DataStreamRecord> records = parser.parse(data);
        assertThat(records).hasSize(2);
        assertThat(records.get(1)).isSameAs(ControlRecord.ECD);
    }

    @Test
    void testParseControlFunctionUNDO() {
        byte[] data = new byte[32 + 1];
        data[32] = (byte) 0x84; // UNDO

        List<DataStreamRecord> records = parser.parse(data);
        assertThat(records).hasSize(2);
        assertThat(records.get(1)).isSameAs(ControlRecord.UNDO);
    }

    @Test
    void testParseControlFunctionFlash() {
        byte[] data = new byte[32 + 1];
        data[32] = (byte) 0x85; // FLASH

        List<DataStreamRecord> records = parser.parse(data);
        assertThat(records).hasSize(2);
        assertThat(records.get(1)).isSameAs(ControlRecord.FLASH);
    }

    @Test
    void testParseControlFunctionRK() {
        byte[] data = new byte[32 + 1];
        data[32] = (byte) 0x87; // RK

        List<DataStreamRecord> records = parser.parse(data);
        assertThat(records).hasSize(2);
        assertThat(records.get(1)).isSameAs(ControlRecord.RK);
    }

    @Test
    void testParseControlFunctionATN() {
        byte[] data = new byte[32 + 1];
        data[32] = (byte) 0x88; // ATN

        List<DataStreamRecord> records = parser.parse(data);
        assertThat(records).hasSize(2);
        assertThat(records.get(1)).isSameAs(ControlRecord.ATN);
    }

    @Test
    void testGetRecords() {
        parser.parse(new byte[32]);
        assertThat(parser.getRecords()).hasSize(1);
    }

    @Test
    void testReadLengthNormal() {
        byte[] data = new byte[]{0x05};
        int[] result = readLengthPrivate(data, 0, 1);
        assertThat(result[0]).isEqualTo(5);
        assertThat(result[1]).isEqualTo(1);
    }

    @Test
    void testReadLengthLong() {
        // Length = 256 (128 + 128): byte1=0x80 (cont), byte2=0x80 (cont), byte3=0x00
        // Actually: value in lower 7 bits, so 256 = 0x00 | (0x00<<7) | (0x02<<14) 
        // Simplified: 128 continuation bit set -> next byte. 0x80 | 0x00 = continuation, 0x00 = value 0
        // Let's test a simple case: 0xC0 = 0x40 = 64 with continuation
        // 0x80 | 0x01 = continuation + 1, then 0x00 = end + 0 -> total = 1
        byte[] data = new byte[]{(byte) 0xC0, 0x00}; // 0x40 = 64 with cont, then 0x00 = 0 -> 0
        int[] result = readLengthPrivate(data, 0, 2);
        assertThat(result[1]).isEqualTo(2); // consumed 2 bytes
    }

    @Test
    void testKeyboardSizeConstant() {
        assertThat(TN3270DataStreamParser.KEYBOARD_SIZE).isEqualTo(32);
    }

    // Helper to access private readLength method
    private int[] readLengthPrivate(byte[] data, int pos, int end) {
        try {
            var field = TN3270DataStreamParser.class.getDeclaredMethod("readLength", byte[].class, int.class, int.class);
            field.setAccessible(true);
            return (int[]) field.invoke(parser, data, pos, end);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
