package ssg.legoflow.acl.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AclDomain is the root of ACL management. It owns users, groups, roles, and certificates.
 *
 * <p>Users can have explicit roles and belong to groups. Effective roles are the union of
 * explicit roles and all roles assigned to groups the user belongs to.
 */
public final class AclDomain {
    private final String name;
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    private final Map<String, Role> roles = new ConcurrentHashMap<>();
    private final List<CertificateEntry> certificates = new ArrayList<>();
    private final Map<String, AclRule> aclRules = new ConcurrentHashMap<>();

    public AclDomain(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String name() { return name; }

    public Collection<User> users() { return Collections.unmodifiableCollection(users.values()); }
    public Optional<User> user(String username) { return Optional.ofNullable(users.get(username)); }

    public Collection<Group> groups() { return Collections.unmodifiableCollection(groups.values()); }
    public Optional<Group> group(String name) { return Optional.ofNullable(groups.get(name)); }

    public Collection<Role> roles() { return Collections.unmodifiableCollection(roles.values()); }
    public Optional<Role> role(String name) { return Optional.ofNullable(roles.get(name)); }

    public List<CertificateEntry> certificates() { return Collections.unmodifiableList(certificates); }

    public Collection<AclRule> aclRules() { return Collections.unmodifiableCollection(aclRules.values()); }
    public Optional<AclRule> aclRule(String name) { return Optional.ofNullable(aclRules.get(name)); }

    /** Find ACL rules that match a URI pattern. Exact matches before wildcards. */
    public List<AclRule> aclRulesFor(String uri) {
        var exact = new java.util.ArrayList<AclRule>();
        var wildcard = new java.util.ArrayList<AclRule>();
        for (var rule : aclRules.values()) {
            if (rule.uri().equals(uri) || matchesPattern(rule.uri(), uri)) {
                if (rule.uri().equals(uri)) {
                    exact.add(rule);
                } else {
                    wildcard.add(rule);
                }
            }
        }
        exact.addAll(wildcard);
        return exact;
    }

    /** Simple wildcard URI matching: ** matches any path segment(s). */
    private static boolean matchesPattern(String pattern, String uri) {
        if (pattern.contains("**")) {
            var idx = pattern.indexOf("**");
            var prefix = pattern.substring(0, idx);
            var suffix = pattern.substring(idx + 2);
            return uri.startsWith(prefix) && uri.endsWith(suffix);
        }
        return false;
    }

    /**
     * Create an ACL rule. Returns the rule for further configuration (addRole).
     */
    public AclRule createAclRule(String name, String uri, AclRule.Control control, AclRule.AccessLevel accessLevel) {
        return createAclRule(name, uri, null, control, accessLevel);
    }

    public AclRule createAclRule(String name, String uri, String description,
                                  AclRule.Control control, AclRule.AccessLevel accessLevel) {
        var rule = new AclRule(this, name, uri, description, control, accessLevel);
        if (aclRules.putIfAbsent(name, rule) != null) {
            throw new IllegalArgumentException("ACL rule already exists: " + name);
        }
        return rule;
    }

    /**
     * Check if a user is allowed to perform the specified access on a resource URI.
     * DENY rules take precedence over ALLOW rules.
     */
    public boolean isAllowed(User user, String uri, AclRule.AccessLevel accessLevel) {
        var rules = aclRulesFor(uri);
        boolean allowed = false;
        boolean denied = false;
        for (var rule : rules) {
            if (rule.matchesUser(user) && rule.covers(accessLevel)) {
                if (rule.control() == AclRule.Control.ALLOW) allowed = true;
                else denied = true;
            }
        }
        return !denied && allowed;
    }

    public User createUser(String username, String password) {
        var user = new User(this, username, password);
        if (users.putIfAbsent(username, user) != null) {
            throw new IllegalArgumentException("User already exists: " + username);
        }
        return user;
    }

    public User createUser(String username, char[] password) {
        return createUser(username, new String(password));
    }

    public Group createGroup(String name) {
        var group = new Group(this, name);
        if (groups.putIfAbsent(name, group) != null) {
            throw new IllegalArgumentException("Group already exists: " + name);
        }
        return group;
    }

    public Role createRole(String name) {
        return createRole(name, null, null);
    }

    public Role createRole(String name, Set<String> permissions) {
        return createRole(name, permissions, null);
    }

    public Role createRole(String name, Set<String> permissions, Map<String, Object> attributes) {
        var role = new Role(this, name, permissions, attributes);
        if (roles.putIfAbsent(name, role) != null) {
            throw new IllegalArgumentException("Role already exists: " + name);
        }
        return role;
    }

    public void addCertificate(CertificateEntry cert) {
        synchronized (certificates) {
            certificates.add(Objects.requireNonNull(cert));
        }
    }

    /** Compute the effective roles for a user: explicit roles + group-inherited roles. */
    public Set<Role> effectiveRoles(User user) {
        Set<Role> effective = new LinkedHashSet<>(user.explicitRoles());
        for (Group group : user.groups()) {
            effective.addAll(group.explicitRoles());
        }
        return Collections.unmodifiableSet(effective);
    }

    /** Compute effective permissions (union of all permission sets in effective roles). */
    public Set<String> effectivePermissions(User user) {
        return effectiveRoles(user).stream()
                .flatMap(r -> r.permissions().stream())
                .collect(Collectors.toSet());
    }

    @Override public String toString() { return "AclDomain[" + name + "]"; }
}
