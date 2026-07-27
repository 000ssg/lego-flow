package ssg.legoflow.http.server;

import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class ConnectHandlerTest {

    @Test
    void testParseValidAuthority() {
        // Given
        var handler = new ConnectHandler((h, p, d) -> {});

        // When
        var hostPort = handler.parseAuthority("example.com:443");

        // Then
        assertThat(hostPort).isNotNull();
        assertThat(hostPort.host()).isEqualTo("example.com");
        assertThat(hostPort.port()).isEqualTo(443);
    }

    @Test
    void testParseAuthorityInvalidFormat() {
        var handler = new ConnectHandler((h, p, d) -> {});

        assertThat(handler.parseAuthority(null)).isNull();
        assertThat(handler.parseAuthority("")).isNull();
        assertThat(handler.parseAuthority("noport")).isNull();
        assertThat(handler.parseAuthority(":443")).isNull();
        assertThat(handler.parseAuthority("host:abc")).isNull();
    }

    @Test
    void testParseAuthorityInvalidPort() {
        var handler = new ConnectHandler((h, p, d) -> {});

        assertThat(handler.parseAuthority("host:0")).isNull();
        assertThat(handler.parseAuthority("host:99999")).isNull();
    }

    @Test
    void testHandleConnectSuccess() {
        // Given
        var tunnelInfo = new AtomicReference<String>();
        var handler = new ConnectHandler((host, port, data) ->
                tunnelInfo.set(host + ":" + port));

        var request = HttpRequest.of(HttpMethod.CONNECT, "example.com:443");
        var ctx = createContext(request);

        // When
        var response = handler.handle(ctx, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(tunnelInfo.get()).isEqualTo("example.com:443");
    }

    @Test
    void testHandleConnectWrongMethod() {
        var handler = new ConnectHandler((h, p, d) -> {});
        var request = HttpRequest.of(HttpMethod.GET, "example.com:443");
        var ctx = createContext(request);

        var response = handler.handle(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void testHandleConnectForbiddenHost() {
        // Given — only allow specific hosts
        var handler = new ConnectHandler(Set.of("allowed.com:443"), (h, p, d) -> {});
        var request = HttpRequest.of(HttpMethod.CONNECT, "forbidden.com:443");
        var ctx = createContext(request);

        // When
        var response = handler.handle(ctx, request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testHandleConnectAllowedHost() {
        var handler = new ConnectHandler(Set.of("allowed.com:443"), (h, p, d) -> {});
        var request = HttpRequest.of(HttpMethod.CONNECT, "allowed.com:443");
        var ctx = createContext(request);

        var response = handler.handle(ctx, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testHostPortToString() {
        var hostPort = new ConnectHandler.HostPort("example.com", 443);
        assertThat(hostPort.toString()).isEqualTo("example.com:443");
    }

    private HttpContext createContext(HttpRequest request) {
        return new HttpContext() {
            @Override public HttpRequest getRequest() { return request; }
            @Override public HttpResponse getResponse() { return null; }
            @Override public void setResponse(HttpResponse response) {}
            @Override public org.slf4j.Logger getLogger() { return org.slf4j.LoggerFactory.getLogger(ConnectHandlerTest.class); }
            @Override public ssg.legoflow.blocks.ProcessorStatistics getStatistics() { return null; }
            @Override public void handleError(Throwable error) {}
            @Override public <T> T getAttribute(String key) { return null; }
            @Override public void setAttribute(String key, Object value) {}
            @Override public ssg.legoflow.service.scope.SiteScope getSiteScope() { return null; }
            @Override public ssg.legoflow.service.scope.ApplicationScope getApplicationScope() { return null; }
            @Override public ssg.legoflow.service.scope.SessionScope getSessionScope() { return null; }
            @Override public ssg.legoflow.service.scope.RequestScope getRequestScope() { return null; }
            @Override public ssg.legoflow.service.user.ServiceUser getUser() { return ssg.legoflow.service.user.ServiceUser.anonymous(); }
            @Override public boolean hasRole(ssg.legoflow.service.user.ServiceRole role) { return false; }
            @Override public void checkPermission(String operation) {}
        };
    }
}
