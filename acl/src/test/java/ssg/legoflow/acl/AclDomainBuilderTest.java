package ssg.legoflow.acl;

import org.junit.jupiter.api.*;
import ssg.legoflow.acl.model.AclDomain;
import ssg.legoflow.acl.model.AclRule;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class AclDomainBuilderTest {

    @Test void minimalDomain() {
        var domain = new AclDomainBuilder()
                .name("Test")
                .build();
        assertThat(domain.name()).isEqualTo("Test");
    }

    @Test void nameIsRequired() {
        assertThatThrownBy(() -> new AclDomainBuilder().build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test void rolesGroupsUsers() {
        var domain = new AclDomainBuilder()
                .name("Test")
                .role("admin", Set.of("*"))
                .role("user", Set.of("read"))
                .group("admins", Set.of("admin"), Set.of("alice"), null)
                .user("alice", "secret", Set.of(), Set.of("admins"), null)
                .build();

        assertThat(domain.role("admin")).isPresent();
        assertThat(domain.group("admins")).isPresent();
        assertThat(domain.user("alice")).isPresent();
    }

    @Test void roleInheritanceFromGroups() {
        var domain = new AclDomainBuilder()
                .name("Test")
                .role("admin", Set.of("*"))
                .group("admins", Set.of("admin"), Set.of("alice"), null)
                .user("alice", "secret", Set.of(), Set.of("admins"), null)
                .build();
        var alice = domain.user("alice").get();
        assertThat(alice.hasRole("admin")).isTrue();
        assertThat(alice.effectivePermissions()).contains("*");
    }

    @Test void explicitRoleOverride() {
        var domain = new AclDomainBuilder()
                .name("Test")
                .role("admin", Set.of("*"))
                .role("user", Set.of("read"))
                .user("alice", "secret", Set.of("admin"), Set.of(), null)
                .build();
        var alice = domain.user("alice").get();
        assertThat(alice.hasRole("admin")).isTrue();
        assertThat(alice.explicitRoles()).hasSize(1);
    }

    @Test void mixedExplicitAndGroupRoles() {
        var domain = new AclDomainBuilder()
                .name("Test")
                .role("admin", Set.of("*"))
                .role("user", Set.of("read"))
                .group("users", Set.of("user"), Set.of("alice"), null)
                .user("alice", "secret", Set.of("admin"), Set.of("users"), null)
                .build();
        var alice = domain.user("alice").get();
        var effective = alice.effectiveRoles();
        assertThat(effective).extracting("name").containsExactlyInAnyOrder("admin", "user");
    }

    @Test void aclRulesInBuilder() {
        var domain = new AclDomainBuilder()
                .name("Test")
                .role("admin", Set.of("*"))
                .acl("admin-all", "**", "Admin access", AclRule.Control.ALLOW, AclRule.AccessLevel.ALL, Set.of("admin"))
                .user("alice", "secret", Set.of("admin"), Set.of(), null)
                .build();
        var alice = domain.user("alice").get();
        assertThat(domain.isAllowed(alice, "/anything", AclRule.AccessLevel.DELETE)).isTrue();
    }

    @Test void multipleUsers() {
        var domain = new AclDomainBuilder()
                .name("Test")
                .role("user", Set.of("read"))
                .user("alice", "a", Set.of("user"), Set.of(), null)
                .user("bob", "b", Set.of("user"), Set.of(), null)
                .user("charlie", "c", Set.of(), Set.of(), null)
                .build();
        assertThat(domain.users()).hasSize(3);
        assertThat(domain.user("charlie").get().effectiveRoles()).isEmpty();
    }

    @Test void withCerts() {
        var domain = new AclDomainBuilder()
                .name("Test")
                .withCerts()
                .role("admin")
                .user("alice", "secret", Set.of("admin"), Set.of(), null)
                .user("bob", "secret", Set.of("admin"), Set.of(), null)
                .build();
        assertThat(domain.certificates()).isNotEmpty();
        var alice = domain.user("alice").get();
        assertThat(alice.certificates()).isNotEmpty();
        assertThat(alice.certificates().stream().findFirst().get().hasKey()).isTrue();
    }

    @Test void customKeySize() {
        var domain = new AclDomainBuilder()
                .name("Test")
                .keySize(4096)
                .withCerts()
                .role("admin")
                .user("alice", "s", Set.of("admin"), Set.of(), null)
                .build();
        var cert = domain.user("alice").get().certificates().stream().findFirst().get();
        assertThat(cert.certificate().getPublicKey().getAlgorithm()).isEqualTo("RSA");
    }

    @Test void emptyDomain() {
        var domain = new AclDomainBuilder()
                .name("Empty")
                .build();
        assertThat(domain.users()).isEmpty();
        assertThat(domain.groups()).isEmpty();
        assertThat(domain.roles()).isEmpty();
        assertThat(domain.aclRules()).isEmpty();
    }
}
