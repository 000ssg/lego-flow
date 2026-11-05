package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.acl.model.AclDomain;
import ssg.legoflow.acl.model.AclRule.AccessLevel;
import ssg.legoflow.acl.model.User;

/**
 * MQTT ACL checker backed by an {@link AclDomain}.
 *
 * <p>Maps MQTT topics to ACL URIs and actions to access levels:
 * <ul>
 *   <li>"pub" → {@link AclRule.AccessLevel#WRITE}</li>
 *   <li>"sub" → {@link AclRule.AccessLevel#READ}</li>
 *   <li>"unsub" → {@link AclRule.AccessLevel#READ}</li>
 * </ul>
 *
 * <p>MQTT topic wildcards (+, #) are expanded against the domain's rules
 * before checking {@link AclDomain#isAllowed(User, String, AclRule.AccessLevel)}.
 *
 * <p>FOR TEST PURPOSE ONLY — bridges acl module into MQTT broker tests.
 *
 * @since 0.1.0
 */
class MqttAclDomainChecker implements MqttAclChecker {

    private final AclDomain domain;

    /**
     * Creates an ACL checker backed by the given ACL domain.
     *
     * @param domain the ACL domain containing users and rules
     */
    MqttAclDomainChecker(AclDomain domain) {
        this.domain = domain;
    }

    @Override
    public boolean check(String username, String topic, String action) {
        if (username == null || topic == null) {
            return false;
        }
        var user = domain.user(username).orElse(null);
        if (user == null) {
            return false;
        }
        // Map MQTT action to ACL access level
        var accessLevel = switch (action) {
            case "pub" -> AccessLevel.WRITE;
            case "sub", "unsub" -> AccessLevel.READ;
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };
        // Convert MQTT topic to ACL URI format: "sensors/temp" → "/sensors/temp"
        String uri = topic.startsWith("/") ? topic : "/" + topic;
        return domain.isAllowed(user, uri, accessLevel);
    }
}
