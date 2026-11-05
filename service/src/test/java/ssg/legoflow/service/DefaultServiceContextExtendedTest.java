package ssg.legoflow.service;

import ssg.legoflow.service.user.AccessControl;
import ssg.legoflow.service.user.ServiceRole;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultServiceContextExtendedTest {

    private DefaultServiceContext ctx;
    private AccessControl accessControl;

    @BeforeEach
    void setUp() {
        accessControl = new AccessControl();
        ctx = new DefaultServiceContext(ServiceUser.anonymous(), accessControl);
    }

    @Test
    void testConstructorWithAccessControl() {
        assertNotNull(ctx.getUser());
        assertNotNull(ctx.getSiteScope());
        assertNotNull(ctx.getApplicationScope());
        assertNotNull(ctx.getSessionScope());
        assertNotNull(ctx.getRequestScope());
    }

    @Test
    void testConstructorWithLogger() {
        var logger = org.slf4j.LoggerFactory.getLogger(DefaultServiceContext.class);
        var stats = new ssg.legoflow.blocks.ProcessorStatistics();
        var siteScope = new ssg.legoflow.service.scope.SiteScope();
        var appScope = new ssg.legoflow.service.scope.ApplicationScope();
        var sessionScope = new ssg.legoflow.service.scope.SessionScope();
        var requestScope = new ssg.legoflow.service.scope.RequestScope();
        var user = ServiceUser.anonymous();

        var customCtx = new DefaultServiceContext(logger, stats, siteScope, appScope, sessionScope, requestScope, user, accessControl);

        assertNotNull(customCtx.getLogger());
        assertNotNull(customCtx.getStatistics());
        assertEquals(siteScope, customCtx.getSiteScope());
        assertEquals(appScope, customCtx.getApplicationScope());
        assertEquals(sessionScope, customCtx.getSessionScope());
        assertEquals(requestScope, customCtx.getRequestScope());
    }

    @Test
    void testHandleErrorLogs() {
        assertDoesNotThrow(() -> ctx.handleError(new RuntimeException("test error")));
    }

    @Test
    void testGetAttributeNullWhenNotSet() {
        assertNull(ctx.getAttribute("nonexistent"));
    }

    @Test
    void testSetAndRemoveAttribute() {
        ctx.setAttribute("key", "value");
        assertEquals("value", ctx.getAttribute("key"));
        ctx.setAttribute("key", null);
        assertNull(ctx.getAttribute("key"));
    }

    @Test
    void testSetAndGetDifferentTypes() {
        ctx.setAttribute("string", "hello");
        ctx.setAttribute("integer", 42);
        ctx.setAttribute("list", java.util.List.of("a", "b"));
        ctx.setAttribute("map", java.util.Map.of("k", "v"));

        assertEquals("hello", ctx.getAttribute("string"));
        assertEquals((Integer) 42, ctx.getAttribute("integer"));
        assertEquals(java.util.List.of("a", "b"), ctx.getAttribute("list"));
        assertEquals(java.util.Map.of("k", "v"), ctx.getAttribute("map"));
    }

    @Test
    void testGetUserAnonymous() {
        ServiceUser user = ctx.getUser();
        assertNotNull(user);
        assertEquals("anonymous", user.getId());
        assertEquals("Anonymous", user.getName());
    }

    @Test
    void testHasRoleAnonymous() {
        assertTrue(ctx.hasRole(ServiceRole.GUEST));
        assertFalse(ctx.hasRole(ServiceRole.USER));
        assertFalse(ctx.hasRole(ServiceRole.ADMIN));
    }

    @Test
    void testHasRoleUser() {
        var userCtx = new DefaultServiceContext(ServiceUser.shared("test"), accessControl);
        assertTrue(userCtx.hasRole(ServiceRole.USER));
        assertFalse(userCtx.hasRole(ServiceRole.ADMIN));
    }

    @Test
    void testHasRoleAdmin() {
        var userCtx = new DefaultServiceContext(
            ServiceUser.exact("admin", "Admin", java.util.Set.of(ServiceRole.ADMIN, ServiceRole.USER)),
            accessControl
        );
        assertTrue(userCtx.hasRole(ServiceRole.ADMIN));
        assertTrue(userCtx.hasRole(ServiceRole.USER));
    }

    @Test
    void testCheckPermissionAllowed() {
        // No restrictions configured, should always allow
        assertDoesNotThrow(() -> ctx.checkPermission("any_operation"));
    }

    @Test
    void testCheckPermissionDenied() {
        accessControl.requireRole("protected_op", ServiceRole.ADMIN);

        assertThrows(AccessControl.AccessDeniedException.class,
            () -> ctx.checkPermission("protected_op"));
    }

    @Test
    void testCheckPermissionAdminAllowed() {
        accessControl.requireRole("admin_op", ServiceRole.ADMIN);

        var adminCtx = new DefaultServiceContext(
            ServiceUser.exact("admin", "Admin", java.util.Set.of(ServiceRole.ADMIN)),
            accessControl
        );

        assertDoesNotThrow(() -> adminCtx.checkPermission("admin_op"));
    }

    @Test
    void testAccessDeniedExceptionMessage() {
        AccessControl.AccessDeniedException ex = assertThrows(
            AccessControl.AccessDeniedException.class,
            () -> {
                accessControl.requireRole("op", ServiceRole.ADMIN);
                ctx.checkPermission("op");
            }
        );

        assertTrue(ex.getMessage().contains("denied"));
        assertTrue(ex.getMessage().contains("op"));
    }

    @Test
    void testAccessDeniedExceptionIsRuntimeException() {
        assertInstanceOf(RuntimeException.class,
            new AccessControl.AccessDeniedException("user", "op"));
    }

    @Test
    void testMultipleOperations() {
        accessControl.requireRole("op1", ServiceRole.ADMIN);
        accessControl.requireRole("op2", ServiceRole.ADMIN);

        // Anonymous user denied for op1 (requires ADMIN)
        assertThrows(AccessControl.AccessDeniedException.class,
            () -> ctx.checkPermission("op1"));

        // Anonymous user denied for op2 (requires ADMIN)
        assertThrows(AccessControl.AccessDeniedException.class,
            () -> ctx.checkPermission("op2"));
    }

    @Test
    void testMultipleRolesRequired() {
        accessControl.requireRoles("multi_op", java.util.Set.of(ServiceRole.USER, ServiceRole.ADMIN));

        // User role should satisfy the requirement
        var userCtx = new DefaultServiceContext(ServiceUser.shared("user"), accessControl);
        assertDoesNotThrow(() -> userCtx.checkPermission("multi_op"));
    }
}
