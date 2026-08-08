package ssg.legoflow.network.dns.client;
import org.junit.jupiter.api.Timeout;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class DnsClientTest {
    @Test void testDnsRecordType() {
        assertThat(ssg.legoflow.network.dns.protocol.RecordType.A).isNotNull();
        assertThat(ssg.legoflow.network.dns.protocol.RecordType.AAAA).isNotNull();
        assertThat(ssg.legoflow.network.dns.protocol.RecordType.MX).isNotNull();
    }
    @Test void testDnsOpCode() {
        assertThat(ssg.legoflow.network.dns.protocol.OpCode.QUERY).isNotNull();
    }
}
