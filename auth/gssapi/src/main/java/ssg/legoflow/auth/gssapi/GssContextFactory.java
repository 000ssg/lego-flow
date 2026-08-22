package ssg.legoflow.auth.gssapi;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;
/**
 * Factory for creating {@link GssContextWrapper} instances for client-initiated
 * and server-accept GSS-API contexts.
 *
 * <p>Uses the standard {@link GSSManager} to create contexts with the Kerberos V5
 * or SPNEGO mechanism.</p>
 *
 * @since 0.1.0
 */
public final class GssContextFactory {

    private static final Logger LOG = LoggerFactory.getLogger(GssContextFactory.class);

    private GssContextFactory() {
        // utility class
    }

    /**
     * Creates a client-initiated GSS context for authenticating to a target service.
     *
     * @param config          the GSS configuration
     * @param targetPrincipal the target service principal name
     * @return a wrapper around the client GSS context
     * @throws GssException if context creation fails
     * @since 0.1.0
     */
    public static GssContextWrapper createClientContext(GssConfig config, String targetPrincipal)
            throws GssException {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(targetPrincipal, "targetPrincipal must not be null");

        try {
            GSSManager manager = GSSManager.getInstance();
            GSSName serverName = manager.createName(
                    targetPrincipal, GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);

            GSSContext context = manager.createContext(
                    serverName,
                    GssOids.KERBEROS_V5,
                    null, // use default credentials
                    GSSContext.DEFAULT_LIFETIME);

            context.requestMutualAuth(true);
            context.requestInteg(true);
            context.requestConf(true);

            LOG.debug("Created client GSS context for target: {}", targetPrincipal);
            return new GssContextWrapper(context);
        } catch (GSSException e) {
            throw new GssException("Failed to create client context for " + targetPrincipal, e);
        }
    }

    /**
     * Creates a server-accept GSS context for accepting client authentication.
     *
     * @param config the GSS configuration
     * @return a wrapper around the server GSS context
     * @throws GssException if context creation fails
     * @since 0.1.0
     */
    public static GssContextWrapper createServerContext(GssConfig config) throws GssException {
        Objects.requireNonNull(config, "config must not be null");

        try {
            GSSManager manager = GSSManager.getInstance();
            GSSName serviceName = manager.createName(
                    config.servicePrincipal(), GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);

            GSSCredential serverCred = manager.createCredential(
                    serviceName,
                    GSSCredential.DEFAULT_LIFETIME,
                    GssOids.KERBEROS_V5,
                    GSSCredential.ACCEPT_ONLY);

            GSSContext context = manager.createContext(serverCred);

            LOG.debug("Created server GSS context for principal: {}", config.servicePrincipal());
            return new GssContextWrapper(context);
        } catch (GSSException e) {
            throw new GssException("Failed to create server context for " + config.servicePrincipal(), e);
        }
    }

    /**
     * Creates a client-initiated GSS context using the SPNEGO mechanism.
     *
     * @param config          the GSS configuration
     * @param targetPrincipal the target service principal name
     * @return a wrapper around the SPNEGO client GSS context
     * @throws GssException if context creation fails
     * @since 0.1.0
     */
    public static GssContextWrapper createSpnegoClientContext(GssConfig config, String targetPrincipal)
            throws GssException {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(targetPrincipal, "targetPrincipal must not be null");

        try {
            GSSManager manager = GSSManager.getInstance();
            GSSName serverName = manager.createName(
                    targetPrincipal, GssOids.KRB5_PRINCIPAL_NAME);

            GSSContext context = manager.createContext(
                    serverName,
                    GssOids.SPNEGO,
                    null, // use default credentials
                    GSSContext.DEFAULT_LIFETIME);

            context.requestMutualAuth(true);
            context.requestInteg(true);
            context.requestConf(true);

            LOG.debug("Created SPNEGO client GSS context for target: {}", targetPrincipal);
            return new GssContextWrapper(context);
        } catch (GSSException e) {
            throw new GssException("Failed to create SPNEGO client context for " + targetPrincipal, e);
        }
    }
}
