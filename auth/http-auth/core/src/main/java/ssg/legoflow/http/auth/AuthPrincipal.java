package ssg.legoflow.http.auth;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents an authenticated user principal with identity, roles, and arbitrary attributes.
 *
 * @since 0.1.0
 */
public class AuthPrincipal {

    private final String name;
    private final Set<String> roles;
    private final Map<String, Object> attributes;

    /**
     * Creates a new principal.
     *
     * @param name       the principal name (username, email, etc.)
     * @param roles      the set of roles granted to this principal
     * @param attributes additional attributes associated with this principal
     * @since 0.1.0
     */
    public AuthPrincipal(String name, Set<String> roles, Map<String, Object> attributes) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.roles = roles != null ? Set.copyOf(roles) : Set.of();
        this.attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
    }

    /**
     * Creates a simple principal with a name only.
     *
     * @param name the principal name
     * @return the principal
     * @since 0.1.0
     */
    public static AuthPrincipal of(String name) {
        return new AuthPrincipal(name, Set.of(), Map.of());
    }

    /**
     * Creates a principal with name and roles.
     *
     * @param name  the principal name
     * @param roles the roles
     * @return the principal
     * @since 0.1.0
     */
    public static AuthPrincipal of(String name, Set<String> roles) {
        return new AuthPrincipal(name, roles, Map.of());
    }

    /**
     * Returns the principal name.
     *
     * @return the name
     * @since 0.1.0
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the roles granted to this principal.
     *
     * @return unmodifiable set of roles
     * @since 0.1.0
     */
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * Returns the attributes associated with this principal.
     *
     * @return unmodifiable map of attributes
     * @since 0.1.0
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Checks if this principal has the specified role.
     *
     * @param role the role to check
     * @return true if the principal has the role
     * @since 0.1.0
     */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /**
     * Returns a specific attribute value.
     *
     * @param key the attribute key
     * @param <T> the expected value type
     * @return the attribute value, or null if not present
     * @since 0.1.0
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    @Override
    public String toString() {
        return "AuthPrincipal{name='" + name + "', roles=" + roles + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthPrincipal that)) return false;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
