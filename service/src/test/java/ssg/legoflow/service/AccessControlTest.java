package ssg.legoflow.service;

import ssg.legoflow.service.user.AccessControl;
import ssg.legoflow.service.user.ServiceRole;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class AccessControlTest {

    @Test
    void testNoRestrictionsAllowEveryone() {
        var ac = new AccessControl();
        assertThat(ac.isAllowed(ServiceUser.anonymous(), "anything")).isTrue();
    }

    @Test
    void testRequireRoleGrantsAccess() {
        var ac = new AccessControl();
        ac.requireRole("write", ServiceRole.USER);

        var user = ServiceUser.exact("u1", "User", Set.of(ServiceRole.USER));
        assertThat(ac.isAllowed(user, "write")).isTrue();
    }

    @Test
    void testRequireRoleDeniesAccess() {
        var ac = new AccessControl();
        ac.requireRole("write", ServiceRole.USER);

        var guest = ServiceUser.anonymous();
        assertThat(ac.isAllowed(guest, "write")).isFalse();
    }

    @Test
    void testRequireMultipleRoles() {
        var ac = new AccessControl();
        ac.requireRoles("admin", Set.of(ServiceRole.ADMIN));

        var user = ServiceUser.exact("u1", "User", Set.of(ServiceRole.USER));
        var admin = ServiceUser.exact("a1", "Admin", Set.of(ServiceRole.ADMIN));

        assertThat(ac.isAllowed(user, "admin")).isFalse();
        assertThat(ac.isAllowed(admin, "admin")).isTrue();
    }

    @Test
    void testCheckPermissionThrowsOnDenied() {
        var ac = new AccessControl();
        ac.requireRole("delete", ServiceRole.ADMIN);

        var guest = ServiceUser.anonymous();
        assertThatThrownBy(() -> ac.checkPermission(guest, "delete"))
                .isInstanceOf(AccessControl.AccessDeniedException.class)
                .hasMessageContaining("Anonymous")
                .hasMessageContaining("delete");
    }

    @Test
    void testCheckPermissionSucceedsWhenAllowed() {
        var ac = new AccessControl();
        ac.requireRole("read", ServiceRole.GUEST);

        var guest = ServiceUser.anonymous();
        assertThatCode(() -> ac.checkPermission(guest, "read")).doesNotThrowAnyException();
    }

    @Test
    void testUserTypes() {
        var anon = ServiceUser.anonymous();
        var shared = ServiceUser.shared("team");
        var exact = ServiceUser.exact("e1", "Exact", Set.of(ServiceRole.USER));

        assertThat(anon.getType()).isEqualTo(ssg.legoflow.service.user.UserType.ANONYMOUS);
        assertThat(shared.getType()).isEqualTo(ssg.legoflow.service.user.UserType.SHARED);
        assertThat(exact.getType()).isEqualTo(ssg.legoflow.service.user.UserType.EXACT);
    }

    @Test
    void testSharedUserHasUserRole() {
        var shared = ServiceUser.shared("backend-team");
        assertThat(shared.hasRole(ServiceRole.USER)).isTrue();
        assertThat(shared.getName()).isEqualTo("backend-team");
    }
}
