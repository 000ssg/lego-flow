package ssg.legoflow.network.dns.demo;

import ssg.legoflow.network.dns.client.DnsClient;
import ssg.legoflow.network.dns.client.DnsLookup;
import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.server.AuthoritativeZone;
import ssg.legoflow.network.dns.server.DnsServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * Demonstrates a DNS server with authoritative zones and a client performing lookups.
 *
 * <p>Creates a server with an example.com zone, adds various record types,
 * and resolves queries against it.
 *
 * @since 0.1.0
 */
public final class DnsServerDemo {

    private DnsServerDemo() {}

    /**
     * Runs the DNS server demo.
     *
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    public static void run() throws IOException {
        // Create zone
        AuthoritativeZone zone = AuthoritativeZone.create(
                "example.com", "ns1.example.com", "admin.example.com",
                2024010101L, 3600, 900, 604800, 86400);

        zone.addA("example.com", 300, "93.184.216.34");
        zone.addA("www.example.com", 300, "93.184.216.34");
        zone.addAAAA("example.com", 300, "2606:2800:220:1:248:1893:25c8:1946");
        zone.addNS("example.com", 86400, "ns1.example.com");
        zone.addNS("example.com", 86400, "ns2.example.com");
        zone.addA("ns1.example.com", 86400, "93.184.216.1");
        zone.addA("ns2.example.com", 86400, "93.184.216.2");
        zone.addMX("example.com", 300, 10, "mail.example.com");
        zone.addMX("example.com", 300, 20, "mail2.example.com");
        zone.addA("mail.example.com", 300, "93.184.216.10");
        zone.addTXT("example.com", 300, "v=spf1 include:_spf.example.com ~all");
        zone.addSRV("_sip._tcp.example.com", 300, 10, 60, 5060, "sip.example.com");
        zone.addCNAME("blog.example.com", 300, "www.example.com");

        // Create server on random port
        try (DnsServer server = new DnsServer(new InetSocketAddress("127.0.0.1", 0))) {
            server.addZone(zone);
            server.start();

            InetSocketAddress addr = server.boundAddress();
            System.out.println("DNS server started on " + addr);

            // Create client and lookup
            DnsLookup lookup = new DnsLookup(addr, Duration.ofSeconds(5));

            System.out.println("A records for example.com: " + lookup.resolveA("example.com"));
            System.out.println("MX records for example.com: " + lookup.lookupMx("example.com"));
            System.out.println("TXT records for example.com: " + lookup.lookupTxt("example.com"));

            System.out.println("Queries received: " + server.queriesReceived());
            System.out.println("Responses sent: " + server.responsesSent());
        }
    }
}
