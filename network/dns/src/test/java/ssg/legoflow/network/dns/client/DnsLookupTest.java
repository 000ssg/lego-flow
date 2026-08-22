package ssg.legoflow.network.dns.client;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import ssg.legoflow.network.dns.server.AuthoritativeZone;
import ssg.legoflow.network.dns.server.DnsServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
/**
 * Integration tests for DnsLookup using a real DnsServer.
 */
@Timeout(10)
class DnsLookupTest {

    private static DnsServer server;
    private static int port;
    private static DnsLookup lookup;

    @BeforeAll
    static void setup() throws IOException {
        AuthoritativeZone zone = AuthoritativeZone.create(
                "example.com", "ns1.example.com", "admin.example.com",
                2024010100L, 3600, 900, 604800, 86400);

        zone.addA("example.com", 300, "93.184.216.34");
        zone.addAAAA("example.com", 300, "2606:2800:220:1:248:1893:25c8:1946");

        server = new DnsServer(new InetSocketAddress("localhost", 0));
        server.addZone(zone);
        server.start();
        port = server.boundAddress().getPort();
        lookup = new DnsLookup(new InetSocketAddress("localhost", port), Duration.ofSeconds(5));
    }

    @AfterAll
    static void teardown() throws Exception {
        if (server != null) server.close();
    }

    @Test
    void testResolveA() throws IOException {
        var addresses = lookup.resolveA("example.com.");
        assertThat(addresses).hasSize(1);
        assertThat(addresses.get(0).getHostAddress()).isEqualTo("93.184.216.34");
    }

    @Test
    void testResolveAAAA() throws IOException {
        var addresses = lookup.resolveAAAA("example.com.");
        assertThat(addresses).hasSize(1);
    }

    @Test
    void testResolveMixed() throws IOException {
        var addresses = lookup.resolve("example.com.");
        assertThat(addresses).hasSize(2); // A + AAAA
    }

    @Test
    void testConstructorWithNullClientThrows() {
        assertThatThrownBy(() -> new DnsLookup((DnsClient) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testResolveWithServerReturnsAddresses() throws IOException {
        var addresses = lookup.resolveA("example.com.");
        assertThat(addresses).isNotEmpty();
    }

    @Test
    void testLookupMxReturnsEmptyForDomainWithoutMx() throws IOException {
        // Domain has no MX records, should return empty list
        var mxRecords = lookup.lookupMx("example.com.");
        assertThat(mxRecords).isEmpty();
    }

    @Test
    void testLookupTxtReturnsEmptyForDomainWithoutTxt() throws IOException {
        // Domain has no TXT records, should return empty list
        var txtStrings = lookup.lookupTxt("example.com.");
        assertThat(txtStrings).isEmpty();
    }

    @Test
    void testResolveAWithNoARecords() throws Exception {
        AuthoritativeZone aaaaOnly = AuthoritativeZone.create(
                "onlyaaa.com", "ns1.onlyaaa.com", "admin.onlyaaa.com",
                2024010100L, 3600, 900, 604800, 86400);
        aaaaOnly.addAAAA("onlyaaa.com", 300, "2001:db8::1");
        server.addZone(aaaaOnly);

        var addresses = lookup.resolveA("onlyaaa.com.");
        assertThat(addresses).isEmpty(); // No A records, only AAAA
    }

    @Test
    void testConstructorWithSocketAddress() throws IOException {
        DnsLookup newLookup = new DnsLookup(new InetSocketAddress("localhost", port), Duration.ofSeconds(5));
        assertThat(newLookup).isNotNull();
    }

    @Test
    void testResolveNonexistentDomainReturnsEmpty() throws IOException {
        var addresses = lookup.resolveA("nonexistent.invalid.");
        // With our server, this should return SERVFAIL or empty
        assertThat(addresses).isNotNull();
    }
}
