package ssg.legoflow.coap.block;

import org.junit.jupiter.api.*;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapType;
import ssg.legoflow.coap.protocol.CoapCode;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link BlockOption} and block transfer covering block option construction,
 * encoding/decoding round trip, size calculations, validation, and edge cases.
 */
class BlockTransferTest {

    @Test
    void testBlockOptionConstruction() {
        BlockOption opt = new BlockOption(0, false, 0);
        assertThat(opt.num()).isZero();
        assertThat(opt.szx()).isZero();
        assertThat(opt.more()).isFalse();
        assertThat(opt.getBlockSize()).isEqualTo(16);

        opt = new BlockOption(5, true, 2);
        assertThat(opt.num()).isEqualTo(5);
        assertThat(opt.szx()).isEqualTo(2);
        assertThat(opt.more()).isTrue();
        assertThat(opt.getBlockSize()).isEqualTo(64);
    }

    @Test
    void testBlockSizeOptions() {
        // szx 0 = 16 bytes, 1 = 32, 2 = 64, 3 = 128, 4 = 256, 5 = 512, 6 = 1024
        assertThat(new BlockOption(0, false, 0).getBlockSize()).isEqualTo(16);
        assertThat(new BlockOption(0, false, 1).getBlockSize()).isEqualTo(32);
        assertThat(new BlockOption(0, false, 2).getBlockSize()).isEqualTo(64);
        assertThat(new BlockOption(0, false, 3).getBlockSize()).isEqualTo(128);
        assertThat(new BlockOption(0, false, 4).getBlockSize()).isEqualTo(256);
        assertThat(new BlockOption(0, false, 5).getBlockSize()).isEqualTo(512);
        assertThat(new BlockOption(0, false, 6).getBlockSize()).isEqualTo(1024);
    }

    @Test
    void testBlockOptionEncodeDecodeRoundTrip() {
        var opt1 = new BlockOption(0, false, 0);
        var decoded1 = BlockOption.decode(opt1.encode());
        assertThat(decoded1).isEqualTo(opt1);

        var opt2 = new BlockOption(5, true, 2);
        var decoded2 = BlockOption.decode(opt2.encode());
        assertThat(decoded2).isEqualTo(opt2);

        var opt3 = new BlockOption(127, true, 6);
        var decoded3 = BlockOption.decode(opt3.encode());
        assertThat(decoded3).isEqualTo(opt3);
    }

    @Test
    void testBlockOptionEncoding() {
        BlockOption opt = new BlockOption(1, true, 2);
        // Encode should produce correct wire format: (1 << 4) | 0x08 | 2 = 0x1A
        byte[] encoded = opt.encode();
        assertThat(encoded).hasSize(1);
        assertThat(encoded[0]).isEqualTo((byte) 0x1A);
    }

    @Test
    void testBlockOptionTwoByteEncoding() {
        // value > 0xFF needs 2 bytes
        BlockOption opt = new BlockOption(200, true, 6);
        byte[] encoded = opt.encode();
        assertThat(encoded).hasSize(2);
        var decoded = BlockOption.decode(encoded);
        assertThat(decoded).isEqualTo(opt);
    }

    @Test
    void testBlockOptionThreeByteEncoding() {
        // value > 0xFFFF needs 3 bytes
        BlockOption opt = new BlockOption(4096, true, 6);
        byte[] encoded = opt.encode();
        assertThat(encoded).hasSize(3);
        var decoded = BlockOption.decode(encoded);
        assertThat(decoded).isEqualTo(opt);
    }

    @Test
    void testBlockOptionZeroValue() {
        BlockOption opt = new BlockOption(0, false, 0);
        byte[] encoded = opt.encode();
        assertThat(encoded).hasSize(1);
        assertThat(encoded[0]).isZero();
    }

    @Test
    void testSzxFromBlockSize() {
        assertThat(BlockOption.szxFromBlockSize(16)).isZero();
        assertThat(BlockOption.szxFromBlockSize(32)).isEqualTo(1);
        assertThat(BlockOption.szxFromBlockSize(64)).isEqualTo(2);
        assertThat(BlockOption.szxFromBlockSize(128)).isEqualTo(3);
        assertThat(BlockOption.szxFromBlockSize(256)).isEqualTo(4);
        assertThat(BlockOption.szxFromBlockSize(512)).isEqualTo(5);
        assertThat(BlockOption.szxFromBlockSize(1024)).isEqualTo(6);
    }

    @Test
    void testSzxFromBlockSizeInvalid() {
        assertThatThrownBy(() -> BlockOption.szxFromBlockSize(8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BlockOption.szxFromBlockSize(17))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BlockOption.szxFromBlockSize(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BlockOption.szxFromBlockSize(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testBlockOptionValidationNegativeNum() {
        assertThatThrownBy(() -> new BlockOption(-1, false, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testBlockOptionValidationSzxOutOfRange() {
        assertThatThrownBy(() -> new BlockOption(0, false, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlockOption(0, false, 7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDecodeInvalidData() {
        assertThatThrownBy(() -> BlockOption.decode(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BlockOption.decode(new byte[4]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BlockOption.decode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testBlockOptionMessagePayload() {
        String largeData = "X".repeat(2048);
        var msg = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(999)
                .payload(largeData.getBytes())
                .build();
        assertThat(msg.payload()).hasSize(2048);
    }

    @Test
    void testBlockOptionRecordEquality() {
        var opt1 = new BlockOption(3, true, 1);
        var opt2 = new BlockOption(3, true, 1);
        var opt3 = new BlockOption(3, false, 1);

        assertThat(opt1).isEqualTo(opt2);
        assertThat(opt1.hashCode()).isEqualTo(opt2.hashCode());
        assertThat(opt1).isNotEqualTo(opt3);
    }
}
