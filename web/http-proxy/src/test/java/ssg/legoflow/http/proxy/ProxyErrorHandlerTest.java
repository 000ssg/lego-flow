package ssg.legoflow.http.proxy;

import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyErrorHandlerTest {

    private ProxyErrorHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ProxyErrorHandler("test-proxy");
    }

    @Test
    void testBadGateway() {
        var response = handler.badGateway("connection failed");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBodyAsString()).contains("502");
        assertThat(response.getBodyAsString()).contains("connection failed");
    }

    @Test
    void testGatewayTimeout() {
        var response = handler.gatewayTimeout("read timed out");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBodyAsString()).contains("504");
    }

    @Test
    void testServiceUnavailable() {
        var response = handler.serviceUnavailable("no backends");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBodyAsString()).contains("503");
    }

    @Test
    void testHandleSocketTimeoutException() {
        var response = handler.handleError(new SocketTimeoutException("read timeout"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void testHandleConnectException() {
        var response = handler.handleError(new ConnectException("refused"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void testHandleGenericException() {
        var response = handler.handleError(new RuntimeException("unknown error"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void testProxyNameInBody() {
        var response = handler.badGateway("detail");
        assertThat(response.getBodyAsString()).contains("test-proxy");
    }

    @Test
    void testGetProxyName() {
        assertThat(handler.getProxyName()).isEqualTo("test-proxy");
    }

    @Test
    void testContentTypeHeader() {
        var response = handler.badGateway("error");
        assertThat(response.getHeaders().get("content-type")).contains("text/plain");
    }

    @Test
    void testConnectionCloseHeader() {
        var response = handler.badGateway("error");
        assertThat(response.getHeaders().get("connection")).isEqualTo("close");
    }

    @Test
    void testContentLengthHeader() {
        var response = handler.badGateway("error");
        assertThat(response.getContentLength()).isGreaterThan(0);
    }

    @Test
    void testNullDetail() {
        var response = handler.errorResponse(HttpStatus.BAD_GATEWAY, null);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBodyAsString()).doesNotContain("Detail: null");
    }
}
