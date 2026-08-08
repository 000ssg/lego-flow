package ssg.legoflow.http3.quic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;

/**
 * TLS 1.3 integration for QUIC using {@link SSLEngine}.
 *
 * <p>QUIC mandates TLS 1.3 (RFC 9001). This class wraps the JDK's {@link SSLEngine}
 * to produce and consume handshake data that is carried in QUIC CRYPTO frames
 * rather than over a TCP stream.
 *
 * <h3>Limitations</h3>
 * <ul>
 *   <li>The JDK {@link SSLEngine} is designed for TLS-over-TCP. QUIC requires
 *       direct access to TLS handshake messages (not records), which the standard
 *       {@link SSLEngine} API does not expose. This implementation operates at the
 *       TLS record level and is suitable for testing and prototyping but would need
 *       a QUIC-aware TLS library (e.g., BoringSSL via JNI) for production use.</li>
 *   <li>0-RTT early data is supported only to the extent that {@link SSLEngine}
 *       exposes early data APIs (JDK 25+).</li>
 *   <li>QUIC header and packet protection key derivation is not performed by this
 *       class; it only manages the handshake and exports keying material.</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class QuicTlsEngine {

    private static final Logger LOG = LoggerFactory.getLogger(QuicTlsEngine.class);

    /** TLS protocol version required by QUIC (RFC 9001 Section 4.2). */
    public static final String TLS_PROTOCOL = "TLSv1.3";

    /** QUIC-specific ALPN tokens. */
    public static final String ALPN_H3 = "h3";
    public static final String ALPN_H3_29 = "h3-29";

    /**
     * Handshake state of the QUIC TLS engine.
     *
     * @since 0.1.0
     */
    public enum HandshakeState {
        /** Not yet started. */
        NOT_STARTED,
        /** Handshake is in progress; more data needs to be exchanged. */
        IN_PROGRESS,
        /** Handshake completed successfully. */
        COMPLETED,
        /** Handshake failed. */
        FAILED
    }

    private final SSLEngine engine;
    private final boolean isServer;
    private volatile HandshakeState handshakeState = HandshakeState.NOT_STARTED;

    /**
     * Creates a TLS engine for the client side.
     *
     * @param peerHost the expected server hostname (for SNI)
     * @param peerPort the server port
     * @return a client-side TLS engine
     * @throws QuicTlsException if TLS 1.3 is not available
     * @since 0.1.0
     */
    public static QuicTlsEngine forClient(String peerHost, int peerPort) {
        return forClient(peerHost, peerPort, null);
    }

    /**
     * Creates a TLS engine for the client side with a custom SSLContext.
     *
     * @param peerHost   the expected server hostname (for SNI)
     * @param peerPort   the server port
     * @param sslContext the SSLContext to use, or {@code null} for default
     * @return a client-side TLS engine
     * @throws QuicTlsException if TLS 1.3 is not available
     * @since 0.1.0
     */
    public static QuicTlsEngine forClient(String peerHost, int peerPort, SSLContext sslContext) {
        var ctx = resolveContext(sslContext);
        var sslEngine = ctx.createSSLEngine(peerHost, peerPort);
        sslEngine.setUseClientMode(true);
        return new QuicTlsEngine(sslEngine, false);
    }

    /**
     * Creates a TLS engine for the server side.
     *
     * @param sslContext the SSLContext (must have a KeyManager configured)
     * @return a server-side TLS engine
     * @throws QuicTlsException if TLS 1.3 is not available
     * @since 0.1.0
     */
    public static QuicTlsEngine forServer(SSLContext sslContext) {
        Objects.requireNonNull(sslContext, "SSLContext is required for server");
        var sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false);
        return new QuicTlsEngine(sslEngine, true);
    }

    private QuicTlsEngine(SSLEngine engine, boolean isServer) {
        this.engine = engine;
        this.isServer = isServer;

        // Force TLS 1.3 only
        engine.setEnabledProtocols(new String[]{TLS_PROTOCOL});

        // Set QUIC ALPN
        var sslParams = engine.getSSLParameters();
        sslParams.setApplicationProtocols(new String[]{ALPN_H3, ALPN_H3_29});
        engine.setSSLParameters(sslParams);
    }

    /**
     * Begins the TLS handshake.
     *
     * @throws QuicTlsException if the handshake cannot be initiated
     * @since 0.1.0
     */
    public void beginHandshake() {
        try {
            engine.beginHandshake();
            handshakeState = HandshakeState.IN_PROGRESS;
            LOG.debug("TLS 1.3 handshake initiated ({})", isServer ? "server" : "client");
        } catch (SSLException e) {
            handshakeState = HandshakeState.FAILED;
            throw new QuicTlsException("Failed to begin TLS handshake", e);
        }
    }

    /**
     * Produces handshake data to send to the peer as a CRYPTO frame payload.
     *
     * <p>Call this when the SSLEngine's handshake status is
     * {@link SSLEngineResult.HandshakeStatus#NEED_WRAP NEED_WRAP}.
     *
     * @return the handshake data to send, or an empty buffer if nothing to produce
     * @throws QuicTlsException on TLS errors
     * @since 0.1.0
     */
    public ByteBuffer produceHandshakeData() {
        var outBuf = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        var emptyIn = ByteBuffer.allocate(0);
        try {
            var result = engine.wrap(emptyIn, outBuf);
            outBuf.flip();
            updateHandshakeState(result);
            return outBuf;
        } catch (SSLException e) {
            handshakeState = HandshakeState.FAILED;
            throw new QuicTlsException("Error producing handshake data", e);
        }
    }

    /**
     * Consumes handshake data received from the peer (from a CRYPTO frame).
     *
     * <p>Call this when the SSLEngine's handshake status is
     * {@link SSLEngineResult.HandshakeStatus#NEED_UNWRAP NEED_UNWRAP}.
     *
     * @param data the received handshake data
     * @return the unwrapped application data (empty during handshake)
     * @throws QuicTlsException on TLS errors
     * @since 0.1.0
     */
    public ByteBuffer consumeHandshakeData(ByteBuffer data) {
        var appBuf = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        try {
            var result = engine.unwrap(data, appBuf);
            appBuf.flip();
            updateHandshakeState(result);
            return appBuf;
        } catch (SSLException e) {
            handshakeState = HandshakeState.FAILED;
            throw new QuicTlsException("Error consuming handshake data", e);
        }
    }

    /**
     * Runs any delegated tasks the SSLEngine requires (e.g., key generation).
     *
     * <p>Call this when the SSLEngine's handshake status is
     * {@link SSLEngineResult.HandshakeStatus#NEED_TASK NEED_TASK}.
     *
     * @since 0.1.0
     */
    public void runDelegatedTasks() {
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            task.run();
        }
    }

    /**
     * Returns the current handshake status from the underlying SSLEngine.
     *
     * @return the current handshake status
     * @since 0.1.0
     */
    public SSLEngineResult.HandshakeStatus handshakeStatus() {
        return engine.getHandshakeStatus();
    }

    /**
     * Returns the negotiated ALPN protocol after handshake completes.
     *
     * @return the ALPN protocol string, or {@code null} if not yet negotiated
     * @since 0.1.0
     */
    public String negotiatedAlpn() {
        return engine.getApplicationProtocol();
    }

    /**
     * Returns the negotiated TLS protocol version.
     *
     * @return the protocol version string (e.g., "TLSv1.3")
     * @since 0.1.0
     */
    public String negotiatedProtocol() {
        return engine.getSession().getProtocol();
    }

    /**
     * Returns the negotiated cipher suite.
     *
     * @return the cipher suite name
     * @since 0.1.0
     */
    public String cipherSuite() {
        return engine.getSession().getCipherSuite();
    }

    /**
     * Returns the current handshake state.
     *
     * @return the handshake state
     * @since 0.1.0
     */
    public HandshakeState handshakeState() {
        return handshakeState;
    }

    /**
     * Returns the underlying SSLEngine for advanced configuration.
     *
     * @return the SSLEngine
     * @since 0.1.0
     */
    public SSLEngine sslEngine() {
        return engine;
    }

    /**
     * Returns whether this is the server side.
     *
     * @return {@code true} for server, {@code false} for client
     * @since 0.1.0
     */
    public boolean isServer() {
        return isServer;
    }

    /**
     * Closes the TLS engine, sending close_notify.
     *
     * @since 0.1.0
     */
    public void close() {
        engine.closeOutbound();
        try {
            engine.closeInbound();
        } catch (SSLException e) {
            LOG.debug("Expected exception closing inbound TLS: {}", e.getMessage());
        }
    }

    private void updateHandshakeState(SSLEngineResult result) {
        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED
                || result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            if (handshakeState == HandshakeState.IN_PROGRESS) {
                handshakeState = HandshakeState.COMPLETED;
                LOG.debug("TLS 1.3 handshake completed — cipher: {}, ALPN: {}",
                        cipherSuite(), negotiatedAlpn());
            }
        }
    }

    private static SSLContext resolveContext(SSLContext provided) {
        if (provided != null) return provided;
        try {
            var ctx = SSLContext.getInstance(TLS_PROTOCOL);
            ctx.init(null, new TrustManager[]{new TrustAllManager()}, new SecureRandom());
            return ctx;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new QuicTlsException("TLS 1.3 is not available", e);
        }
    }

    /**
     * Trust-all manager for testing and prototyping. Not for production use.
     */
    private static final class TrustAllManager extends X509ExtendedTrustManager {
        @Override public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
        @Override public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
        @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
        @Override public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType, java.net.Socket socket) {}
        @Override public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType, java.net.Socket socket) {}
        @Override public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType, SSLEngine engine) {}
        @Override public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType, SSLEngine engine) {}
    }
}
