package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.demo.SimpleMediaServerDemo;
import ssg.legoflow.upnp.demo.SimpleMediaRendererDemo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests that the media proxy correctly streams content from an upstream server
 * to the browser via the /api/media/stream endpoint.
 *
 * @since 0.1.0
 */
class MccMediaProxyTest {

    private MccWebServer webServer;
    private ControlPoint controlPoint;
    private SimpleMediaServerDemo serverDemo;
    private SimpleMediaRendererDemo rendererDemo;
    private com.sun.net.httpserver.HttpServer upstreamServer;
    private int webPort;
    private int upstreamPort;

    /** Fake media content. */
    private static final byte[] FAKE_MEDIA = new byte[4096];
    static {
        // Fill with recognizable pattern
        for (int i = 0; i < FAKE_MEDIA.length; i++) {
            FAKE_MEDIA[i] = (byte) (i & 0xFF);
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        // Start a tiny upstream "DLNA server" that serves fake content
        upstreamServer = com.sun.net.httpserver.HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        upstreamPort = upstreamServer.getAddress().getPort();

        upstreamServer.createContext("/media/test.mp3", exchange -> {
            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                // Handle Range request
                String range = rangeHeader.substring("bytes=".length());
                String[] parts = range.split("-");
                int start = Integer.parseInt(parts[0]);
                int end = (parts.length > 1 && !parts[1].isEmpty())
                        ? Integer.parseInt(parts[1]) : FAKE_MEDIA.length - 1;
                int len = end - start + 1;
                exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
                exchange.getResponseHeaders().set("Content-Range",
                        "bytes " + start + "-" + end + "/" + FAKE_MEDIA.length);
                exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                exchange.sendResponseHeaders(206, len);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(FAKE_MEDIA, start, len);
                }
            } else {
                // Full content
                exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
                exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                exchange.sendResponseHeaders(200, FAKE_MEDIA.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(FAKE_MEDIA);
                }
            }
        });
        upstreamServer.start();

        // Start MCC web server
        serverDemo = new SimpleMediaServerDemo();
        rendererDemo = new SimpleMediaRendererDemo();
        serverDemo.start();
        rendererDemo.start();

        controlPoint = new ControlPoint();
        controlPoint.start();
        controlPoint.registerLocalServer(serverDemo.getServer());
        controlPoint.registerLocalRenderer(rendererDemo.getRenderer());

