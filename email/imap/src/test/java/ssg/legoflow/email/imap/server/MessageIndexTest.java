package ssg.legoflow.email.imap.server;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MessageIndexTest {
    @Test void testAddAndGet() {
        var index = new MessageIndex();
        index.add(1L);
        index.add(5L);
        index.add(3L);
        assertThat(index.uidForSeqNum(1)).isEqualTo(1L);
        assertThat(index.uidForSeqNum(2)).isEqualTo(5L);
    }

    @Test void testSize() {
        var index = new MessageIndex();
        assertThat(index.size()).isZero();
        index.add(1L);
        assertThat(index.size()).isEqualTo(1);
    }

    @Test void testRemove() {
        var index = new MessageIndex();
        index.add(1L);
        index.add(2L);
        index.remove(1L);
        assertThat(index.size()).isEqualTo(1);
        assertThat(index.contains(1L)).isFalse();
    }

    @Test void testContains() {
        var index = new MessageIndex();
        index.add(42L);
        assertThat(index.contains(42L)).isTrue();
        assertThat(index.contains(99L)).isFalse();
    }

    @Test void testSeqNumForUid() {
        var index = new MessageIndex();
        index.add(100L);
        index.add(200L);
        assertThat(index.seqNumForUid(100L)).isEqualTo(1);
        assertThat(index.seqNumForUid(200L)).isEqualTo(2);
    }

    @Test void testAllUids() {
        var index = new MessageIndex();
        index.add(5L);
        index.add(10L);
        assertThat(index.allUids()).containsExactly(5L, 10L);
    }

    @Test void testClear() {
        var index = new MessageIndex();
        index.add(1L);
        index.clear();
        assertThat(index.size()).isZero();
    }
}
