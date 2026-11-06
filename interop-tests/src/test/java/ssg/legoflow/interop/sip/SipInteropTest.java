package ssg.legoflow.interop.sip;

import org.junit.jupiter.api.*;
import ssg.legoflow.media.sip.registration.SipRegistrationClient;
import static org.assertj.core.api.Assertions.*;

/**
 * Interoperability test: Lego Flow SIP client ↔ real SIP server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SipInteropTest {

    private final String host = System.getProperty("interop.sip.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.sip.port", "5060"));

    @Test
    void testRegister() throws Exception {
        var client = SipRegistrationClient.connect(host, port);
        var response = client.register("sip:test@localhost");
        assertThat(response).isNotNull();
        client.close();
    }
}
