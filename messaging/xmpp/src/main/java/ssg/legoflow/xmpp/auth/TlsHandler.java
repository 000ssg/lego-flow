package ssg.legoflow.xmpp.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
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
 * @since 0.1.0
 */
public class TlsHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TlsHandler.class);

    /** The XMPP STARTTLS namespace. */
    public static final String NAMESPACE = "urn:ietf:params:xml:ns:xmpp-tls";

    /**
     * TLS handshake state.
     *
     * @since 0.1.0
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
        this(hostname, port, false);
    }

    /**
     * Creates a TLS handler for the specified host and port.
     *
     * @param hostname     the target hostname
     * @param port         the target port
     * @param trustAllCerts if true, accept all server certificates (self-signed, etc.)
     * @throws RuntimeException if SSLContext initialization fails
     */
    public TlsHandler(String hostname, int port, boolean trustAllCerts) {
        this.hostname = Objects.requireNonNull(hostname, "hostname must not be null");
        this.port = port;
        this.state = TlsState.NOT_STARTED;

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManager[] trustManagers;

            if (trustAllCerts) {
                trustManagers = new TrustManager[]{
                    new X509TrustManager() {
                        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
                };
                LOG.info("TlsHandler created with trustAllCerts=true (self-signed certs accepted)");
            } else {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                tmf.init((KeyStore) null); // use default trust store
                trustManagers = tmf.getTrustManagers();
            }

            sslContext.init(null, trustManagers, null);

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
            throw new IllegalStateException("Unexpected proceed in state: " + state);
        }
        state = TlsState.HANDSHAKING;
        LOG.info("Starting TLS handshake for {}:{}", hostname, port);

        runDelegatedTasks();

        var hsStatus = sslEngine.getHandshakeStatus();
        if (hsStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            return drainHandshakeOutput();
        }
        return ByteBuffer.allocate(0);
    }

    /**
     * Drives the TLS handshake with the provided server data.
     *
     * @param serverData the data received from the server during handshake
     * @return handshake output to send to the server
     * @throws IOException if the handshake fails
     */
    public ByteBuffer processHandshake(ByteBuffer serverData) throws IOException {
        if (state != TlsState.HANDSHAKING) {
            throw new IllegalStateException("Not handshaking in state: " + state);
        }

        if (serverData != null && serverData.hasRemaining()) {
            appInBuffer.clear();
            SSLEngineResult result = sslEngine.unwrap(serverData, appInBuffer);
            LOG.debug("Handshake unwrap: status={}, hsStatus={}",
                    result.getStatus(), result.getHandshakeStatus());
        }

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
