package ssg.legoflow.ws.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.server.HttpRouter;
import ssg.legoflow.ws.WebServiceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class TodoApiDemoTest {

    private HttpRouter router;
    private TodoApiService todoService;

    @BeforeEach
    void setUp() {
        todoService = new TodoApiService();
        var registry = new WebServiceRegistry();
        registry.register(todoService);
        router = new HttpRouter();
        registry.installRoutes(router);
    }

    @Test
    void testCreateTodo() {
        var request = HttpRequest.of(HttpMethod.POST, "/todos");
        request.setBody(ByteBuffer.wrap("{\"title\":\"Buy milk\"}".getBytes(StandardCharsets.UTF_8)));
        var response = router.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBodyAsString(StandardCharsets.UTF_8)).contains("Buy milk");
        assertThat(todoService.getTodoCount()).isEqualTo(1);
    }

    @Test
    void testListTodos() {
        var create = HttpRequest.of(HttpMethod.POST, "/todos");
        create.setBody(ByteBuffer.wrap("{\"title\":\"Task 1\"}".getBytes(StandardCharsets.UTF_8)));
        router.dispatch(null, create);
        create = HttpRequest.of(HttpMethod.POST, "/todos");
        create.setBody(ByteBuffer.wrap("{\"title\":\"Task 2\"}".getBytes(StandardCharsets.UTF_8)));
        router.dispatch(null, create);

        var list = HttpRequest.of(HttpMethod.GET, "/todos");
        var response = router.dispatch(null, list);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        var body = response.getBodyAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("Task 1").contains("Task 2");
    }

    @Test
    void testDeleteTodo() {
        var create = HttpRequest.of(HttpMethod.POST, "/todos");
        create.setBody(ByteBuffer.wrap("{\"title\":\"To delete\"}".getBytes(StandardCharsets.UTF_8)));
        router.dispatch(null, create);
        assertThat(todoService.getTodoCount()).isEqualTo(1);

        var delete = HttpRequest.of(HttpMethod.DELETE, "/todos?id=1");
        var response = router.dispatch(null, delete);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(todoService.getTodoCount()).isEqualTo(0);
    }

    @Test
    void testDeleteNonExistent() {
        var delete = HttpRequest.of(HttpMethod.DELETE, "/todos?id=999");
        var response = router.dispatch(null, delete);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testCrudLifecycle() {
        var create = HttpRequest.of(HttpMethod.POST, "/todos");
        create.setBody(ByteBuffer.wrap("{\"title\":\"Original\"}".getBytes(StandardCharsets.UTF_8)));
        var created = router.dispatch(null, create);
        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);

        var list = HttpRequest.of(HttpMethod.GET, "/todos");
        var listed = router.dispatch(null, list);
        assertThat(listed.getBodyAsString(StandardCharsets.UTF_8)).contains("Original");

        var update = HttpRequest.of(HttpMethod.PUT, "/todos");
        update.setBody(ByteBuffer.wrap("{\"id\":\"1\",\"title\":\"Updated\"}".getBytes(StandardCharsets.UTF_8)));
        var updated = router.dispatch(null, update);
        assertThat(updated.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBodyAsString(StandardCharsets.UTF_8)).contains("Updated");
    }
}
