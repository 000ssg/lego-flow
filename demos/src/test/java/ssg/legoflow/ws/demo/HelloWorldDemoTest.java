package ssg.legoflow.ws.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.server.HttpRouter;
import ssg.legoflow.ws.WebServiceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class HelloWorldDemoTest {

    private HttpRouter router;

    @BeforeEach
    void setUp() {
        var registry = new WebServiceRegistry();
        registry.register(new HelloWorldService());
        router = new HttpRouter();
        registry.installRoutes(router);
    }

    @Test
    void testHelloWorldGet() {
        var request = HttpRequest.of(HttpMethod.GET, "/hello");
        var response = router.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("Hello, World!");
    }

    @Test
    void testHelloWorldNotFound() {
        var request = HttpRequest.of(HttpMethod.GET, "/unknown");
        var response = router.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
