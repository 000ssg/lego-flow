package ssg.legoflow.network.dns.server;

import ssg.legoflow.network.dns.client.DnsClient;
import ssg.legoflow.network.dns.protocol.*;
import ssg.legoflow.network.dns.rdata.ARecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
/**
 * Integration tests for DnsServer covering server lifecycle,
 * zone management, and query handling via UDP.
 */
@Timeout(10)
class DnsServerIntegrationTest {

    private DnsServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        AuthoritativeZone zone = AuthoritativeZone.create("example.com", "ns1.example.com", 
                "admin.example.com", 1L, 3600, 900, 604800, 86400);
        zone.addRecord(DnsRecord.of("example.com.", 3600, ARecord.of("93.184.216.34")));
        zone.addRecord(DnsRecord.of("www.example.com.", 300, ARecord.of("93.184.216.35")));
        
        server = new DnsServer(new InetSocketAddress("localhost", 0));
        server.addZone(zone);
        server.start();
        port = server.boundAddress().getPort();
    }

    @AfterEach
    void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void testServerStartAndStop() throws Exception {
        assertThat(server.isRunning()).isTrue();
        assertThat(server.boundAddress().getPort()).isEqualTo(port);
        
        server.close();
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void testQueryARecordOverUdp() throws IOException {
        try (DnsClient client = new DnsClient(
                new InetSocketAddress("localhost", port), Duration.ofSeconds(5))) {
            DnsMessage response = client.query("example.com.", RecordType.A);
            
            assertThat(response.header().rCode()).isEqualTo(ResponseCode.NOERROR);
            assertThat(response.answers()).isNotEmpty();
            assertThat(response.answers().get(0).type()).isEqualTo(RecordType.A);
        }
    }

    @Test
    void testQuerySubdomain() throws IOException {
        try (DnsClient client = new DnsClient(
                new InetSocketAddress("localhost", port), Duration.ofSeconds(5))) {
            DnsMessage response = client.query("www.example.com.", RecordType.A);
            
            assertThat(response.header().rCode()).isEqualTo(ResponseCode.NOERROR);
            assertThat(response.answers()).isNotEmpty();
        }
    }

    @Test
    void testQueryNonExistentDomain() throws IOException {
        try (DnsClient client = new DnsClient(
                new InetSocketAddress("localhost", port), Duration.ofSeconds(5))) {
            DnsMessage response = client.query("nonexistent.example.com.", RecordType.A);
            
            assertThat(response.header().rCode()).isEqualTo(ResponseCode.NXDOMAIN);
        }
    }

    @Test
    void testQuerySoaRecord() throws IOException {
        try (DnsClient client = new DnsClient(
                new InetSocketAddress("localhost", port), Duration.ofSeconds(5))) {
            DnsMessage response = client.query("example.com.", RecordType.SOA);
            
            assertThat(response.header().rCode()).isEqualTo(ResponseCode.NOERROR);
        }
    }

    @Timeout(30)
    @Test
    void testServerQueryCounters() throws Exception {
        try (DnsClient client = new DnsClient(
                new InetSocketAddress("localhost", port), Duration.ofSeconds(5))) {
            long queriesBefore = server.queriesReceived();
            long responsesBefore = server.responsesSent();
            
            client.query("example.com.", RecordType.A);
            
            // Allow async virtual thread to update counters (Windows CI can be slow with virtual thread scheduling)
            int retries = 100;
            while (server.queriesReceived() <= queriesBefore && retries-- > 0) {
                Thread.sleep(200);
            }
            
            assertThat(server.queriesReceived()).isGreaterThan(queriesBefore);
            assertThat(server.responsesSent()).isGreaterThan(responsesBefore);
        }
    }

    @Test
    void testServerConstructorValidation() {
        DnsHandler handler = (query, sender) -> null;
        assertThatThrownBy(() -> new DnsServer(null, handler))
            .isInstanceOf(NullPointerException.class);
        
        assertThatThrownBy(() -> new DnsServer(new InetSocketAddress("localhost", 53), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullAddressConstructorThrows() {
        assertThatThrownBy(() -> new DnsServer((InetSocketAddress) null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testAlreadyRunningThrowsOnStart() throws Exception {
        assertThatThrownBy(server::start)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testCloseWithoutStartDoesNotThrow() throws Exception {
        DnsServer fresh = new DnsServer(new InetSocketAddress("localhost", 0));
        assertThatCode(fresh::close).doesNotThrowAnyException();
    }

    @Test
    void testCustomDnsHandler() throws IOException {
        DnsHandler customHandler = (query, sender) -> 
            DnsMessage.responseFor(query, ResponseCode.NXDOMAIN).build();
        
        DnsServer customServer = new DnsServer(
            new InetSocketAddress("localhost", 0), customHandler);
        customServer.start();
        
        try {
            int customPort = customServer.boundAddress().getPort();
            try (DnsClient client = new DnsClient(
                    new InetSocketAddress("localhost", customPort), Duration.ofSeconds(5))) {
                DnsMessage response = client.query("anything.example.com.", RecordType.A);
                assertThat(response.header().rCode()).isEqualTo(ResponseCode.NXDOMAIN);
            }
        } finally {
            customServer.close();
        }
    }

    @Test
    void testMultipleConcurrentQueries() throws Exception {
        try (DnsClient client = new DnsClient(
                new InetSocketAddress("localhost", port), Duration.ofSeconds(5))) {
            for (int i = 0; i < 5; i++) {
                DnsMessage response = client.query("example.com.", RecordType.A);
                assertThat(response.header().rCode()).isEqualTo(ResponseCode.NOERROR);
            }
            
            // Allow async virtual thread to update counters (Windows CI can be slow with virtual thread scheduling)
            int retries = 100;
            while (server.queriesReceived() < 5 && retries-- > 0) {
                Thread.sleep(200);
            }
            
            assertThat(server.queriesReceived()).isGreaterThanOrEqualTo(5);
        }
    }

    @Test
    void testQueryByStringHostname() throws IOException {
        try (DnsClient client = new DnsClient(
                new InetSocketAddress("localhost", port), Duration.ofSeconds(5))) {
            // Test the String-based query method
            DnsMessage response = client.query("example.com.", RecordType.A);
            
            assertThat(response.header().rCode()).isEqualTo(ResponseCode.NOERROR);
        }
    }
}
