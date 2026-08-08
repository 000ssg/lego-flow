package ssg.legoflow.http.demo.multi;

import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.server.HttpServer;

import java.util.List;

/**
 * Demo running multiple HTTP servers on different ports with different profiles.
 *
 * <p>Creates a minimal server, a standard server, and a full server, each on
 * a separate port and with different feature sets.
 *
 * @since 0.1
 */
public class MultiServerDemo {

    private final HttpServer minimalServer;
    private final HttpServer standardServer;
    private final HttpServer fullServer;

    public MultiServerDemo() {
        this(9001, 9002, 9003);
    }

    public MultiServerDemo(int minimalPort, int standardPort, int fullPort) {
        var minimalConfig = new ServerConfig(StandardProfiles.serverMinimal());
        minimalConfig.setPort(minimalPort);
        this.minimalServer = new HttpServer("minimal", minimalConfig);
        minimalServer.getRouter().get("/", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "Minimal Server"));
        minimalServer.getRouter().get("/info", (ctx, req) -> {
            var response = HttpResponse.of(HttpStatus.OK, "{\"profile\":\"minimal\",\"port\":" + minimalPort + "}");
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            return response;
        });

        var standardConfig = new ServerConfig(StandardProfiles.serverStandard());
        standardConfig.setPort(standardPort);
        this.standardServer = new HttpServer("standard", standardConfig);
        standardServer.getRouter().get("/", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "Standard Server"));
        standardServer.getRouter().get("/info", (ctx, req) -> {
            var response = HttpResponse.of(HttpStatus.OK, "{\"profile\":\"standard\",\"port\":" + standardPort + "}");
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            return response;
        });

        var fullConfig = new ServerConfig(StandardProfiles.serverFull());
        fullConfig.setPort(fullPort);
        this.fullServer = new HttpServer("full", fullConfig);
        fullServer.getRouter().get("/", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "Full Server"));
        fullServer.getRouter().get("/info", (ctx, req) -> {
            var response = HttpResponse.of(HttpStatus.OK, "{\"profile\":\"full\",\"port\":" + fullPort + "}");
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            return response;
        });
    }

    /**
     * Returns all servers as an ordered list.
     *
     * @return the servers
     */
    public List<HttpServer> getServers() {
        return List.of(minimalServer, standardServer, fullServer);
    }

    /**
     * Returns the minimal-profile server.
     *
     * @return the minimal server
     */
    public HttpServer getMinimalServer() {
        return minimalServer;
    }

    /**
     * Returns the standard-profile server.
     *
     * @return the standard server
     */
    public HttpServer getStandardServer() {
        return standardServer;
    }

    /**
     * Returns the full-profile server.
     *
     * @return the full server
     */
    public HttpServer getFullServer() {
        return fullServer;
    }
}
