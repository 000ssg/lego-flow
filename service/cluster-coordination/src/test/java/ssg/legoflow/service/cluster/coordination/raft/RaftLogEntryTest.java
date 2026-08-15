package ssg.legoflow.service.cluster.coordination.raft;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class RaftLogEntryTest {

    @Test
    void of_createsEntry() {
        RaftLogEntry entry = RaftLogEntry.of(1, 0, RaftLogEntry.EntryType.NORMAL, "data".getBytes());
        assertThat(entry.term()).isEqualTo(1);
        assertThat(entry.index()).isEqualTo(0);
        assertThat(entry.entryType()).isEqualTo(RaftLogEntry.EntryType.NORMAL);
        assertThat(entry.timestamp()).isNotNull();
    }

    @Test
    void noop_createsNoopEntry() {
        RaftLogEntry entry = RaftLogEntry.noop(5, 10);
        assertThat(entry.term()).isEqualTo(5);
        assertThat(entry.index()).isEqualTo(10);
        assertThat(entry.entryType()).isEqualTo(RaftLogEntry.EntryType.NOOP);
        assertThat(entry.data()).isNull();
    }

    @Test
    void configChange_entry() {
        RaftLogEntry entry = RaftLogEntry.of(2, 1, RaftLogEntry.EntryType.CONFIG_CHANGE,
                "add-node".getBytes());
        assertThat(entry.entryType()).isEqualTo(RaftLogEntry.EntryType.CONFIG_CHANGE);
    }

    @Test
    void negativeTerm_throws() {
        assertThatThrownBy(() -> RaftLogEntry.of(-1, 0, RaftLogEntry.EntryType.NORMAL, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeIndex_throws() {
        assertThatThrownBy(() -> RaftLogEntry.of(1, -1, RaftLogEntry.EntryType.NORMAL, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullEntryType_throws() {
        assertThatThrownBy(() -> new RaftLogEntry(1, 0, null, null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullTimestamp_throws() {
        assertThatThrownBy(() -> new RaftLogEntry(1, 0, RaftLogEntry.EntryType.NORMAL,
                null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void data_cloned() {
        byte[] data = "value".getBytes();
        RaftLogEntry entry = RaftLogEntry.of(1, 0, RaftLogEntry.EntryType.NORMAL, data);

        assertThat(entry.data()).isNotSameAs(data);
        data[0] = 'X';
        assertThat(entry.data()[0]).isEqualTo((byte) 'v');
    }

    @Test
    void toString_containsInfo() {
        RaftLogEntry entry = RaftLogEntry.of(3, 5, RaftLogEntry.EntryType.NORMAL, "d".getBytes());
        String s = entry.toString();
        assertThat(s).contains("RaftLogEntry");
        assertThat(s).contains("term=3");
        assertThat(s).contains("index=5");
        assertThat(s).contains("type=NORMAL");
    }

    @Test
    void entryType_values() {
        assertThat(RaftLogEntry.EntryType.NORMAL.name()).isEqualTo("NORMAL");
        assertThat(RaftLogEntry.EntryType.NOOP.name()).isEqualTo("NOOP");
        assertThat(RaftLogEntry.EntryType.CONFIG_CHANGE.name()).isEqualTo("CONFIG_CHANGE");
    }

    @Test
    void zeroTermAndIndex_allowed() {
        RaftLogEntry entry = RaftLogEntry.of(0, 0, RaftLogEntry.EntryType.NOOP, null);
        assertThat(entry.term()).isZero();
        assertThat(entry.index()).isZero();
    }
}
