package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;
import ssg.legoflow.upnp.controlpoint.MediaServerProxy;
import ssg.legoflow.upnp.demo.SimpleMediaRendererDemo;
import ssg.legoflow.upnp.demo.SimpleMediaServerDemo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MccApiRouter}.
 *
 * @since 0.1.0
 */
class MccApiRouterTest {

    private MccWebServer server;
    private ControlPoint controlPoint;
    private SimpleMediaServerDemo serverDemo;
    private SimpleMediaRendererDemo rendererDemo;
    private String serverUdn;
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

        serverUdn = serverDemo.getServer().getUdn();
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
    void testListDevices() {
        // When
        HttpResponse response = server.handleRequest(
                HttpRequest.of(HttpMethod.GET, "/api/devices"));

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        assertThat(body).contains(serverUdn);
        assertThat(body).contains(rendererUdn);
    }

    @Test
    void testListServers() {
        // When
        HttpResponse response = server.handleRequest(
                HttpRequest.of(HttpMethod.GET, "/api/devices/servers"));

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        assertThat(body).contains(serverUdn);
        assertThat(body).contains("Lego Flow Demo Media Server");
        assertThat(body).doesNotContain(rendererUdn);
    }

    @Test
    void testListRenderers() {
        // When
        HttpResponse response = server.handleRequest(
                HttpRequest.of(HttpMethod.GET, "/api/devices/renderers"));

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        assertThat(body).contains(rendererUdn);
        assertThat(body).contains("Lego Flow Demo Renderer");
        assertThat(body).doesNotContain(serverUdn);
    }

    @Test
    void testBrowseRoot() {
        // When
        HttpResponse response = server.handleRequest(
                HttpRequest.of(HttpMethod.GET,
                        "/api/servers/" + serverUdn + "/browse/root"));

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        assertThat(body).contains("Music");
        assertThat(body).contains("Video");
        assertThat(body).contains("Photos");
    }

    @Test
    void testBrowseContainer() {
        // When: browse the Music container (id=1), then album (id=10)
        HttpResponse response = server.handleRequest(
                HttpRequest.of(HttpMethod.GET,
                        "/api/servers/" + serverUdn + "/browse?id=10&start=0&count=50"));

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        assertThat(body).contains("Track1.mp3");
        assertThat(body).contains("Track2.mp3");
    }

    @Test
    void testSearchContent() {
        // When
        HttpResponse response = server.handleRequest(
                HttpRequest.of(HttpMethod.GET,
                        "/api/servers/" + serverUdn + "/search?query=Track"));

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        assertThat(body).contains("Track");
    }

    @Test
    void testPlayControl() {
        // Given: browse to get a track
        HttpResponse browseResponse = server.handleRequest(
                HttpRequest.of(HttpMethod.GET,
                        "/api/servers/" + serverUdn + "/browse?id=10&start=0&count=50"));
        assertThat(browseResponse.getStatus()).isEqualTo(HttpStatus.OK);

        // When: play
        HttpRequest playRequest = HttpRequest.of(HttpMethod.POST,
                "/api/renderers/" + rendererUdn + "/play");
        String playBody = "{\"itemUri\":\"http://127.0.0.1:8200/content/track1.mp3\",\"itemMetadata\":\"\"}";
        playRequest.setBody(ByteBuffer.wrap(playBody.getBytes(StandardCharsets.UTF_8)));
        HttpResponse playResponse = server.handleRequest(playRequest);

        // Then
        assertThat(playResponse.getStatus()).isEqualTo(HttpStatus.OK);
        String playJson = playResponse.getBodyAsString();
        assertThat(playJson).contains("\"state\":\"PLAYING\"");

        // When: pause
        HttpResponse pauseResponse = server.handleRequest(
                HttpRequest.of(HttpMethod.POST,
                        "/api/renderers/" + rendererUdn + "/pause"));
        assertThat(pauseResponse.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(pauseResponse.getBodyAsString()).contains("\"state\":\"PAUSED_PLAYBACK\"");

        // When: stop
        HttpResponse stopResponse = server.handleRequest(
                HttpRequest.of(HttpMethod.POST,
                        "/api/renderers/" + rendererUdn + "/stop"));
        assertThat(stopResponse.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(stopResponse.getBodyAsString()).contains("\"state\":\"STOPPED\"");
    }

    @Test
    void testVolumeControl() {
        // When: set volume
        HttpRequest volRequest = HttpRequest.of(HttpMethod.PUT,
                "/api/renderers/" + rendererUdn + "/volume");
        volRequest.setBody(ByteBuffer.wrap("{\"volume\":80}".getBytes(StandardCharsets.UTF_8)));
        HttpResponse volResponse = server.handleRequest(volRequest);

        // Then
        assertThat(volResponse.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(volResponse.getBodyAsString()).contains("\"volume\":80");

        // When: set mute
        HttpRequest muteRequest = HttpRequest.of(HttpMethod.PUT,
                "/api/renderers/" + rendererUdn + "/mute");
        muteRequest.setBody(ByteBuffer.wrap("{\"muted\":true}".getBytes(StandardCharsets.UTF_8)));
        HttpResponse muteResponse = server.handleRequest(muteRequest);

        // Then
        assertThat(muteResponse.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(muteResponse.getBodyAsString()).contains("\"muted\":true");
    }
}
