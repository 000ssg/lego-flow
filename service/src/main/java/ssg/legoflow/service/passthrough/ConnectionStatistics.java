package ssg.legoflow.service.passthrough;

/**
 * Immutable snapshot of I/O statistics for a connection or aggregate of connections.
 *
 * @param localBytesRead     total bytes read from the local (client) socket
 * @param localBytesWritten  total bytes written to the local (client) socket
 * @param remoteBytesRead    total bytes read from the remote (target) socket
 * @param remoteBytesWritten total bytes written to the remote (target) socket
 * @since 1.0.0
 */
public record ConnectionStatistics(
        long localBytesRead,
        long localBytesWritten,
        long remoteBytesRead,
        long remoteBytesWritten) {

    /**
     * Returns the total bytes transferred in both directions.
     *
     * @return total bytes transferred
     */
    public long totalBytes() {
        return localBytesRead + localBytesWritten + remoteBytesRead + remoteBytesWritten;
    }

    /**
     * Returns a new statistics snapshot that is the sum of this and another snapshot.
     *
     * @param other the other statistics to add
     * @return combined statistics
     */
    public ConnectionStatistics add(ConnectionStatistics other) {
        return new ConnectionStatistics(
                localBytesRead + other.localBytesRead,
                localBytesWritten + other.localBytesWritten,
                remoteBytesRead + other.remoteBytesRead,
                remoteBytesWritten + other.remoteBytesWritten);
    }
}
