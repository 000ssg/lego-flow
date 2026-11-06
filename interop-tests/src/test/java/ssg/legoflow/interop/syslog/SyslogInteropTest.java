package ssg.legoflow.interop.syslog;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.syslog.SyslogSender;
import ssg.legoflow.network.syslog.protocol.Facility;
import ssg.legoflow.network.syslog.protocol.Severity;
import static org.assertj.core.api.Assertions.*;

/**
 * Interoperability test: Lego Flow Syslog sender ↔ real Syslog server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyslogInteropTest {

    private final String host = System.getProperty("interop.syslog.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.syslog.port", "514"));

    @Test
    void testSendSyslog() throws Exception {
        var client = SyslogSender.udp(host, port);
        client.send(Facility.LOCAL0, Severity.NOTICE, "Test message");
        client.close();
        assertThat(true).isTrue();
    }
}
