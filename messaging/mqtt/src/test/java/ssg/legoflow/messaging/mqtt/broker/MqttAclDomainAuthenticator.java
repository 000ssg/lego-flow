package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.acl.model.AclDomain;
import ssg.legoflow.acl.model.User;

/**
 * MQTT authenticator backed by an {@link AclDomain}.
 *
 * <p>Looks up the username in the domain and delegates password verification
 * to {@link User#checkPassword(String)}. Rejects unknown users and null credentials.
 *
 * <p>FOR TEST PURPOSE ONLY — bridges acl module into MQTT broker tests.
 *
 * @since 0.1.0
 */
class MqttAclDomainAuthenticator implements MqttAuthenticator {

    private final AclDomain domain;

    /**
     * Creates an authenticator backed by the given ACL domain.
     *
     * @param domain the ACL domain containing users
     */
    MqttAclDomainAuthenticator(AclDomain domain) {
        this.domain = domain;
    }

    @Override
    public boolean authenticate(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        return domain.user(username)
                .map(u -> u.checkPassword(password))
                .orElse(false);
    }
}
