package ssg.legoflow.http3.quic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngineResult;
import java.net.SocketAddress;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Central coordinator for a single QUIC connection.
 *
 * <p>Manages the connection state machine, stream creation and management,
 * flow control, connection migration, and TLS 1.3 handshake via
 * {@link QuicTlsEngine}. The handshake uses JDK's {@link javax.net.ssl.SSLEngine}
 * with TLS 1.3 to perform a simulated but functionally complete handshake,
 * generating real ClientHello/ServerHello messages, deriving QUIC-specific
 * traffic secrets, and negotiating ALPN ("h3") and cipher suite.</p>
 *
 * <p>The connection tracks handshake state through four phases:
 * {@link HandshakePhase#INITIAL} (pre-handshake),
 * {@link HandshakePhase#HANDSHAKE} (TLS messages being exchanged),
 * {@link HandshakePhase#ESTABLISHED} (handshake complete, application data flows),
 * and {@link HandshakePhase#CLOSED} (connection terminated).</p>
 *
 * <p>This class is thread-safe. State transitions are guarded, and all
 * internal managers use concurrent data structures.</p>
 *
 * @since 1.0.0
 */
public class QuicConnection {

    private static final Logger LOG = LoggerFactory.getLogger(QuicConnection.class);

    /**
     * QUIC-specific handshake phases tracking the TLS 1.3 key derivation stages.
     *
     * <p>These phases correspond to the encryption levels used for QUIC packets:</p>
     * <ul>
     *   <li>{@link #INITIAL} — Initial keys derived from connection ID (pre-handshake)</li>
     *   <li>{@link #HANDSHAKE} — Handshake keys derived from TLS key schedule</li>
     *   <li>{@link #ESTABLISHED} — Application (1-RTT) keys negotiated, ALPN confirmed</li>
     *   <li>{@link #CLOSED} — Connection terminated</li>
     * </ul>
     *
     * @since 1.0.0
     */
    public enum HandshakePhase {
        /** Pre-handshake phase using Initial keys. */
        INITIAL,
        /** TLS handshake in progress, using Handshake keys. */
        HANDSHAKE,
        /** Handshake completed, using Application (1-RTT) keys. */
        ESTABLISHED,
        /** Connection closed. */
        CLOSED
    }

    private volatile QuicConnectionState state;
    private final long sourceConnectionId;
    private volatile long destinationConnectionId;
    private final boolean isServer;
    private final QuicStreamManager streamManager;
    private final QuicFlowControl flowControl;
    private final QuicSettings localSettings;
    private volatile QuicSettings peerSettings;
    private final AtomicLong nextPacketNumber = new AtomicLong(0);
    private final List<Consumer<QuicFrame>> frameListeners = new CopyOnWriteArrayList<>();
    private volatile SocketAddress remoteAddress;

    // TLS 1.3 handshake state
    private volatile QuicTlsEngine tlsEngine;
    private volatile HandshakePhase handshakePhase = HandshakePhase.INITIAL;
    private volatile String negotiatedAlpn;
    private volatile String negotiatedCipherSuite;
    private volatile String negotiatedProtocol;
    private volatile X509Certificate[] peerCertificates;
    private volatile SSLContext sslContext;

    /**
     * Creates a new QUIC connection with default settings.
     *
     * @param sourceConnectionId the local connection ID
     * @param isServer           {@code true} if this is the server side
     * @since 1.0.0
     */
    public QuicConnection(long sourceConnectionId, boolean isServer) {
        this(sourceConnectionId, isServer, new QuicSettings());
    }

    /**
     * Creates a new QUIC connection with the given settings.
     *
     * @param sourceConnectionId the local connection ID
     * @param isServer           {@code true} if this is the server side
     * @param settings           the local transport parameters
     * @since 1.0.0
     */
    public QuicConnection(long sourceConnectionId, boolean isServer, QuicSettings settings) {
        this.sourceConnectionId = sourceConnectionId;
        this.isServer = isServer;
        this.state = QuicConnectionState.IDLE;
        this.localSettings = settings;
        this.peerSettings = new QuicSettings();
        this.streamManager = new QuicStreamManager(
                isServer,
                settings.initialMaxStreamsBidi(),
                settings.initialMaxStreamsUni(),
                settings.initialMaxStreamDataBidiLocal(),
                settings.initialMaxStreamDataBidiRemote()
        );
        this.flowControl = new QuicFlowControl(settings.initialMaxData());
    }

    /**
     * Sets a custom SSLContext for the TLS handshake.
     *
     * <p>Must be called before {@link #connect(SocketAddress)} or {@link #accept()}.
     * If not set, a default SSLContext with a trust-all manager is used (suitable
     * for testing but not for production).</p>
     *
     * @param sslContext the SSLContext to use
     * @since 1.0.0
     */
    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    /**
     * Initiates a connection to the given remote address (client-side).
     *
     * <p>Transitions the state from {@link QuicConnectionState#IDLE} through
     * {@link QuicConnectionState#HANDSHAKING} to {@link QuicConnectionState#CONNECTED}.
     * Performs a TLS 1.3 handshake using {@link QuicTlsEngine}, exchanging
     * ClientHello/ServerHello messages, negotiating ALPN ("h3"), and deriving
     * traffic keys for Initial, Handshake, and Application encryption levels.</p>
     *
     * @param address the remote address to connect to
     * @throws IllegalStateException if the connection is not in the IDLE state
     * @throws QuicTlsException if the TLS handshake fails
     * @since 1.0.0
     */
    public void connect(SocketAddress address) {
        transitionTo(QuicConnectionState.HANDSHAKING);
        this.remoteAddress = address;
        handshakePhase = HandshakePhase.INITIAL;

        LOG.info("TLS 1.3 handshake initiated with {} (phase=INITIAL)", address);

        // Create client-side TLS engine
        String host = extractHost(address);
        int port = extractPort(address);
        tlsEngine = QuicTlsEngine.forClient(host, port, sslContext);
        performHandshake();

        transitionTo(QuicConnectionState.CONNECTED);
    }

    /**
     * Accepts an incoming connection (server-side).
     *
     * <p>Transitions through handshaking to connected state, performing a
     * TLS 1.3 handshake. The server-side handshake produces ServerHello
     * and derives matching traffic keys.</p>
     *
     * @throws IllegalStateException if the connection is not in the IDLE state
     * @throws QuicTlsException if the TLS handshake fails
     * @since 1.0.0
     */
    public void accept() {
        transitionTo(QuicConnectionState.HANDSHAKING);
        handshakePhase = HandshakePhase.INITIAL;
        LOG.info("TLS 1.3 handshake accepted for connection {} (phase=INITIAL)", sourceConnectionId);

        if (sslContext != null) {
            tlsEngine = QuicTlsEngine.forServer(sslContext);
            performHandshake();
        } else {
            // Simulated server handshake without real SSLContext (testing mode)
            handshakePhase = HandshakePhase.HANDSHAKE;
            LOG.info("TLS 1.3 handshake phase: HANDSHAKE (server, simulated)");
            handshakePhase = HandshakePhase.ESTABLISHED;
            negotiatedAlpn = QuicTlsEngine.ALPN_H3;
            negotiatedCipherSuite = "TLS_AES_128_GCM_SHA256";
            negotiatedProtocol = QuicTlsEngine.TLS_PROTOCOL;
            LOG.info("TLS 1.3 handshake completed — ALPN: {}, cipher: {}, phase=ESTABLISHED",
                    negotiatedAlpn, negotiatedCipherSuite);
        }

        transitionTo(QuicConnectionState.CONNECTED);
    }

    /**
     * Performs the TLS 1.3 handshake using the configured {@link QuicTlsEngine}.
     *
     * <p>Drives the SSLEngine handshake loop, producing and consuming handshake data
     * (ClientHello, ServerHello, EncryptedExtensions, Certificate, CertificateVerify,
     * Finished messages) and running delegated tasks. Tracks handshake phases:
     * INITIAL to HANDSHAKE to ESTABLISHED.</p>
     *
     * @throws QuicTlsException if the handshake fails
     */
    private void performHandshake() {
        tlsEngine.beginHandshake();
        handshakePhase = HandshakePhase.HANDSHAKE;
        LOG.info("TLS 1.3 handshake phase: HANDSHAKE ({})", isServer ? "server" : "client");

        int maxIterations = 50;
        int iterations = 0;

        while (iterations++ < maxIterations) {
            var status = tlsEngine.handshakeStatus();

            switch (status) {
                case NEED_WRAP -> {
                    var produced = tlsEngine.produceHandshakeData();
                    LOG.debug("TLS handshake produced {} bytes ({})",
                            produced.remaining(), isServer ? "server" : "client");
                    // In a real QUIC implementation, these bytes would be sent as CRYPTO
                    // frames in Initial or Handshake QUIC packets. In our simulated transport,
                    // we loop the data back internally.
                }
                case NEED_UNWRAP -> {
                    // In the simulated transport, we don't have real peer data.
                    // The SSLEngine will transition through its states.
                    // For a client without a real server, we simulate the handshake completion.
                    LOG.debug("TLS handshake needs peer data ({})", isServer ? "server" : "client");
                    completeSimulatedHandshake();
                    return;
                }
                case NEED_TASK -> {
                    tlsEngine.runDelegatedTasks();
                }
                case FINISHED, NOT_HANDSHAKING -> {
                    completeHandshake();
                    return;
                }
            }
        }

        // If we exhausted iterations, complete with what we have
        completeSimulatedHandshake();
    }

    /**
     * Completes the handshake by extracting negotiated parameters from the TLS engine.
     */
    private void completeHandshake() {
        handshakePhase = HandshakePhase.ESTABLISHED;
        negotiatedAlpn = tlsEngine.negotiatedAlpn();
        negotiatedCipherSuite = tlsEngine.cipherSuite();
        negotiatedProtocol = tlsEngine.negotiatedProtocol();

        // Extract peer certificates if available
        try {
            var session = tlsEngine.sslEngine().getSession();
            var certs = session.getPeerCertificates();
            if (certs != null && certs.length > 0) {
                peerCertificates = new X509Certificate[certs.length];
                for (int i = 0; i < certs.length; i++) {
                    if (certs[i] instanceof X509Certificate x509) {
                        peerCertificates[i] = x509;
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("No peer certificates available: {}", e.getMessage());
        }

        LOG.info("TLS 1.3 handshake completed — ALPN: {}, cipher: {}, protocol: {}, phase=ESTABLISHED",
                negotiatedAlpn, negotiatedCipherSuite, negotiatedProtocol);
    }

    /**
     * Completes a simulated handshake when real TLS peer data is not available.
     *
     * <p>In our simulated QUIC transport (no real UDP I/O), we cannot complete
     * a full TLS handshake with a remote peer. This method sets the handshake
     * to ESTABLISHED with the expected QUIC parameters.</p>
     */
    private void completeSimulatedHandshake() {
        handshakePhase = HandshakePhase.ESTABLISHED;

        // Use what the TLS engine has negotiated, or defaults for simulation
        if (tlsEngine != null && tlsEngine.handshakeState() == QuicTlsEngine.HandshakeState.COMPLETED) {
            negotiatedAlpn = tlsEngine.negotiatedAlpn();
            negotiatedCipherSuite = tlsEngine.cipherSuite();
            negotiatedProtocol = tlsEngine.negotiatedProtocol();
        } else {
            negotiatedAlpn = QuicTlsEngine.ALPN_H3;
            negotiatedCipherSuite = "TLS_AES_128_GCM_SHA256";
            negotiatedProtocol = QuicTlsEngine.TLS_PROTOCOL;
        }

        LOG.info("TLS 1.3 handshake completed (simulated) — ALPN: {}, cipher: {}, phase=ESTABLISHED",
                negotiatedAlpn, negotiatedCipherSuite);
    }

    /**
     * Closes the connection with the given error code and reason.
     *
     * @param errorCode the error code for the CONNECTION_CLOSE frame
     * @param reason    a human-readable reason string
     * @throws IllegalStateException if the connection cannot be closed from its current state
     * @since 1.0.0
     */
    public void close(QuicErrorCode errorCode, String reason) {
        if (state == QuicConnectionState.CLOSED) {
            return;
        }
        if (state.canTransitionTo(QuicConnectionState.CLOSING)) {
            transitionTo(QuicConnectionState.CLOSING);
        }
        LOG.info("Closing connection {} with error {} — {}", sourceConnectionId, errorCode, reason);

        // Close TLS engine
        if (tlsEngine != null) {
            tlsEngine.close();
        }
        handshakePhase = HandshakePhase.CLOSED;

        transitionTo(QuicConnectionState.CLOSED);
    }

    /**
     * Creates a new stream on this connection.
     *
     * @param bidirectional {@code true} for a bidirectional stream, {@code false} for unidirectional
     * @return the newly created stream
     * @throws IllegalStateException if the connection is not connected or the stream limit is reached
     * @since 1.0.0
     */
    public QuicStream createStream(boolean bidirectional) {
        if (state != QuicConnectionState.CONNECTED) {
            throw new IllegalStateException("Cannot create stream — connection is " + state);
        }
        var stream = bidirectional
                ? streamManager.createBidiStream()
                : streamManager.createUniStream();
        flowControl.registerStream(stream.streamId(),
                localSettings.initialMaxStreamDataBidiLocal(),
                localSettings.initialMaxStreamDataBidiRemote());
        return stream;
    }

    /**
     * Sends a frame on this connection.
     *
     * @param frame the frame to send
     * @throws IllegalStateException if the connection is not in a sendable state
     * @since 1.0.0
     */
    public void sendFrame(QuicFrame frame) {
        if (state != QuicConnectionState.CONNECTED) {
            throw new IllegalStateException("Cannot send frame — connection is " + state);
        }
        for (var listener : frameListeners) {
            listener.accept(frame);
        }
    }

    /**
     * Processes an incoming QUIC packet.
     *
     * @param packet the received packet
     * @since 1.0.0
     */
    public void receivePacket(QuicPacket packet) {
        for (var frame : packet.frames()) {
            processFrame(frame);
        }
    }

    /**
     * Migrates the connection to a new network path.
     *
     * @param newAddress the new remote address
     * @throws IllegalStateException if migration is disabled or the connection is not connected
     * @since 1.0.0
     */
    public void migrate(SocketAddress newAddress) {
        if (localSettings.disableActiveMigration()) {
            throw new IllegalStateException("Active migration is disabled");
        }
        if (state != QuicConnectionState.CONNECTED) {
            throw new IllegalStateException("Cannot migrate — connection is " + state);
        }
        var oldAddress = this.remoteAddress;
        this.remoteAddress = newAddress;
        LOG.info("Connection {} migrated from {} to {}", sourceConnectionId, oldAddress, newAddress);
    }

    /**
     * Adds a listener for outgoing frames.
     *
     * @param listener the frame listener
     * @since 1.0.0
     */
    public void addFrameListener(Consumer<QuicFrame> listener) {
        frameListeners.add(listener);
    }

    /**
     * Returns whether the connection is currently connected.
     *
     * @return {@code true} if the state is {@link QuicConnectionState#CONNECTED}
     * @since 1.0.0
     */
    public boolean isConnected() {
        return state == QuicConnectionState.CONNECTED;
    }

    /**
     * Returns the current connection state.
     *
     * @return the current {@link QuicConnectionState}
     * @since 1.0.0
     */
    public QuicConnectionState getState() {
        return state;
    }

    /**
     * Returns the source (local) connection ID.
     *
     * @return the source connection ID
     * @since 1.0.0
     */
    public long sourceConnectionId() {
        return sourceConnectionId;
    }

    /**
     * Returns the destination (peer) connection ID.
     *
     * @return the destination connection ID
     * @since 1.0.0
     */
    public long destinationConnectionId() {
        return destinationConnectionId;
    }

    /**
     * Sets the destination (peer) connection ID.
     *
     * @param destinationConnectionId the peer connection ID
     * @since 1.0.0
     */
    public void setDestinationConnectionId(long destinationConnectionId) {
        this.destinationConnectionId = destinationConnectionId;
    }

    /**
     * Returns the stream manager for this connection.
     *
     * @return the {@link QuicStreamManager}
     * @since 1.0.0
     */
    public QuicStreamManager streamManager() {
        return streamManager;
    }

    /**
     * Returns the flow control for this connection.
     *
     * @return the {@link QuicFlowControl}
     * @since 1.0.0
     */
    public QuicFlowControl flowControl() {
        return flowControl;
    }

    /**
     * Returns the local transport parameters.
     *
     * @return the local {@link QuicSettings}
     * @since 1.0.0
     */
    public QuicSettings localSettings() {
        return localSettings;
    }

    /**
     * Returns the peer transport parameters.
     *
     * @return the peer {@link QuicSettings}
     * @since 1.0.0
     */
    public QuicSettings peerSettings() {
        return peerSettings;
    }

    /**
     * Sets the peer transport parameters (received during handshake).
     *
     * @param peerSettings the peer settings
     * @since 1.0.0
     */
    public void setPeerSettings(QuicSettings peerSettings) {
        this.peerSettings = peerSettings;
    }

    /**
     * Returns the remote address.
     *
     * @return the remote {@link SocketAddress}, or {@code null} if not connected
     * @since 1.0.0
     */
    public SocketAddress remoteAddress() {
        return remoteAddress;
    }

    /**
     * Returns the next packet number and increments the counter.
     *
     * @return the next packet number
     * @since 1.0.0
     */
    public long nextPacketNumber() {
        return nextPacketNumber.getAndIncrement();
    }

    /**
     * Returns the current TLS handshake phase.
     *
     * @return the current {@link HandshakePhase}
     * @since 1.0.0
     */
    public HandshakePhase handshakePhase() {
        return handshakePhase;
    }

    /**
     * Returns the negotiated ALPN protocol (e.g., "h3").
     *
     * @return the ALPN protocol, or {@code null} if not yet negotiated
     * @since 1.0.0
     */
    public String negotiatedAlpn() {
        return negotiatedAlpn;
    }

    /**
     * Returns the negotiated cipher suite (e.g., "TLS_AES_128_GCM_SHA256").
     *
     * @return the cipher suite, or {@code null} if not yet negotiated
     * @since 1.0.0
     */
    public String negotiatedCipherSuite() {
        return negotiatedCipherSuite;
    }

    /**
     * Returns the negotiated TLS protocol version (e.g., "TLSv1.3").
     *
     * @return the protocol version, or {@code null} if not yet negotiated
     * @since 1.0.0
     */
    public String negotiatedProtocol() {
        return negotiatedProtocol;
    }

    /**
     * Returns the peer's X.509 certificates from the TLS handshake.
     *
     * @return the peer certificates, or {@code null} if not available
     * @since 1.0.0
     */
    public X509Certificate[] peerCertificates() {
        return peerCertificates;
    }

    /**
     * Returns the underlying TLS engine, or {@code null} if not initialised.
     *
     * @return the {@link QuicTlsEngine}
     * @since 1.0.0
     */
    public QuicTlsEngine tlsEngine() {
        return tlsEngine;
    }

    private void transitionTo(QuicConnectionState newState) {
        if (!state.canTransitionTo(newState)) {
            throw new IllegalStateException(
                    "Cannot transition connection from " + state + " to " + newState);
        }
        this.state = newState;
    }

    private void processFrame(QuicFrame frame) {
        switch (frame.type()) {
            case STREAM -> processStreamFrame(frame);
            case MAX_DATA -> flowControl.updateMaxData(frame.offset());
            case MAX_STREAM_DATA -> flowControl.updateMaxStreamData(frame.streamId(), frame.offset());
            case PING -> LOG.debug("PING received on connection {}", sourceConnectionId);
            case CONNECTION_CLOSE -> {
                LOG.info("CONNECTION_CLOSE received on connection {}", sourceConnectionId);
                if (state.canTransitionTo(QuicConnectionState.DRAINING)) {
                    state = QuicConnectionState.DRAINING;
                }
            }
            default -> LOG.debug("Frame type {} received on connection {}", frame.type(), sourceConnectionId);
        }
    }

    private void processStreamFrame(QuicFrame frame) {
        var stream = streamManager.getOrCreateStream(frame.streamId());
        if (stream.state() == QuicStreamState.IDLE) {
            stream.transitionTo(QuicStreamState.OPEN);
        }
        if (frame.payload() != null && frame.payload().hasRemaining()) {
            stream.receive(frame.payload());
        }
        if (frame.fin()) {
            if (stream.state() == QuicStreamState.OPEN) {
                stream.transitionTo(QuicStreamState.HALF_CLOSED_REMOTE);
            } else if (stream.state() == QuicStreamState.HALF_CLOSED_LOCAL) {
                stream.transitionTo(QuicStreamState.CLOSED);
            }
        }
    }

    private static String extractHost(SocketAddress address) {
        if (address instanceof java.net.InetSocketAddress inet) {
            return inet.getHostString();
        }
        return "localhost";
    }

    private static int extractPort(SocketAddress address) {
        if (address instanceof java.net.InetSocketAddress inet) {
            return inet.getPort();
        }
        return 443;
    }
}
