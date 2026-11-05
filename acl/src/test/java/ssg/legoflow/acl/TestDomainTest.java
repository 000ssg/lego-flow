package ssg.legoflow.acl;

import org.junit.jupiter.api.*;
import ssg.legoflow.acl.model.AclRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

class TestDomainTest {

    @Test void instanceExists() {
        var domain = TestDomain.INSTANCE;
        assertThat(domain).isNotNull();
        assertThat(domain.name()).isEqualTo("Test");
    }

    @Test void userCount() {
        var domain = TestDomain.INSTANCE;
        assertThat(domain.users()).hasSize(9);
    }

    @Test void roleCount() {
        assertThat(TestDomain.INSTANCE.roles()).hasSize(4);
        var d = TestDomain.INSTANCE;
        assertThat(d.role("admin")).isPresent();
        assertThat(d.role("manager")).isPresent();
        assertThat(d.role("user")).isPresent();
        assertThat(d.role("guest")).isPresent();
    }

    @Test void groupCount() {
        var d = TestDomain.INSTANCE;
        assertThat(d.group("admins")).isPresent();
        assertThat(d.group("managers")).isPresent();
        assertThat(d.group("users")).isPresent();
        assertThat(d.group("guests")).isPresent();
    }

    @Test void aclRules() {
        var d = TestDomain.INSTANCE;
        assertThat(d.aclRules()).hasSize(5);
    }

    @Test void adminHasFullAccess() {
        var d = TestDomain.INSTANCE;
        var admin = d.user("admin").get();
        assertThat(d.isAllowed(admin, "/anything", AclRule.AccessLevel.ALL)).isTrue();
        assertThat(d.isAllowed(admin, "/resources/data", AclRule.AccessLevel.DELETE)).isTrue();
    }

    @Test void managerCanWriteResources() {
        var d = TestDomain.INSTANCE;
        var m1 = d.user("manager1").get();
        assertThat(d.isAllowed(m1, "/resources/data", AclRule.AccessLevel.WRITE)).isTrue();
        assertThat(d.isAllowed(m1, "/resources/data", AclRule.AccessLevel.READ)).isTrue();
    }

    @Test void userCanRead() {
        var d = TestDomain.INSTANCE;
        var u1 = d.user("user1").get();
        assertThat(d.isAllowed(u1, "/resources/data", AclRule.AccessLevel.READ)).isTrue();
        assertThat(d.isAllowed(u1, "/resources/data", AclRule.AccessLevel.WRITE)).isFalse();
    }

    @Test void guestLimitedAccess() {
        var d = TestDomain.INSTANCE;
        var guest = d.user("guest").get();
        assertThat(d.isAllowed(guest, "/public/info", AclRule.AccessLevel.READ)).isTrue();
        // Guest is denied write on /resources
        assertThat(d.isAllowed(guest, "/resources/data", AclRule.AccessLevel.WRITE)).isFalse();
    }

    @Test void nobodyHasNoAccess() {
        var d = TestDomain.INSTANCE;
        var nobody = d.user("nobody").get();
        assertThat(d.isAllowed(nobody, "/resources/data", AclRule.AccessLevel.READ)).isFalse();
        assertThat(d.isAllowed(nobody, "/public/info", AclRule.AccessLevel.READ)).isFalse();
    }

    @Test void powerUserHasManagerAccess() {
        var d = TestDomain.INSTANCE;
        var pu = d.user("poweruser").get();
        // poweruser belongs to users + managers
        assertThat(pu.hasRole("user")).isTrue();
        assertThat(pu.hasRole("manager")).isTrue();
        assertThat(d.isAllowed(pu, "/resources/data", AclRule.AccessLevel.WRITE)).isTrue();
    }

    @Test void superAdmin() {
        var d = TestDomain.INSTANCE;
        var sa = d.user("superadmin").get();
        assertThat(sa.hasRole("admin")).isTrue();
        assertThat(sa.hasRole("manager")).isTrue();
        assertThat(d.isAllowed(sa, "/anything/deep", AclRule.AccessLevel.DELETE)).isTrue();
    }

    @Test void certificatesPresent() {
        var d = TestDomain.INSTANCE;
        assertThat(d.certificates()).hasSizeGreaterThan(9); // CA + 9 user certs
    }

    @Test void userCertificates() {
        var d = TestDomain.INSTANCE;
        var admin = d.user("admin").get();
        assertThat(admin.certificates()).isNotEmpty();
        assertThat(admin.certificates().stream().findFirst().get().hasKey()).isTrue();
    }

    @Test void passwordCheck() {
        var d = TestDomain.INSTANCE;
        assertThat(d.user("admin").get().checkPassword("admin")).isTrue();
        assertThat(d.user("admin").get().checkPassword("wrong")).isFalse();
        assertThat(d.user("guest").get().checkPassword("guest")).isTrue();
    }
}
