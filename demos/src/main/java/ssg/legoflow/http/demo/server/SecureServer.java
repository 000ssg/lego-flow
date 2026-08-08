package ssg.legoflow.http.demo.server;

import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.security.HstsPolicy;
import ssg.legoflow.http.security.SslConfig;
import ssg.legoflow.http.server.HttpServer;

/**
 * HTTPS server demo with TLS and HSTS (Strict-Transport-Security) support.
 *
 * <p>Configures an {@link SslConfig} for TLS and attaches an {@link HstsPolicy}
 * header to every response, enforcing HTTPS connections.
 *
 * @since 0.1
 */
public class SecureServer {

    private final HttpServer server;
    private final SslConfig sslConfig;
    private final HstsPolicy hstsPolicy;

    public SecureServer() {
        this(8443, defaultSslConfig(), new HstsPolicy());
    }

    public SecureServer(int port, SslConfig sslConfig, HstsPolicy hstsPolicy) {
        this.sslConfig = sslConfig;
        this.hstsPolicy = hstsPolicy;

        var config = new ServerConfig(StandardProfiles.serverFull());
        config.setPort(port);
        config.setSslConfig(sslConfig);
        this.server = new HttpServer("secure-server", config);

        var router = server.getRouter();

        router.get("/", (ctx, req) -> {
            var response = HttpResponse.of(HttpStatus.OK, "Secure Hello!");
            applyHsts(response);
            return response;
        });

        router.get("/status", (ctx, req) -> {
            var response = HttpResponse.of(HttpStatus.OK, "{\"secure\":true,\"protocol\":\"TLSv1.3\"}");
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            applyHsts(response);
            return response;
        });
    }

    private void applyHsts(HttpResponse response) {
        response.getHeaders().set(HttpHeaders.STRICT_TRANSPORT_SECURITY, hstsPolicy.toHeaderValue());
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
     * Returns the SSL configuration.
     *
     * @return the SSL config
     */
    public SslConfig getSslConfig() {
        return sslConfig;
    }

    /**
     * Returns the HSTS policy.
     *
     * @return the HSTS policy
     */
    public HstsPolicy getHstsPolicy() {
        return hstsPolicy;
    }

    private static SslConfig defaultSslConfig() {
        var ssl = new SslConfig();
        ssl.setKeystorePath("/etc/ssl/keystore.jks");
        ssl.setKeystorePassword("changeit");
        return ssl;
    }
}
