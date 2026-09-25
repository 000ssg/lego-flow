package ssg.legoflow.messaging.mqtt.broker;

/**
 * Pluggable topic-level ACL checker for MQTT broker.
 *
 * <p>Checks whether an authenticated user (identified by username) is allowed
 * to perform a specific action (publish/subscribe/unsubscribe) on a topic.
 * The broker calls this on each SUBSCRIBE, PUBLISH, and UNSUBSCRIBE operation.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface MqttAclChecker {

    /**
     * Checks whether the user is allowed to perform the specified action on the topic.
     *
     * @param username the authenticated username (may be {@code null} for anonymous connections)
     * @param topic    the MQTT topic
     * @param action   the action: "pub", "sub", or "unsub"
     * @return {@code true} if allowed, {@code false} to deny
     */
    boolean check(String username, String topic, String action);
}
