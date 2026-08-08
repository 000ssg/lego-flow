package ssg.legoflow.http.demo.server;

import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.server.HttpServer;

/**
 * Minimal HTTP server demo with a single GET route returning "Hello, World!".
 *
 * <p>Uses the {@code serverMinimal} profile which includes only core HTTP features,
 * fixed-length transfer, and basic connection management.
 *
 * @since 0.1
 */
public class MinimalServer {

    private final HttpServer server;

    public MinimalServer() {
        this(8080);
    }

    public MinimalServer(int port) {
        var config = new ServerConfig(StandardProfiles.serverMinimal());
        config.setPort(port);
        this.server = new HttpServer("minimal-server", config);

        server.getRouter().get("/", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "Hello, World!"));
    }

    /**
     * Returns the underlying HttpServer instance.
     *
     * @return the server
     */
    public HttpServer getServer() {
        return server;
    }
}
