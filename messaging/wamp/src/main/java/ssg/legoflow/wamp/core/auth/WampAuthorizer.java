package ssg.legoflow.wamp.core.auth;

import ssg.legoflow.wamp.core.WampSession;

/**
 * Authorization interface for controlling access to WAMP operations.
 * Implementations define role-based permissions for pub/sub topics and RPC procedures.
 *
 * <p>The default implementation ({@link #ALLOW_ALL}) permits all operations.
 * Custom implementations can restrict access based on session attributes
 * (authid, authrole, realm, etc.).</p>
 *
 * @since 1.0.0
 */
public interface WampAuthorizer {

    /**
     * Default authorizer that allows all operations.
     */
    WampAuthorizer ALLOW_ALL = new WampAuthorizer() {
        @Override public boolean canPublish(WampSession session, String topic) { return true; }
        @Override public boolean canSubscribe(WampSession session, String topic) { return true; }
        @Override public boolean canCall(WampSession session, String procedure) { return true; }
        @Override public boolean canRegister(WampSession session, String procedure) { return true; }
    };

    /**
     * Checks whether the given session is authorized to publish to the topic.
     *
     * @param session the client session
     * @param topic   the topic URI
     * @return {@code true} if publishing is allowed
     */
    boolean canPublish(WampSession session, String topic);

    /**
     * Checks whether the given session is authorized to subscribe to the topic.
     *
     * @param session the client session
     * @param topic   the topic URI
     * @return {@code true} if subscribing is allowed
     */
    boolean canSubscribe(WampSession session, String topic);

    /**
     * Checks whether the given session is authorized to call the procedure.
     *
     * @param session   the client session
     * @param procedure the procedure URI
     * @return {@code true} if calling is allowed
     */
    boolean canCall(WampSession session, String procedure);

    /**
     * Checks whether the given session is authorized to register the procedure.
     *
     * @param session   the client session
     * @param procedure the procedure URI
     * @return {@code true} if registering is allowed
     */
    boolean canRegister(WampSession session, String procedure);
}
