package ssg.legoflow.mqtt.broker;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link MqttAuthenticator}.
 *
 * <p>Stores username/password pairs in a thread-safe map.
 * Suitable for testing and small deployments. For production use,
 * implement a custom {@link MqttAuthenticator} backed by an external
 * credential store.
 *
 * @since 1.0.0
 */
public final class InMemoryAuthenticator implements MqttAuthenticator {

    private final Map<String, String> credentials = new ConcurrentHashMap<>();

    /**
     * Creates an empty authenticator with no registered users.
     */
    public InMemoryAuthenticator() {
    }

    /**
     * Creates an authenticator with pre-registered users.
     *
     * @param credentials a map of username to password
     */
    public InMemoryAuthenticator(Map<String, String> credentials) {
        this.credentials.putAll(Objects.requireNonNull(credentials));
    }

    /**
     * Registers a user with the given credentials.
     *
     * @param username the username
     * @param password the password
     * @return this authenticator for chaining
     */
    public InMemoryAuthenticator addUser(String username, String password) {
        credentials.put(Objects.requireNonNull(username), Objects.requireNonNull(password));
        return this;
    }

    /**
     * Removes a user.
     *
     * @param username the username to remove
     * @return this authenticator for chaining
     */
    public InMemoryAuthenticator removeUser(String username) {
        credentials.remove(username);
        return this;
    }

    /**
     * Returns the number of registered users.
     *
     * @return the user count
     */
    public int userCount() {
        return credentials.size();
    }

    @Override
    public boolean authenticate(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        String stored = credentials.get(username);
        return stored != null && stored.equals(password);
    }
}
