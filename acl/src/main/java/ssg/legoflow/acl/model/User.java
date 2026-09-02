package ssg.legoflow.acl.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * User within an ACL domain. A user has an explicit role set and can belong to multiple groups.
 * Effective roles = explicit roles ∪ (roles of all groups the user belongs to).
 */
public final class User {
    private final AclDomain domain;
    private final String username;
    private final String passwordHash;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private final CopyOnWriteArraySet<Role> explicitRoles = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArrayList<Group> memberOfGroups = new CopyOnWriteArrayList<>();
    private final List<CertificateEntry> certificates = new CopyOnWriteArrayList<>();

    User(AclDomain domain, String username, String password) {
        this.domain = domain;
        this.username = Objects.requireNonNull(username);
        this.passwordHash = hashPassword(password);
    }

    public String username() { return username; }
    public AclDomain domain() { return domain; }

    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public User setAttribute(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    public Collection<Role> explicitRoles() {
        return Collections.unmodifiableCollection(explicitRoles);
    }

    public User assignRole(Role role) {
        explicitRoles.add(Objects.requireNonNull(role));
        return this;
    }

    public User removeRole(Role role) {
        explicitRoles.remove(role);
        return this;
    }

    public Collection<Group> groups() {
        return Collections.unmodifiableCollection(memberOfGroups);
    }

    public User joinGroup(Group group) {
        memberOfGroups.add(Objects.requireNonNull(group));
        return this;
    }

    public User leaveGroup(Group group) {
        memberOfGroups.remove(group);
        return this;
    }

    public Collection<CertificateEntry> certificates() {
        return Collections.unmodifiableCollection(certificates);
    }

    public User addCertificate(CertificateEntry cert) {
        certificates.add(Objects.requireNonNull(cert));
        return this;
    }

    public User removeCertificate(CertificateEntry cert) {
        certificates.remove(cert);
        return this;
    }

    /** Verify the supplied password against stored hash. */
    public boolean checkPassword(String password) {
        return passwordHash.equals(hashPassword(password));
    }

    public boolean checkPassword(char[] password) {
        try { return checkPassword(new String(password)); }
        finally { Arrays.fill(password, '\0'); }
    }

    /** Compute effective roles: explicit + inherited from all groups. */
    public Set<Role> effectiveRoles() {
        return domain.effectiveRoles(this);
    }

    /** Compute effective permissions. */
    public Set<String> effectivePermissions() {
        return domain.effectivePermissions(this);
    }

    /** Check if the user has a specific role (explicit or inherited). */
    public boolean hasRole(String roleName) {
        return effectiveRoles().stream().anyMatch(r -> r.name().equals(roleName));
    }

    /** Check if the user has a specific permission. */
    public boolean hasPermission(String permission) {
        return effectivePermissions().contains(permission);
    }

    private static String hashPassword(String password) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Override public String toString() { return "User[" + username + "]"; }
}
