package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.demo.SimpleMediaRendererDemo;
import ssg.legoflow.upnp.demo.SimpleMediaServerDemo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MccWebServer}.
 *
 * @since 1.0.0
 */
class MccWebServerTest {

    private MccWebServer server;
    private ControlPoint controlPoint;
    private SimpleMediaServerDemo serverDemo;
    private SimpleMediaRendererDemo rendererDemo;

    @BeforeEach
    void setUp() {
        serverDemo = new SimpleMediaServerDemo();
        rendererDemo = new SimpleMediaRendererDemo();
        serverDemo.start();
        rendererDemo.start();

        controlPoint = new ControlPoint();
        controlPoint.start();
        controlPoint.registerLocalServer(serverDemo.getServer());
        controlPoint.registerLocalRenderer(rendererDemo.getRenderer());

        server = new MccWebServer(0, controlPoint);  // port 0 = auto-assign for tests
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        controlPoint.stop();
        rendererDemo.stop();
        serverDemo.stop();
    }

    @Test
    void testServerStarts() {
        // Then
        assertThat(server.isRunning()).isTrue();
        assertThat(server.getPort()).isGreaterThan(0);
    }

    @Test
    void testServeIndex() {
        // When
        HttpRequest request = HttpRequest.of(HttpMethod.GET, "/");
        HttpResponse response = server.handleRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        assertThat(body).contains("<!DOCTYPE html>");
        assertThat(body).contains("Media Control Center");
        assertThat(body).contains("react");
    }

    @Test
    void testCorsHeaders() {
        // When
        HttpRequest request = HttpRequest.of(HttpMethod.GET, "/api/devices");
        HttpResponse response = server.handleRequest(request);

        // Then
        assertThat(response.getHeaders().get("access-control-allow-origin")).isEqualTo("*");
        assertThat(response.getHeaders().get("access-control-allow-methods"))
                .contains("GET", "POST", "PUT");
    }

    @Test
    void testApiDevices() {
        // When
        HttpRequest request = HttpRequest.of(HttpMethod.GET, "/api/devices");
        HttpResponse response = server.handleRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        assertThat(body).startsWith("[");
        assertThat(body).endsWith("]");
        assertThat(body).contains("\"udn\":");
        assertThat(body).contains("\"friendlyName\":");
    }

    @Test
    void testApiNotFound() {
        // When
        HttpRequest request = HttpRequest.of(HttpMethod.GET, "/api/nonexistent");
        HttpResponse response = server.handleRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        String body = response.getBodyAsString();
        assertThat(body).contains("\"error\":");
    }

    @Test
    void testServerStop() {
        // When
        server.stop();

        // Then
        assertThat(server.isRunning()).isFalse();

        // Requests return 503 when stopped
        HttpRequest request = HttpRequest.of(HttpMethod.GET, "/api/devices");
        HttpResponse response = server.handleRequest(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
