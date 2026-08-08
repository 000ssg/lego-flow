package ssg.legoflow.email.imap.server;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class StoredMessageTest {
    private final Instant now = Instant.now();
    private final byte[] content = ("From: sender@example.com\r\nTo: recipient@example.com\r\nSubject: Test Message\r\n\r\nHello World").getBytes(StandardCharsets.UTF_8);

    @Test void testCreation() {
        Set<String> flags = Set.of("\\Seen", "\\Flagged");
        var msg = new StoredMessage(1L, now, content, flags);
        assertThat(msg.uid()).isEqualTo(1L);
        assertThat(msg.internalDate()).isEqualTo(now);
        assertThat(msg.content()).isEqualTo(content);
        assertThat(msg.size()).isEqualTo(content.length);
        assertThat(msg.flags()).containsExactlyInAnyOrder("\\Seen", "\\Flagged");
        assertThat(msg.modSeq()).isZero();
    }

    @Test void testCreationWithNullFlags() {
        var msg = new StoredMessage(1L, now, content, null);
        assertThat(msg.flags()).isEmpty();
    }

    @Test void testContentReturnsCopy() {
        var msg = new StoredMessage(1L, now, content, Set.of());
        byte[] copy = msg.content();
        copy[0] = (byte) 'X';
        assertThat(msg.content()[0]).isNotEqualTo((byte) 'X');
    }

    @Test void testContentAsUtf8String() {
        var msg = new StoredMessage(1L, now, content, Set.of());
        assertThat(msg.contentAsString()).isEqualTo(new String(content, StandardCharsets.UTF_8));
    }

    @Test void testFlagsMutability() {
        var msg = new StoredMessage(1L, now, content, Set.of("\\Seen"));
        assertThat(msg.hasFlag("\\Seen")).isTrue();
        assertThat(msg.hasFlag("\\Flagged")).isFalse();
        assertThat(msg.addFlag("\\Flagged")).isTrue();
        assertThat(msg.hasFlag("\\Flagged")).isTrue();
        assertThat(msg.addFlag("\\Flagged")).isFalse();
        assertThat(msg.removeFlag("\\Seen")).isTrue();
        assertThat(msg.hasFlag("\\Seen")).isFalse();
        assertThat(msg.removeFlag("\\Seen")).isFalse();
    }

    @Test void testSetFlags() {
        var msg = new StoredMessage(1L, now, content, Set.of("\\Seen", "\\Flagged"));
        msg.setFlags(Set.of("\\Deleted"));
        assertThat(msg.flags()).containsExactly("\\Deleted");
    }

    @Test void testFlagsViewIsUnmodifiable() {
        var msg = new StoredMessage(1L, now, content, Set.of("\\Seen"));
        assertThatThrownBy(() -> msg.flags().add("X")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void testModSeq() {
        var msg = new StoredMessage(1L, now, content, Set.of());
        assertThat(msg.modSeq()).isZero();
        msg.setModSeq(42);
        assertThat(msg.modSeq()).isEqualTo(42);
    }

    @Test void testGetHeader() {
        var msg = new StoredMessage(1L, now, content, Set.of());
        assertThat(msg.getHeader("From")).isEqualTo("sender@example.com");
        assertThat(msg.getHeader("TO")).isEqualTo("recipient@example.com");
        assertThat(msg.getHeader("Subject")).isEqualTo("Test Message");
        assertThat(msg.getHeader("Nonexistent")).isNull();
    }

    @Test void testGetHeaderCaseInsensitive() {
        var msg = new StoredMessage(1L, now, content, Set.of());
        assertThat(msg.getHeader("from")).isEqualTo("sender@example.com");
        assertThat(msg.getHeader("subject")).isEqualTo("Test Message");
    }

    @Test void testGetHeaderWithContinuation() {
        byte[] cl = ("From: a@b.com\r\nX-Multi: part1\r\n  part2\r\n\r\nbody").getBytes(StandardCharsets.UTF_8);
        var msg = new StoredMessage(1L, now, cl, Set.of());
        assertThat(msg.getHeader("X-Multi")).isEqualTo("part1 part2");
    }

    @Test void testGetBody() {
        var msg = new StoredMessage(1L, now, content, Set.of());
        assertThat(msg.getBody()).isEqualTo("Hello World");
    }

    @Test void testGetHeaders() {
        var msg = new StoredMessage(1L, now, content, Set.of());
        String h = msg.getHeaders();
        assertThat(h).contains("From: sender@example.com");
        assertThat(h).doesNotContain("Hello World");
    }

    @Test void testBodyWithoutHeaders() {
        byte[] nc = "Just body text".getBytes(StandardCharsets.UTF_8);
        var msg = new StoredMessage(1L, now, nc, Set.of());
        // Without CRLF separator, body is empty since getBody looks for header boundary
        assertThat(msg.getBody()).isEmpty();
    }

    @Test void testBodyWithLfOnly() {
        byte[] lc = ("From: a@b.com\nSubject: Hi\n\nBody").getBytes(StandardCharsets.UTF_8);
        var msg = new StoredMessage(1L, now, lc, Set.of());
        assertThat(msg.getBody()).isEqualTo("Body");
    }

    @Test void testToString() {
        var msg = new StoredMessage(42L, now, content, Set.of("\\Seen"));
        String s = msg.toString();
        assertThat(s).contains("uid=42").contains("\\Seen").contains("size=" + content.length);
    }

    @Test void testEqualsAndHashCode() {
        var m1 = new StoredMessage(1L, now, content, Set.of());
        var m2 = new StoredMessage(1L, Instant.EPOCH, "x".getBytes(), Set.of("X"));
        var m3 = new StoredMessage(2L, now, content, Set.of());
        assertThat(m1).isEqualTo(m2);
        assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
        assertThat(m1).isNotEqualTo(m3);
        assertThat(m1).isEqualTo(m1);
        assertThat(m1).isNotEqualTo("not a StoredMessage");
    }

    @Test void testNullDateThrows() {
        assertThatThrownBy(() -> new StoredMessage(1L, null, content, Set.of())).isInstanceOf(NullPointerException.class);
    }

    @Test void testNullContentThrows() {
        assertThatThrownBy(() -> new StoredMessage(1L, now, null, Set.of())).isInstanceOf(NullPointerException.class);
    }
}
