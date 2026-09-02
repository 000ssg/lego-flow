package ssg.legoflow.ws.demo;

import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class MultiEndpointDemoTest {

    private CompositeApiServer server;

    @BeforeEach
    void setUp() {
        server = new CompositeApiServer();
    }

    @Test
    void testHealthEndpoint() {
        var request = HttpRequest.of(HttpMethod.GET, "/health");
        var response = server.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString(StandardCharsets.UTF_8)).contains("UP");
    }

    @Test
    void testTodosEndpoint() {
        var create = HttpRequest.of(HttpMethod.POST, "/todos");
        create.setBody(ByteBuffer.wrap("{\"title\":\"Composite task\"}".getBytes(StandardCharsets.UTF_8)));
        var response = server.dispatch(null, create);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);

        var list = HttpRequest.of(HttpMethod.GET, "/todos");
        var listed = server.dispatch(null, list);
        assertThat(listed.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBodyAsString(StandardCharsets.UTF_8)).contains("Composite task");
    }

    @Test
    void testUsersEndpointListEmpty() {
        var request = HttpRequest.of(HttpMethod.GET, "/users");
        var response = server.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString(StandardCharsets.UTF_8)).isEqualTo("[]");
    }

    @Test
    void testUnknownEndpoint() {
        var request = HttpRequest.of(HttpMethod.GET, "/unknown");
        var response = server.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testAllEndpointsCoexist() {
        var health = HttpRequest.of(HttpMethod.GET, "/health");
        assertThat(server.dispatch(null, health).getStatus()).isEqualTo(HttpStatus.OK);

        var todos = HttpRequest.of(HttpMethod.GET, "/todos");
        assertThat(server.dispatch(null, todos).getStatus()).isEqualTo(HttpStatus.OK);

        var users = HttpRequest.of(HttpMethod.GET, "/users");
        assertThat(server.dispatch(null, users).getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testTodoAndUserIndependent() {
        var createTodo = HttpRequest.of(HttpMethod.POST, "/todos");
        createTodo.setBody(ByteBuffer.wrap("{\"title\":\"Task\"}".getBytes(StandardCharsets.UTF_8)));
        server.dispatch(null, createTodo);

        assertThat(server.getTodoService().getTodoCount()).isEqualTo(1);
        assertThat(server.getUserService().getUserCount()).isEqualTo(0);
    }

    @Test
    void testRouterRegisteredPaths() {
        var paths = server.getRouter().getRegisteredPaths();
        assertThat(paths).contains("/todos", "/users", "/health");
    }
}
