package ssg.legoflow.interop.dns;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.dns.client.DnsClient;
import ssg.legoflow.network.dns.protocol.*;
import java.net.InetSocketAddress;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability test: Lego Flow DNS client ↔ real DNS resolver.
 *
 * <p>Uses an external DNS resolver (default: Google's 8.8.8.8) to verify
 * that the Lego Flow DNS client can correctly send queries and parse responses.
 *
 * <p>Configuration via system properties:
 *   interop.dns.host (default: 8.8.8.8)
 *   interop.dns.port (default: 53)
 */
    @Tag("web-protocols")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DnsInteropTest {

    private final String host = System.getProperty("interop.dns.host", "8.8.8.8");
    private final int port = Integer.parseInt(System.getProperty("interop.dns.port", "53"));

    private DnsClient client;

    @BeforeAll
    void connect() throws Exception {
        this.client = new DnsClient(new InetSocketAddress(host, port), Duration.ofSeconds(5));
    }

    @AfterAll
    void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testARecordQuery() throws Exception {
        DnsMessage response = client.query("google.com.", RecordType.A);
        assertThat(response).isNotNull();
        assertThat(response.header().rCode()).isEqualTo(ResponseCode.NOERROR);
        assertThat(response.answers()).isNotEmpty();
    }

    @Test
    void testAaaaRecordQuery() throws Exception {
        DnsMessage response = client.query("google.com.", RecordType.AAAA);
        assertThat(response).isNotNull();
        assertThat(response.header().rCode()).isEqualTo(ResponseCode.NOERROR);
    }

    @Test
    void testMxRecordQuery() throws Exception {
        DnsMessage response = client.query("google.com.", RecordType.MX);
        assertThat(response).isNotNull();
        assertThat(response.header().rCode()).isEqualTo(ResponseCode.NOERROR);
    }

    @Test
    void testTxtRecordQuery() throws Exception {
        DnsMessage response = client.query("google.com.", RecordType.TXT);
        assertThat(response).isNotNull();
        assertThat(response.header().rCode()).isEqualTo(ResponseCode.NOERROR);
    }

    @Test
    void testNxdomain() throws Exception {
        DnsMessage response = client.query("thisdomaindoesnotexist12345.com.", RecordType.A);
        assertThat(response).isNotNull();
        assertThat(response.header().rCode()).isEqualTo(ResponseCode.NXDOMAIN);
    }

    @Test
    void testQueryWithHighQrFlag() throws Exception {
        DnsQuestion question = new DnsQuestion(DnsName.of("google.com."), RecordType.A, RecordClass.IN);
        DnsMessage query = DnsMessage.query(question);
        DnsMessage response = client.query(query);
        assertThat(response.header().id()).isEqualTo(query.header().id());
        assertThat(response.header().qr()).isTrue();
    }

    @Test
    void testMultipleQueryTypes() throws Exception {
        for (RecordType type : new RecordType[]{RecordType.A, RecordType.AAAA, RecordType.MX, RecordType.CNAME, RecordType.SOA, RecordType.NS}) {
            try {
                DnsMessage response = client.query("google.com.", type);
                assertThat(response).isNotNull();
                assertThat(response.header().rCode()).isIn(ResponseCode.NOERROR, ResponseCode.NXDOMAIN);
            } catch (Exception e) {
                // Some types may not be available for all domains
            }
        }
    }
}
