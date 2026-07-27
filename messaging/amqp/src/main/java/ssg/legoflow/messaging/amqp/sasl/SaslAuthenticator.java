package ssg.legoflow.messaging.amqp.sasl;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side SASL authenticator for AMQP connections.
 *
 * <p>Supports ANONYMOUS, PLAIN, and EXTERNAL mechanisms. For PLAIN, maintains
 * an in-memory credential store. Can be configured with a custom authentication
 * function for integration with external identity providers.
 *
 * @since 1.0.0
 */
public final class SaslAuthenticator {

    /**
     * Authentication result.
     */
    public enum Result {
        /** Authentication succeeded. */
        OK,
        /** Authentication failed (bad credentials). */
        AUTH,
        /** A system error occurred. */
        SYS,
        /** Temporary system error. */
        SYS_TEMP,
        /** Permanent system error. */
        SYS_PERM
    }

    private final Set<String> mechanisms = new LinkedHashSet<>();
    private final Map<String, String> credentials = new ConcurrentHashMap<>();
    private boolean allowAnonymous;
    private AuthFunction customAuth;

    /**
     * Custom authentication function.
     */
    @FunctionalInterface
    public interface AuthFunction {
        /**
         * Authenticates a user.
         *
         * @param mechanism the SASL mechanism name
         * @param username  the username (null for ANONYMOUS)
         * @param password  the password (null for ANONYMOUS/EXTERNAL)
         * @return the authentication result
         */
        Result authenticate(String mechanism, String username, String password);
    }

    /** Creates an authenticator allowing anonymous access. */
    public SaslAuthenticator() {
        this.allowAnonymous = true;
        this.mechanisms.add("ANONYMOUS");
    }

    /**
     * Enables PLAIN authentication and adds credentials.
     *
     * @param username the username
     * @param password the password
     * @return this authenticator
     */
    public SaslAuthenticator addCredentials(String username, String password) {
        credentials.put(username, password);
        mechanisms.add("PLAIN");
        return this;
    }

    /**
     * Enables or disables anonymous access.
     *
     * @param allow whether to allow anonymous access
     * @return this authenticator
     */
    public SaslAuthenticator allowAnonymous(boolean allow) {
        this.allowAnonymous = allow;
        if (allow) {
            mechanisms.add("ANONYMOUS");
        } else {
            mechanisms.remove("ANONYMOUS");
        }
        return this;
    }

    /**
     * Enables EXTERNAL mechanism.
     *
     * @return this authenticator
     */
    public SaslAuthenticator enableExternal() {
        mechanisms.add("EXTERNAL");
        return this;
    }

    /**
     * Sets a custom authentication function.
     *
     * @param authFunction the custom function
     * @return this authenticator
     */
    public SaslAuthenticator customAuth(AuthFunction authFunction) {
        this.customAuth = authFunction;
        return this;
    }

    /**
     * Returns the list of supported SASL mechanisms.
     *
     * @return the mechanism names
     */
    public List<String> mechanisms() {
        return List.copyOf(mechanisms);
    }

    /**
     * Authenticates using the given mechanism and initial response.
     *
     * @param mechanism       the mechanism name
     * @param initialResponse the initial response bytes
     * @return the authentication result
     */
    public Result authenticate(String mechanism, byte[] initialResponse) {
        if (!mechanisms.contains(mechanism)) {
            return Result.AUTH;
        }

        return switch (mechanism) {
            case "ANONYMOUS" -> allowAnonymous ? Result.OK : Result.AUTH;
            case "PLAIN" -> authenticatePlain(initialResponse);
            case "EXTERNAL" -> Result.OK; // Assumes external validation (e.g. TLS)
            default -> {
                if (customAuth != null) {
                    yield customAuth.authenticate(mechanism, null, null);
                }
                yield Result.AUTH;
            }
        };
    }

    private Result authenticatePlain(byte[] response) {
        // Format: \0<username>\0<password>
        if (response == null || response.length < 3) {
            return Result.AUTH;
        }

        // Find the boundaries
        int firstNull = -1;
        int secondNull = -1;
        for (int i = 0; i < response.length; i++) {
            if (response[i] == 0) {
                if (firstNull < 0) {
                    firstNull = i;
                } else {
                    secondNull = i;
                    break;
                }
            }
        }

        if (firstNull < 0 || secondNull < 0) {
            return Result.AUTH;
        }

        String username = new String(response, firstNull + 1, secondNull - firstNull - 1, StandardCharsets.UTF_8);
        String password = new String(response, secondNull + 1, response.length - secondNull - 1, StandardCharsets.UTF_8);

        if (customAuth != null) {
            return customAuth.authenticate("PLAIN", username, password);
        }

        String stored = credentials.get(username);
        if (stored != null && stored.equals(password)) {
            return Result.OK;
        }
        return Result.AUTH;
    }
}
