package ssg.legoflow.interop.http;

import org.junit.jupiter.api.*;
import ssg.legoflow.http.client.HttpClient;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Disabled;

/**
 * Interoperability test: Lego Flow HTTP client ↔ real HTTP server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled("Requires external HTTP server: docker compose -f interop-tests/docker-compose.core.yml up -d nginx")
class HttpInteropTest {

    private final String host = System.getProperty("interop.http.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.http.port", "80"));

    private HttpClient client;

    @BeforeAll
    void connect() throws IOException {
        this.client = HttpClient.newBuilder()
            .withHost(host)
            .withPort(port)
            .build();
        client.start();
    }

    @AfterAll
    void disconnect() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testGetRequest() throws Exception {
        var response = client.get("/");
        assertThat(response.statusCode()).isBetween(200, 599);
    }
}
