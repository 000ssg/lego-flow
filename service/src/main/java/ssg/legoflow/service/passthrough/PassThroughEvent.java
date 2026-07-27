package ssg.legoflow.service.passthrough;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Sealed hierarchy of events emitted by a {@link PassThroughConnection} during its lifecycle.
 *
 * @since 1.0.0
 */
public sealed interface PassThroughEvent {

    /**
     * Emitted when the pass-through connection has started and is listening on its configured ports.
     *
     * @param source    the pass-through connection that started
     * @param bindings  mapping of local ports to remote addresses
     * @param timestamp when the event occurred
     */
    record Started(PassThroughConnection source, Map<Integer, InetSocketAddress> bindings,
                   Instant timestamp) implements PassThroughEvent {}

    /**
     * Emitted when the pass-through connection has been stopped.
     *
     * @param source    the pass-through connection that stopped
     * @param timestamp when the event occurred
     */
    record Stopped(PassThroughConnection source, Instant timestamp) implements PassThroughEvent {}

    /**
     * Emitted when the pass-through connection has been paused.
     *
     * @param source    the pass-through connection that was paused
     * @param duration  the duration of the pause
     * @param timestamp when the event occurred
     */
    record Paused(PassThroughConnection source, Duration duration,
                  Instant timestamp) implements PassThroughEvent {}

    /**
     * Emitted when a new client connection has been accepted and linked to a remote connection.
     *
     * @param connection the newly established bidirectional connection
     * @param timestamp  when the event occurred
     */
    record ConnectionAccepted(EstablishedConnection connection,
                              Instant timestamp) implements PassThroughEvent {}

    /**
     * Emitted when an established connection has been closed.
     *
     * @param connection the connection that was closed
     * @param stats      final I/O statistics for the connection
     * @param timestamp  when the event occurred
     */
    record ConnectionClosed(EstablishedConnection connection, ConnectionStatistics stats,
                            Instant timestamp) implements PassThroughEvent {}

    /**
     * Emitted when data has been transferred through a connection.
     *
     * @param connection the connection that transferred data
     * @param direction  the direction of the data transfer
     * @param bytes      number of bytes transferred
     * @param timestamp  when the event occurred
     */
    record DataTransferred(EstablishedConnection connection, Direction direction, int bytes,
                           Instant timestamp) implements PassThroughEvent {}

    /**
     * Emitted when an error occurs in the pass-through connection or one of its established connections.
     *
     * @param source    the object where the error occurred
     * @param message   human-readable error description
     * @param cause     the exception that caused the error, may be null
     * @param timestamp when the event occurred
     */
    record Error(Object source, String message, Exception cause,
                 Instant timestamp) implements PassThroughEvent {}
}
