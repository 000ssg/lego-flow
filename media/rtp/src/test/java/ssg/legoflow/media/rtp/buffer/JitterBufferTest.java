package ssg.legoflow.media.rtp.buffer;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.rtp.packet.RtpPacket;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link JitterBuffer}.
 */
class JitterBufferTest {

    @Test
    void testInsertAndPollInOrder() {
        var buffer = new JitterBuffer();
        buffer.insert(makePacket(1));
        buffer.insert(makePacket(2));
        buffer.insert(makePacket(3));

        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer.poll()).isPresent().get()
                .extracting(p -> p.header().sequenceNumber()).isEqualTo(1);
        assertThat(buffer.poll()).isPresent().get()
                .extracting(p -> p.header().sequenceNumber()).isEqualTo(2);
        assertThat(buffer.poll()).isPresent().get()
                .extracting(p -> p.header().sequenceNumber()).isEqualTo(3);
        assertThat(buffer.poll()).isEmpty();
    }

    @Test
    void testReorderOutOfSequence() {
        var buffer = new JitterBuffer();
        buffer.insert(makePacket(1));
        buffer.insert(makePacket(3));
        buffer.insert(makePacket(2));

        // First packet should be seq=1
        assertThat(buffer.poll()).isPresent().get()
                .extracting(p -> p.header().sequenceNumber()).isEqualTo(1);
        // seq=2 should be next
        assertThat(buffer.poll()).isPresent().get()
                .extracting(p -> p.header().sequenceNumber()).isEqualTo(2);
        assertThat(buffer.poll()).isPresent().get()
                .extracting(p -> p.header().sequenceNumber()).isEqualTo(3);
    }

    @Test
    void testDuplicateDetection() {
        var buffer = new JitterBuffer();
        assertThat(buffer.insert(makePacket(1))).isEqualTo(JitterBuffer.InsertResult.ACCEPTED);
        assertThat(buffer.insert(makePacket(1))).isEqualTo(JitterBuffer.InsertResult.DUPLICATE);
        assertThat(buffer.duplicateCount()).isEqualTo(1);
        assertThat(buffer.size()).isEqualTo(1);
    }

    @Test
    void testLatePacketDetection() {
        var buffer = new JitterBuffer();
        buffer.insert(makePacket(1));
        buffer.insert(makePacket(2));
        buffer.poll(); // consume seq=1
        buffer.poll(); // consume seq=2

        // seq=1 is now late
        assertThat(buffer.insert(makePacket(1))).isEqualTo(JitterBuffer.InsertResult.LATE);
        assertThat(buffer.latePacketCount()).isEqualTo(1);
    }

    @Test
    void testCapacityOverflow() {
        var buffer = new JitterBuffer(3, 20, 200);
        buffer.insert(makePacket(1));
        buffer.insert(makePacket(2));
        buffer.insert(makePacket(3));
        assertThat(buffer.size()).isEqualTo(3);

        // This should cause oldest to be dropped
        buffer.insert(makePacket(4));
        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer.overflowCount()).isEqualTo(1);
    }

    @Test
    void testSkipGap() {
        var buffer = new JitterBuffer();
        buffer.insert(makePacket(1));
        buffer.poll(); // consume seq=1, now expects seq=2

        // Insert seq=5, gap of 2-4
        buffer.insert(makePacket(5));
        // poll won't return it because it expects 2
        assertThat(buffer.poll()).isEmpty();

        // skip should return seq=5
        assertThat(buffer.skip()).isPresent().get()
                .extracting(p -> p.header().sequenceNumber()).isEqualTo(5);
    }

    @Test
    void testClear() {
        var buffer = new JitterBuffer();
        buffer.insert(makePacket(1));
        buffer.insert(makePacket(2));
        assertThat(buffer.size()).isEqualTo(2);

        buffer.clear();
        assertThat(buffer.size()).isZero();
        assertThat(buffer.poll()).isEmpty();
    }

    @Test
    void testAdaptiveDelay() {
        var buffer = new JitterBuffer(100, 20, 200);
        assertThat(buffer.adaptiveDelayMs()).isEqualTo(20);

        // High jitter should increase delay
        for (int i = 0; i < 100; i++) {
            buffer.adaptDelay(100.0);
        }
        assertThat(buffer.adaptiveDelayMs()).isGreaterThan(20);
    }

    @Test
    void testAdaptiveDelayBounded() {
        var buffer = new JitterBuffer(100, 20, 200);

        // Extremely high jitter should not exceed max
        for (int i = 0; i < 1000; i++) {
            buffer.adaptDelay(10000.0);
        }
        assertThat(buffer.adaptiveDelayMs()).isLessThanOrEqualTo(200);
    }

    @Test
    void testSequenceWrapAround() {
        var buffer = new JitterBuffer();
        buffer.insert(makePacket(65534));
        buffer.insert(makePacket(65535));
        buffer.insert(makePacket(0)); // wrap around
        buffer.insert(makePacket(1));

        assertThat(buffer.poll()).isPresent().get()
                .extracting(p -> p.header().sequenceNumber()).isEqualTo(65534);
        assertThat(buffer.poll()).isPresent().get()
                .extracting(p -> p.header().sequenceNumber()).isEqualTo(65535);
        assertThat(buffer.poll()).isPresent().get()
                .extracting(p -> p.header().sequenceNumber()).isEqualTo(0);
        assertThat(buffer.poll()).isPresent().get()
                .extracting(p -> p.header().sequenceNumber()).isEqualTo(1);
    }

    @Test
    void testStatistics() {
        var buffer = new JitterBuffer();
        buffer.insert(makePacket(1));
        buffer.insert(makePacket(2));
        buffer.insert(makePacket(2)); // duplicate
        buffer.poll();
        buffer.poll();

        assertThat(buffer.totalReceived()).isEqualTo(3);
        assertThat(buffer.totalPlayed()).isEqualTo(2);
        assertThat(buffer.duplicateCount()).isEqualTo(1);
    }

    @Test
    void testCapacityAndDefaults() {
        var buffer = new JitterBuffer();
        assertThat(buffer.capacity()).isEqualTo(JitterBuffer.DEFAULT_CAPACITY);

        var custom = new JitterBuffer(10, 5, 50);
        assertThat(custom.capacity()).isEqualTo(10);
    }

    @Test
    void testInvalidConstructorParams() {
        assertThatThrownBy(() -> new JitterBuffer(0, 20, 200))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JitterBuffer(100, -1, 200))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JitterBuffer(100, 200, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPollEmptyBuffer() {
        var buffer = new JitterBuffer();
        assertThat(buffer.poll()).isEmpty();
        assertThat(buffer.skip()).isEmpty();
    }

    private static RtpPacket makePacket(int seq) {
        return RtpPacket.of(0, seq, seq * 160L, 0x12345678L, new byte[]{(byte) seq});
    }
}
