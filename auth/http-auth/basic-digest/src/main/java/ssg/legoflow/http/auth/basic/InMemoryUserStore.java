package ssg.legoflow.http.auth.basic;

import ssg.legoflow.http.auth.AuthContext;
import ssg.legoflow.http.auth.AuthPrincipal;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory user store for testing and demos. Stores usernames, passwords, and roles
 * in a thread-safe map.
 *
 * @since 1.0.0
 */
public class InMemoryUserStore implements BasicUserStore, AuthContext.UserStore {

    private final Map<String, UserEntry> users = new ConcurrentHashMap<>();

    private record UserEntry(String password, Set<String> roles) {}

    /**
     * Creates an empty user store.
     *
     * @since 1.0.0
     */
    public InMemoryUserStore() {
    }

    /**
     * Adds a user with password and roles.
     *
     * @param username the username
     * @param password the password
     * @param roles    the roles
     * @return this store for chaining
     * @since 1.0.0
     */
    public InMemoryUserStore addUser(String username, String password, Set<String> roles) {
        users.put(username, new UserEntry(password, roles != null ? Set.copyOf(roles) : Set.of()));
        return this;
    }

    /**
     * Adds a user with password only (no roles).
     *
     * @param username the username
     * @param password the password
     * @return this store for chaining
     * @since 1.0.0
     */
    public InMemoryUserStore addUser(String username, String password) {
        return addUser(username, password, Set.of());
    }

    /**
     * Removes a user.
     *
     * @param username the username to remove
     * @return this store for chaining
     * @since 1.0.0
     */
    public InMemoryUserStore removeUser(String username) {
        users.remove(username);
        return this;
    }

    @Override
    public Optional<AuthPrincipal> authenticate(String username, String password) {
        var entry = users.get(username);
        if (entry == null || !entry.password().equals(password)) {
            return Optional.empty();
        }
        return Optional.of(new AuthPrincipal(username, entry.roles(), Map.of()));
    }

    @Override
    public boolean userExists(String username) {
        return users.containsKey(username);
    }

    @Override
    public Optional<AuthPrincipal> findByUsername(String username) {
        var entry = users.get(username);
        if (entry == null) return Optional.empty();
        return Optional.of(new AuthPrincipal(username, entry.roles(), Map.of()));
    }

    @Override
    public Optional<String> getPassword(String username) {
        var entry = users.get(username);
        return entry != null ? Optional.of(entry.password()) : Optional.empty();
    }

    /**
     * Returns the number of users in the store.
     *
     * @return the user count
     * @since 1.0.0
     */
    public int size() {
        return users.size();
    }
}
