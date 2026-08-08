package ssg.legoflow.email.imap.server;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MailboxTest {
    @Test void testNameAndUidValidity() {
        var mb = new Mailbox("INBOX", 12345L);
        assertThat(mb.name()).isEqualTo("INBOX");
        assertThat(mb.uidNext()).isPositive();
        assertThat(mb.uidValidity()).isEqualTo(12345L);
    }

    @Test void testAppendAndGetMessage() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] content = "From: a@b.com\r\n\r\nbody".getBytes(StandardCharsets.UTF_8);
        var msg = mb.append(content, Set.of("\\Recent"), Instant.now());
        assertThat(msg.uid()).isPositive();
        StoredMessage stored = mb.getMessage(msg.uid());
        assertThat(stored).isNotNull();
        assertThat(stored.content()).isEqualTo(content);
    }

    @Test void testGetNonexistentMessage() {
        var mb = new Mailbox("INBOX", 1L);
        assertThat(mb.getMessage(999)).isNull();
    }

    @Test void testExpungeMessage() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] c = "x".getBytes(StandardCharsets.UTF_8);
        var msg = mb.append(c, Set.of(), Instant.now());
        assertThat(mb.expunge()).isEmpty();
        msg.addFlag("\\Deleted");
        assertThat(mb.expunge()).hasSize(1);
    }

    @Test void testMessageCount() {
        var mb = new Mailbox("INBOX", 1L);
        assertThat(mb.messageCount()).isZero();
        byte[] c = "x".getBytes(StandardCharsets.UTF_8);
        mb.append(c, Set.of(), Instant.now());
        mb.append(c, Set.of(), Instant.now());
        assertThat(mb.messageCount()).isEqualTo(2);
    }

    @Test void testUidNextIncrements() {
        var mb = new Mailbox("INBOX", 1L);
        long init = mb.uidNext();
        byte[] c = "x".getBytes(StandardCharsets.UTF_8);
        mb.append(c, Set.of(), Instant.now());
        assertThat(mb.uidNext()).isGreaterThan(init);
    }

    @Test void testIsReadOnly() {
        var mb = new Mailbox("INBOX", 1L);
        assertThat(mb.isReadOnly()).isFalse();
        mb.setReadOnly(true);
        assertThat(mb.isReadOnly()).isTrue();
    }

    @Test void testAllMessages() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] c = "x".getBytes(StandardCharsets.UTF_8);
        mb.append(c, Set.of("\\Seen"), Instant.now());
        mb.append(c, Set.of(), Instant.now());
        assertThat(mb.allMessages()).hasSize(2);
    }

    @Test void testPermanentFlags() {
        var mb = new Mailbox("INBOX", 1L);
        Set<String> pf = new HashSet<>(mb.permanentFlags());
        assertThat(pf).contains("\\Answered", "\\Flagged", "\\Deleted", "\\Seen", "\\Draft");
    }

    @Test void testHighestModSeq() {
        var mb = new Mailbox("INBOX", 1L);
        assertThat(mb.highestModSeq()).isZero();
        byte[] c = "x".getBytes(StandardCharsets.UTF_8);
        mb.append(c, Set.of(), Instant.now());
        assertThat(mb.highestModSeq()).isPositive();
    }

    @Test void testUnseenCount() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] c = "x".getBytes(StandardCharsets.UTF_8);
        mb.append(c, Set.of("\\Seen"), Instant.now());
        mb.append(c, Set.of(), Instant.now());
        assertThat(mb.unseenCount()).isEqualTo(1);
    }

    @Test void testFirstUnseen() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] c = "x".getBytes(StandardCharsets.UTF_8);
        mb.append(c, Set.of("\\Seen"), Instant.now());
        mb.append(c, Set.of("\\Seen"), Instant.now());
        mb.append(c, Set.of(), Instant.now());
        assertThat(mb.firstUnseen()).isEqualTo(3);
    }

    @Test void testRecentCount() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] c = "x".getBytes(StandardCharsets.UTF_8);
        mb.append(c, Set.of("\\Recent"), Instant.now());
        assertThat(mb.recentCount()).isEqualTo(1);
    }

    @Test void testGetMessageBySeqNum() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] c = "hello".getBytes(StandardCharsets.UTF_8);
        mb.append(c, Set.of(), Instant.now());
        assertThat(mb.getMessageBySeqNum(1)).isNotNull();
    }

    @Test void testIndex() {
        var mb = new Mailbox("INBOX", 1L);
        assertThat(mb.index()).isNotNull();
    }

    @Test void testStoreFlags() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] c = "x".getBytes(StandardCharsets.UTF_8);
        var msg = mb.append(c, Set.of(), Instant.now());
        mb.storeFlags(msg.uid(), Set.of("\\Seen"), Mailbox.FlagOperation.ADD);
        assertThat(msg.hasFlag("\\Seen")).isTrue();
    }

    @Test void testSubscribeUnsubscribe() {
        var mb = new Mailbox("Drafts", 1L);
        mb.subscribe("alice");
        assertThat(mb.isSubscribed("alice")).isTrue();
        mb.unsubscribe("alice");
        assertThat(mb.isSubscribed("alice")).isFalse();
    }

    @Test void testCopyMessage() {
        var src = new Mailbox("INBOX", 1L);
        var dst = new Mailbox("Archive", 2L);
        byte[] c = "data".getBytes(StandardCharsets.UTF_8);
        var msg = src.append(c, Set.of("\\Seen"), Instant.now());
        long newUid = src.copyMessage(msg.uid(), dst);
        assertThat(newUid).isPositive();
        assertThat(dst.getMessage(newUid)).isNotNull();
    }

    @Test void testMoveMessage() {
        var src = new Mailbox("INBOX", 1L);
        var dst = new Mailbox("Trash", 2L);
        byte[] c = "data".getBytes(StandardCharsets.UTF_8);
        var msg = src.append(c, Set.of(), Instant.now());
        long uid = msg.uid();
        long newUid = src.moveMessage(uid, dst);
        assertThat(src.getMessage(uid)).isNull();
        assertThat(dst.getMessage(newUid)).isNotNull();
    }

    @Test void testFlagOperationValues() {
        assertThat(Mailbox.FlagOperation.SET.name()).isEqualTo("SET");
        assertThat(Mailbox.FlagOperation.ADD.name()).isEqualTo("ADD");
        assertThat(Mailbox.FlagOperation.REMOVE.name()).isEqualTo("REMOVE");
    }

    @Test void testSystemFlags() {
        assertThat(Mailbox.SYSTEM_FLAGS).contains("\\Answered", "\\Flagged", "\\Deleted", "\\Seen", "\\Draft");
    }
}
