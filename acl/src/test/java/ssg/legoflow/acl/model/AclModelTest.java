package ssg.legoflow.acl.model;

import org.junit.jupiter.api.*;
import ssg.legoflow.acl.cert.CertificateFactory;
import ssg.legoflow.acl.cert.DomainCerts;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AclModelTest {

    private AclDomain domain;

    @BeforeEach void setUp() {
        domain = new AclDomain("Test");
    }

    @Test @Order(1) void createUserAndGetByUserName() {
        var user = domain.createUser("alice", "secret");
        assertThat(domain.user("alice")).contains(user);
        assertThat(domain.user("bob")).isEmpty();
    }

    @Test @Order(2) void duplicateUserThrows() {
        domain.createUser("alice", "secret");
        assertThatThrownBy(() -> domain.createUser("alice", "other"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @Order(3) void createGroupAndGetByName() {
        var group = domain.createGroup("admins");
        assertThat(domain.group("admins")).contains(group);
        assertThat(domain.group("users")).isEmpty();
    }

    @Test @Order(4) void duplicateGroupThrows() {
        domain.createGroup("admins");
        assertThatThrownBy(() -> domain.createGroup("admins"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @Order(5) void createRoleAndGetByName() {
        var role = domain.createRole("admin");
        assertThat(domain.role("admin")).contains(role);
        assertThat(domain.role("user")).isEmpty();
    }

    @Test @Order(6) void duplicateRoleThrows() {
        domain.createRole("admin");
        assertThatThrownBy(() -> domain.createRole("admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @Order(7) void roleWithPermissions() {
        var role = domain.createRole("admin", Set.of("*"));
        assertThat(role.permissions()).contains("*");
        assertThat(role.hasPermission("*")).isTrue();
        assertThat(role.hasPermission("read")).isFalse();
    }

    @Test @Order(8) void passwordHashAndCheck() {
        var user = domain.createUser("alice", "secret");
        assertThat(user.checkPassword("secret")).isTrue();
        assertThat(user.checkPassword("wrong")).isFalse();
    }

    @Test @Order(9) void explicitRoleAssignment() {
        var role = domain.createRole("admin", Set.of("read"));
        var user = domain.createUser("alice", "secret");
        user.assignRole(role);
        assertThat(user.explicitRoles()).contains(role);
    }

    @Test @Order(10) void groupMembership() {
        var group = domain.createGroup("admins");
        var user = domain.createUser("alice", "secret");
        user.joinGroup(group);
        assertThat(user.groups()).contains(group);
    }

    @Test @Order(11) void effectiveRolesFromGroup() {
        var role = domain.createRole("admin", Set.of("read"));
        var group = domain.createGroup("admins");
        group.assignRole(role);
        var user = domain.createUser("alice", "secret");
        user.joinGroup(group);
        var effective = user.effectiveRoles();
        assertThat(effective).contains(role);
    }

    @Test @Order(12) void effectiveRolesExplicitAndInherited() {
        var adminRole = domain.createRole("admin", Set.of("*"));
        var userRole = domain.createRole("user", Set.of("read"));
        var group = domain.createGroup("admins");
        group.assignRole(adminRole);
        var user = domain.createUser("alice", "secret");
        user.assignRole(userRole);
        user.joinGroup(group);
        var effective = user.effectiveRoles();
        assertThat(effective).containsExactlyInAnyOrder(adminRole, userRole);
    }

    @Test @Order(13) void effectivePermissions() {
        var adminRole = domain.createRole("admin", Set.of("*"));
        var userRole = domain.createRole("user", Set.of("read", "list"));
        var group = domain.createGroup("admins");
        group.assignRole(adminRole);
        var user = domain.createUser("alice", "secret");
        user.assignRole(userRole);
        user.joinGroup(group);
        var perms = user.effectivePermissions();
        assertThat(perms).contains("*", "read", "list");
    }

    @Test @Order(14) void hasRoleCheck() {
        var role = domain.createRole("admin", Set.of("*"));
        var group = domain.createGroup("admins");
        group.assignRole(role);
        var user = domain.createUser("alice", "secret");
        user.joinGroup(group);
        assertThat(user.hasRole("admin")).isTrue();
        assertThat(user.hasRole("user")).isFalse();
    }

    @Test @Order(15) void hasPermissionCheck() {
        var role = domain.createRole("admin", Set.of("read", "write"));
        var user = domain.createUser("alice", "secret");
        user.assignRole(role);
        assertThat(user.hasPermission("read")).isTrue();
        assertThat(user.hasPermission("delete")).isFalse();
    }

    @Test @Order(16) void attributes() {
        var user = domain.createUser("alice", "secret");
        user.setAttribute("email", "alice@example.com");
        assertThat(user.getAttribute("email")).isEqualTo("alice@example.com");
        assertThat(user.hasAttribute("email")).isTrue();
        assertThat(user.hasAttribute("phone")).isFalse();
    }

    @Test @Order(17) void groupAttributes() {
        var group = domain.createGroup("admins");
        group.setAttribute("description", "Administrators");
        assertThat(group.getAttribute("description")).isEqualTo("Administrators");
    }

    @Test @Order(18) void removeUserFromGroup() {
        var group = domain.createGroup("admins");
        var user = domain.createUser("alice", "secret");
        user.joinGroup(group);
        user.leaveGroup(group);
        assertThat(user.groups()).isEmpty();
    }

    @Test @Order(19) void removeRoleFromUser() {
        var role = domain.createRole("admin");
        var user = domain.createUser("alice", "secret");
        user.assignRole(role);
        user.removeRole(role);
        assertThat(user.explicitRoles()).isEmpty();
    }

    @Test @Order(20) void certificatesOnUser() {
        var user = domain.createUser("alice", "secret");
        var cert = CertificateFactory.selfSigned("alice", "CN=alice", 2048, 1);
        user.addCertificate(cert);
        assertThat(user.certificates()).contains(cert);
        user.removeCertificate(cert);
        assertThat(user.certificates()).isEmpty();
    }

    @Test @Order(21) void passwordCharArray() {
        var user = domain.createUser("alice", "secret");
        char[] pass = {'s','e','c','r','e','t'};
        assertThat(user.checkPassword(pass)).isTrue();
        char[] wrong = {'w','r','o','n','g'};
        assertThat(user.checkPassword(wrong)).isFalse();
    }
}
