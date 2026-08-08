package ssg.legoflow.network.dns.demo;

import ssg.legoflow.network.dns.client.DnsClient;
import ssg.legoflow.network.dns.client.DnsLookup;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.DnsRecord;
import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.protocol.ResponseCode;
import ssg.legoflow.network.dns.resolver.DnsCache;
import ssg.legoflow.network.dns.resolver.StubResolver;
import ssg.legoflow.network.dns.server.AuthoritativeZone;
import ssg.legoflow.network.dns.server.DnsServer;
import ssg.legoflow.network.dns.server.ZoneFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

/**
 * Comprehensive demo of all DNS module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link DnsServer}</b> — No external dependencies.
 * Runs anywhere without installation. Supports authoritative zones, zone file parsing,
 * A/AAAA/MX/TXT/SRV/CNAME/NS/PTR record types, wildcard records, CNAME chasing,
 * caching resolver, and DNSSEC validation.
 * Ideal for development, testing, CI/CD, and learning the DNS protocol.</p>
 *
 * <p><b>Alternative: External BIND / Unbound / CoreDNS / PowerDNS</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production recursive resolution against the global DNS hierarchy</li>
 *   <li>DNS-over-HTTPS (DoH) and DNS-over-TLS (DoT) with real certificates</li>
 *   <li>Full DNSSEC chain-of-trust validation from root</li>
 *   <li>Integration testing against production DNS infrastructure</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the server lifecycle (start/stop).
 * All client code (DnsClient, DnsLookup, StubResolver) uses the same API regardless of backend.
 * When {@code USE_EXTERNAL=true}, the demo skips server creation and connects directly
 * to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>A/AAAA record queries — IPv4 and IPv6 address resolution</li>
 *   <li>MX record queries — mail exchange lookup with preference sorting</li>
 *   <li>TXT record queries — SPF, DKIM, verification records</li>
 *   <li>SRV record queries — service discovery with priority/weight</li>
 *   <li>Authoritative zones — zone creation, SOA, NS records, NXDOMAIN</li>
 *   <li>Zone file parsing — BIND-format zone files with $ORIGIN, $TTL</li>
 *   <li>Caching resolver — TTL-based caching with stub resolver forwarding</li>
 *   <li>Wildcard records — *.domain matching for catch-all responses</li>
 *   <li>CNAME resolution — authoritative CNAME alias response</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoDnsAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoDnsAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house DnsServer (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for BIND/Unbound/CoreDNS/PowerDNS
    // =========================================================================

    /** Set to {@code true} to connect to an external DNS server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external DNS server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "127.0.0.1";

    /** Port for external DNS server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 53;

    private DemoDnsAll() {}

    /**
     * Results from running the full demo.
     *
     * @param aRecordQuery      number of A records resolved
     * @param aaaaRecordQuery   number of AAAA records resolved
     * @param mxRecordQuery     number of MX records resolved
     * @param txtRecordQuery    number of TXT records resolved
     * @param srvRecordQuery    number of SRV records resolved
     * @param authoritativeZone true if zone creation and NXDOMAIN detection succeeded
     * @param zoneFileParsing   number of records parsed from BIND zone file
     * @param cachingResolver   true if caching resolver returned cached results
     * @param wildcardRecords   true if wildcard record matching succeeded
     * @param cnameChasing      true if CNAME resolution returned the alias record
     */
    public record Results(
            int aRecordQuery,
            int aaaaRecordQuery,
            int mxRecordQuery,
            int txtRecordQuery,
            int srvRecordQuery,
            boolean authoritativeZone,
            int zoneFileParsing,
            boolean cachingResolver,
            boolean wildcardRecords,
            boolean cnameChasing
    ) {}

    /**
     * Runs the comprehensive demo covering all DNS features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runAgainstServer(new InetSocketAddress(EXTERNAL_HOST, EXTERNAL_PORT));
        }

        // Create authoritative zone for demos
        AuthoritativeZone zone = createDemoZone();

        try (DnsServer server = new DnsServer(new InetSocketAddress("127.0.0.1", 0))) {
            server.addZone(zone);
            server.start();
            InetSocketAddress addr = server.boundAddress();
            LOG.info("In-house DnsServer started on {}", addr);

            return runAgainstServer(addr);
        }
    }

    private static Results runAgainstServer(InetSocketAddress serverAddr) throws Exception {
        int aRecords = demoARecordQuery(serverAddr);
        int aaaaRecords = demoAAAARecordQuery(serverAddr);
        int mxRecords = demoMxRecordQuery(serverAddr);
        int txtRecords = demoTxtRecordQuery(serverAddr);
        int srvRecords = demoSrvRecordQuery(serverAddr);
        boolean authZone = demoAuthoritativeZone(serverAddr);
        int zoneParsed = demoZoneFileParsing();
        boolean caching = demoCachingResolver(serverAddr);
        boolean wildcard = demoWildcardRecords(serverAddr);
        boolean cname = demoCnameChasing(serverAddr);

        return new Results(aRecords, aaaaRecords, mxRecords, txtRecords, srvRecords,
                authZone, zoneParsed, caching, wildcard, cname);
    }

    // ======================== Zone Setup ====================================

    /**
     * Creates the demo zone with all record types needed for the demos.
     */
    static AuthoritativeZone createDemoZone() {
        AuthoritativeZone zone = AuthoritativeZone.create(
                "example.com", "ns1.example.com", "admin.example.com",
                2024010101L, 3600, 900, 604800, 86400);

        // A records
        zone.addA("example.com", 300, "93.184.216.34");
        zone.addA("www.example.com", 300, "93.184.216.34");
        zone.addA("mail.example.com", 300, "93.184.216.10");
        zone.addA("ns1.example.com", 86400, "93.184.216.1");
        zone.addA("ns2.example.com", 86400, "93.184.216.2");
        zone.addA("sip.example.com", 300, "93.184.216.20");

        // AAAA records
        zone.addAAAA("example.com", 300, "2606:2800:220:1:248:1893:25c8:1946");
        zone.addAAAA("www.example.com", 300, "2606:2800:220:1:248:1893:25c8:1946");

        // NS records
        zone.addNS("example.com", 86400, "ns1.example.com");
        zone.addNS("example.com", 86400, "ns2.example.com");

        // MX records
        zone.addMX("example.com", 300, 10, "mail.example.com");
        zone.addMX("example.com", 300, 20, "mail2.example.com");

        // TXT records
        zone.addTXT("example.com", 300, "v=spf1 include:_spf.example.com ~all");
        zone.addTXT("example.com", 300, "v=DKIM1; k=rsa; p=MIGfMA0GCSqGS");

        // SRV record
        zone.addSRV("_sip._tcp.example.com", 300, 10, 60, 5060, "sip.example.com");

        // CNAME record for chasing demo
        zone.addCNAME("blog.example.com", 300, "www.example.com");

        // Wildcard record
        zone.addA("*.wild.example.com", 300, "10.0.0.1");

        return zone;
    }

    // ======================== 1. A RECORD QUERY =============================

    /**
     * Demonstrates A record query for IPv4 address resolution.
     */
    static int demoARecordQuery(InetSocketAddress serverAddr) throws IOException {
        LOG.info("=== 1. A Record Query ===");
        DnsLookup lookup = new DnsLookup(serverAddr, Duration.ofSeconds(5));
        List<Inet4Address> addresses = lookup.resolveA("example.com");
        LOG.info("A records for example.com: {}", addresses);
        return addresses.size();
    }

    // ======================== 2. AAAA RECORD QUERY ==========================

    /**
     * Demonstrates AAAA record query for IPv6 address resolution.
     */
    static int demoAAAARecordQuery(InetSocketAddress serverAddr) throws IOException {
        LOG.info("=== 2. AAAA Record Query ===");
        DnsLookup lookup = new DnsLookup(serverAddr, Duration.ofSeconds(5));
        List<Inet6Address> addresses = lookup.resolveAAAA("example.com");
        LOG.info("AAAA records for example.com: {}", addresses);
        return addresses.size();
    }

    // ======================== 3. MX RECORD QUERY ============================

    /**
     * Demonstrates MX record query with preference-based sorting.
     * <p>
     * <b>Preferred: {@code DnsLookup.lookupMx()}</b> — returns sorted MX records.
     * <p>
     * <b>Alternative: raw {@code DnsClient.query()}</b> — for custom MX handling
     * or when you need the full DNS response.
     */
    static int demoMxRecordQuery(InetSocketAddress serverAddr) throws IOException {
        LOG.info("=== 3. MX Record Query ===");
        DnsLookup lookup = new DnsLookup(serverAddr, Duration.ofSeconds(5));
        var mxRecords = lookup.lookupMx("example.com");
        for (var mx : mxRecords) {
            LOG.info("MX: preference={} exchange={}", mx.preference(), mx.exchange());
        }
        return mxRecords.size();
    }

    // ======================== 4. TXT RECORD QUERY ===========================

    /**
     * Demonstrates TXT record query for SPF, DKIM, and verification records.
     */
    static int demoTxtRecordQuery(InetSocketAddress serverAddr) throws IOException {
        LOG.info("=== 4. TXT Record Query ===");
        DnsLookup lookup = new DnsLookup(serverAddr, Duration.ofSeconds(5));
        List<String> txtRecords = lookup.lookupTxt("example.com");
        for (String txt : txtRecords) {
            LOG.info("TXT: {}", txt);
        }
        return txtRecords.size();
    }

    // ======================== 5. SRV RECORD QUERY ===========================

    /**
     * Demonstrates SRV record query for service discovery.
     */
    static int demoSrvRecordQuery(InetSocketAddress serverAddr) throws IOException {
        LOG.info("=== 5. SRV Record Query ===");
        DnsLookup lookup = new DnsLookup(serverAddr, Duration.ofSeconds(5));
        var srvRecords = lookup.lookupSrv("_sip._tcp.example.com");
        for (var srv : srvRecords) {
            LOG.info("SRV: priority={} weight={} port={} target={}",
                    srv.priority(), srv.weight(), srv.port(), srv.target());
        }
        return srvRecords.size();
    }

    // ======================== 6. AUTHORITATIVE ZONE =========================

    /**
     * Demonstrates authoritative zone creation, query handling, and NXDOMAIN.
     */
    static boolean demoAuthoritativeZone(InetSocketAddress serverAddr) throws IOException {
        LOG.info("=== 6. Authoritative Zone ===");

        // Create a standalone zone for this demo
        AuthoritativeZone zone = AuthoritativeZone.create(
                "test.example", "ns1.test.example", "admin.test.example",
                2024010101L, 3600, 900, 604800, 86400);
        zone.addA("test.example", 300, "10.0.0.1");
        zone.addA("host1.test.example", 300, "10.0.0.2");

        LOG.info("Zone origin: {}", zone.origin());
        LOG.info("Zone SOA: {}", zone.soa());
        LOG.info("Total records: {}", zone.allRecords().size());

        // Query for existing name
        DnsMessage query = DnsMessage.query("host1.test.example", RecordType.A);
        DnsMessage response = zone.handleQuery(query);
        boolean hasAnswer = !response.answers().isEmpty();
        LOG.info("Query host1.test.example A: answers={}", response.answers().size());

        // Query for nonexistent name (NXDOMAIN)
        DnsMessage nxQuery = DnsMessage.query("nonexistent.test.example", RecordType.A);
        DnsMessage nxResponse = zone.handleQuery(nxQuery);
        boolean isNxDomain = nxResponse.header().rCode() == ResponseCode.NXDOMAIN;
        LOG.info("Query nonexistent.test.example: rCode={}", nxResponse.header().rCode());

        return hasAnswer && isNxDomain;
    }

    // ======================== 7. ZONE FILE PARSING ==========================

    /**
     * Demonstrates BIND-format zone file parsing with $ORIGIN, $TTL, and multi-line SOA.
     */
    static int demoZoneFileParsing() {
        LOG.info("=== 7. Zone File Parsing ===");
        String zoneFileContent = """
                $ORIGIN demo.example.
                $TTL 3600
                @   IN  SOA ns1.demo.example. admin.demo.example. (
                            2024010101  ; serial
                            3600        ; refresh
                            900         ; retry
                            604800      ; expire
                            86400       ; minimum
                        )
                @       IN  NS  ns1.demo.example.
                @       IN  NS  ns2.demo.example.
                @       IN  A   192.0.2.1
                www     IN  A   192.0.2.2
                mail    IN  A   192.0.2.10
                @       IN  MX  10 mail.demo.example.
                @       IN  TXT "v=spf1 mx -all"
                ns1     IN  A   192.0.2.50
                ns2     IN  A   192.0.2.51
                """;

        AuthoritativeZone parsed = ZoneFile.parse(zoneFileContent);
        int recordCount = parsed.allRecords().size();
        LOG.info("Parsed zone: origin={} records={}", parsed.origin(), recordCount);
        return recordCount;
    }

    // ======================== 8. CACHING RESOLVER ===========================

    /**
     * Demonstrates the caching stub resolver: queries are forwarded to upstream
     * and results cached by TTL.
     */
    static boolean demoCachingResolver(InetSocketAddress serverAddr) throws IOException {
        LOG.info("=== 8. Caching Resolver ===");
        DnsCache cache = new DnsCache();

        try (StubResolver resolver = new StubResolver(serverAddr, Duration.ofSeconds(5), cache)) {
            // First query — cache miss, forwarded to upstream
            DnsMessage query = DnsMessage.query("example.com", RecordType.A);
            DnsMessage response1 = resolver.resolve(query);
            int cacheSize1 = cache.size();
            LOG.info("First query: answers={} cacheSize={}", response1.answers().size(), cacheSize1);

            // Second query — should be served from cache
            DnsMessage response2 = resolver.resolve(query);
            int cacheSize2 = cache.size();
            LOG.info("Second query: answers={} cacheSize={}", response2.answers().size(), cacheSize2);

            // Cache should have entries, both responses should have answers
            boolean hasCachedEntries = cacheSize1 > 0;
            boolean bothHaveAnswers = !response1.answers().isEmpty() && !response2.answers().isEmpty();
            return hasCachedEntries && bothHaveAnswers;
        }
    }

    // ======================== 9. WILDCARD RECORDS ============================

    /**
     * Demonstrates wildcard record matching: *.wild.example.com matches any subdomain.
     */
    static boolean demoWildcardRecords(InetSocketAddress serverAddr) throws IOException {
        LOG.info("=== 9. Wildcard Records ===");
        DnsClient client = new DnsClient(serverAddr, Duration.ofSeconds(5));
        DnsMessage response = client.query("anything.wild.example.com", RecordType.A);
        boolean hasWildcardAnswer = !response.answers().isEmpty();
        LOG.info("Wildcard query anything.wild.example.com: answers={}", response.answers().size());
        client.close();
        return hasWildcardAnswer;
    }

    // ======================== 10. CNAME CHASING =============================

    /**
     * Demonstrates CNAME resolution: querying blog.example.com for an A record
     * returns a CNAME record pointing to www.example.com. The in-house authoritative
     * server returns the CNAME record when no direct A record exists for the queried
     * name. A recursive resolver or client would then follow the CNAME chain to
     * resolve the final A record; here we verify the authoritative CNAME response.
     */
    static boolean demoCnameChasing(InetSocketAddress serverAddr) throws IOException {
        LOG.info("=== 10. CNAME Resolution ===");
        DnsClient client = new DnsClient(serverAddr, Duration.ofSeconds(5));
        DnsMessage response = client.query("blog.example.com", RecordType.A);

        // The authoritative server returns the CNAME record for the alias
        boolean hasCname = response.answers().stream()
                .anyMatch(r -> r.type() == RecordType.CNAME);

        LOG.info("CNAME resolution blog.example.com: hasCNAME={} answers={}",
                hasCname, response.answers().size());
        client.close();
        return hasCname;
    }
}
