package ssg.legoflow.messaging.amqp091.sasl;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * SASL PLAIN mechanism for AMQP 0-9-1 (RFC 4616).
 *
 * <p>Format: \0 username \0 password
 *
 * @since 0.2.0
 */
public class PlainMechanism implements SaslMechanism {

    private final String username;
    private final String password;

    public PlainMechanism(String username, String password) {
        this.username = Objects.requireNonNull(username, "username required");
        this.password = Objects.requireNonNull(password, "password required");
    }

    @Override
    public String name() {
        return "PLAIN";
    }

    @Override
    public byte[] initialResponse() {
        // \0 username \0 password
        String response = "\0" + username + "\0" + password;
        return response.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] respond(byte[] challenge) {
        // No challenge in PLAIN — return empty
        return new byte[0];
    }
}
