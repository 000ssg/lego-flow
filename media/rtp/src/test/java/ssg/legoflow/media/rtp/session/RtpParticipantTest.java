package ssg.legoflow.media.rtp.session;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link RtpParticipant}.
 */
class RtpParticipantTest {

    @Test
    void testCreation() {
        var participant = new RtpParticipant(0x12345678L);
        assertThat(participant.ssrc()).isEqualTo(0x12345678L);
        assertThat(participant.cname()).isEmpty();
        assertThat(participant.packetsReceived()).isZero();
        assertThat(participant.packetsSent()).isZero();
        assertThat(participant.isSender()).isFalse();
    }

    @Test
    void testSetCname() {
        var participant = new RtpParticipant(1L);
        participant.setCname("user@host.example.com");
        assertThat(participant.cname()).hasValue("user@host.example.com");
    }

    @Test
    void testRecordReceived() {
        var participant = new RtpParticipant(1L);
        participant.recordReceived(100, 160);
        participant.recordReceived(101, 160);

        assertThat(participant.packetsReceived()).isEqualTo(2);
        assertThat(participant.bytesReceived()).isEqualTo(320);
        assertThat(participant.highestSequenceNumber()).isEqualTo(101);
    }

    @Test
    void testRecordSent() {
        var participant = new RtpParticipant(1L);
        participant.recordSent(80);
        participant.recordSent(80);

        assertThat(participant.packetsSent()).isEqualTo(2);
        assertThat(participant.bytesSent()).isEqualTo(160);
        assertThat(participant.isSender()).isTrue();
    }

    @Test
    void testUpdateJitter() {
        var participant = new RtpParticipant(1L);
        participant.updateJitter(320);
        assertThat(participant.jitter()).isEqualTo(320);
    }

    @Test
    void testLastActivity() {
        var participant = new RtpParticipant(1L);
        var before = participant.lastActivity();
        participant.recordReceived(1, 10);
        assertThat(participant.lastActivity()).isAfterOrEqualTo(before);
    }

    @Test
    void testAddLost() {
        var participant = new RtpParticipant(1L);
        participant.addLost(5);
        participant.addLost(3);
        assertThat(participant.packetsLost()).isEqualTo(8);
    }

    @Test
    void testLastSRReceived() {
        var participant = new RtpParticipant(1L);
        assertThat(participant.lastSRReceived()).isEmpty();

        var now = java.time.Instant.now();
        participant.setLastSRReceived(now);
        assertThat(participant.lastSRReceived()).hasValue(now);
    }

    @Test
    void testToString() {
        var participant = new RtpParticipant(0x12345678L);
        participant.setCname("test");
        assertThat(participant.toString()).contains("12345678", "test");
    }
}
