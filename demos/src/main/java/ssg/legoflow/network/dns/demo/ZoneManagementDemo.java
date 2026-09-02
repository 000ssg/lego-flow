package ssg.legoflow.network.dns.demo;

import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.server.AuthoritativeZone;
import ssg.legoflow.network.dns.server.ZoneFile;
/**
 * Demonstrates zone management: creating zones, adding records, and zone file parsing.
 *
 * @since 0.1.0
 */
public final class ZoneManagementDemo {

    private ZoneManagementDemo() {}

    /**
     * Runs the zone management demo.
     *
     * @since 0.1.0
     */
    public static void run() {
        // Parse a BIND-format zone file
        String zoneFileContent = """
                $ORIGIN example.org.
                $TTL 3600
                @   IN  SOA ns1.example.org. admin.example.org. (
                            2024010101  ; serial
                            3600        ; refresh
                            900         ; retry
                            604800      ; expire
                            86400       ; minimum
                        )
                @       IN  NS  ns1.example.org.
                @       IN  NS  ns2.example.org.
                @       IN  A   203.0.113.1
                www     IN  A   203.0.113.2
                mail    IN  A   203.0.113.10
                @       IN  MX  10 mail.example.org.
                @       IN  TXT "v=spf1 mx -all"
                ns1     IN  A   203.0.113.50
                ns2     IN  A   203.0.113.51
                """;

        AuthoritativeZone zone = ZoneFile.parse(zoneFileContent);
        System.out.println("Parsed zone: " + zone.origin());
        System.out.println("SOA: " + zone.soa());
        System.out.println("Total records: " + zone.allRecords().size());

        // Query the zone
        DnsMessage query = DnsMessage.query("www.example.org", RecordType.A);
        DnsMessage response = zone.handleQuery(query);
        System.out.println("Response for www.example.org A: " + response.answers());

        query = DnsMessage.query("example.org", RecordType.MX);
        response = zone.handleQuery(query);
        System.out.println("Response for example.org MX: " + response.answers());

        // NXDOMAIN
        query = DnsMessage.query("nonexistent.example.org", RecordType.A);
        response = zone.handleQuery(query);
        System.out.println("Response for nonexistent: " + response.header().rCode());
    }
    public static void main(String[] args) {
        run();
    }
}
