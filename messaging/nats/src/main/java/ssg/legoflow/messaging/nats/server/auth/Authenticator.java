package ssg.legoflow.messaging.nats.server.auth;

import ssg.legoflow.messaging.nats.protocol.ConnectOptions;

/**
 * Authentication interface for NATS server.
 *
 * <p>Implementations validate client connection options against
 * configured credentials. The server calls {@link #authenticate}
 * during the CONNECT handshake.
 *
 * @since 1.0.0
 */
public interface Authenticator {

    /**
     * Authenticates a client based on its connect options.
     *
     * @param options the client's connect options
     * @return true if authentication succeeds
     */
    boolean authenticate(ConnectOptions options);
}
