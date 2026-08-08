package ssg.legoflow.coap.block;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BlockOption}.
 *
 * @since 0.1.0
 */
class BlockOptionTest {

    @Test
    void testEncodeDecodeBlock0More() {
        var block = new BlockOption(0, true, 2); // block 0, more=true, szx=2 (64 bytes)
        var encoded = block.encode();
        var decoded = BlockOption.decode(encoded);

        assertThat(decoded.num()).isZero();
        assertThat(decoded.more()).isTrue();
        assertThat(decoded.szx()).isEqualTo(2);
    }

    @Test
    void testEncodeDecodeBlock5NoMore() {
        var block = new BlockOption(5, false, 4); // block 5, more=false, szx=4 (256 bytes)
        var encoded = block.encode();
        var decoded = BlockOption.decode(encoded);

        assertThat(decoded.num()).isEqualTo(5);
        assertThat(decoded.more()).isFalse();
        assertThat(decoded.szx()).isEqualTo(4);
    }

    @Test
    void testBlockSizes() {
        assertThat(new BlockOption(0, false, 0).getBlockSize()).isEqualTo(16);
        assertThat(new BlockOption(0, false, 1).getBlockSize()).isEqualTo(32);
        assertThat(new BlockOption(0, false, 2).getBlockSize()).isEqualTo(64);
        assertThat(new BlockOption(0, false, 3).getBlockSize()).isEqualTo(128);
        assertThat(new BlockOption(0, false, 4).getBlockSize()).isEqualTo(256);
        assertThat(new BlockOption(0, false, 5).getBlockSize()).isEqualTo(512);
        assertThat(new BlockOption(0, false, 6).getBlockSize()).isEqualTo(1024);
    }

    @Test
    void testSzxFromBlockSize() {
        assertThat(BlockOption.szxFromBlockSize(16)).isZero();
        assertThat(BlockOption.szxFromBlockSize(64)).isEqualTo(2);
        assertThat(BlockOption.szxFromBlockSize(256)).isEqualTo(4);
        assertThat(BlockOption.szxFromBlockSize(1024)).isEqualTo(6);
    }

    @Test
    void testSzxFromInvalidBlockSizeThrows() {
        assertThatThrownBy(() -> BlockOption.szxFromBlockSize(100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMoreFlag() {
        var withMore = new BlockOption(0, true, 2);
        var withoutMore = new BlockOption(0, false, 2);

        assertThat(withMore.more()).isTrue();
        assertThat(withoutMore.more()).isFalse();
    }

    @Test
    void testLargeBlockNumber() {
        var block = new BlockOption(1000, false, 5);
        var encoded = block.encode();
        var decoded = BlockOption.decode(encoded);

        assertThat(decoded.num()).isEqualTo(1000);
        assertThat(decoded.more()).isFalse();
        assertThat(decoded.szx()).isEqualTo(5);
    }

    @Test
    void testInvalidSzxThrows() {
        assertThatThrownBy(() -> new BlockOption(0, false, 7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlockOption(0, false, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNegativeBlockNumberThrows() {
        assertThatThrownBy(() -> new BlockOption(-1, false, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
