package ssg.legoflow.network.cluster.core.hashing;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
class MurmurHash3Test {

    private static final MurmurHash3 HASH = MurmurHash3.INSTANCE;

    // Reference values from the MurmurHash3 spec
    // https://github.com/aappleby/smhasher/blob/master/src/MurmurHash3.cpp

    @Test
    void hashOfEmptyArray() {
        long result = HASH.hash(new byte[0]);
        // MurmurHash3 x86 32 with seed=0, empty input
        assertThat(result).isEqualTo(0x00000000L);
    }

    @Test
    void hashOfSingleByte() {
        long result = HASH.hash(new byte[]{0x00});
        assertThat(result).isGreaterThan(0);
    }

    @Test
    void hashOfString() {
        long result = HASH.hash("test");
        assertThat(result).isBetween(0L, 0xFFFFFFFFL);
    }

    @Test
    void consistentHashForSameInput() {
        byte[] data = "consistent-test".getBytes(StandardCharsets.UTF_8);

        long h1 = HASH.hash(data);
        long h2 = HASH.hash(data);
        long h3 = HASH.hash(data);

        assertThat(h1).isEqualTo(h2);
        assertThat(h2).isEqualTo(h3);
    }

    @Test
    void differentInputsProduceDifferentHashes() {
        long h1 = HASH.hash("key-a".getBytes());
        long h2 = HASH.hash("key-b".getBytes());

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void hashFitsInUnsigned32Bits() {
        for (int i = 0; i < 100; i++) {
            long hash = HASH.hash("value-" + i);
            assertThat(hash).isBetween(0L, 0xFFFFFFFFL);
        }
    }

    @Test
    void hash32WithSeed() {
        byte[] data = "seeded".getBytes();

        long h0 = MurmurHash3.hash32(data, 0, data.length, 0);
        long h1 = MurmurHash3.hash32(data, 0, data.length, 1);
        long h42 = MurmurHash3.hash32(data, 0, data.length, 42);

        assertThat(h0).isNotEqualTo(h1);
        assertThat(h0).isNotEqualTo(h42);
        assertThat(h1).isNotEqualTo(h42);

        // Consistency with same seed
        long h0b = MurmurHash3.hash32(data, 0, data.length, 0);
        assertThat(h0).isEqualTo(h0b);
    }

    @Test
    void hash32WithOffsetAndLength() {
        byte[] data = "prefix---payload---suffix".getBytes();

        // Hash just "payload" portion
        int payloadStart = "prefix---".length();
        int payloadLen = "payload".length();

        long offsetHash = MurmurHash3.hash32(data, payloadStart, payloadLen, 0);
        long directHash = MurmurHash3.hash32("payload".getBytes(), 0, "payload".length(), 0);

        assertThat(offsetHash).isEqualTo(directHash);
    }

    @Test
    void hash32TailBytes() {
        // Test tail handling: 1, 2, 3 byte tails
        long h1 = MurmurHash3.hash32(new byte[]{0x01}, 0, 1, 0);
        long h2 = MurmurHash3.hash32(new byte[]{0x01, 0x02}, 0, 2, 0);
        long h3 = MurmurHash3.hash32(new byte[]{0x01, 0x02, 0x03}, 0, 3, 0);

        assertThat(h1).isNotEqualTo(h2);
        assertThat(h2).isNotEqualTo(h3);
        assertThat(h1).isNotEqualTo(h3);
    }

    @Test
    void largeDataHash() {
        byte[] large = new byte[10000];
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) (i & 0xFF);
        }

        long result = HASH.hash(large);
        assertThat(result).isBetween(0L, 0xFFFFFFFFL);
    }

    @Test
    void nameReturnsMurmurHash3() {
        assertThat(HASH.name()).isEqualTo("MurmurHash3");
    }

    @Test
    void singletonIsConsistent() {
        assertThat(MurmurHash3.INSTANCE).isSameAs(HashFunction.murmurHash3());
    }

    @Test
    void hashDistributionQuality() {
        // Verify reasonable distribution across the hash space
        var counts = new int[16]; // 16 buckets
        for (int i = 0; i < 10000; i++) {
            long hash = HASH.hash("distribution-" + i);
            int bucket = (int) ((hash * 16) / 0x100000000L);
            counts[bucket]++;
        }

        int expected = 10000 / 16; // 625 per bucket
        for (int count : counts) {
            // Allow 50% variance for this simple check
            assertThat(count).isBetween(expected / 2, expected * 2);
        }
    }
}
