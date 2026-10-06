package ssg.legoflow.messaging.stomp.core;

/**
 * Configuration for {@link StompBroker}.
 *
 * <p>Supports authentication, ACL checking, message selectors, priority queues,
 * persistence, and queue size limits.</p>
 *
 * @param host the broker bind address
 * @param port the broker port
 * @param supportedVersions protocol versions string (e.g., "1.0,1.1,1.2")
 * @param heartbeatSend broker send heart-beat interval in ms (0 = disabled)
 * @param heartbeatReceive broker receive heart-beat interval in ms (0 = disabled)
 * @param authenticator optional login/passcode validator
 * @param aclChecker optional destination access control checker
 * @param persistenceAdapter optional persistence adapter (null = no persistence)
 * @param defaultMaxQueueSize per-destination queue size limit (0 = unlimited)
 * @since 0.2.0
 */
public record StompBrokerConfig(
        String host,
        int port,
        String supportedVersions,
        int heartbeatSend,
        int heartbeatReceive,
        StompAuthenticator authenticator,
        StompAclChecker aclChecker,
        StompPersistenceAdapter persistenceAdapter,
        int defaultMaxQueueSize
) {

    /** Optional login/passcode validator. */
    @FunctionalInterface
    public interface StompAuthenticator {
        /**
         * Authenticates a login/passcode pair.
         *
         * @param login    the login header
         * @param passcode the passcode header (may be null)
         * @return {@code true} if the credentials are valid
         */
        boolean authenticate(String login, String passcode);
    }

    /** Optional destination-level access control. */
    @FunctionalInterface
    public interface StompAclChecker {
        /**
         * Checks whether a user may perform an action on a destination.
         *
         * @param login       the authenticated login (may be null)
         * @param destination the destination name
         * @param action      "send" or "subscribe"
         * @return {@code true} if the action is allowed
         */
        boolean check(String login, String destination, String action);
    }

    /**
     * Default configuration: listen on 61613, STOMP 1.0/1.1/1.2, 10s heart-beat, no auth/ACL.
     */
    public static StompBrokerConfig defaults() {
        return new StompBrokerConfig("0.0.0.0", 61613, "1.0,1.1,1.2",
                10000, 10000, null, null, null, 0);
    }

    /** Returns a config with the given authenticator. */
    public StompBrokerConfig authenticator(StompAuthenticator a) {
        return new StompBrokerConfig(host, port, supportedVersions, heartbeatSend, heartbeatReceive,
                a, aclChecker, persistenceAdapter, defaultMaxQueueSize);
    }

    /** Returns a config with the given ACL checker. */
    public StompBrokerConfig aclChecker(StompAclChecker c) {
        return new StompBrokerConfig(host, port, supportedVersions, heartbeatSend, heartbeatReceive,
                authenticator, c, persistenceAdapter, defaultMaxQueueSize);
    }

    /** Returns a config with the given persistence adapter. */
    public StompBrokerConfig persistenceAdapter(StompPersistenceAdapter p) {
        return new StompBrokerConfig(host, port, supportedVersions, heartbeatSend, heartbeatReceive,
                authenticator, aclChecker, p, defaultMaxQueueSize);
    }

    /** Returns a config with the given max queue size. */
    public StompBrokerConfig defaultMaxQueueSize(int size) {
        return new StompBrokerConfig(host, port, supportedVersions, heartbeatSend, heartbeatReceive,
                authenticator, aclChecker, persistenceAdapter, size);
    }
}
