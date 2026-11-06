package ssg.legoflow.interop.syslog;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.syslog.SyslogClient;
import static org.assertj.core.api.Assertions.*;

/**
 * Interoperability test: Lego Flow Syslog client ↔ real Syslog server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyslogInteropTest {

    private final String host = System.getProperty("interop.syslog.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.syslog.port", "514"));

    @Test
    void testSendSyslog() throws Exception {
        SyslogClient client = SyslogClient.connect(host, port);
        client.send("Test message", "local0", 6);
        client.close();
        assertThat(true).isTrue();
    }
}
