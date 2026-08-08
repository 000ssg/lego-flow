package ssg.legoflow.messaging.amqp.sasl;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * SASL PLAIN mechanism — username/password authentication.
 *
 * <p>The initial response is encoded as: {@code \0username\0password}
 * (NUL-separated authzid, authcid, passwd, with empty authzid).
 *
 * @since 0.1.0
 */
public final class PlainMechanism implements SaslMechanism {

    private final String username;
    private final String password;

    /**
     * Creates a PLAIN mechanism with the given credentials.
     *
     * @param username the username
     * @param password the password
     */
    public PlainMechanism(String username, String password) {
        this.username = Objects.requireNonNull(username);
        this.password = Objects.requireNonNull(password);
    }

    @Override
    public String name() {
        return "PLAIN";
    }

    @Override
    public byte[] initialResponse() {
        // Format: \0<username>\0<password>
        byte[] userBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] passBytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] response = new byte[1 + userBytes.length + 1 + passBytes.length];
        response[0] = 0; // authzid (empty)
        System.arraycopy(userBytes, 0, response, 1, userBytes.length);
        response[1 + userBytes.length] = 0;
        System.arraycopy(passBytes, 0, response, 2 + userBytes.length, passBytes.length);
        return response;
    }

    @Override
    public byte[] respond(byte[] challenge) {
        // PLAIN has no challenge-response phase
        return new byte[0];
    }

    /** Returns the username. */
    public String username() { return username; }
}
