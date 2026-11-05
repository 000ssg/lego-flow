package ssg.legoflow.acl.model;

import java.util.*;

/**
 * Role with a name, optional permissions, and optional attributes.
 */
public final class Role {
    private final AclDomain domain;
    private final String name;
    private final Set<String> permissions;
    private final Map<String, Object> attributes;

    Role(AclDomain domain, String name, Set<String> permissions, Map<String, Object> attributes) {
        this.domain = domain;
        this.name = Objects.requireNonNull(name);
        this.permissions = permissions != null ? Collections.unmodifiableSet(new HashSet<>(permissions)) : Collections.emptySet();
        this.attributes = attributes != null ? Collections.unmodifiableMap(new LinkedHashMap<>(attributes)) : Collections.emptyMap();
    }

    public String name() { return name; }
    public AclDomain domain() { return domain; }
    public Set<String> permissions() { return permissions; }
    public Map<String, Object> attributes() { return attributes; }

    /** Check if this role grants the given permission. */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role)) return false;
        return name.equals(((Role) o).name);
    }

    @Override public int hashCode() { return name.hashCode(); }
    @Override public String toString() { return "Role[" + name + "]"; }
}
