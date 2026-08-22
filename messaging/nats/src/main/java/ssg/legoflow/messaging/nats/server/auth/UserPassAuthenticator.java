package ssg.legoflow.messaging.nats.server.auth;

import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Username/password authenticator that validates against registered credentials.
 *
 * @since 0.1.0
 */
public final class UserPassAuthenticator implements Authenticator {

    private final Map<String, String> credentials = new ConcurrentHashMap<>();

    /**
     * Adds a user with the given password.
     *
     * @param user the username
     * @param pass the password
     * @return this authenticator for chaining
     */
    public UserPassAuthenticator addUser(String user, String pass) {
        credentials.put(Objects.requireNonNull(user), Objects.requireNonNull(pass));
        return this;
    }

    /**
     * Removes a user.
     *
     * @param user the username to remove
     * @return this authenticator for chaining
     */
    public UserPassAuthenticator removeUser(String user) {
        credentials.remove(user);
        return this;
    }

    @Override
    public boolean authenticate(ConnectOptions options) {
        if (options.user() == null || options.pass() == null) {
            return false;
        }
        String expected = credentials.get(options.user());
        return expected != null && expected.equals(options.pass());
    }

    /**
     * Returns the number of registered users.
     *
     * @return user count
     */
    public int userCount() {
        return credentials.size();
    }
}
