package ssg.legoflow.interop.rtsp;

import org.junit.jupiter.api.*;
import ssg.legoflow.media.rtsp.client.RtspClient;
import static org.assertj.core.api.Assertions.*;

/**
 * Interoperability test: Lego Flow RTSP client ↔ real RTSP server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RtspInteropTest {

    private final String host = System.getProperty("interop.rtsp.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.rtsp.port", "554"));

    @Test
    void testDescribe() throws Exception {
        var client = RtspClient.connect(host, port);
        var response = client.describe("rtsp://localhost/test");
        assertThat(response).isNotNull();
        client.close();
    }
}
