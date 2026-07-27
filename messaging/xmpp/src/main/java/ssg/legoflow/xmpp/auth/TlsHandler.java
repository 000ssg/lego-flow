package ssg.legoflow.xmpp.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Handles TLS/STARTTLS negotiation for XMPP streams (RFC 6120 section 5).
 *
 * <p>After the XMPP stream advertises the STARTTLS feature and the client sends
 * {@code <starttls>}, this handler wraps the underlying byte stream with TLS
 * using the JDK's {@link SSLEngine}. The handler manages the TLS handshake,
 * wrapping outbound plaintext and unwrapping inbound ciphertext.
 *
 * <p>Usage:
 * <ol>
 *   <li>Create a TlsHandler for the target host</li>
 *   <li>Send the STARTTLS proceed element</li>
 *   <li>Call {@link #beginHandshake()} to initiate the TLS handshake</li>
 *   <li>Use {@link #wrap(ByteBuffer)} and {@link #unwrap(ByteBuffer)} for data</li>
 * </ol>
 *
 * @since 1.0.0
 */
public class TlsHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TlsHandler.class);

    /** The XMPP STARTTLS namespace. */
    public static final String NAMESPACE = "urn:ietf:params:xml:ns:xmpp-tls";

    /**
     * TLS handshake state.
     *
     * @since 1.0.0
     */
    public enum TlsState {
        /** TLS has not been initiated. */
        NOT_STARTED,
        /** STARTTLS has been requested. */
        STARTTLS_REQUESTED,
        /** Server has sent proceed. */
        PROCEED_RECEIVED,
        /** TLS handshake is in progress. */
        HANDSHAKING,
        /** TLS is fully established. */
        ESTABLISHED,
        /** TLS negotiation failed. */
        FAILED
    }

    private final SSLEngine sslEngine;
    private final String hostname;
    private final int port;
    private TlsState state;

    private ByteBuffer netOutBuffer;
    private ByteBuffer netInBuffer;
    private ByteBuffer appOutBuffer;
    private ByteBuffer appInBuffer;

    /**
     * Creates a TLS handler for the specified host and port.
     *
     * @param hostname the target hostname
     * @param port     the target port
     * @throws RuntimeException if SSLContext initialization fails
     */
    public TlsHandler(String hostname, int port) {
        this.hostname = Objects.requireNonNull(hostname, "hostname must not be null");
        this.port = port;
        this.state = TlsState.NOT_STARTED;

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null); // use default trust store
            sslContext.init(null, tmf.getTrustManagers(), null);

            this.sslEngine = sslContext.createSSLEngine(hostname, port);
            sslEngine.setUseClientMode(true);

            // Allocate buffers based on SSLEngine session
            var session = sslEngine.getSession();
            netOutBuffer = ByteBuffer.allocate(session.getPacketBufferSize());
            netInBuffer = ByteBuffer.allocate(session.getPacketBufferSize());
            appOutBuffer = ByteBuffer.allocate(session.getApplicationBufferSize());
            appInBuffer = ByteBuffer.allocate(session.getApplicationBufferSize());

            LOG.debug("TlsHandler created for {}:{}", hostname, port);
        } catch (NoSuchAlgorithmException | java.security.KeyManagementException |
                 java.security.KeyStoreException e) {
            state = TlsState.FAILED;
            throw new RuntimeException("Failed to initialize TLS: " + e.getMessage(), e);
        }
    }

    /**
     * Generates the STARTTLS request XML element.
     *
     * @return the {@code <starttls>} XML element
     */
    public String generateStartTlsXml() {
        state = TlsState.STARTTLS_REQUESTED;
        return "<starttls xmlns=\"" + NAMESPACE + "\"/>";
    }

    /**
     * Handles the server's {@code <proceed/>} response.
     *
     * <p>After this, call {@link #beginHandshake()} to start the TLS handshake.
     */
    public void handleProceed() {
        if (state != TlsState.STARTTLS_REQUESTED) {
            throw new IllegalStateException("Unexpected proceed in state: " + state);
        }
        state = TlsState.PROCEED_RECEIVED;
        LOG.info("STARTTLS proceed received from server");
    }

    /**
     * Begins the TLS handshake.
     *
     * <p>Initiates the SSLEngine handshake. After calling this, use
     * {@link #processHandshake(ByteBuffer)} to drive the handshake to completion.
     *
     * @return the initial handshake data to send to the server (may be empty)
     * @throws IOException if the handshake initiation fails
     */
    public ByteBuffer beginHandshake() throws IOException {
        if (state != TlsState.PROCEED_RECEIVED && state != TlsState.NOT_STARTED) {
            throw new IllegalStateException("Cannot begin handshake in state: " + state);
        }
        state = TlsState.HANDSHAKING;
        sslEngine.beginHandshake();
        LOG.info("TLS handshake initiated with {}:{}", hostname, port);

        return drainHandshakeOutput();
    }

    /**
     * Processes incoming handshake data and produces outgoing handshake data.
     *
     * @param incomingData the incoming network data from the peer
     * @return outgoing network data to send to the peer (may be empty)
     * @throws IOException if a handshake error occurs
     */
    public ByteBuffer processHandshake(ByteBuffer incomingData) throws IOException {
        if (state != TlsState.HANDSHAKING) {
            throw new IllegalStateException("Not in handshaking state: " + state);
        }

        if (incomingData != null && incomingData.hasRemaining()) {
            netInBuffer.put(incomingData);
            netInBuffer.flip();

            SSLEngineResult result;
            do {
                result = sslEngine.unwrap(netInBuffer, appInBuffer);
                LOG.debug("Handshake unwrap: status={}, hsStatus={}",
                        result.getStatus(), result.getHandshakeStatus());
            } while (result.getStatus() == SSLEngineResult.Status.OK &&
                    result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_UNWRAP);

            netInBuffer.compact();
        }

        // Run delegated tasks
        runDelegatedTasks();

        // Check if handshake is complete
        var hsStatus = sslEngine.getHandshakeStatus();
        if (hsStatus == SSLEngineResult.HandshakeStatus.FINISHED ||
                hsStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            state = TlsState.ESTABLISHED;
            LOG.info("TLS handshake completed: protocol={}, cipher={}",
                    sslEngine.getSession().getProtocol(),
                    sslEngine.getSession().getCipherSuite());
        }

        return drainHandshakeOutput();
    }

    /**
     * Wraps plaintext application data into TLS records.
     *
     * @param plaintext the plaintext data to wrap
     * @return the TLS-wrapped ciphertext
     * @throws IOException if wrapping fails
     */
    public ByteBuffer wrap(ByteBuffer plaintext) throws IOException {
        if (state != TlsState.ESTABLISHED) {
            throw new IllegalStateException("TLS not established, cannot wrap data");
        }

        netOutBuffer.clear();
        SSLEngineResult result = sslEngine.wrap(plaintext, netOutBuffer);

        if (result.getStatus() != SSLEngineResult.Status.OK) {
            throw new IOException("TLS wrap failed: " + result.getStatus());
        }

        netOutBuffer.flip();
        var output = ByteBuffer.allocate(netOutBuffer.remaining());
        output.put(netOutBuffer);
        output.flip();
        return output;
    }

    /**
     * Unwraps TLS records into plaintext application data.
     *
     * @param ciphertext the TLS ciphertext to unwrap
     * @return the plaintext application data
     * @throws IOException if unwrapping fails
     */
    public ByteBuffer unwrap(ByteBuffer ciphertext) throws IOException {
        if (state != TlsState.ESTABLISHED) {
            throw new IllegalStateException("TLS not established, cannot unwrap data");
        }

        appInBuffer.clear();
        SSLEngineResult result = sslEngine.unwrap(ciphertext, appInBuffer);

        if (result.getStatus() != SSLEngineResult.Status.OK &&
                result.getStatus() != SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            throw new IOException("TLS unwrap failed: " + result.getStatus());
        }

        appInBuffer.flip();
        var output = ByteBuffer.allocate(appInBuffer.remaining());
        output.put(appInBuffer);
        output.flip();
        return output;
    }

    /**
     * Returns the current TLS state.
     *
     * @return the TLS state
     */
    public TlsState getState() {
        return state;
    }

    /**
     * Returns whether TLS is fully established.
     *
     * @return true if TLS is established
     */
    public boolean isEstablished() {
        return state == TlsState.ESTABLISHED;
    }

    /**
     * Returns the negotiated TLS protocol version.
     *
     * @return the protocol string, or null if not established
     */
    public String getProtocol() {
        if (state != TlsState.ESTABLISHED) {
            return null;
        }
        return sslEngine.getSession().getProtocol();
    }

    /**
     * Returns the negotiated cipher suite.
     *
     * @return the cipher suite string, or null if not established
     */
    public String getCipherSuite() {
        if (state != TlsState.ESTABLISHED) {
            return null;
        }
        return sslEngine.getSession().getCipherSuite();
    }

    /**
     * Returns the underlying SSLEngine.
     *
     * @return the SSLEngine
     */
    public SSLEngine getSslEngine() {
        return sslEngine;
    }

    /**
     * Returns the target hostname.
     *
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Returns the target port.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Closes the TLS session.
     */
    public void close() {
        sslEngine.closeOutbound();
        LOG.info("TLS session closed");
    }

    private ByteBuffer drainHandshakeOutput() throws IOException {
        var hsStatus = sslEngine.getHandshakeStatus();
        if (hsStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            netOutBuffer.clear();
            appOutBuffer.clear();
            appOutBuffer.flip();
            SSLEngineResult result = sslEngine.wrap(appOutBuffer, netOutBuffer);
            LOG.debug("Handshake wrap: status={}, hsStatus={}",
                    result.getStatus(), result.getHandshakeStatus());

            runDelegatedTasks();

            netOutBuffer.flip();
            var output = ByteBuffer.allocate(netOutBuffer.remaining());
            output.put(netOutBuffer);
            output.flip();
            return output;
        }
        return ByteBuffer.allocate(0);
    }

    private void runDelegatedTasks() {
        Runnable task;
        while ((task = sslEngine.getDelegatedTask()) != null) {
            LOG.debug("Running SSLEngine delegated task");
            task.run();
        }
    }
}
