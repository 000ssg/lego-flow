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
 * SSL handshake on construction, and handles wrap/unwrap transparently in
 * {@code send()} / {@code receive()}.</p>
 *
 * <p>One instance per TLS connection.</p>
 */
public final class MqttTlsTransport implements MqttTransport {

    private static final Logger LOG = LoggerFactory.getLogger(MqttTlsTransport.class);
    private static final int BUFFER_SIZE = 65536;

    private final MqttTransport inner;
    private final SSLEngine engine;

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
     * Creates a TLS transport wrapper and performs the SSL handshake.
     *
     * @param inner  the underlying transport
     * @param engine the configured SSL engine (handshake not yet started)
     * @throws IOException if handshake fails
     */
    public MqttTlsTransport(MqttTransport inner, SSLEngine engine) throws IOException {
        this.inner = inner;
        this.engine = engine;
        engine.beginHandshake();
        doHandshake();
    }

    private void doHandshake() throws IOException {
        SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
        int netBufSize = engine.getSession().getPacketBufferSize();
        ByteBuffer myNetData = ByteBuffer.allocate(netBufSize);
        ByteBuffer peerNetData = ByteBuffer.allocate(netBufSize);
        ByteBuffer myAppData = ByteBuffer.allocate(1); // no app data during handshake
        ByteBuffer peerAppData = ByteBuffer.allocate(1);

        while (hs != SSLEngineResult.HandshakeStatus.FINISHED
                && hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            switch (hs) {
                case NEED_UNWRAP -> {
                    peerNetData.clear();
                    int n = inner.receiveWithTimeout(peerNetData, 5, TimeUnit.SECONDS);
                    if (n <= 0) throw new IOException("TLS handshake: peer closed or timed out");
                    peerNetData.flip();
                    peerAppData.clear();
                    SSLEngineResult res = engine.unwrap(peerNetData, peerAppData);
                    if (res.getStatus() == SSLEngineResult.Status.CLOSED) {
                        throw new IOException("TLS handshake: peer closed");
                    }
                    hs = res.getHandshakeStatus();
                }
                case NEED_WRAP -> {
                    myNetData.clear();
                    myAppData.clear();
                    SSLEngineResult res = engine.wrap(myAppData, myNetData);
                    hs = res.getHandshakeStatus();
                    if (res.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        hs = SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
                    }
                    myNetData.flip();
                    inner.send(myNetData);
                }
                case NEED_TASK -> {
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    hs = engine.getHandshakeStatus();
                }
                default -> throw new IOException("Unexpected handshake status: " + hs);
            }
        }
    }

    /**
     * Called when data arrives on the inbound side.
     * Unwraps TLS and pushes decrypted bytes into the ring buffer.
     */
    void unwrapAndAdd(ByteBuffer netData) throws IOException {
        netData.flip();
        ByteBuffer appData = ByteBuffer.allocate(netBufSize());
        SSLEngineResult res = engine.unwrap(netData, appData);
        if (res.getStatus() == SSLEngineResult.Status.CLOSED) {
            close();
            return;
        }
        appData.flip();
        add(appData);
        inAvailable.release(1);
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
        // Wrap app data into TLS and send via inner transport
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
        try {
            if (!inAvailable.tryAcquire(timeout, unit)) return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
        return fetch(buffer);
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            inAvailable.release(1); // Wake up any waiting receive()
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

    /** Returns the inner (wrapped) transport for pipeline routing. */
    public MqttTransport getInnerTransport() {
        return inner;
    }
}
