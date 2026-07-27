package ssg.legoflow.http.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.demo.client.MinimalClient;
import ssg.legoflow.http.demo.server.MinimalServer;
import ssg.legoflow.http.server.HttpRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Demonstrates creating a MinimalServer and dispatching requests through
 * the router directly (no socket). Verifies request handling, status codes,
 * and response bodies.
 */
class SimpleServerClientDemoTest {

    private MinimalServer minimalServer;
    private MinimalClient minimalClient;
    private HttpRouter router;

    @BeforeEach
    void setUp() {
        minimalServer = new MinimalServer();
        minimalClient = new MinimalClient();
        router = minimalServer.getServer().getRouter();
    }

    @Test
    void testGetRootReturnsHelloWorld() {
        // Given: a GET request to the root path
        var request = minimalClient.createGetRequest("/");

        // When: dispatched through the router
        var response = router.dispatch(null, request);

        // Then: returns 200 OK with "Hello, World!" body
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("Hello, World!");
    }

    @Test
    void testGetUnknownPathReturns404() {
        // Given: a GET request to an unknown path
        var request = minimalClient.createGetRequest("/unknown");

        // When: dispatched through the router
        var response = router.dispatch(null, request);

        // Then: returns 404 Not Found
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBodyAsString()).isEqualTo("Not Found");
    }

    @Test
    void testPostToRootReturns405MethodNotAllowed() {
        // Given: a POST request to the root path (only GET is registered)
        var request = minimalClient.createPostRequest("/", "some body");

        // When: dispatched through the router
        var response = router.dispatch(null, request);

        // Then: returns 405 Method Not Allowed with Allow header
        assertThat(response.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().get("allow")).contains("GET");
    }

    @Test
    void testMultipleSequentialRequests() {
        // Given: multiple GET requests to root
        var req1 = minimalClient.createGetRequest("/");
        var req2 = minimalClient.createGetRequest("/");
        var req3 = minimalClient.createGetRequest("/");

        // When: dispatched sequentially
        var resp1 = router.dispatch(null, req1);
        var resp2 = router.dispatch(null, req2);
        var resp3 = router.dispatch(null, req3);

        // Then: all return the same successful response
        assertThat(resp1.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(resp2.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(resp3.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(resp1.getBodyAsString()).isEqualTo(resp2.getBodyAsString());
    }

    @Test
    void testServerConfigAndRoutes() {
        // Given: the minimal server instance

        // Then: server has expected configuration
        assertThat(minimalServer.getServer().getConfig().getPort()).isEqualTo(8080);
        assertThat(router.getRegisteredPaths()).contains("/");

        // And: client is properly configured
        assertThat(minimalClient.getClient()).isNotNull();
        assertThat(minimalClient.getClient().getConfig()).isNotNull();
    }
}
