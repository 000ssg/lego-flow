package ssg.legoflow.http.demo.client;

import ssg.legoflow.http.client.HttpClient;
import ssg.legoflow.http.client.HttpClientBuilder;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.security.SslConfig;

/**
 * HTTPS client demo with custom trust store configuration.
 *
 * <p>Uses {@link SslConfig} to configure a trust store for server certificate
 * verification when communicating over TLS.
 *
 * @since 0.1
 */
public class SecureClient {

    private final HttpClient client;
    private final SslConfig sslConfig;

    public SecureClient() {
        this(defaultSslConfig());
    }

    public SecureClient(SslConfig sslConfig) {
        this.sslConfig = sslConfig;
        this.client = new HttpClientBuilder()
                .full()
                .ssl(sslConfig)
                .build();
    }

    /**
     * Creates a secure GET request for the given path.
     *
     * @param path the request URI path
     * @return a new GET HttpRequest
     */
    public HttpRequest createSecureGetRequest(String path) {
        return HttpRequest.of(HttpMethod.GET, path);
    }

    /**
     * Creates a secure POST request for the given path with a body.
     *
     * @param path the request URI path
     * @param body the request body text
     * @return a new POST HttpRequest with body set
     */
    public HttpRequest createSecurePostRequest(String path, String body) {
        var request = HttpRequest.of(HttpMethod.POST, path);
        request.setBody(java.nio.ByteBuffer.wrap(body.getBytes()));
        return request;
    }

    /**
     * Returns the underlying HttpClient instance.
     *
     * @return the client
     */
    public HttpClient getClient() {
        return client;
    }

    /**
     * Returns the SSL configuration.
     *
     * @return the SSL config
     */
    public SslConfig getSslConfig() {
        return sslConfig;
    }

    private static SslConfig defaultSslConfig() {
        var ssl = new SslConfig();
        ssl.setTruststorePath("/etc/ssl/truststore.jks");
        ssl.setTruststorePassword("changeit");
        return ssl;
    }
}
