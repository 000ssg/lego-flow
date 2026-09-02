package ssg.legoflow.service.demo.procedural;

import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.user.ServiceRole;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class AccessControlDemoTest {

    private AuthenticatedService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthenticatedService();
    }

    @Test
    void testAnonymousCanRead() {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        authService.connect(ctx);
        authService.consume(ctx, "read");
        assertThat(authService.getStatistics().getInCount(String.class)).isEqualTo(1);
    }

    @Test
    void testAnonymousCannotWrite() {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        authService.connect(ctx);
        authService.consume(ctx, "write");
        assertThat(authService.getStatistics().getOutCount(String.class)).isEqualTo(1);
    }

    @Test
    void testUserCanWrite() {
        var user = ServiceUser.exact("u1", "User1", Set.of(ServiceRole.USER));
        var ctx = new DefaultServiceContext(user);
        authService.connect(ctx);
        authService.consume(ctx, "write");
        assertThat(authService.getStatistics().getOutCount(String.class)).isEqualTo(1);
    }

    @Test
    void testAdminCanDelete() {
        var admin = ServiceUser.exact("a1", "Admin", Set.of(ServiceRole.ADMIN, ServiceRole.USER));
        var ctx = new DefaultServiceContext(admin);
        authService.connect(ctx);
        authService.consume(ctx, "delete");
        assertThat(authService.getStatistics().getOutCount(String.class)).isEqualTo(1);
    }

    @Test
    void testUserCannotDelete() {
        var user = ServiceUser.exact("u1", "User1", Set.of(ServiceRole.USER));
        var ctx = new DefaultServiceContext(user);
        authService.connect(ctx);
        authService.consume(ctx, "delete");
        assertThat(authService.getStatistics().getOutCount(String.class)).isEqualTo(1);
    }
}
