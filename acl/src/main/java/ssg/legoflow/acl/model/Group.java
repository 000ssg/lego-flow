package ssg.legoflow.acl.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Group within an ACL domain. Groups own explicit roles and member users.
 */
public final class Group {
    private final AclDomain domain;
    private final String name;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private final CopyOnWriteArraySet<Role> explicitRoles = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArrayList<User> members = new CopyOnWriteArrayList<>();

    Group(AclDomain domain, String name) {
        this.domain = domain;
        this.name = Objects.requireNonNull(name);
    }

    public String name() { return name; }
    public AclDomain domain() { return domain; }

    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public Group setAttribute(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public Collection<Role> explicitRoles() {
        return Collections.unmodifiableCollection(explicitRoles);
    }

    public Group assignRole(Role role) {
        explicitRoles.add(Objects.requireNonNull(role));
        return this;
    }

    public Group removeRole(Role role) {
        explicitRoles.remove(role);
        return this;
    }

    public Collection<User> members() {
        return Collections.unmodifiableCollection(members);
    }

    public void removeMember(User user) {
        members.remove(user);
    }

    @Override public String toString() { return "Group[" + name + "]"; }
}
