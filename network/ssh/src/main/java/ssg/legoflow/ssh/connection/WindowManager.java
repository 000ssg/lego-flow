package ssg.legoflow.ssh.connection;

import java.util.concurrent.atomic.AtomicLong;

/**
 * SSH channel window size management per RFC 4254 section 5.2.
 *
 * <p>Manages flow control for SSH channels using sliding windows.
 *
 * @since 1.0.0
 */
public final class WindowManager {

    /** Default initial window size (2 MB). */
    public static final long DEFAULT_WINDOW_SIZE = 2 * 1024 * 1024;

    /** Default maximum packet size (32 KB). */
    public static final long DEFAULT_MAX_PACKET_SIZE = 32768;

    /** Threshold at which to send a window adjust (25% of initial). */
    public static final long WINDOW_ADJUST_THRESHOLD = DEFAULT_WINDOW_SIZE / 4;

    private final AtomicLong localWindow;
    private final AtomicLong remoteWindow;
    private final long initialWindowSize;
    private final long maxPacketSize;

    /**
     * Creates a new window manager with default sizes.
     */
    public WindowManager() {
        this(DEFAULT_WINDOW_SIZE, DEFAULT_MAX_PACKET_SIZE);
    }

    /**
     * Creates a new window manager with specified sizes.
     *
     * @param initialWindowSize initial window size
     * @param maxPacketSize     maximum packet size
     */
    public WindowManager(long initialWindowSize, long maxPacketSize) {
        this.initialWindowSize = initialWindowSize;
        this.maxPacketSize = maxPacketSize;
        this.localWindow = new AtomicLong(initialWindowSize);
        this.remoteWindow = new AtomicLong(0);
    }

    /**
     * Sets the remote window size (from channel open confirmation).
     *
     * @param size the remote window size
     */
    public void setRemoteWindow(long size) {
        remoteWindow.set(size);
    }

    /**
     * Adjusts the remote window by adding bytes.
     *
     * @param bytesToAdd bytes to add to remote window
     */
    public void adjustRemoteWindow(long bytesToAdd) {
        remoteWindow.addAndGet(bytesToAdd);
    }

    /**
     * Consumes bytes from the remote window (for sending data).
     *
     * @param bytes bytes to consume
     * @return true if sufficient window space was available
     */
    public boolean consumeRemoteWindow(long bytes) {
        while (true) {
            long current = remoteWindow.get();
            if (current < bytes) return false;
            if (remoteWindow.compareAndSet(current, current - bytes)) return true;
        }
    }

    /**
     * Consumes bytes from the local window (for received data).
     *
     * @param bytes bytes consumed
     */
    public void consumeLocalWindow(long bytes) {
        localWindow.addAndGet(-bytes);
    }

    /**
     * Checks if a window adjust should be sent to the remote side.
     *
     * @return true if local window is below threshold
     */
    public boolean shouldAdjust() {
        return localWindow.get() < WINDOW_ADJUST_THRESHOLD;
    }

    /**
     * Resets the local window to initial size and returns bytes to add.
     *
     * @return the number of bytes to send in a window adjust
     */
    public long adjustLocalWindow() {
        long current = localWindow.get();
        long toAdd = initialWindowSize - current;
        localWindow.addAndGet(toAdd);
        return toAdd;
    }

    /**
     * Returns the current local window size.
     *
     * @return local window bytes remaining
     */
    public long localWindow() { return localWindow.get(); }

    /**
     * Returns the current remote window size.
     *
     * @return remote window bytes remaining
     */
    public long remoteWindow() { return remoteWindow.get(); }

    /**
     * Returns the initial window size.
     *
     * @return initial window size
     */
    public long initialWindowSize() { return initialWindowSize; }

    /**
     * Returns the maximum packet size.
     *
     * @return max packet size
     */
    public long maxPacketSize() { return maxPacketSize; }
}
