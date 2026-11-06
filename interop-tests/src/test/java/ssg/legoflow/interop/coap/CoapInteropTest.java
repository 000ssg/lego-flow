package ssg.legoflow.interop.coap;

import org.junit.jupiter.api.*;
import ssg.legoflow.coap.client.CoapClient;
import static org.assertj.core.api.Assertions.*;

/**
 * Interoperability test: Lego Flow CoAP client ↔ real CoAP server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoapInteropTest {

    private final String host = System.getProperty("interop.coap.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.coap.port", "5683"));

    @Test
    void testObserve() throws Exception {
        var client = CoapClient.connect(host, port);
        var response = client.get("coap://localhost/test");
        assertThat(response).isNotNull();
        client.close();
    }
}
