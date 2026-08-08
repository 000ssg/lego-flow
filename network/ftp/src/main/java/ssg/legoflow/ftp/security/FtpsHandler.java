package ssg.legoflow.ftp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.Objects;

/**
 * Handles FTPS (FTP over TLS) negotiation as defined in RFC 4217.
 *
 * <p>Manages the AUTH TLS handshake, PBSZ/PROT commands, and TLS socket upgrades
 * for both control and data connections.
 *
 * @since 0.1.0
 */
public final class FtpsHandler {

    private static final Logger LOG = LoggerFactory.getLogger(FtpsHandler.class);

    private final FtpsConfig config;
    private final SSLContext sslContext;
    private volatile boolean controlEncrypted = false;
    private volatile boolean dataProtected = false;
    private volatile int protectionBufferSize = 0;

    /**
     * Creates an FTPS handler with the given configuration.
     *
     * @param config the FTPS configuration
     * @throws GeneralSecurityException if SSL context creation fails
     * @throws IOException              if keystore/truststore cannot be loaded
     */
    public FtpsHandler(FtpsConfig config) throws GeneralSecurityException, IOException {
        this.config = Objects.requireNonNull(config, "config");
        this.sslContext = config.createSslContext();
    }

    /**
     * Upgrades a plain socket to a TLS socket (for AUTH TLS on the control connection).
     *
     * @param socket the plain socket to upgrade
     * @param host   the remote host name (for SNI)
     * @param port   the remote port
     * @return the upgraded SSL socket
     * @throws IOException if the TLS handshake fails
     */
    public SSLSocket upgradeToTls(Socket socket, String host, int port) throws IOException {
        SSLSocketFactory factory = sslContext.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, host, port, true);
        sslSocket.setEnabledProtocols(config.protocols());
        if (config.cipherSuites() != null) {
            sslSocket.setEnabledCipherSuites(config.cipherSuites());
        }
        sslSocket.setUseClientMode(true);
        sslSocket.startHandshake();
        controlEncrypted = true;
        LOG.info("TLS handshake complete: {}", sslSocket.getSession().getProtocol());
        return sslSocket;
    }

    /**
     * Upgrades a plain socket to a TLS socket on the server side.
     *
     * @param socket the plain socket to upgrade
     * @return the upgraded SSL socket
     * @throws IOException if the TLS handshake fails
     */
    public SSLSocket upgradeToTlsServer(Socket socket) throws IOException {
        SSLSocketFactory factory = sslContext.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket) factory.createSocket(
                socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
        sslSocket.setEnabledProtocols(config.protocols());
        if (config.cipherSuites() != null) {
            sslSocket.setEnabledCipherSuites(config.cipherSuites());
        }
        sslSocket.setUseClientMode(false);
        if (config.clientAuth()) {
            sslSocket.setNeedClientAuth(true);
        }
        sslSocket.startHandshake();
        controlEncrypted = true;
        LOG.info("Server TLS handshake complete: {}", sslSocket.getSession().getProtocol());
        return sslSocket;
    }

    /**
     * Wraps a data connection socket with TLS when PROT P is active.
     *
     * @param socket the plain data socket
     * @param host   the remote host
     * @param port   the remote port
     * @return the TLS-wrapped socket if data protection is enabled, otherwise the original socket
     * @throws IOException if the TLS handshake fails
     */
    public Socket wrapDataConnection(Socket socket, String host, int port) throws IOException {
        if (!dataProtected) {
            return socket;
        }
        SSLSocketFactory factory = sslContext.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, host, port, true);
        sslSocket.setEnabledProtocols(config.protocols());
        sslSocket.setUseClientMode(true);
        sslSocket.startHandshake();
        LOG.debug("Data connection TLS handshake complete");
        return sslSocket;
    }

    /**
     * Wraps a data connection socket with TLS on the server side.
     *
     * @param socket the plain data socket
     * @return the TLS-wrapped socket if data protection is enabled, otherwise the original socket
     * @throws IOException if the TLS handshake fails
     */
    public Socket wrapDataConnectionServer(Socket socket) throws IOException {
        if (!dataProtected) {
            return socket;
        }
        SSLSocketFactory factory = sslContext.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket) factory.createSocket(
                socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
        sslSocket.setEnabledProtocols(config.protocols());
        sslSocket.setUseClientMode(false);
        sslSocket.startHandshake();
        LOG.debug("Server data connection TLS handshake complete");
        return sslSocket;
    }

    /**
     * Handles the PBSZ command. For TLS, the only valid value is 0.
     *
     * @param size the protection buffer size
     * @return {@code true} if the value is accepted
     */
    public boolean handlePbsz(int size) {
        // RFC 4217: For TLS/SSL the only valid value is 0
        this.protectionBufferSize = 0;
        LOG.debug("PBSZ set to 0 (TLS mode)");
        return true;
    }

    /**
     * Handles the PROT command.
     *
     * @param level the protection level: "C" (clear) or "P" (private)
     * @return {@code true} if the level is accepted
     */
    public boolean handleProt(String level) {
        if (level == null) return false;
        return switch (level.toUpperCase()) {
            case "C" -> {
                dataProtected = false;
                LOG.debug("Data channel protection: CLEAR");
                yield true;
            }
            case "P" -> {
                dataProtected = true;
                LOG.debug("Data channel protection: PRIVATE");
                yield true;
            }
            default -> {
                LOG.warn("Unsupported PROT level: {}", level);
                yield false;
            }
        };
    }

    /**
     * Returns {@code true} if the control connection is encrypted.
     *
     * @return true if TLS is active on the control channel
     */
    public boolean isControlEncrypted() {
        return controlEncrypted;
    }

    /**
     * Returns {@code true} if data connections should be protected with TLS.
     *
     * @return true if PROT P has been set
     */
    public boolean isDataProtected() {
        return dataProtected;
    }

    /**
     * Returns the protection buffer size (always 0 for TLS).
     *
     * @return the PBSZ value
     */
    public int getProtectionBufferSize() {
        return protectionBufferSize;
    }

    /**
     * Returns the FTPS configuration.
     *
     * @return the config
     */
    public FtpsConfig config() {
        return config;
    }

    /**
     * Returns the underlying SSL context.
     *
     * @return the SSL context
     */
    public SSLContext sslContext() {
        return sslContext;
    }

    /**
     * Resets encryption state (for REIN or CCC commands).
     */
    public void reset() {
        controlEncrypted = false;
        dataProtected = false;
        protectionBufferSize = 0;
    }
}
