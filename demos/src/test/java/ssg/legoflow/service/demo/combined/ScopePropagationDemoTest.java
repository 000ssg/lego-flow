package ssg.legoflow.service.demo.combined;

import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.scope.*;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ScopePropagationDemoTest {

    @Test
    void testSiteScope() {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ctx.getSiteScope().setAttribute("site.name", "TestSite");
        assertThat(ctx.getSiteScope().<String>getAttribute("site.name")).isEqualTo("TestSite");
    }

    @Test
    void testApplicationScope() {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ctx.getApplicationScope().setAttribute("app.version", "1.0");
        assertThat(ctx.getApplicationScope().<String>getAttribute("app.version")).isEqualTo("1.0");
    }

    @Test
    void testSessionScopeIsolation() {
        var ctx1 = new DefaultServiceContext(ServiceUser.anonymous());
        var ctx2 = new DefaultServiceContext(ServiceUser.shared("team"));
        ctx1.getSessionScope().setAttribute("counter", 10);
        ctx2.getSessionScope().setAttribute("counter", 20);
        assertThat(ctx1.getSessionScope().<Integer>getAttribute("counter")).isEqualTo(10);
        assertThat(ctx2.getSessionScope().<Integer>getAttribute("counter")).isEqualTo(20);
    }

    @Test
    void testRequestScopeLifecycle() {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ctx.getRequestScope().setAttribute("req.id", "R001");
        assertThat(ctx.getRequestScope().<String>getAttribute("req.id")).isEqualTo("R001");
        ctx.getRequestScope().destroy();
        assertThat(ctx.getRequestScope().<String>getAttribute("req.id")).isNull();
    }

    @Test
    void testAllScopesHierarchy() {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ctx.getSiteScope().setAttribute("level", "site");
        ctx.getApplicationScope().setAttribute("level", "app");
        ctx.getSessionScope().setAttribute("level", "session");
        ctx.getRequestScope().setAttribute("level", "request");

        assertThat(ctx.getSiteScope().<String>getAttribute("level")).isEqualTo("site");
        assertThat(ctx.getApplicationScope().<String>getAttribute("level")).isEqualTo("app");
        assertThat(ctx.getSessionScope().<String>getAttribute("level")).isEqualTo("session");
        assertThat(ctx.getRequestScope().<String>getAttribute("level")).isEqualTo("request");
    }

    @Test
    void testScopeDestroyDoesNotAffectOther() {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ctx.getSessionScope().setAttribute("data", "value");
        ctx.getRequestScope().setAttribute("data", "value");
        ctx.getRequestScope().destroy();
        assertThat(ctx.getSessionScope().<String>getAttribute("data")).isEqualTo("value");
        assertThat(ctx.getRequestScope().<String>getAttribute("data")).isNull();
    }
}
