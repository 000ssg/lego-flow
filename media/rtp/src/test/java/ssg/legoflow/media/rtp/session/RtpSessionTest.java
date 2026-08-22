package ssg.legoflow.media.rtp.session;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link RtpSession}.
 */
class RtpSessionTest {

    @Test
    void testCreateSessionWithCname() {
        var session = new RtpSession("user@host.example.com");
        assertThat(session.cname()).isEqualTo("user@host.example.com");
        assertThat(session.localSsrc()).isGreaterThanOrEqualTo(0);
        assertThat(session.participantCount()).isEqualTo(1);
    }

    @Test
    void testCreateSessionWithSsrc() {
        var session = new RtpSession(0x12345678L, "test@test.com");
        assertThat(session.localSsrc()).isEqualTo(0x12345678L);
        assertThat(session.localParticipant()).isNotNull();
        assertThat(session.localParticipant().cname()).hasValue("test@test.com");
    }

    @Test
    void testGetOrCreateParticipant() {
        var session = new RtpSession(1L, "cname");
        var participant = session.getOrCreateParticipant(0xAAAAAAAAL);
        assertThat(participant.ssrc()).isEqualTo(0xAAAAAAAAL);
        assertThat(session.participantCount()).isEqualTo(2);

        // Same SSRC should return the same participant
        var same = session.getOrCreateParticipant(0xAAAAAAAAL);
        assertThat(same).isSameAs(participant);
        assertThat(session.participantCount()).isEqualTo(2);
    }

    @Test
    void testGetParticipant() {
        var session = new RtpSession(1L, "cname");
        assertThat(session.getParticipant(0xBBBBBBBBL)).isEmpty();

        session.getOrCreateParticipant(0xBBBBBBBBL);
        assertThat(session.getParticipant(0xBBBBBBBBL)).isPresent();
    }

    @Test
    void testRemoveParticipant() {
        var session = new RtpSession(1L, "cname");
        session.getOrCreateParticipant(0xCCCCCCCCL);
        assertThat(session.participantCount()).isEqualTo(2);

        var removed = session.removeParticipant(0xCCCCCCCCL);
        assertThat(removed).isPresent();
        assertThat(session.participantCount()).isEqualTo(1);
    }

    @Test
    void testCannotRemoveLocalParticipant() {
        var session = new RtpSession(1L, "cname");
        var removed = session.removeParticipant(1L);
        assertThat(removed).isEmpty();
        assertThat(session.participantCount()).isEqualTo(1);
    }

    @Test
    void testDetectCollision() {
        var session = new RtpSession(0x12345678L, "cname");
        assertThat(session.detectCollision(0x12345678L)).isTrue();
        assertThat(session.detectCollision(0xABCDEF00L)).isFalse();
        assertThat(session.collisionCount()).isEqualTo(1);
    }

    @Test
    void testResolveCollision() {
        var session = new RtpSession(0x12345678L, "cname");
        long newSsrc = session.resolveCollision();
        assertThat(newSsrc).isNotEqualTo(0x12345678L);
    }

    @Test
    void testSenderCount() {
        var session = new RtpSession(1L, "cname");
        assertThat(session.senderCount()).isEqualTo(0);

        session.localParticipant().recordSent(100);
        assertThat(session.senderCount()).isEqualTo(1);

        var remote = session.getOrCreateParticipant(2L);
        assertThat(session.senderCount()).isEqualTo(1);
        remote.recordSent(50);
        assertThat(session.senderCount()).isEqualTo(2);
    }

    @Test
    void testParticipants() {
        var session = new RtpSession(1L, "cname");
        session.getOrCreateParticipant(2L);
        session.getOrCreateParticipant(3L);

        assertThat(session.participants()).hasSize(3);
    }

    @Test
    void testGenerateSsrc() {
        // Just verify it produces valid values
        long ssrc1 = RtpSession.generateSsrc();
        long ssrc2 = RtpSession.generateSsrc();
        assertThat(ssrc1).isBetween(0L, 0xFFFFFFFFL);
        assertThat(ssrc2).isBetween(0L, 0xFFFFFFFFL);
        // Very unlikely to be equal
        // (don't assert inequality since it's theoretically possible)
    }
}
