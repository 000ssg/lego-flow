package ssg.legoflow.database.postgresql.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cleartext password authentication.
 *
 * <p>The client sends the password as plaintext. This is the simplest
 * but least secure authentication method.
 *
 * @since 0.1.0
 */
public final class CleartextAuth implements PgAuthenticator {

    private final Map<String, String> credentials = new ConcurrentHashMap<>();

    /**
     * Creates a new cleartext authenticator.
     */
    public CleartextAuth() {}

    /**
     * Registers a user with a password.
     *
     * @param username the username
     * @param password the password
     * @return this authenticator for chaining
     */
    public CleartextAuth addUser(String username, String password) {
        credentials.put(username, password);
        return this;
    }

    @Override
    public String method() {
        return "cleartext";
    }

    @Override
    public boolean authenticate(String username, String password) {
        String expected = credentials.get(username);
        return expected != null && expected.equals(password);
    }
}
