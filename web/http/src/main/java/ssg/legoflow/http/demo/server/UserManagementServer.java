package ssg.legoflow.http.demo.server;

import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.server.HttpServer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User management HTTP server demo with full CRUD routes.
 *
 * <p>Manages an in-memory map of users (id to name) and exposes:
 * <ul>
 *   <li>GET /users — list all users as JSON-like text</li>
 *   <li>GET /users/{id} — get a single user by id</li>
 *   <li>POST /users — create a new user (body = name)</li>
 *   <li>PUT /users/{id} — update a user by id (body = new name)</li>
 *   <li>DELETE /users/{id} — delete a user by id</li>
 * </ul>
 *
 * @since 1.0
 */
public class UserManagementServer {

    private final HttpServer server;
    private final Map<String, String> users = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);

    public UserManagementServer() {
        this(8081);
    }

    public UserManagementServer(int port) {
        var config = new ServerConfig(StandardProfiles.serverStandard());
        config.setPort(port);
        this.server = new HttpServer("user-mgmt-server", config);

        var router = server.getRouter();

        // GET /users — list all users
        router.get("/users", (ctx, req) -> {
            var sb = new StringBuilder("{");
            var entries = users.entrySet().stream().toList();
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                sb.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                if (i < entries.size() - 1) sb.append(",");
            }
            sb.append("}");
            var response = HttpResponse.of(HttpStatus.OK, sb.toString());
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            return response;
        });

        // GET /users/{id} — get single user
        router.get("/users/{id}", (ctx, req) -> {
            var id = extractPathParam(req.getUri(), "/users/");
            var name = users.get(id);
            if (name == null) {
                return HttpResponse.of(HttpStatus.NOT_FOUND, "User not found");
            }
            var response = HttpResponse.of(HttpStatus.OK, "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}");
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            return response;
        });

        // POST /users — create user
        router.post("/users", (ctx, req) -> {
            var body = req.getBodyAsString();
            if (body == null || body.isBlank()) {
                return HttpResponse.of(HttpStatus.BAD_REQUEST, "Name is required");
            }
            var id = String.valueOf(idCounter.incrementAndGet());
            users.put(id, body.strip());
            var response = HttpResponse.of(HttpStatus.CREATED,
                    "{\"id\":\"" + id + "\",\"name\":\"" + body.strip() + "\"}");
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            response.getHeaders().set(HttpHeaders.LOCATION, "/users/" + id);
            return response;
        });

        // PUT /users/{id} — update user
        router.put("/users/{id}", (ctx, req) -> {
            var id = extractPathParam(req.getUri(), "/users/");
            if (!users.containsKey(id)) {
                return HttpResponse.of(HttpStatus.NOT_FOUND, "User not found");
            }
            var body = req.getBodyAsString();
            if (body == null || body.isBlank()) {
                return HttpResponse.of(HttpStatus.BAD_REQUEST, "Name is required");
            }
            users.put(id, body.strip());
            var response = HttpResponse.of(HttpStatus.OK,
                    "{\"id\":\"" + id + "\",\"name\":\"" + body.strip() + "\"}");
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            return response;
        });

        // DELETE /users/{id} — delete user
        router.delete("/users/{id}", (ctx, req) -> {
            var id = extractPathParam(req.getUri(), "/users/");
            var removed = users.remove(id);
            if (removed == null) {
                return HttpResponse.of(HttpStatus.NOT_FOUND, "User not found");
            }
            return HttpResponse.of(HttpStatus.NO_CONTENT);
        });
    }

    /**
     * Returns the underlying HttpServer instance.
     *
     * @return the server
     */
    public HttpServer getServer() {
        return server;
    }

    /**
     * Returns the in-memory user store for direct inspection.
     *
     * @return the users map
     */
    public Map<String, String> getUsers() {
        return users;
    }

    private static String extractPathParam(String uri, String prefix) {
        var path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;
        return path.startsWith(prefix) ? path.substring(prefix.length()) : "";
    }
}
