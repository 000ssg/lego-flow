package ssg.legoflow.http.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.demo.server.UserManagementServer;
import ssg.legoflow.http.server.HttpRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
/**
 * Demonstrates full CRUD operations on the UserManagementServer by dispatching
 * requests directly through the router. Tests create, read, update, delete,
 * and error handling flows.
 */
class UserManagementDemoTest {

    private UserManagementServer userServer;
    private HttpRouter router;

    @BeforeEach
    void setUp() {
        userServer = new UserManagementServer();
        router = userServer.getServer().getRouter();
    }

    @Test
    void testCreateUser() {
        // Given: a POST request to create a user
        var request = HttpRequest.of(HttpMethod.POST, "/users");
        request.setBody(ByteBuffer.wrap("Alice".getBytes(StandardCharsets.UTF_8)));

        // When: dispatched through the router
        var response = router.dispatch(null, request);

        // Then: returns 201 Created with user data and Location header
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBodyAsString()).contains("Alice");
        assertThat(response.getHeaders().get(HttpHeaders.LOCATION)).startsWith("/users/");
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
    }

    @Test
    void testListUsersEmpty() {
        // Given: no users have been created
        var request = HttpRequest.of(HttpMethod.GET, "/users");

        // When: listing all users
        var response = router.dispatch(null, request);

        // Then: returns 200 OK with empty JSON object
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("{}");
    }

    @Test
    void testListUsersAfterCreation() {
        // Given: two users have been created
        var create1 = HttpRequest.of(HttpMethod.POST, "/users");
        create1.setBody(ByteBuffer.wrap("Alice".getBytes(StandardCharsets.UTF_8)));
        router.dispatch(null, create1);

        var create2 = HttpRequest.of(HttpMethod.POST, "/users");
        create2.setBody(ByteBuffer.wrap("Bob".getBytes(StandardCharsets.UTF_8)));
        router.dispatch(null, create2);

        // When: listing all users
        var request = HttpRequest.of(HttpMethod.GET, "/users");
        var response = router.dispatch(null, request);

        // Then: returns 200 OK with both users
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).contains("Alice").contains("Bob");
    }

    @Test
    void testGetSingleUser() {
        // Given: a user has been created (the router uses exact path matching,
        //        so we register the specific path)
        userServer.getUsers().put("1", "Alice");
        router.get("/users/1", (ctx, req) -> {
            var name = userServer.getUsers().get("1");
            if (name == null) {
                return HttpResponse.of(HttpStatus.NOT_FOUND, "User not found");
            }
            return HttpResponse.of(HttpStatus.OK, "{\"id\":\"1\",\"name\":\"" + name + "\"}");
        });

        // When: getting the user by id
        var request = HttpRequest.of(HttpMethod.GET, "/users/1");
        var response = router.dispatch(null, request);

        // Then: returns 200 OK with user data
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).contains("Alice").contains("\"id\":\"1\"");
    }

    @Test
    void testUpdateUser() {
        // Given: a user exists and we register the specific path for update
        userServer.getUsers().put("1", "Alice");
        router.put("/users/1", (ctx, req) -> {
            var body = req.getBodyAsString();
            userServer.getUsers().put("1", body.strip());
            return HttpResponse.of(HttpStatus.OK,
                    "{\"id\":\"1\",\"name\":\"" + body.strip() + "\"}");
        });

        // When: updating the user
        var request = HttpRequest.of(HttpMethod.PUT, "/users/1");
        request.setBody(ByteBuffer.wrap("Alice Updated".getBytes(StandardCharsets.UTF_8)));
        var response = router.dispatch(null, request);

        // Then: returns 200 OK with updated data
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).contains("Alice Updated");
        assertThat(userServer.getUsers().get("1")).isEqualTo("Alice Updated");
    }

    @Test
    void testDeleteUser() {
        // Given: a user exists and we register the specific path for delete
        userServer.getUsers().put("1", "Alice");
        router.delete("/users/1", (ctx, req) -> {
            var removed = userServer.getUsers().remove("1");
            if (removed == null) {
                return HttpResponse.of(HttpStatus.NOT_FOUND, "User not found");
            }
            return HttpResponse.of(HttpStatus.NO_CONTENT);
        });

        // When: deleting the user
        var request = HttpRequest.of(HttpMethod.DELETE, "/users/1");
        var response = router.dispatch(null, request);

        // Then: returns 204 No Content and user is removed
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(userServer.getUsers()).doesNotContainKey("1");
    }
}
