package ssg.legoflow.email.imap.condstore;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.imap.server.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ConditionalStoreTest {
    @Test void testModSequenceConstruction() {
        assertThat(new ModSequence(0L).current()).isZero();
        assertThat(new ModSequence(42L).current()).isEqualTo(42L);
    }

    @Test void testModSequenceDefault() {
        assertThat(new ModSequence().current()).isZero();
    }

    @Test void testModSequenceIncrement() {
        var ms = new ModSequence(10L);
        long inc = ms.next();
        assertThat(inc).isGreaterThan(10L);
        assertThat(ms.current()).isEqualTo(inc);
    }

    @Test void testModSequenceUpdateIfHigher() {
        var ms = new ModSequence(5L);
        ms.updateIfHigher(10L);
        assertThat(ms.current()).isEqualTo(10L);
        ms.updateIfHigher(3L);
        assertThat(ms.current()).isEqualTo(10L);
    }

    @Test void testModSequenceToString() {
        assertThat(new ModSequence(42L).toString()).contains("42");
    }

    @Test void testConditionalStoreSuccess() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] c = "x".getBytes(StandardCharsets.UTF_8);
        var msg = mb.append(c, Set.of(), Instant.now());
        long modSeq = msg.modSeq();
        var updated = ConditionalStore.conditionalStore(mb, msg.uid(), Set.of("\\Seen"), Mailbox.FlagOperation.ADD, modSeq);
        assertThat(updated).isNotNull();
        assertThat(updated.hasFlag("\\Seen")).isTrue();
    }

    @Test void testConditionalStoreFailsWhenModified() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] c = "x".getBytes(StandardCharsets.UTF_8);
        mb.append(c, Set.of(), Instant.now());
        assertThat(ConditionalStore.conditionalStore(mb, 1L, Set.of("\\Seen"), Mailbox.FlagOperation.ADD, 0L)).isNull();
    }

    @Test void testConditionalStoreNonexistentMessage() {
        var mb = new Mailbox("INBOX", 1L);
        assertThat(ConditionalStore.conditionalStore(mb, 999L, Set.of("\\Seen"), Mailbox.FlagOperation.ADD, 0L)).isNull();
    }

    @Test void testFindModified() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] c = "x".getBytes(StandardCharsets.UTF_8);
        mb.append(c, Set.of(), Instant.now());
        mb.append(c, Set.of(), Instant.now());
        assertThat(ConditionalStore.findModified(mb, 0L)).hasSize(2);
        assertThat(ConditionalStore.findModified(mb, Long.MAX_VALUE)).isEmpty();
    }
}
