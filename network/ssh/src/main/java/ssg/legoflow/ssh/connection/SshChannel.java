package ssg.legoflow.ssh.connection;

import ssg.legoflow.ssh.transport.SshTransport;
import ssg.legoflow.ssh.transport.SshTransportCodec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract SSH channel with windowed flow control per RFC 4254.
 *
 * @since 1.0.0
 */
public abstract class SshChannel implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SshChannel.class);

    private final int localId;
    private int remoteId;
    private final WindowManager windowManager;
    private final SshTransport transport;
    private final BlockingQueue<byte[]> dataQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<byte[]> extDataQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final CountDownLatch openLatch = new CountDownLatch(1);
    private final BlockingQueue<Boolean> requestReplyQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean eofSent = new AtomicBoolean(false);
    private final AtomicBoolean eofReceived = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private Integer exitStatus;

    /**
     * Creates a new channel.
     *
     * @param localId   the local channel ID
     * @param transport the transport layer
     */
    protected SshChannel(int localId, SshTransport transport) {
        this.localId = localId;
        this.transport = transport;
        this.windowManager = new WindowManager();
    }

    /**
     * Returns the SSH channel type name.
     *
     * @return the channel type (e.g., "session", "direct-tcpip")
     */
    public abstract String channelType();

    /**
     * Returns the local channel ID.
     *
     * @return the local channel number
     */
    public int localId() { return localId; }

    /**
     * Returns the remote channel ID.
     *
     * @return the remote channel number
     */
    public int remoteId() { return remoteId; }

    /**
     * Sets the remote channel ID.
     *
     * @param remoteId the remote channel number
     */
    public void setRemoteId(int remoteId) {
        this.remoteId = remoteId;
    }

    /**
     * Returns the window manager.
     *
     * @return the window manager
     */
    public WindowManager windowManager() { return windowManager; }

    /**
     * Returns the transport.
     *
     * @return the transport layer
     */
    public SshTransport transport() { return transport; }

    /**
     * Marks the channel as open and signals waiting threads.
     */
    public void setOpen() {
        open.set(true);
        openLatch.countDown();
    }

    /**
     * Waits for the channel to be opened by the remote side.
     *
     * @param timeoutMs timeout in milliseconds
     * @return true if the channel was opened, false if timeout
     * @throws InterruptedException if interrupted
     */
    public boolean waitForOpen(long timeoutMs) throws InterruptedException {
        return openLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns whether the channel is open.
     *
     * @return true if open
     */
    public boolean isOpen() { return open.get() && !closed.get(); }

    /**
     * Signals that a channel request succeeded.
     */
    public void onRequestSuccess() {
        requestReplyQueue.offer(Boolean.TRUE);
    }

    /**
     * Signals that a channel request failed.
     */
    public void onRequestFailure() {
        requestReplyQueue.offer(Boolean.FALSE);
    }

    /**
     * Waits for the reply to a channel request.
     *
     * @param timeoutMs timeout in milliseconds
     * @return true if the request succeeded, false if failed or timeout
     * @throws InterruptedException if interrupted
     */
    public boolean waitForRequestReply(long timeoutMs) throws InterruptedException {
        Boolean result = requestReplyQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        return result != null && result;
    }

    /**
     * Sends data on this channel.
     *
     * @param data the data to send
     * @throws IOException if an I/O error occurs
     */
    public void sendData(byte[] data) throws IOException {
        if (!isOpen()) throw new IOException("Channel not open");
        if (eofSent.get()) throw new IOException("EOF already sent");

        // Wait for window space
        while (!windowManager.consumeRemoteWindow(data.length)) {
            Thread.onSpinWait();
        }

        ByteBuffer buf = ByteBuffer.allocate(9 + data.length);
        buf.put((byte) 94); // SSH_MSG_CHANNEL_DATA
        buf.putInt(remoteId);
        SshTransportCodec.writeBinary(buf, data);
        buf.flip();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        transport.sendPacket(payload);
    }

    /**
     * Receives data from this channel (blocks until data available).
     *
     * @return the received data
     * @throws InterruptedException if interrupted
     */
    public byte[] receiveData() throws InterruptedException {
        return dataQueue.take();
    }

    /**
     * Receives data with timeout.
     *
     * @param timeoutMs timeout in milliseconds
     * @return the received data, or null if timeout
     * @throws InterruptedException if interrupted
     */
    public byte[] receiveData(long timeoutMs) throws InterruptedException {
        return dataQueue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Called when data is received on this channel.
     *
     * @param data the received data
     */
    public void onData(byte[] data) {
        windowManager.consumeLocalWindow(data.length);
        dataQueue.offer(data);

        // Send window adjust if needed
        if (windowManager.shouldAdjust()) {
            try {
                long toAdd = windowManager.adjustLocalWindow();
                sendWindowAdjust(toAdd);
            } catch (IOException e) {
                LOG.warn("Failed to send window adjust", e);
            }
        }
    }

    /**
     * Called when extended data is received.
     *
     * @param dataType the data type code
     * @param data     the received data
     */
    public void onExtendedData(int dataType, byte[] data) {
        windowManager.consumeLocalWindow(data.length);
        extDataQueue.offer(data);
    }

    /**
     * Receives extended data (stderr).
     *
     * @return the extended data
     * @throws InterruptedException if interrupted
     */
    public byte[] receiveExtendedData() throws InterruptedException {
        return extDataQueue.take();
    }

    /**
     * Sends EOF on this channel.
     *
     * @throws IOException if an I/O error occurs
     */
    public void sendEof() throws IOException {
        if (eofSent.compareAndSet(false, true)) {
            ByteBuffer buf = ByteBuffer.allocate(5);
            buf.put((byte) 96);
            buf.putInt(remoteId);
            transport.sendPacket(buf.array());
        }
    }

    /**
     * Called when EOF is received.
     */
    public void onEof() {
        eofReceived.set(true);
        dataQueue.offer(new byte[0]); // Signal EOF
    }

    /**
     * Returns whether EOF has been received.
     *
     * @return true if EOF received
     */
    public boolean isEofReceived() { return eofReceived.get(); }

    /**
     * Sets the exit status.
     *
     * @param status the exit status code
     */
    public void setExitStatus(int status) {
        this.exitStatus = status;
    }

    /**
     * Returns the exit status.
     *
     * @return the exit status, or null if not yet received
     */
    public Integer exitStatus() { return exitStatus; }

    /**
     * Sends a window adjust message.
     */
    private void sendWindowAdjust(long bytesToAdd) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(9);
        buf.put((byte) 93);
        buf.putInt(remoteId);
        buf.putInt((int) bytesToAdd);
        transport.sendPacket(buf.array());
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            if (!eofSent.get()) {
                try { sendEof(); } catch (IOException ignored) {}
            }
            ByteBuffer buf = ByteBuffer.allocate(5);
            buf.put((byte) 97); // SSH_MSG_CHANNEL_CLOSE
            buf.putInt(remoteId);
            transport.sendPacket(buf.array());
            open.set(false);
        }
    }
}
