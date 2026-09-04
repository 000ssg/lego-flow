package ssg.legoflow.acl;

import ssg.legoflow.acl.cert.CertificateFactory;
import ssg.legoflow.acl.cert.DomainCerts;
import ssg.legoflow.acl.model.*;

import java.util.*;

/**
 * Fluent builder for AclDomain with certificate generation.
 */
public final class AclDomainBuilder {
    private String name;
    private final List<UserSpec> users = new ArrayList<>();
    private final List<GroupSpec> groups = new ArrayList<>();
    private final List<RoleSpec> roles = new ArrayList<>();
    private final List<AclSpec> acls = new ArrayList<>();
    private int keySize = 2048;
    private long validityYears = 10;
    private boolean generateCerts = false;

    public AclDomainBuilder name(String name) { this.name = name; return this; }
    public AclDomainBuilder keySize(int keySize) { this.keySize = keySize; return this; }
    public AclDomainBuilder validityYears(long years) { this.validityYears = years; return this; }
    public AclDomainBuilder withCerts() { this.generateCerts = true; return this; }

    public AclDomainBuilder role(String name) {
        roles.add(new RoleSpec(name));
        return this;
    }

    public AclDomainBuilder role(String name, Set<String> permissions) {
        roles.add(new RoleSpec(name, permissions, null));
        return this;
    }

    public AclDomainBuilder role(String name, Set<String> permissions, Map<String, Object> attributes) {
        roles.add(new RoleSpec(name, permissions, attributes));
        return this;
    }

    public AclDomainBuilder acl(String name, String uri, AclRule.Control control, AclRule.AccessLevel accessLevel) {
        acls.add(new AclSpec(name, uri, null, control, accessLevel));
        return this;
    }

    public AclDomainBuilder acl(String name, String uri, String description,
                                  AclRule.Control control, AclRule.AccessLevel accessLevel) {
        acls.add(new AclSpec(name, uri, description, control, accessLevel));
        return this;
    }

    public AclDomainBuilder acl(String name, String uri, String description,
                                  AclRule.Control control, AclRule.AccessLevel accessLevel,
                                  Set<String> roleNames) {
        acls.add(new AclSpec(name, uri, description, control, accessLevel, roleNames));
        return this;
    }

    public AclDomainBuilder group(String name) {
        groups.add(new GroupSpec(name));
        return this;
    }

    public AclDomainBuilder group(String name, Set<String> roleNames, Set<String> memberUsernames, Map<String, Object> attributes) {
        groups.add(new GroupSpec(name, roleNames, memberUsernames, attributes));
        return this;
    }

    public AclDomainBuilder user(String username, String password) {
        users.add(new UserSpec(username, password));
        return this;
    }

    public AclDomainBuilder user(String username, String password, Set<String> roleNames, Set<String> groupNames, Map<String, Object> attributes) {
        users.add(new UserSpec(username, password, roleNames, groupNames, attributes));
        return this;
    }

    public AclDomain build() {
        Objects.requireNonNull(name, "Domain name is required");
        var domain = new AclDomain(name);

        // Create roles
        for (var spec : roles) {
            domain.createRole(spec.name(), spec.permissions(), spec.attributes());
        }

        // Create groups
        for (var spec : groups) {
            var group = domain.createGroup(spec.name());
            if (spec.attributes() != null) spec.attributes().forEach(group::setAttribute);
        }

        // Create users
        for (var spec : users) {
            var user = domain.createUser(spec.username(), spec.password());
            if (spec.attributes() != null) spec.attributes().forEach(user::setAttribute);
        }

        // Build lookup maps
        var roleMap = new HashMap<String, Role>();
        for (var spec : roles) roleMap.put(spec.name(), domain.role(spec.name()).orElse(null));
        var groupMap = new HashMap<String, Group>();
        for (var spec : groups) groupMap.put(spec.name(), domain.group(spec.name()).orElse(null));

        // Wire user roles and group memberships
        for (var spec : users) {
            var user = domain.user(spec.username()).orElseThrow();
            for (String rn : spec.roleNames()) {
                var role = roleMap.get(rn);
                if (role != null) user.assignRole(role);
            }
            for (String gn : spec.groupNames()) {
                var group = groupMap.get(gn);
                if (group != null) user.joinGroup(group);
            }
        }

        // Wire group role assignments and memberships
        for (var spec : groups) {
            var group = domain.group(spec.name()).orElseThrow();
            for (String rn : spec.roleNames()) {
                var role = roleMap.get(rn);
                if (role != null) group.assignRole(role);
            }
            for (String mn : spec.memberUsernames()) {
                var user = domain.user(mn).orElse(null);
                if (user != null) user.joinGroup(group);
            }
        }

        // Create ACL rules and wire roles
        for (var spec : acls) {
            var rule = domain.createAclRule(spec.name(), spec.uri(), spec.description(),
                    spec.control(), spec.accessLevel());
            for (String rn : spec.roleNames()) {
                var role = roleMap.get(rn);
                if (role != null) rule.addRole(role);
            }
        }

        // Generate certificates if requested
        if (generateCerts) {
            String[] subjects = users.stream()
                    .map(u -> u.username())
                    .toArray(String[]::new);
            var certs = CertificateFactory.generateDomainCerts(name, keySize, validityYears, subjects);
            domain.addCertificate(certs.ca());
            for (var cert : certs.signedCerts()) {
                domain.addCertificate(cert);
                var username = cert.alias();
                domain.user(username).ifPresent(u -> u.addCertificate(cert));
            }
        }

        return domain;
    }

    record RoleSpec(String name, Set<String> permissions, Map<String, Object> attributes) {
        RoleSpec(String name) { this(name, null, null); }
    }

    record AclSpec(String name, String uri, String description,
                    AclRule.Control control, AclRule.AccessLevel accessLevel,
                    Set<String> roleNames) {
        AclSpec(String name, String uri, String description, AclRule.Control control, AclRule.AccessLevel accessLevel) {
            this(name, uri, description, control, accessLevel, Set.of());
        }
    }

    record GroupSpec(String name, Set<String> roleNames, Set<String> memberUsernames, Map<String, Object> attributes) {
        GroupSpec(String name) { this(name, Set.of(), Set.of(), null); }
    }

    record UserSpec(String username, String password, Set<String> roleNames, Set<String> groupNames, Map<String, Object> attributes) {
        UserSpec(String username, String password) { this(username, password, Set.of(), Set.of(), null); }
    }
}
