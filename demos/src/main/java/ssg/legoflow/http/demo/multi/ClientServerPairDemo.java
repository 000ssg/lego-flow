package ssg.legoflow.http.demo.multi;

import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.client.HttpClient;
import ssg.legoflow.http.client.HttpClientBuilder;
import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.*;
import ssg.legoflow.http.server.HttpServer;
/**
 * Demo pairing an HTTP client and server in the same process for full request-response exchange.
 *
 * <p>The client creates requests and dispatches them through the server's
 * {@link HttpServer#handleRequest} method, simulating a full exchange
 * without opening network sockets.
 *
 * @since 0.1
 */
public class ClientServerPairDemo {

    private final HttpServer server;
    private final HttpClient client;
    private final DefaultContext ctx;

    public ClientServerPairDemo() {
        this(8090);
    }

    public ClientServerPairDemo(int port) {
        var serverConfig = new ServerConfig(StandardProfiles.serverStandard());
        serverConfig.setPort(port);
        this.server = new HttpServer("pair-server", serverConfig);
        this.client = new HttpClientBuilder().standard().build();
        this.ctx = new DefaultContext();

        server.getRouter().get("/ping", (httpCtx, req) ->
                HttpResponse.of(HttpStatus.OK, "pong"));

        server.getRouter().post("/echo", (httpCtx, req) -> {
            var body = req.getBodyAsString();
            var response = HttpResponse.of(HttpStatus.OK, body != null ? body : "");
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE,
                    req.getHeaders().get(HttpHeaders.CONTENT_TYPE) != null
                            ? req.getHeaders().get(HttpHeaders.CONTENT_TYPE)
                            : "text/plain");
            return response;
        });

        server.getRouter().get("/headers", (httpCtx, req) -> {
            var sb = new StringBuilder("{");
            var names = req.getHeaders().names().stream().sorted().toList();
            for (int i = 0; i < names.size(); i++) {
                sb.append("\"").append(names.get(i)).append("\":\"")
                        .append(req.getHeaders().get(names.get(i))).append("\"");
                if (i < names.size() - 1) sb.append(",");
            }
            sb.append("}");
            var response = HttpResponse.of(HttpStatus.OK, sb.toString());
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            return response;
        });
    }

    /**
     * Sends a request through the paired server and returns the response.
     *
     * @param request the HTTP request
     * @return the HTTP response
     */
    public HttpResponse exchange(HttpRequest request) {
        return server.handleRequest(ctx, request);
    }

    /**
     * Returns the server instance.
     *
     * @return the server
     */
    public HttpServer getServer() {
        return server;
    }

    /**
     * Returns the client instance.
     *
     * @return the client
     */
    public HttpClient getClient() {
        return client;
    }
}
