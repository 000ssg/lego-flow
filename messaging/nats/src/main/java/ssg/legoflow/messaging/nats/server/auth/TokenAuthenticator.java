package ssg.legoflow.messaging.nats.server.auth;

import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import java.util.Objects;
/**
 * Token-based authenticator that validates against a configured token.
 *
 * @since 0.1.0
 */
public final class TokenAuthenticator implements Authenticator {

    private final String token;

    /**
     * Creates a token authenticator.
     *
     * @param token the required token
     */
    public TokenAuthenticator(String token) {
        this.token = Objects.requireNonNull(token, "token must not be null");
    }

    @Override
    public boolean authenticate(ConnectOptions options) {
        return token.equals(options.authToken());
    }
}
