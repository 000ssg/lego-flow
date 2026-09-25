package ssg.legoflow.acl.model;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class AclRuleTest {

    private AclDomain domain;

    @BeforeEach void setUp() {
        domain = new AclDomain("Test");
    }

    @Test void createAclRule() {
        var rule = domain.createAclRule("r1", "/api/users", "Access users",
                AclRule.Control.ALLOW, AclRule.AccessLevel.READ);
        assertThat(rule.name()).isEqualTo("r1");
        assertThat(rule.uri()).isEqualTo("/api/users");
        assertThat(rule.description()).isEqualTo("Access users");
        assertThat(rule.control()).isEqualTo(AclRule.Control.ALLOW);
        assertThat(rule.accessLevel()).isEqualTo(AclRule.AccessLevel.READ);
    }

    @Test void duplicateAclRuleThrows() {
        domain.createAclRule("r1", "/api", AclRule.Control.ALLOW, AclRule.AccessLevel.READ);
        assertThatThrownBy(() -> domain.createAclRule("r1", "/api2", AclRule.Control.DENY, AclRule.AccessLevel.WRITE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void aclRuleRoles() {
        var role = domain.createRole("admin");
        var rule = domain.createAclRule("r1", "/admin", AclRule.Control.ALLOW, AclRule.AccessLevel.ALL);
        rule.addRole(role);
        assertThat(rule.roles()).contains(role);
    }

    @Test void aclRuleIsAllowed() {
        var role = domain.createRole("admin");
        var user = domain.createUser("alice", "secret");
        user.assignRole(role);
        var rule = domain.createAclRule("r1", "/admin", AclRule.Control.ALLOW, AclRule.AccessLevel.ALL);
        rule.addRole(role);
        assertThat(rule.isAllowed(user, AclRule.AccessLevel.READ)).isTrue();
        assertThat(rule.isAllowed(user, AclRule.AccessLevel.WRITE)).isTrue();
    }

    @Test void aclRuleAllCoversAll() {
        var role = domain.createRole("admin");
        var user = domain.createUser("alice", "secret");
        user.assignRole(role);
        var rule = domain.createAclRule("r1", "/admin", AclRule.Control.ALLOW, AclRule.AccessLevel.ALL);
        rule.addRole(role);
        assertThat(rule.isAllowed(user, AclRule.AccessLevel.LIST)).isTrue();
        assertThat(rule.isAllowed(user, AclRule.AccessLevel.READ)).isTrue();
        assertThat(rule.isAllowed(user, AclRule.AccessLevel.WRITE)).isTrue();
        assertThat(rule.isAllowed(user, AclRule.AccessLevel.DELETE)).isTrue();
        assertThat(rule.isAllowed(user, AclRule.AccessLevel.EXECUTE)).isTrue();
    }

    @Test void aclRuleSpecificLevel() {
        var role = domain.createRole("viewer");
        var user = domain.createUser("alice", "secret");
        user.assignRole(role);
        var rule = domain.createAclRule("r1", "/read", AclRule.Control.ALLOW, AclRule.AccessLevel.READ);
        rule.addRole(role);
        assertThat(rule.isAllowed(user, AclRule.AccessLevel.READ)).isTrue();
        assertThat(rule.isAllowed(user, AclRule.AccessLevel.WRITE)).isFalse();
    }

    @Test void denyRule() {
        var role = domain.createRole("guest");
        var user = domain.createUser("guest", "guest");
        user.assignRole(role);
        var rule = domain.createAclRule("deny", "/secret", AclRule.Control.DENY, AclRule.AccessLevel.WRITE);
        rule.addRole(role);
        // DENY WRITE blocks WRITE
        assertThat(rule.isAllowed(user, AclRule.AccessLevel.WRITE)).isFalse();
        // DENY WRITE does NOT block READ (specific level)
        assertThat(rule.isAllowed(user, AclRule.AccessLevel.READ)).isTrue();
    }

    @Test void domainIsAllowedExactMatch() {
        var role = domain.createRole("admin");
        var user = domain.createUser("alice", "secret");
        user.assignRole(role);
        var rule = domain.createAclRule("r1", "/api/data", AclRule.Control.ALLOW, AclRule.AccessLevel.WRITE);
        rule.addRole(role);
        assertThat(domain.isAllowed(user, "/api/data", AclRule.AccessLevel.WRITE)).isTrue();
        assertThat(domain.isAllowed(user, "/api/other", AclRule.AccessLevel.WRITE)).isFalse();
    }

    @Test void domainIsAllowedWildcardMatch() {
        var role = domain.createRole("admin");
        var user = domain.createUser("alice", "secret");
        user.assignRole(role);
        var rule = domain.createAclRule("r1", "/api/**", AclRule.Control.ALLOW, AclRule.AccessLevel.WRITE);
        rule.addRole(role);
        assertThat(domain.isAllowed(user, "/api/data", AclRule.AccessLevel.WRITE)).isTrue();
        assertThat(domain.isAllowed(user, "/api/users/123", AclRule.AccessLevel.WRITE)).isTrue();
        assertThat(domain.isAllowed(user, "/other", AclRule.AccessLevel.WRITE)).isFalse();
    }

    @Test void domainIsAllowedCatchAll() {
        var role = domain.createRole("admin");
        var user = domain.createUser("alice", "secret");
        user.assignRole(role);
        var rule = domain.createAclRule("all", "**", AclRule.Control.ALLOW, AclRule.AccessLevel.ALL);
        rule.addRole(role);
        assertThat(domain.isAllowed(user, "/anything/deep", AclRule.AccessLevel.DELETE)).isTrue();
    }

    @Test void denyTakesPrecedence() {
        var adminRole = domain.createRole("admin");
        var user = domain.createUser("alice", "secret");
        user.assignRole(adminRole);
        // Allow ALL
        domain.createAclRule("allow", "**", AclRule.Control.ALLOW, AclRule.AccessLevel.ALL).addRole(adminRole);
        // But deny delete on /secure
        domain.createAclRule("deny", "/secure/**", AclRule.Control.DENY, AclRule.AccessLevel.DELETE).addRole(adminRole);
        assertThat(domain.isAllowed(user, "/secure/data", AclRule.AccessLevel.DELETE)).isFalse();
        assertThat(domain.isAllowed(user, "/secure/data", AclRule.AccessLevel.READ)).isTrue();
    }

    @Test void aclRulesForUri() {
        domain.createAclRule("r1", "/api/data", AclRule.Control.ALLOW, AclRule.AccessLevel.READ);
        domain.createAclRule("r2", "/api/**", AclRule.Control.ALLOW, AclRule.AccessLevel.WRITE);
        domain.createAclRule("r3", "/other", AclRule.Control.ALLOW, AclRule.AccessLevel.READ);
        var matches = domain.aclRulesFor("/api/data");
        assertThat(matches).hasSize(2);
        assertThat(matches).extracting(AclRule::name).containsExactly("r1", "r2");
    }

    @Test void domainCollectionAccess() {
        domain.createAclRule("r1", "/api", AclRule.Control.ALLOW, AclRule.AccessLevel.READ);
        domain.createAclRule("r2", "/admin", AclRule.Control.DENY, AclRule.AccessLevel.WRITE);
        assertThat(domain.aclRules()).hasSize(2);
        assertThat(domain.aclRule("r1")).isPresent();
        assertThat(domain.aclRule("missing")).isEmpty();
    }
}
