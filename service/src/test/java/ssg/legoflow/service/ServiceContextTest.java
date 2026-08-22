package ssg.legoflow.service;

import ssg.legoflow.service.user.AccessControl;
import ssg.legoflow.service.user.ServiceRole;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class ServiceContextTest {

    @Test
    void testDefaultContextWithAnonymousUser() {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        assertThat(ctx.getUser().getName()).isEqualTo("Anonymous");
        assertThat(ctx.getUser().getType()).isEqualTo(ssg.legoflow.service.user.UserType.ANONYMOUS);
        assertThat(ctx.getUser().hasRole(ServiceRole.GUEST)).isTrue();
    }

    @Test
    void testContextScopes() {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        assertThat(ctx.getSiteScope()).isNotNull();
        assertThat(ctx.getApplicationScope()).isNotNull();
        assertThat(ctx.getSessionScope()).isNotNull();
        assertThat(ctx.getRequestScope()).isNotNull();
        assertThat(ctx.getSiteScope().getId()).isNotEmpty();
    }

    @Test
    void testContextAttributes() {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ctx.setAttribute("key", "value");
        assertThat(ctx.<String>getAttribute("key")).isEqualTo("value");
        ctx.setAttribute("key", null);
        assertThat(ctx.<String>getAttribute("key")).isNull();
    }

    @Test
    void testScopeAttributes() {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ctx.getSessionScope().setAttribute("session-key", 42);
        assertThat(ctx.getSessionScope().<Integer>getAttribute("session-key")).isEqualTo(42);
    }

    @Test
    void testContextWithExactUser() {
        var user = ServiceUser.exact("u1", "Alice", Set.of(ServiceRole.ADMIN, ServiceRole.USER));
        var ctx = new DefaultServiceContext(user);
        assertThat(ctx.getUser().getName()).isEqualTo("Alice");
        assertThat(ctx.hasRole(ServiceRole.ADMIN)).isTrue();
        assertThat(ctx.hasRole(ServiceRole.USER)).isTrue();
        assertThat(ctx.hasRole(ServiceRole.GUEST)).isFalse();
    }

    @Test
    void testCheckPermission() {
        var ac = new AccessControl();
        ac.requireRole("delete", ServiceRole.ADMIN);
        var admin = ServiceUser.exact("a1", "Admin", Set.of(ServiceRole.ADMIN));
        var guest = ServiceUser.anonymous();

        var adminCtx = new DefaultServiceContext(admin, ac);
        assertThatCode(() -> adminCtx.checkPermission("delete")).doesNotThrowAnyException();

        var guestCtx = new DefaultServiceContext(guest, ac);
        assertThatThrownBy(() -> guestCtx.checkPermission("delete"))
                .isInstanceOf(AccessControl.AccessDeniedException.class);
    }

    @Test
    void testLoggerNotNull() {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        assertThat(ctx.getLogger()).isNotNull();
    }

    @Test
    void testStatisticsNotNull() {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        assertThat(ctx.getStatistics()).isNotNull();
    }
}
