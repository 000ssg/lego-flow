package ssg.legoflow.http.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of available authentication schemes. Thread-safe — schemes can be registered
 * and looked up concurrently.
 *
 * @since 0.1.0
 */
public class AuthSchemeRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AuthSchemeRegistry.class);

    private final Map<String, AuthenticationScheme> schemes = new ConcurrentHashMap<>();

    /**
     * Creates an empty registry.
     *
     * @since 0.1.0
     */
    public AuthSchemeRegistry() {
    }

    /**
     * Registers an authentication scheme. The scheme name (case-insensitive) is used as the key.
     *
     * @param scheme the authentication scheme to register
     * @return this registry for chaining
     * @since 0.1.0
     */
    public AuthSchemeRegistry register(AuthenticationScheme scheme) {
        Objects.requireNonNull(scheme, "scheme must not be null");
        String key = scheme.schemeName().toLowerCase(Locale.ROOT);
        schemes.put(key, scheme);
        LOG.debug("Registered authentication scheme: {}", scheme.schemeName());
        return this;
    }

    /**
     * Looks up an authentication scheme by name (case-insensitive).
     *
     * @param schemeName the scheme name (e.g., "Basic", "Bearer", "Digest")
     * @return the scheme, or empty if not registered
     * @since 0.1.0
     */
    public Optional<AuthenticationScheme> get(String schemeName) {
        if (schemeName == null) return Optional.empty();
        return Optional.ofNullable(schemes.get(schemeName.toLowerCase(Locale.ROOT)));
    }

    /**
     * Returns all registered scheme names.
     *
     * @return unmodifiable set of scheme names
     * @since 0.1.0
     */
    public Set<String> schemeNames() {
        return Collections.unmodifiableSet(schemes.keySet());
    }

    /**
     * Returns all registered schemes.
     *
     * @return unmodifiable collection of schemes
     * @since 0.1.0
     */
    public Collection<AuthenticationScheme> schemes() {
        return Collections.unmodifiableCollection(schemes.values());
    }

    /**
     * Removes a scheme by name.
     *
     * @param schemeName the scheme name to remove
     * @return true if the scheme was removed
     * @since 0.1.0
     */
    public boolean remove(String schemeName) {
        if (schemeName == null) return false;
        return schemes.remove(schemeName.toLowerCase(Locale.ROOT)) != null;
    }

    /**
     * Returns the number of registered schemes.
     *
     * @return the scheme count
     * @since 0.1.0
     */
    public int size() {
        return schemes.size();
    }
}
