package ssg.legoflow.acl;

import ssg.legoflow.acl.model.AclDomain;
import ssg.legoflow.acl.model.AclRule;

import java.util.Set;

/**
 * Pre-built test domain with 10-year self-signed certificates, 4 roles, 4 groups,
 * and users covering common authentication/authorization scenarios.
 *
 * <p>Reusable by any lego-flow protocol implementation unit tests.
 */
public final class TestDomain {

    private TestDomain() {}

    /** The pre-built "Test" domain. Call once, reuse everywhere. */
    public static AclDomain INSTANCE = build();

    private static AclDomain build() {
        return new AclDomainBuilder()
                .name("Test")
                .validityYears(10)
                .withCerts()

                // Roles
                .role("admin", Set.of("*"))
                .role("manager", Set.of("read", "write", "list"))
                .role("user", Set.of("read", "list"))
                .role("guest", Set.of("read"))

                // ACL rules
                .acl("admin-all", "**", "Admin full access", AclRule.Control.ALLOW, AclRule.AccessLevel.ALL, Set.of("admin"))
                .acl("manager-resources", "/resources/**", "Manager resource access", AclRule.Control.ALLOW, AclRule.AccessLevel.ALL, Set.of("manager"))
                .acl("user-read", "/resources/**", "User read access", AclRule.Control.ALLOW, AclRule.AccessLevel.READ, Set.of("user"))
                .acl("guest-public", "/public/**", "Guest public read", AclRule.Control.ALLOW, AclRule.AccessLevel.READ, Set.of("guest"))
                .acl("deny-guest-write", "/resources/**", "Deny guest write", AclRule.Control.DENY, AclRule.AccessLevel.WRITE, Set.of("guest"))

                // Groups
                .group("admins", Set.of("admin"), Set.of(), null)
                .group("managers", Set.of("manager"), Set.of(), null)
                .group("users", Set.of("user"), Set.of(), null)
                .group("guests", Set.of("guest"), Set.of(), null)

                // Users — admin
                .user("admin", "admin", Set.of("admin"), Set.of("admins"), null)

                // Users — managers (explicit role + group)
                .user("manager1", "manager1", Set.of("manager"), Set.of("managers"), null)
                .user("manager2", "manager2", Set.of(), Set.of("managers"), null)

                // Users — regular (group-only roles)
                .user("user1", "user1", Set.of(), Set.of("users"), null)
                .user("user2", "user2", Set.of("user"), Set.of(), null)

                // Users — multi-group (user + manager)
                .user("poweruser", "poweruser", Set.of(), Set.of("users", "managers"), null)

                // Users — guest
                .user("guest", "guest", Set.of("guest"), Set.of("guests"), null)

                // Users — no role (unauthenticated)
                .user("nobody", "nobody", Set.of(), Set.of(), null)

                // Users — multi-role (admin + manager)
                .user("superadmin", "superadmin", Set.of("admin", "manager"), Set.of("admins", "managers"), null)

                .build();
    }

    /** Convenience: get a certificate for the given username. */
    public static void main(String[] args) {
        System.out.println("Test domain: " + INSTANCE);
        System.out.println("  Users: " + INSTANCE.users().size());
        System.out.println("  Groups: " + INSTANCE.groups().size());
        System.out.println("  Roles: " + INSTANCE.roles().size());
        System.out.println("  ACL rules: " + INSTANCE.aclRules().size());
        System.out.println("  Certificates: " + INSTANCE.certificates().size());
    }
}
