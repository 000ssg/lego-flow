package ssg.legoflow.service;

import ssg.legoflow.service.user.ServiceRole;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServiceContextExtendedTest {

    private ServiceContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new DefaultServiceContext(ServiceUser.anonymous());
    }

    @Test
    void testGetSiteScope() {
        assertNotNull(ctx.getSiteScope());
    }

    @Test
    void testGetApplicationScope() {
        assertNotNull(ctx.getApplicationScope());
    }

    @Test
    void testGetSessionScope() {
        assertNotNull(ctx.getSessionScope());
    }

    @Test
    void testGetRequestScope() {
        assertNotNull(ctx.getRequestScope());
    }

    @Test
    void testGetUser() {
        ServiceUser user = ctx.getUser();
        assertNotNull(user);
        assertEquals("anonymous", user.getId());
        assertEquals("Anonymous", user.getName());
    }

    @Test
    void testHasRole() {
        assertTrue(ctx.hasRole(ServiceRole.GUEST));
        assertFalse(ctx.hasRole(ServiceRole.ADMIN));
    }

    @Test
    void testCheckPermissionAnonymousAllowed() {
        // Anonymous user should be allowed by default (no restrictions)
        assertDoesNotThrow(() -> ctx.checkPermission("test_operation"));
    }

    @Test
    void testCheckPermissionWithAccessControl() {
        var accessControl = new ssg.legoflow.service.user.AccessControl();
        accessControl.requireRole("admin_operation", ServiceRole.ADMIN);

        ServiceContext adminCtx = new DefaultServiceContext(
            ServiceUser.exact("admin", "Admin", java.util.Set.of(ServiceRole.ADMIN)),
            accessControl
        );

        ServiceContext anonCtx = new DefaultServiceContext(
            ServiceUser.anonymous(),
            accessControl
        );

        // Admin should be allowed
        assertDoesNotThrow(() -> adminCtx.checkPermission("admin_operation"));

        // Anonymous should be denied
        assertThrows(ssg.legoflow.service.user.AccessControl.AccessDeniedException.class,
            () -> anonCtx.checkPermission("admin_operation"));
    }

    @Test
    void testChannelManagerNotConfigured() {
        // Channel manager not set by default
        assertNull(ctx.getChannelManager());
    }

    @Test
    void testChannelRegisterThrowsWhenManagerNull() {
        var service = new ssg.legoflow.service.demo.procedural.EchoService();
        // This should throw because channel manager is not configured
        assertThrows(IllegalStateException.class, () -> {
            ctx.registerChannel(service, null);
        });
    }

    @Test
    void testAttributesInheritedFromContext() {
        ctx.setAttribute("testKey", "testValue");
        assertEquals("testValue", ctx.getAttribute("testKey"));
    }

    @Test
    void testStatisticsInheritedFromContext() {
        assertNotNull(ctx.getStatistics());
    }

    @Test
    void testLoggerInheritedFromContext() {
        assertNotNull(ctx.getLogger());
    }
}
