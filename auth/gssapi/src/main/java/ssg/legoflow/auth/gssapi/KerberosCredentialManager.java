package ssg.legoflow.auth.gssapi;

import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
/**
 * Manages Kerberos credential lifecycle — login, credential acquisition, validation,
 * and renewal using JAAS {@link LoginContext} with the Krb5LoginModule.
 *
 * @since 0.1.0
 */
public final class KerberosCredentialManager {

    private static final Logger LOG = LoggerFactory.getLogger(KerberosCredentialManager.class);
    private static final String KRB5_LOGIN_MODULE = "com.sun.security.auth.module.Krb5LoginModule";

    private KerberosCredentialManager() {
        // utility class
    }

    /**
     * Logs in using a keytab file and returns the authenticated Subject.
     *
     * @param principal  the Kerberos principal (e.g., "user@EXAMPLE.COM")
     * @param keytabPath the path to the keytab file
     * @return the authenticated Subject
     * @throws GssException if login fails
     * @since 0.1.0
     */
    public static Subject loginWithKeytab(String principal, String keytabPath) throws GssException {
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(keytabPath, "keytabPath must not be null");

        Map<String, String> options = new HashMap<>();
        options.put("useKeyTab", "true");
        options.put("keyTab", keytabPath);
        options.put("principal", principal);
        options.put("storeKey", "true");
        options.put("doNotPrompt", "true");
        options.put("isInitiator", "true");

        return login(principal, options, null);
    }

    /**
     * Logs in using a password and returns the authenticated Subject.
     *
     * @param principal the Kerberos principal (e.g., "user@EXAMPLE.COM")
     * @param password  the password
     * @return the authenticated Subject
     * @throws GssException if login fails
     * @since 0.1.0
     */
    public static Subject loginWithPassword(String principal, char[] password) throws GssException {
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(password, "password must not be null");

        Map<String, String> options = new HashMap<>();
        options.put("useKeyTab", "false");
        options.put("storeKey", "true");
        options.put("doNotPrompt", "false");
        options.put("isInitiator", "true");

        CallbackHandler handler = callbacks -> {
            for (Callback cb : callbacks) {
                if (cb instanceof NameCallback nc) {
                    nc.setName(principal);
                } else if (cb instanceof PasswordCallback pc) {
                    pc.setPassword(password);
                }
            }
        };

        return login(principal, options, handler);
    }

    /**
     * Obtains a GSS credential for the service described in the configuration.
     *
     * @param subject the authenticated Subject
     * @param config  the GSS configuration containing the service principal
     * @return the GSS credential for the service
     * @throws GssException if credential acquisition fails
     * @since 0.1.0
     */
    public static GSSCredential getServiceCredential(Subject subject, GssConfig config) throws GssException {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(config, "config must not be null");

        try {
            GSSManager manager = GSSManager.getInstance();
            GSSName serviceName = manager.createName(
                    config.servicePrincipal(), GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);

            return manager.createCredential(
                    serviceName,
                    GSSCredential.DEFAULT_LIFETIME,
                    GssOids.KERBEROS_V5,
                    GSSCredential.ACCEPT_ONLY);
        } catch (GSSException e) {
            throw new GssException("Failed to get service credential for " + config.servicePrincipal(), e);
        }
    }

    /**
     * Checks whether a GSS credential is still valid (not expired).
     *
     * @param cred the credential to check
     * @return true if the credential is valid
     * @since 0.1.0
     */
    public static boolean isCredentialValid(GSSCredential cred) {
        if (cred == null) {
            return false;
        }
        try {
            int remaining = cred.getRemainingLifetime();
            return remaining > 0 || remaining == GSSCredential.INDEFINITE_LIFETIME;
        } catch (GSSException e) {
            LOG.debug("Error checking credential validity: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Attempts to renew a GSS credential by re-acquiring it.
     *
     * <p>Note: True credential renewal depends on the underlying mechanism.
     * This method creates a new credential with the same name and mechanism.</p>
     *
     * @param cred the credential to renew
     * @return the renewed credential
     * @throws GssException if renewal fails
     * @since 0.1.0
     */
    public static GSSCredential renewCredential(GSSCredential cred) throws GssException {
        Objects.requireNonNull(cred, "cred must not be null");
        try {
            GSSManager manager = GSSManager.getInstance();
            GSSName name = cred.getName();
            int usage = cred.getUsage();

            return manager.createCredential(
                    name,
                    GSSCredential.DEFAULT_LIFETIME,
                    GssOids.KERBEROS_V5,
                    usage);
        } catch (GSSException e) {
            throw new GssException("Failed to renew credential", e);
        }
    }

    private static Subject login(String principal, Map<String, String> options, CallbackHandler handler)
            throws GssException {
        Configuration config = new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                return new AppConfigurationEntry[]{
                        new AppConfigurationEntry(
                                KRB5_LOGIN_MODULE,
                                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                                options)
                };
            }
        };

        try {
            LoginContext lc = new LoginContext("LegoFlowGss", new Subject(), handler, config);
            lc.login();
            LOG.debug("Successfully logged in as: {}", principal);
            return lc.getSubject();
        } catch (LoginException e) {
            throw new GssException("Kerberos login failed for " + principal, e);
        }
    }
}
