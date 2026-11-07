package ssg.legoflow.interop.http;

import org.junit.jupiter.api.*;
import ssg.legoflow.http.client.HttpClient;
import ssg.legoflow.http.config.ClientConfig;
import ssg.legoflow.http.feature.HttpFeatureSet;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpMethod;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Disabled;

/**
 * Interoperability test: Lego Flow HTTP client ↔ real HTTP server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled("Requires external HTTP server: docker compose -f interop-tests/docker-compose.core.yml up -d nginx")
@Tag("interop-rest")
class HttpInteropTest {

    private final String host = System.getProperty("interop.http.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.http.port", "80"));

    private HttpClient client;

    @BeforeAll
    void connect() throws IOException {
        var config = new ClientConfig(new HttpFeatureSet("default"));
        this.client = new HttpClient(config);
    }

    @AfterAll
    void disconnect() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testGetRequest() throws Exception {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        var response = client.send(request);
        assertThat(response.getStatus().code()).isBetween(200, 599);
    }
}