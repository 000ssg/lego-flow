package ssg.legoflow.http.demo.server;

import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.server.HttpServer;
import ssg.legoflow.http.staticcontent.ContentResolver;
import ssg.legoflow.http.staticcontent.StaticContentConfig;
import ssg.legoflow.http.staticcontent.StaticContentHandler;

/**
 * Static file server demo that serves content through a {@link StaticContentHandler}
 * backed by a configurable {@link ContentResolver}.
 *
 * <p>Routes all requests matching the configured URL prefix to the static content handler,
 * and returns a simple "OK" response for the root path.
 *
 * @since 1.0
 */
public class StaticFileServer {

    private final HttpServer server;
    private final StaticContentHandler contentHandler;

    public StaticFileServer(ContentResolver resolver) {
        this(resolver, new StaticContentConfig(), 8082);
    }

    public StaticFileServer(ContentResolver resolver, StaticContentConfig config, int port) {
        var serverConfig = new ServerConfig(StandardProfiles.serverFull());
        serverConfig.setPort(port);
        serverConfig.setStaticContentConfig(config);
        this.server = new HttpServer("static-file-server", serverConfig);
        this.contentHandler = new StaticContentHandler(resolver, config);

        var router = server.getRouter();

        // Root route
        router.get("/", (ctx, req) -> HttpResponse.of(HttpStatus.OK, "Static File Server"));

        // Delegate to static content handler for files under the configured prefix
        router.get(config.getUrlPrefix(), (ctx, req) -> contentHandler.handle(req));
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
     * Returns the static content handler.
     *
     * @return the content handler
     */
    public StaticContentHandler getContentHandler() {
        return contentHandler;
    }
}
