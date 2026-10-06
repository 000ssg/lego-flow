package ssg.legoflow.acl.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Named ACL rule: associates a resource (URI) with access control for specific roles.
 *
 * <p>An ACL defines:
 * <ul>
 *   <li>{@code name} — unique identifier within the domain</li>
 *   <li>{@code uri} — the resource path/pattern this rule applies to</li>
 *   <li>{@code description} — optional human-readable description</li>
 *   <li>{@code control} — ALLOW or DENY</li>
 *   <li>{@code accessLevel} — the access operation (READ, WRITE, DELETE, EXECUTE, LIST, etc.)</li>
 *   <li>{@code roles} — which roles this rule applies to</li>
 * </ul>
 */
public final class AclRule {
    private final AclDomain domain;
    private final String name;
    private final String uri;
    private final String description;
    private final Control control;
    private final AccessLevel accessLevel;
    private final CopyOnWriteArraySet<Role> roles = new CopyOnWriteArraySet<>();

    public enum Control { ALLOW, DENY }
    public enum AccessLevel { LIST, READ, WRITE, DELETE, EXECUTE, ALL }

    AclRule(AclDomain domain, String name, String uri, String description,
            Control control, AccessLevel accessLevel) {
        this.domain = Objects.requireNonNull(domain);
        this.name = Objects.requireNonNull(name);
        this.uri = Objects.requireNonNull(uri);
        this.description = description;
        this.control = Objects.requireNonNull(control);
        this.accessLevel = Objects.requireNonNull(accessLevel);
    }

    public String name() { return name; }
    public String uri() { return uri; }
    public String description() { return description; }
    public Control control() { return control; }
    public AccessLevel accessLevel() { return accessLevel; }
    public AclDomain domain() { return domain; }

    public Collection<Role> roles() {
        return Collections.unmodifiableCollection(roles);
    }

    public AclRule addRole(Role role) {
        roles.add(Objects.requireNonNull(role));
        return this;
    }

    public AclRule removeRole(Role role) {
        roles.remove(role);
        return this;
    }

    /**
     * Check if the given user is allowed to perform the specified access on this ACL's resource.
     * For ALLOW rules: returns true if user has a matching role and the access level is covered.
     * For DENY rules: returns false if user has a matching role and the access level is covered (denied).
     */
    public boolean isAllowed(User user, AccessLevel requested) {
        boolean matched = hasMatchingRole(user) && covers(requested);
        return control == Control.ALLOW ? matched : !matched;
    }

    /**
     * Check if the user has any role associated with this rule (regardless of ALLOW/DENY).
     */
    public boolean matchesUser(User user) {
        return hasMatchingRole(user);
    }

    private boolean hasMatchingRole(User user) {
        var effectiveRoles = user.effectiveRoles();
        for (var role : effectiveRoles) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /** Check if this ACL's access level covers the requested level. */
    public boolean covers(AccessLevel requested) {
        return accessLevel == AccessLevel.ALL || accessLevel == requested;
    }

    @Override public String toString() {
        return "AclRule[" + name + ", " + uri + ", " + control + "/" + accessLevel + "]";
    }
}
