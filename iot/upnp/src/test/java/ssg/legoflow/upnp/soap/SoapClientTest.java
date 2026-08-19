package ssg.legoflow.upnp.soap;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;
class SoapClientTest {

    private static final String SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1";

    private HttpServer server;
    private SoapClient client;
    private URI controlUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        controlUrl = URI.create("http://localhost:" + server.getAddress().getPort() + "/control");
        client = new SoapClient(Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.stop(0);
    }

    @Test
    void shouldInvokeActionSuccessfully() throws Exception {
        // Given: a mock UPnP service that responds with output arguments
        server.createContext("/control", exchange -> {
            var responseXml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                     s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                    <u:PlayResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                    <Status>OK</Status>
                    </u:PlayResponse>
                    </s:Body>
                    </s:Envelope>
                    """;
            var bytes = responseXml.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        // When: invoking the Play action
        var response = client.invoke(controlUrl, SERVICE_TYPE, "Play",
                Map.of("InstanceID", "0", "Speed", "1"));

        // Then: response is successful
        assertThat(response.success()).isTrue();
        assertThat(response.outputArguments()).containsEntry("Status", "OK");
    }

    @Test
    void shouldHandleSoapFault() throws Exception {
        // Given: a mock UPnP service that returns a SOAP fault
        server.createContext("/control", exchange -> {
            var faultXml = SoapMessage.serializeFault(
                    new SoapFault(SoapConstants.ERROR_INVALID_ACTION, "Invalid Action"));
            var bytes = faultXml.getBytes();
            exchange.sendResponseHeaders(500, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        // When: invoking an invalid action
        var response = client.invoke(controlUrl, SERVICE_TYPE, "InvalidAction", Map.of());

        // Then: response is a failure with fault details
        assertThat(response.success()).isFalse();
        assertThat(response.fault()).isNotNull();
        assertThat(response.fault().errorCode()).isEqualTo(SoapConstants.ERROR_INVALID_ACTION);
        assertThat(response.fault().errorDescription()).isEqualTo("Invalid Action");
    }

    @Test
    void shouldInvokeAsyncSuccessfully() throws Exception {
        // Given: a mock service
        server.createContext("/control", exchange -> {
            var responseXml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                     s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                    <u:GetVolumeResponse xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                    <CurrentVolume>42</CurrentVolume>
                    </u:GetVolumeResponse>
                    </s:Body>
                    </s:Envelope>
                    """;
            var bytes = responseXml.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        // When: invoking asynchronously
        var future = client.invokeAsync(controlUrl,
                "urn:schemas-upnp-org:service:RenderingControl:1",
                "GetVolume", Map.of("InstanceID", "0", "Channel", "Master"));
        var response = future.get();

        // Then: response has the volume
        assertThat(response.success()).isTrue();
        assertThat(response.outputArguments()).containsEntry("CurrentVolume", "42");
    }

    @Test
    void shouldTimeoutOnSlowServer() {
        // Given: a very slow server
        server.createContext("/control", exchange -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        // When: invoking with a short timeout client
        var shortClient = new SoapClient(Duration.ofMillis(500));

        // Then: the invocation times out
        assertThatThrownBy(() ->
                shortClient.invoke(controlUrl, SERVICE_TYPE, "Play", Map.of())
        ).isInstanceOf(Exception.class);

        shortClient.close();
    }

    @Test
    void shouldSendCorrectSoapActionHeader() throws Exception {
        // Given: a server that captures the SOAPAction header
        var capturedHeaders = new java.util.concurrent.ConcurrentHashMap<String, String>();
        server.createContext("/control", exchange -> {
            var soapAction = exchange.getRequestHeaders().getFirst("SOAPAction");
            if (soapAction != null) {
                capturedHeaders.put("SOAPAction", soapAction);
            }
            var responseXml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                     s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                    <u:StopResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                    </u:StopResponse>
                    </s:Body>
                    </s:Envelope>
                    """;
            var bytes = responseXml.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        // When: invoking
        client.invoke(controlUrl, SERVICE_TYPE, "Stop", Map.of("InstanceID", "0"));

        // Then: SOAPAction header was set correctly
        assertThat(capturedHeaders.get("SOAPAction"))
                .isEqualTo("\"" + SERVICE_TYPE + "#Stop\"");
    }

    @Test
    void shouldSendCorrectContentType() throws Exception {
        // Given: a server that captures Content-Type
        var capturedHeaders = new java.util.concurrent.ConcurrentHashMap<String, String>();
        server.createContext("/control", exchange -> {
            var ct = exchange.getRequestHeaders().getFirst("Content-Type");
            if (ct != null) {
                capturedHeaders.put("Content-Type", ct);
            }
            var responseXml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                     s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                    <u:PauseResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                    </u:PauseResponse>
                    </s:Body>
                    </s:Envelope>
                    """;
            var bytes = responseXml.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        // When: invoking
        client.invoke(controlUrl, SERVICE_TYPE, "Pause", Map.of("InstanceID", "0"));

        // Then: Content-Type was set correctly
        assertThat(capturedHeaders.get("Content-Type")).isEqualTo(SoapConstants.CONTENT_TYPE);
    }
}
