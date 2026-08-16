package ssg.legoflow.service.cluster.coordination.raft;

import java.time.Instant;
import java.util.Objects;

/**
 * A log entry in the Raft consensus protocol.
 *
 * <p>Each entry represents a state transition command that was
 * proposed to the cluster. The entry carries a term, an index,
 * and a command type with associated data.
 *
 * <p>In the context of etcd-backed coordination, this maps to
 * etcd revision entries with Raft-term semantics for leader election.
 *
 * @param term       the Raft term during which this entry was created
 * @param index      the monotonically increasing index
 * @param entryType  the type of operation
 * @param data       the serialized operation data
 * @param timestamp  when the entry was created
 * @since 0.2.0
 */
public record RaftLogEntry(
        long term,
        long index,
        EntryType entryType,
        byte[] data,
        Instant timestamp
) {
    /**
     * Types of Raft log entries.
     *
     * @since 0.2.0
     */
    public enum EntryType {
        /** A normal state machine command. */
        NORMAL,
        /** A no-op entry used for term transitions. */
        NOOP,
        /** A configuration change (add/remove member). */
        CONFIG_CHANGE
    }

    public RaftLogEntry {
        Objects.requireNonNull(entryType, "entryType must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (term < 0) throw new IllegalArgumentException("term must be non-negative");
        if (index < 0) throw new IllegalArgumentException("index must be non-negative");
    }

    /**
     * Creates a new entry with the current timestamp.
     *
     * @param term      the Raft term
     * @param index     the log index
     * @param entryType the entry type
     * @param data      the operation data (null for NOOP)
     * @return a new log entry
     * @since 0.2.0
     */
    public static RaftLogEntry of(long term, long index, EntryType entryType, byte[] data) {
        return new RaftLogEntry(term, index, entryType,
                data != null ? data.clone() : null, Instant.now());
    }

    /**
     * Creates a NOOP entry (used for term transitions).
     *
     * @param term  the Raft term
     * @param index the log index
     * @return a new NOOP entry
     * @since 0.2.0
     */
    public static RaftLogEntry noop(long term, long index) {
        return new RaftLogEntry(term, index, EntryType.NOOP, null, Instant.now());
    }

    @Override
    public String toString() {
        return "RaftLogEntry{term=" + term + ", index=" + index
                + ", type=" + entryType + '}';
    }
}
