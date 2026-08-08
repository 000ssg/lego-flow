package ssg.legoflow.auth.gssapi;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.MessageProp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Wraps a {@link GSSContext} for simpler token exchange in Kerberos/SPNEGO flows.
 *
 * <p>Provides a simplified API for context establishment (both client and server side),
 * message protection (wrap/unwrap), and integrity verification (MIC).</p>
 *
 * <p>Implements {@link AutoCloseable} to ensure proper disposal of the underlying
 * GSS context.</p>
 *
 * @since 0.1.0
 */
public class GssContextWrapper implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GssContextWrapper.class);
    private static final byte[] EMPTY_TOKEN = new byte[0];

    private final GSSContext context;

    /**
     * Creates a wrapper around the given GSS context.
     *
     * @param context the GSS context to wrap
     * @since 0.1.0
     */
    public GssContextWrapper(GSSContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
    }

    /**
     * Performs a client-side context initiation step.
     *
     * @param inputToken the token received from the server (empty for first call)
     * @return the output token to send to the server
     * @throws GssException if context initiation fails
     * @since 0.1.0
     */
    public byte[] initSecContext(byte[] inputToken) throws GssException {
        Objects.requireNonNull(inputToken, "inputToken must not be null");
        try {
            byte[] token = context.initSecContext(inputToken, 0, inputToken.length);
            return token != null ? token : EMPTY_TOKEN;
        } catch (GSSException e) {
            throw new GssException("Failed to initiate security context", e);
        }
    }

    /**
     * Performs a server-side context acceptance step.
     *
     * @param inputToken the token received from the client
     * @return the output token to send back to the client
     * @throws GssException if context acceptance fails
     * @since 0.1.0
     */
    public byte[] acceptSecContext(byte[] inputToken) throws GssException {
        Objects.requireNonNull(inputToken, "inputToken must not be null");
        try {
            byte[] token = context.acceptSecContext(inputToken, 0, inputToken.length);
            return token != null ? token : EMPTY_TOKEN;
        } catch (GSSException e) {
            throw new GssException("Failed to accept security context", e);
        }
    }

    /**
     * Returns whether the security context has been fully established.
     *
     * @return true if the context is established
     * @since 0.1.0
     */
    public boolean isEstablished() {
        return context.isEstablished();
    }

    /**
     * Returns whether mutual authentication was negotiated.
     *
     * @return true if mutual authentication is enabled
     * @throws GssException if the state cannot be queried
     * @since 0.1.0
     */
    public boolean getMutualAuth() {
        return context.getMutualAuthState();
    }

    /**
     * Returns whether per-message integrity is available.
     *
     * @return true if integrity protection is available
     * @since 0.1.0
     */
    public boolean getIntegrity() {
        return context.getIntegState();
    }

    /**
     * Returns whether per-message confidentiality is available.
     *
     * @return true if confidentiality protection is available
     * @since 0.1.0
     */
    public boolean getConfidentiality() {
        return context.getConfState();
    }

    /**
     * Returns the source principal name.
     *
     * @return the source (initiator) principal name
     * @throws GssException if the name cannot be retrieved
     * @since 0.1.0
     */
    public String getSrcName() throws GssException {
        try {
            return context.getSrcName().toString();
        } catch (GSSException e) {
            throw new GssException("Failed to get source name", e);
        }
    }

    /**
     * Returns the target principal name.
     *
     * @return the target (acceptor) principal name
     * @throws GssException if the name cannot be retrieved
     * @since 0.1.0
     */
    public String getTargName() throws GssException {
        try {
            return context.getTargName().toString();
        } catch (GSSException e) {
            throw new GssException("Failed to get target name", e);
        }
    }

    /**
     * Wraps (encrypts) data for message protection.
     *
     * @param data the data to wrap
     * @return the wrapped data
     * @throws GssException if wrapping fails
     * @since 0.1.0
     */
    public byte[] wrap(byte[] data) throws GssException {
        Objects.requireNonNull(data, "data must not be null");
        try {
            MessageProp prop = new MessageProp(0, true);
            return context.wrap(data, 0, data.length, prop);
        } catch (GSSException e) {
            throw new GssException("Failed to wrap message", e);
        }
    }

    /**
     * Unwraps (decrypts) data from message protection.
     *
     * @param data the wrapped data to unwrap
     * @return the unwrapped data
     * @throws GssException if unwrapping fails
     * @since 0.1.0
     */
    public byte[] unwrap(byte[] data) throws GssException {
        Objects.requireNonNull(data, "data must not be null");
        try {
            MessageProp prop = new MessageProp(0, true);
            return context.unwrap(data, 0, data.length, prop);
        } catch (GSSException e) {
            throw new GssException("Failed to unwrap message", e);
        }
    }

    /**
     * Generates a Message Integrity Code (MIC) for the given data.
     *
     * @param data the data to generate a MIC for
     * @return the MIC bytes
     * @throws GssException if MIC generation fails
     * @since 0.1.0
     */
    public byte[] getMIC(byte[] data) throws GssException {
        Objects.requireNonNull(data, "data must not be null");
        try {
            MessageProp prop = new MessageProp(0, true);
            return context.getMIC(data, 0, data.length, prop);
        } catch (GSSException e) {
            throw new GssException("Failed to generate MIC", e);
        }
    }

    /**
     * Verifies a Message Integrity Code (MIC) for the given data.
     *
     * @param data the data that was protected
     * @param mic  the MIC to verify
     * @return true if the MIC is valid
     * @since 0.1.0
     */
    public boolean verifyMIC(byte[] data, byte[] mic) {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(mic, "mic must not be null");
        try {
            MessageProp prop = new MessageProp(0, true);
            context.verifyMIC(mic, 0, mic.length, data, 0, data.length, prop);
            return true;
        } catch (GSSException e) {
            LOG.debug("MIC verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Disposes of the underlying GSS context and releases resources.
     *
     * @since 0.1.0
     */
    public void dispose() {
        try {
            context.dispose();
        } catch (GSSException e) {
            LOG.debug("Error disposing GSS context: {}", e.getMessage());
        }
    }

    /**
     * Returns the underlying GSS context for advanced usage.
     *
     * @return the wrapped GSSContext
     * @since 0.1.0
     */
    public GSSContext getContext() {
        return context;
    }

    @Override
    public void close() {
        dispose();
    }
}
