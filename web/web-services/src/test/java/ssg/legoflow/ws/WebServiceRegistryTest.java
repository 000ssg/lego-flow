package ssg.legoflow.ws;

import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.server.HttpRouter;
import ssg.legoflow.ws.demo.HelloWorldService;
import ssg.legoflow.ws.demo.EchoWebService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class WebServiceRegistryTest {

    @Test
    void testRegisterAndGet() {
        var registry = new WebServiceRegistry();
        var hello = new HelloWorldService();
        registry.register(hello);
        assertThat(registry.getService("/hello")).isSameAs(hello);
    }

    @Test
    void testUnregister() {
        var registry = new WebServiceRegistry();
        registry.register(new HelloWorldService());
        registry.unregister("/hello");
        assertThat(registry.getService("/hello")).isNull();
    }

    @Test
    void testGetServices() {
        var registry = new WebServiceRegistry();
        registry.register(new HelloWorldService());
        registry.register(new EchoWebService());
        assertThat(registry.getServices()).hasSize(2);
    }

    @Test
    void testInstallRoutes() {
        var registry = new WebServiceRegistry();
        registry.register(new HelloWorldService());
        var router = new HttpRouter();
        registry.installRoutes(router);
        assertThat(router.getRegisteredPaths()).contains("/hello");
    }
}
