package ssg.legoflow.messaging.mqtt.transport;

import ssg.legoflow.service.channel.DataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TLS transport wrapper — transparently encrypts/decrypts over any {@link MqttTransport}.
 *
 * <p>Follows the DF (DataFilter) pattern: wraps an inner transport, performs
 * SSL handshake lazily on first data arrival, and handles wrap/unwrap transparently
 * in {@code send()} / {@code receive()}.</p>
 *
 * <p>The handshake is driven by {@link #onRead(DataChannel, ByteBuffer)} callbacks
 * from the pipeline, NOT by a blocking constructor. This works correctly with both
 * network transports (selector-driven) and in-memory transports (queue-driven).</p>
 *
 * <p>One instance per TLS connection.</p>
 */
public final class MqttTlsTransport implements MqttTransport {

    private static final Logger LOG = LoggerFactory.getLogger(MqttTlsTransport.class);
    private static final int BUFFER_SIZE = 65536;

    private final MqttTransport inner;
    private final SSLEngine engine;

    // Handshake state: tracks whether handshake is complete
    private volatile boolean handshakeComplete = false;
    private volatile Throwable handshakeError;

    // Inbound ring buffer (decrypted app data)
    private final byte[] inBuffer = new byte[BUFFER_SIZE];
    private int inStart;
    private int inEnd;
    private volatile int inCount;
    private final Semaphore inAvailable = new Semaphore(0);

    // Outbound queue
    private final LinkedBlockingQueue<ByteBuffer> outboundQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean open = new AtomicBoolean(true);

    /**
     * Creates a TLS transport wrapper. Handshake is NOT performed in the constructor —
     * it runs lazily when {@link #onRead(DataChannel, ByteBuffer)} is called with
     * handshake data from the peer.
     *
     * @param inner  the underlying transport
     * @param engine the configured SSL engine (handshake not yet started)
     */
    public MqttTlsTransport(MqttTransport inner, SSLEngine engine) {
        this.inner = inner;
        this.engine = engine;
        // If engine was pre-handshaked (e.g. engines handshaked externally for testing),
        // detect this and skip beginHandshake()
        if (engine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            try {
                engine.beginHandshake();
            } catch (javax.net.ssl.SSLException e) {
                throw new RuntimeException("TLS handshake init failed", e);
            }
        }
        handshakeComplete = (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING);
    }

    /**
     * Called by the pipeline when data arrives from the channel.
     * Drives handshake if in progress, then unwraps and adds to ring buffer.
     */
    public void onRead(DataChannel channel, ByteBuffer data) {
        try {
            unwrapAndAdd(data);
        } catch (IOException e) {
            LOG.debug("TLS unwrap error", e);
            close();
        }
    }

    /**
     * Called by the pipeline when the channel is writable.
     */
    public void onWrite(DataChannel channel) {
    }

    /**
     * Unwraps TLS data and pushes decrypted bytes into the ring buffer.
     * Caller must ensure netData has position=0 (ready to read).
     * If handshake is in progress, drives the handshake loop using the provided net data.
     * After handshake completes, normal unwrap is used.
     */
    void unwrapAndAdd(ByteBuffer netData) throws IOException {
        if (!handshakeComplete) {
            checkHandshakeError();
            // netData is already at position=0 — no flip needed
            doHandshakeIncremental(netData);
            handshakeComplete = (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING);
            if (handshakeComplete) {
                LOG.debug("TLS handshake complete");
                return;
            }
        }
        // Normal unwrap path — netData already at position=0
        ByteBuffer appData = ByteBuffer.allocate(netBufSize());
        SSLEngineResult res = engine.unwrap(netData, appData);
        if (res.getStatus() == SSLEngineResult.Status.CLOSED) {
            close();
            return;
        }
        if (res.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            return;
        }
        appData.flip();
        add(appData);
        inAvailable.release(1);
    }

    private void checkHandshakeError() {
        if (handshakeError != null) {
            throw new RuntimeException("TLS handshake failed", handshakeError);
        }
    }

    /**
     * Incremental handshake: feeds peer data into the engine's stash,
     * unwraps what it can, wraps responses. Uses compact() to manage
     * the stash across multiple onRead() calls.
     */
    private void doHandshakeIncremental(ByteBuffer peerNetData) throws IOException {
        // peerNetData is already at position=0 (caller ensures this)
        // Feed into the engine directly
        try {
            doHandshakeRound(peerNetData);
        } catch (Exception e) {
            handshakeError = e;
            throw new IOException("TLS handshake failed: " + e.getMessage(), e);
        }
    }

    private void doHandshakeRound(ByteBuffer peerNetData) throws IOException {
        int netBufSize = engine.getSession().getPacketBufferSize();
        ByteBuffer myNetData = ByteBuffer.allocate(netBufSize);
        ByteBuffer myAppData = ByteBuffer.allocate(1);
        ByteBuffer peerAppData = ByteBuffer.allocate(1);

        // If we have peer data, try to unwrap it first
        if (peerNetData != null && peerNetData.hasRemaining()) {
            SSLEngineResult unwrapRes = engine.unwrap(peerNetData, peerAppData);
            if (unwrapRes.getStatus() == SSLEngineResult.Status.CLOSED) {
                throw new IOException("TLS handshake: peer closed");
            }
            if (unwrapRes.getStatus() == SSLEngineResult.Status.OK ||
                unwrapRes.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                // Unwrap succeeded — just wrap response
                handshakeStatusWrap(myNetData, myAppData);
                return;
            }
            // BUFFER_UNDERFLOW: engine needs more data — let it keep the internal buffer
            // and try to wrap a response
        }

        handshakeStatusWrap(myNetData, myAppData);
    }

    private void handshakeStatusWrap(ByteBuffer myNetData, ByteBuffer myAppData) throws IOException {
        myNetData.clear();
        myAppData.clear();
        SSLEngineResult wrapRes = engine.wrap(myAppData, myNetData);
        if (wrapRes.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            throw new IOException("TLS buffer overflow during handshake");
        }
        if (wrapRes.getStatus() != SSLEngineResult.Status.OK) {
            throw new IOException("TLS unexpected wrap status: " + wrapRes.getStatus());
        }
        myNetData.flip();
        if (myNetData.hasRemaining()) {
            inner.send(myNetData);
        }
    }

    private int netBufSize() {
        return engine.getSession().getPacketBufferSize();
    }

    // --- Ring buffer ---

    private synchronized int add(ByteBuffer src) {
        int n = src.remaining();
        if (n == 0) return 0;
        if (inCount + n > inBuffer.length) {
            if (inCount > 0) {
                System.arraycopy(inBuffer, inStart, inBuffer, 0, inCount);
                inEnd = inCount;
                inStart = 0;
            }
            if (inCount + n > inBuffer.length) {
                LOG.warn("TLS buffer overflow: {} + {} > {}", inCount, n, inBuffer.length);
                return 0;
            }
        }
        int first = Math.min(n, inBuffer.length - inEnd);
        src.get(inBuffer, inEnd, first);
        inEnd = (inEnd + first) % inBuffer.length;
        if (first < n) {
            src.get(inBuffer, 0, n - first);
            inEnd = n - first;
        }
        inCount += n;
        return n;
    }

    private synchronized int fetch(ByteBuffer dst) {
        if (inCount == 0) return 0;
        int n = Math.min(inCount, dst.remaining());
        int first = Math.min(n, inBuffer.length - inStart);
        dst.put(inBuffer, inStart, first);
        if (first < n) {
            dst.put(inBuffer, 0, n - first);
        }
        inStart = (inStart + n) % inBuffer.length;
        inCount -= n;
        return n;
    }

    // --- MqttTransport ---

    @Override
    public void send(ByteBuffer data) {
        if (!open.get()) return;
        try {
            ByteBuffer netBuf = ByteBuffer.allocate(netBufSize());
            SSLEngineResult res = engine.wrap(data, netBuf);
            netBuf.flip();
            inner.send(netBuf);
        } catch (Exception e) {
            LOG.debug("TLS wrap failed", e);
            close();
        }
    }

    @Override
    public int receiveWithTimeout(ByteBuffer buffer, long timeout, TimeUnit unit) {
        if (!open.get()) return -1;
        if (inCount > 0) {
            return fetch(buffer);
        }
        // In the pipeline model, data is pushed via onRead(channel, data).
        // This method only reads from the ring buffer (filled by onRead).
        // Block until ring buffer has data or deadline is reached.
        try {
            if (inAvailable.tryAcquire(timeout, unit)) {
                return inCount > 0 ? fetch(buffer) : -1;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
        return inCount > 0 ? fetch(buffer) : -1;
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            inAvailable.release(1);
            inner.close();
        }
    }

    @Override
    public boolean isOpen() {
        return open.get() && inner.isOpen();
    }

    @Override
    public DataChannel getChannel() {
        return inner.getChannel();
    }

    /** Returns the SSL engine for packet buffer sizing. */
    public SSLEngine engine() { return engine; }
    public MqttTransport getInnerTransport() {
        return inner;
    }

    /** Returns whether the TLS handshake has completed. */
    public boolean isHandshakeComplete() {
        return handshakeComplete;
    }
}
