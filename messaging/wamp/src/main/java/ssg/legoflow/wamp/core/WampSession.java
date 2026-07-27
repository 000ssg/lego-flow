package ssg.legoflow.wamp.core;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of a single WAMP session, tracking subscriptions and registrations.
 *
 * @since 1.0.0
 */
public class WampSession {

    private long sessionId;
    private String realm;
    private volatile boolean established;
    private String authId;
    private String authRole;
    private String authMethod;
    private final Map<Long, String> subscriptions = new ConcurrentHashMap<>();
    private final Map<Long, String> registrations = new ConcurrentHashMap<>();

    /**
     * Establishes this session with the given identifier and realm.
     *
     * @param sessionId the session identifier assigned by the router
     * @param realm     the realm this session belongs to
     */
    public void establish(long sessionId, String realm) {
        this.sessionId = sessionId;
        this.realm = realm;
        this.established = true;
    }

    /**
     * Closes this session, clearing all subscriptions and registrations.
     */
    public void close() {
        this.established = false;
        this.subscriptions.clear();
        this.registrations.clear();
    }

    /**
     * Returns whether this session is currently established.
     *
     * @return {@code true} if established
     */
    public boolean isEstablished() {
        return established;
    }

    /**
     * Returns the session identifier.
     *
     * @return the session ID
     */
    public long getSessionId() {
        return sessionId;
    }

    /**
     * Returns the realm this session belongs to.
     *
     * @return the realm name
     */
    public String getRealm() {
        return realm;
    }

    /**
     * Records a subscription.
     *
     * @param subscriptionId the subscription identifier
     * @param topic          the subscribed topic URI
     */
    public void subscribe(long subscriptionId, String topic) {
        subscriptions.put(subscriptionId, topic);
    }

    /**
     * Removes a subscription.
     *
     * @param subscriptionId the subscription identifier to remove
     */
    public void unsubscribe(long subscriptionId) {
        subscriptions.remove(subscriptionId);
    }

    /**
     * Records a procedure registration.
     *
     * @param registrationId the registration identifier
     * @param procedure      the registered procedure URI
     */
    public void register(long registrationId, String procedure) {
        registrations.put(registrationId, procedure);
    }

    /**
     * Removes a procedure registration.
     *
     * @param registrationId the registration identifier to remove
     */
    public void unregister(long registrationId) {
        registrations.remove(registrationId);
    }

    /**
     * Returns an unmodifiable view of current subscriptions.
     *
     * @return map of subscription ID to topic URI
     */
    public Map<Long, String> getSubscriptions() {
        return Collections.unmodifiableMap(subscriptions);
    }

    /**
     * Returns an unmodifiable view of current registrations.
     *
     * @return map of registration ID to procedure URI
     */
    public Map<Long, String> getRegistrations() {
        return Collections.unmodifiableMap(registrations);
    }

    /**
     * Returns the authentication identity.
     *
     * @return the auth ID, or {@code null} if not authenticated
     * @since 1.0.0
     */
    public String getAuthId() {
        return authId;
    }

    /**
     * Sets the authentication identity.
     *
     * @param authId the auth ID
     * @since 1.0.0
     */
    public void setAuthId(String authId) {
        this.authId = authId;
    }

    /**
     * Returns the authentication role.
     *
     * @return the auth role, or {@code null} if not authenticated
     * @since 1.0.0
     */
    public String getAuthRole() {
        return authRole;
    }

    /**
     * Sets the authentication role.
     *
     * @param authRole the auth role
     * @since 1.0.0
     */
    public void setAuthRole(String authRole) {
        this.authRole = authRole;
    }

    /**
     * Returns the authentication method used.
     *
     * @return the auth method, or {@code null} if not authenticated
     * @since 1.0.0
     */
    public String getAuthMethod() {
        return authMethod;
    }

    /**
     * Sets the authentication method.
     *
     * @param authMethod the auth method
     * @since 1.0.0
     */
    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }
}
