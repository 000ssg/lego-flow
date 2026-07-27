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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MccPlaybackHandler}.
 *
 * @since 1.0.0
 */
class MccPlaybackHandlerTest {

    private MccWebServer server;
    private ControlPoint controlPoint;
    private SimpleMediaServerDemo serverDemo;
    private SimpleMediaRendererDemo rendererDemo;
    private String rendererUdn;

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

        rendererUdn = rendererDemo.getRenderer().getUdn();

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
    void testPlay() {
        // When: play with URI
        HttpRequest request = HttpRequest.of(HttpMethod.POST,
                "/api/renderers/" + rendererUdn + "/play");
        request.setBody(ByteBuffer.wrap(
                "{\"itemUri\":\"http://127.0.0.1:8200/content/track1.mp3\",\"itemMetadata\":\"\"}"
                        .getBytes(StandardCharsets.UTF_8)));
        HttpResponse response = server.handleRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).contains("\"state\":\"PLAYING\"");
    }

    @Test
    void testPause() {
        // Given: start playing first
        startPlayback();

        // When: pause
        HttpResponse response = server.handleRequest(
                HttpRequest.of(HttpMethod.POST,
                        "/api/renderers/" + rendererUdn + "/pause"));

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).contains("\"state\":\"PAUSED_PLAYBACK\"");
    }

    @Test
    void testStop() {
        // Given: start playing first
        startPlayback();

        // When: stop
        HttpResponse response = server.handleRequest(
                HttpRequest.of(HttpMethod.POST,
                        "/api/renderers/" + rendererUdn + "/stop"));

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        // After stop, state is STOPPED or NO_MEDIA_PRESENT
        assertThat(body).containsAnyOf("STOPPED", "NO_MEDIA_PRESENT");
    }

    @Test
    void testSeek() {
        // Given: start playing first
        startPlayback();

        // When: seek to 2:30
        HttpRequest request = HttpRequest.of(HttpMethod.POST,
                "/api/renderers/" + rendererUdn + "/seek");
        request.setBody(ByteBuffer.wrap(
                "{\"position\":\"0:02:30\"}".getBytes(StandardCharsets.UTF_8)));
        HttpResponse response = server.handleRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        assertThat(body).contains("\"state\":");
    }

    @Test
    void testTransportInfo() {
        // Given: start playing
        startPlayback();

        // When
        HttpResponse response = server.handleRequest(
                HttpRequest.of(HttpMethod.GET,
                        "/api/renderers/" + rendererUdn + "/transport"));

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        assertThat(body).contains("\"state\":\"PLAYING\"");
        assertThat(body).contains("\"speed\":\"1\"");
        assertThat(body).contains("\"trackDuration\":");
    }

    @Test
    void testPositionInfo() {
        // Given: start playing
        startPlayback();

        // When
        HttpResponse response = server.handleRequest(
                HttpRequest.of(HttpMethod.GET,
                        "/api/renderers/" + rendererUdn + "/position"));

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        assertThat(body).contains("\"track\":");
        assertThat(body).contains("\"trackDuration\":");
        assertThat(body).contains("\"relTime\":");
    }

    private void startPlayback() {
        HttpRequest request = HttpRequest.of(HttpMethod.POST,
                "/api/renderers/" + rendererUdn + "/play");
        request.setBody(ByteBuffer.wrap(
                "{\"itemUri\":\"http://127.0.0.1:8200/content/track1.mp3\",\"itemMetadata\":\"\"}"
                        .getBytes(StandardCharsets.UTF_8)));
        server.handleRequest(request);
    }
}
