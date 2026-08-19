package ssg.legoflow.http.demo;

import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.core.*;
import ssg.legoflow.http.demo.server.SecureServer;
import ssg.legoflow.http.security.HstsPolicy;
import ssg.legoflow.http.security.SslConfig;
import ssg.legoflow.http.security.SslFilter;
import ssg.legoflow.http.security.SslHandshakeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class SecureServerDemoTest {

    private SecureServer secureServer;
    private DefaultContext ctx;

    @BeforeEach
    void setUp() {
        var sslConfig = new SslConfig();
        sslConfig.setKeystorePath("/test/keystore.jks");
        sslConfig.setKeystorePassword("secret");
        sslConfig.setProtocols(List.of("TLSv1.3"));
        var hstsPolicy = new HstsPolicy(63072000, true, true);
        secureServer = new SecureServer(8443, sslConfig, hstsPolicy);
        ctx = new DefaultContext();
    }

    @Test
    void testSecureServerReturnsHstsHeader() {
        var request = HttpRequest.of(HttpMethod.GET, "/");

        var response = secureServer.getServer().handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.STRICT_TRANSPORT_SECURITY))
                .contains("max-age=63072000")
                .contains("includeSubDomains")
                .contains("preload");
    }

    @Test
    void testStatusEndpointReturnsJson() {
        var request = HttpRequest.of(HttpMethod.GET, "/status");

        var response = secureServer.getServer().handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
        assertThat(response.getBodyAsString()).contains("\"secure\":true");
    }

    @Test
    void testStatusEndpointAlsoHasHsts() {
        var request = HttpRequest.of(HttpMethod.GET, "/status");

        var response = secureServer.getServer().handleRequest(ctx, request);

        assertThat(response.getHeaders().get(HttpHeaders.STRICT_TRANSPORT_SECURITY)).isNotNull();
    }

    @Test
    void testSslConfigPreserved() {
        assertThat(secureServer.getSslConfig().getKeystorePath()).isEqualTo("/test/keystore.jks");
        assertThat(secureServer.getSslConfig().getProtocols()).containsExactly("TLSv1.3");
    }

    @Test
    void testServerHasSslEnabled() {
        assertThat(secureServer.getServer().getConfig().isSslEnabled()).isTrue();
    }

    @Test
    void testHstsPolicyParsing() {
        var headerValue = secureServer.getHstsPolicy().toHeaderValue();
        var parsed = HstsPolicy.parse(headerValue);

        assertThat(parsed.getMaxAge()).isEqualTo(63072000);
        assertThat(parsed.isIncludeSubDomains()).isTrue();
        assertThat(parsed.isPreload()).isTrue();
    }

    @Test
    void testSslHandshakeLifecycle() {
        var handler = new SslHandshakeHandler(secureServer.getSslConfig());
        handler.beginHandshake();
        assertThat(handler.getState()).isEqualTo(SslHandshakeHandler.HandshakeState.IN_PROGRESS);
        handler.completeHandshake();
        assertThat(handler.getState()).isEqualTo(SslHandshakeHandler.HandshakeState.COMPLETED);
    }

    @Test
    void testSslFilterCreation() {
        var filter = new SslFilter(secureServer.getSslConfig(), SslFilter.Mode.ENCRYPT);

        assertThat(filter.getConfig()).isSameAs(secureServer.getSslConfig());
        assertThat(filter.getMode()).isEqualTo(SslFilter.Mode.ENCRYPT);
    }

    @Test
    void testDefaultSecureServer() {
        var defaultServer = new SecureServer();

        assertThat(defaultServer.getServer()).isNotNull();
        assertThat(defaultServer.getSslConfig()).isNotNull();
        assertThat(defaultServer.getHstsPolicy()).isNotNull();
    }
}