        webServer = new MccWebServer(0, controlPoint);
        webServer.start();
        webPort = webServer.getPort();
    }

    @AfterEach
    void tearDown() {
        webServer.stop();
        controlPoint.stop();
        rendererDemo.stop();
        serverDemo.stop();
        upstreamServer.stop(0);
    }

    @Test
    void testStreamByUrlReturnsFullContent() throws Exception {
        String upstreamUrl = "http://127.0.0.1:" + upstreamPort + "/media/test.mp3";
        String proxyUrl = "http://127.0.0.1:" + webPort
                + "/api/media/stream?url=" + java.net.URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8)
                + "&mime=audio/mpeg";

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder().uri(URI.create(proxyUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type"))
                    .hasValue("audio/mpeg");
            assertThat(response.headers().firstValueAsLong("content-length"))
                    .hasValue(FAKE_MEDIA.length);
            assertThat(response.body()).isEqualTo(FAKE_MEDIA);
        }
    }

    @Test
    void testStreamByUrlWithRangeReturns206() throws Exception {
        String upstreamUrl = "http://127.0.0.1:" + upstreamPort + "/media/test.mp3";
        String proxyUrl = "http://127.0.0.1:" + webPort
                + "/api/media/stream?url=" + java.net.URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8)
                + "&mime=audio/mpeg";

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(proxyUrl))
                            .header("Range", "bytes=0-99")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            assertThat(response.statusCode()).isEqualTo(206);
            assertThat(response.headers().firstValue("content-range"))
                    .hasValue("bytes 0-99/" + FAKE_MEDIA.length);
            assertThat(response.body()).hasSize(100);
            // Verify first 100 bytes match
            byte[] expected = new byte[100];
            System.arraycopy(FAKE_MEDIA, 0, expected, 0, 100);
            assertThat(response.body()).isEqualTo(expected);
        }
    }

    @Test
    void testStreamByUrlWithMidRangeRequest() throws Exception {
        String upstreamUrl = "http://127.0.0.1:" + upstreamPort + "/media/test.mp3";
        String proxyUrl = "http://127.0.0.1:" + webPort
                + "/api/media/stream?url=" + java.net.URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8)
                + "&mime=audio/mpeg";

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(proxyUrl))
                            .header("Range", "bytes=2048-3071")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            assertThat(response.statusCode()).isEqualTo(206);
            assertThat(response.body()).hasSize(1024);
            byte[] expected = new byte[1024];
            System.arraycopy(FAKE_MEDIA, 2048, expected, 0, 1024);
            assertThat(response.body()).isEqualTo(expected);
        }
    }

    @Test
    void testStreamHasAcceptRangesHeader() throws Exception {
        String upstreamUrl = "http://127.0.0.1:" + upstreamPort + "/media/test.mp3";
        String proxyUrl = "http://127.0.0.1:" + webPort
                + "/api/media/stream?url=" + java.net.URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8)
                + "&mime=audio/mpeg";

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder().uri(URI.create(proxyUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            assertThat(response.headers().firstValue("accept-ranges"))
                    .hasValue("bytes");
        }
    }

    @Test
    void testStreamMissingUrlReturns400() throws Exception {
        String proxyUrl = "http://127.0.0.1:" + webPort + "/api/media/stream";

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder().uri(URI.create(proxyUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).contains("Missing");
        }
    }

    @Test
    void testStreamEmptyMimeUsesUpstreamContentType() throws Exception {
        // Add an image endpoint to the upstream server
        upstreamServer.createContext("/media/photo.jpg", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, FAKE_MEDIA.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(FAKE_MEDIA);
            }
        });

        String upstreamUrl = "http://127.0.0.1:" + upstreamPort + "/media/photo.jpg";
        // Send empty mime — proxy should use upstream's Content-Type
        String proxyUrl = "http://127.0.0.1:" + webPort
                + "/api/media/stream?url=" + java.net.URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8)
                + "&mime=";

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder().uri(URI.create(proxyUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type"))
                    .hasValue("image/jpeg");
            assertThat(response.body()).isEqualTo(FAKE_MEDIA);
        }
    }

    @Test
    void testStreamStarMimeUsesUpstreamContentType() throws Exception {
        upstreamServer.createContext("/media/video.mp4", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "video/mp4");
            exchange.sendResponseHeaders(200, FAKE_MEDIA.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(FAKE_MEDIA);
            }
        });

        String upstreamUrl = "http://127.0.0.1:" + upstreamPort + "/media/video.mp4";
        // Send * as mime — proxy should fall through to upstream Content-Type
        String proxyUrl = "http://127.0.0.1:" + webPort
                + "/api/media/stream?url=" + java.net.URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8)
                + "&mime=*";

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder().uri(URI.create(proxyUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type"))
                    .hasValue("video/mp4");
        }
    }

    @Test
    void testStreamInfersMimeFromUrlExtension() throws Exception {
        // Upstream returns no Content-Type — proxy should infer from .png extension
        upstreamServer.createContext("/media/image.png", exchange -> {
            // Deliberately no Content-Type header
            exchange.sendResponseHeaders(200, FAKE_MEDIA.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(FAKE_MEDIA);
            }
        });

        String upstreamUrl = "http://127.0.0.1:" + upstreamPort + "/media/image.png";
        String proxyUrl = "http://127.0.0.1:" + webPort
                + "/api/media/stream?url=" + java.net.URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8)
                + "&mime=";

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder().uri(URI.create(proxyUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type"))
                    .hasValue("image/png");
        }
    }

    @Test
    void testStreamImageUsesInteractiveTransferMode() throws Exception {
        // DLNA servers reject "Streaming" transfer mode for images with 406.
        // The proxy should use "Interactive" for image/* MIME types.
        // We verify by checking that the upstream receives the correct header.
        var receivedTransferMode = new java.util.concurrent.atomic.AtomicReference<String>();
        upstreamServer.createContext("/media/dlna-photo.jpg", exchange -> {
            receivedTransferMode.set(exchange.getRequestHeaders().getFirst("transferMode.dlna.org"));
            exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, FAKE_MEDIA.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(FAKE_MEDIA);
            }
        });

        String upstreamUrl = "http://127.0.0.1:" + upstreamPort + "/media/dlna-photo.jpg";
        String proxyUrl = "http://127.0.0.1:" + webPort
                + "/api/media/stream?url=" + java.net.URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8)
                + "&mime=image/jpeg";

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder().uri(URI.create(proxyUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(receivedTransferMode.get()).isEqualTo("Interactive");
            assertThat(response.body()).isEqualTo(FAKE_MEDIA);
        }
    }

    @Test
    void testStreamAudioUsesStreamingTransferMode() throws Exception {
        // Audio/video content should use "Streaming" transfer mode
        var receivedTransferMode = new java.util.concurrent.atomic.AtomicReference<String>();
        upstreamServer.createContext("/media/dlna-song.mp3", exchange -> {
            receivedTransferMode.set(exchange.getRequestHeaders().getFirst("transferMode.dlna.org"));
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, FAKE_MEDIA.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(FAKE_MEDIA);
            }
        });

        String upstreamUrl = "http://127.0.0.1:" + upstreamPort + "/media/dlna-song.mp3";
        String proxyUrl = "http://127.0.0.1:" + webPort
                + "/api/media/stream?url=" + java.net.URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8)
                + "&mime=audio/mpeg";

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder().uri(URI.create(proxyUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(receivedTransferMode.get()).isEqualTo("Streaming");
        }
    }

    @Test
    void testStreamUpstreamErrorReturns502() throws Exception {
        String upstreamUrl = "http://127.0.0.1:" + upstreamPort + "/nonexistent";
        String proxyUrl = "http://127.0.0.1:" + webPort
                + "/api/media/stream?url=" + java.net.URLEncoder.encode(upstreamUrl, StandardCharsets.UTF_8)
                + "&mime=audio/mpeg";

        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder().uri(URI.create(proxyUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(502);
        }
    }
}
