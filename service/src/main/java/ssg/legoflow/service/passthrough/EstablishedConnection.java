package ssg.legoflow.service.passthrough;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Represents a single bidirectional TCP pipe between a local (client) socket and a
 * remote (target) socket. Uses two virtual threads to relay data in each direction.
 * <p>
 * Thread-safe. Statistics are tracked with atomic counters and can be read at any time.
 *
 * @since 0.1.0
 */
public class EstablishedConnection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EstablishedConnection.class);
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

    private final int id;
    private final SocketChannel local;
    private final SocketChannel remote;
    private final int bufferSize;

    private final AtomicLong localBytesRead = new AtomicLong();
    private final AtomicLong localBytesWritten = new AtomicLong();
    private final AtomicLong remoteBytesRead = new AtomicLong();
    private final AtomicLong remoteBytesWritten = new AtomicLong();

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition resumeCondition = pauseLock.newCondition();
    private volatile boolean paused = false;

    private volatile Thread localToRemoteThread;
    private volatile Thread remoteToLocalThread;

    /**
     * Creates a new established connection between a local and remote socket channel.
     *
     * @param local      the local (client) socket channel
     * @param remote     the remote (target) socket channel
     * @param bufferSize the buffer size for relay operations
     * @throws IllegalArgumentException if local or remote is null, or bufferSize is not positive
     */
    public EstablishedConnection(SocketChannel local, SocketChannel remote, int bufferSize) {
        if (local == null) {
            throw new IllegalArgumentException("local must not be null");
        }
        if (remote == null) {
            throw new IllegalArgumentException("remote must not be null");
        }
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be positive: " + bufferSize);
        }
        this.id = ID_GENERATOR.incrementAndGet();
        this.local = local;
        this.remote = remote;
        this.bufferSize = bufferSize;
    }

    /**
     * Returns the unique identifier for this connection.
     *
     * @return the connection id
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the local (client) socket channel.
     *
     * @return the local socket channel
     */
    public SocketChannel getLocal() {
        return local;
    }

    /**
     * Returns the remote (target) socket channel.
     *
     * @return the remote socket channel
     */
    public SocketChannel getRemote() {
        return remote;
    }

    /**
     * Returns the local socket address of this connection, or null if unavailable.
     *
     * @return the local socket address
     */
    public SocketAddress getLocalAddress() {
        try {
            return local.getLocalAddress();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Returns the remote socket address of this connection, or null if unavailable.
     *
     * @return the remote socket address
     */
    public SocketAddress getRemoteAddress() {
        try {
            return remote.getRemoteAddress();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Returns a snapshot of the current I/O statistics for this connection.
     *
     * @return current connection statistics
     */
    public ConnectionStatistics getStatistics() {
        return new ConnectionStatistics(
                localBytesRead.get(),
                localBytesWritten.get(),
                remoteBytesRead.get(),
                remoteBytesWritten.get());
    }

    /**
     * Returns whether this connection is currently paused.
     *
     * @return {@code true} if paused
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Pauses data relay on this connection. Relay threads will block until resumed.
     */
    public void pause() {
        paused = true;
        LOG.debug("Connection {} paused", id);
    }

    /**
     * Resumes data relay on this connection after a pause.
     */
    public void resume() {
        paused = false;
        pauseLock.lock();
        try {
            resumeCondition.signalAll();
        } finally {
            pauseLock.unlock();
        }
        LOG.debug("Connection {} resumed", id);
    }

    /**
     * Returns whether this connection has been closed.
     *
     * @return {@code true} if closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Starts the two relay virtual threads for bidirectional data transfer.
     *
     * @param interceptors list of data interceptors to apply
     * @param eventSink    consumer for data transfer and error events
     */
    void startRelay(List<DataInterceptor> interceptors, Consumer<PassThroughEvent> eventSink) {
        localToRemoteThread = Thread.ofVirtual()
                .name("ptc-relay-l2r-" + id)
                .start(() -> relay(local, remote, Direction.LOCAL_TO_REMOTE, interceptors, eventSink));

        remoteToLocalThread = Thread.ofVirtual()
                .name("ptc-relay-r2l-" + id)
                .start(() -> relay(remote, local, Direction.REMOTE_TO_LOCAL, interceptors, eventSink));
    }

    private void relay(SocketChannel from, SocketChannel to, Direction direction,
                       List<DataInterceptor> interceptors, Consumer<PassThroughEvent> eventSink) {
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        try {
            while (!Thread.currentThread().isInterrupted() && from.isOpen() && to.isOpen()) {
                buffer.clear();
                int bytesRead = from.read(buffer);
                if (bytesRead == -1) {
                    break;
                }
                if (bytesRead == 0) {
                    continue;
                }

                // Track read stats
                if (direction == Direction.LOCAL_TO_REMOTE) {
                    localBytesRead.addAndGet(bytesRead);
                } else {
                    remoteBytesRead.addAndGet(bytesRead);
                }

                // Handle pause — block before writing so data is held until resumed
                if (paused) {
                    pauseLock.lock();
                    try {
                        while (paused && !Thread.currentThread().isInterrupted()) {
                            resumeCondition.await();
                        }
                    } finally {
                        pauseLock.unlock();
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                }

                buffer.flip();

                // Apply interceptors
                ByteBuffer data = buffer;
                for (DataInterceptor interceptor : interceptors) {
                    ByteBuffer transformed = (direction == Direction.LOCAL_TO_REMOTE)
                            ? interceptor.onLocalToRemote(this, data)
                            : interceptor.onRemoteToLocal(this, data);
                    if (transformed != null) {
                        data = transformed;
                    }
                }

                // Write all data to destination
                while (data.hasRemaining()) {
                    int written = to.write(data);
                    if (written > 0) {
                        if (direction == Direction.LOCAL_TO_REMOTE) {
                            remoteBytesWritten.addAndGet(written);
                        } else {
                            localBytesWritten.addAndGet(written);
                        }
                    }
                }

                eventSink.accept(new PassThroughEvent.DataTransferred(
                        this, direction, bytesRead, java.time.Instant.now()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.debug("Relay thread interrupted for connection {}, direction {}", id, direction);
        } catch (IOException e) {
            if (!closed.get()) {
                LOG.debug("I/O error in relay for connection {}, direction {}: {}", id, direction, e.getMessage());
                eventSink.accept(new PassThroughEvent.Error(
                        this, "Relay I/O error: " + e.getMessage(), e, java.time.Instant.now()));
            }
        } finally {
            closeQuietly();
        }
    }

    /**
     * Closes both socket channels and interrupts the relay threads.
     */
    @Override
    public void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (closed.compareAndSet(false, true)) {
            LOG.debug("Closing connection {}", id);
            // Resume any paused threads so they can exit
            resume();
            try {
                local.close();
            } catch (IOException e) {
                LOG.trace("Error closing local channel for connection {}", id, e);
            }
            try {
                remote.close();
            } catch (IOException e) {
                LOG.trace("Error closing remote channel for connection {}", id, e);
            }
            if (localToRemoteThread != null) {
                localToRemoteThread.interrupt();
            }
            if (remoteToLocalThread != null) {
                remoteToLocalThread.interrupt();
            }
        }
    }

    @Override
    public String toString() {
        return "EstablishedConnection{id=" + id + ", local=" + getLocalAddress()
                + ", remote=" + getRemoteAddress() + "}";
    }
}
