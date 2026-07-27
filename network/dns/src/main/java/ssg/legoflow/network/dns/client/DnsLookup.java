package ssg.legoflow.network.dns.client;

import ssg.legoflow.network.dns.protocol.*;
import ssg.legoflow.network.dns.rdata.*;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * High-level DNS lookup utility for common resolution tasks.
 *
 * <p>Provides convenience methods for resolving hostnames to addresses,
 * looking up MX records, SRV records, and other common queries.
 *
 * @since 1.0.0
 */
public final class DnsLookup {

    private final DnsClient client;

    /**
     * Creates a lookup utility with the given client.
     *
     * @param client the DNS client
     * @since 1.0.0
     */
    public DnsLookup(DnsClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    /**
     * Creates a lookup utility targeting the given DNS server.
     *
     * @param server  the DNS server address
     * @param timeout the query timeout
     * @since 1.0.0
     */
    public DnsLookup(InetSocketAddress server, Duration timeout) {
        this(new DnsClient(server, timeout));
    }

    /**
     * Resolves a hostname to IPv4 addresses.
     *
     * @param hostname the hostname to resolve
     * @return list of IPv4 addresses
     * @throws IOException if the query fails
     * @since 1.0.0
     */
    public List<Inet4Address> resolveA(String hostname) throws IOException {
        DnsMessage response = client.query(hostname, RecordType.A);
        return response.answers().stream()
                .filter(r -> r.rdata() instanceof ARecord)
                .map(r -> ((ARecord) r.rdata()).address())
                .collect(Collectors.toList());
    }

    /**
     * Resolves a hostname to IPv6 addresses.
     *
     * @param hostname the hostname to resolve
     * @return list of IPv6 addresses
     * @throws IOException if the query fails
     * @since 1.0.0
     */
    public List<Inet6Address> resolveAAAA(String hostname) throws IOException {
        DnsMessage response = client.query(hostname, RecordType.AAAA);
        return response.answers().stream()
                .filter(r -> r.rdata() instanceof AaaaRecord)
                .map(r -> ((AaaaRecord) r.rdata()).address())
                .collect(Collectors.toList());
    }

    /**
     * Resolves a hostname to all addresses (IPv4 and IPv6).
     *
     * @param hostname the hostname to resolve
     * @return list of addresses
     * @throws IOException if the query fails
     * @since 1.0.0
     */
    public List<InetAddress> resolve(String hostname) throws IOException {
        List<InetAddress> addresses = new ArrayList<>();
        addresses.addAll(resolveA(hostname));
        addresses.addAll(resolveAAAA(hostname));
        return addresses;
    }

    /**
     * Looks up MX records for a domain, sorted by preference.
     *
     * @param domain the domain name
     * @return sorted list of MX records
     * @throws IOException if the query fails
     * @since 1.0.0
     */
    public List<MxRecord> lookupMx(String domain) throws IOException {
        DnsMessage response = client.query(domain, RecordType.MX);
        return response.answers().stream()
                .filter(r -> r.rdata() instanceof MxRecord)
                .map(r -> (MxRecord) r.rdata())
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Looks up SRV records for a service, sorted by priority and weight.
     *
     * @param service the SRV service name (e.g., "_sip._tcp.example.com")
     * @return sorted list of SRV records
     * @throws IOException if the query fails
     * @since 1.0.0
     */
    public List<SrvRecord> lookupSrv(String service) throws IOException {
        DnsMessage response = client.query(service, RecordType.SRV);
        return response.answers().stream()
                .filter(r -> r.rdata() instanceof SrvRecord)
                .map(r -> (SrvRecord) r.rdata())
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Looks up TXT records for a domain.
     *
     * @param domain the domain name
     * @return list of TXT record strings
     * @throws IOException if the query fails
     * @since 1.0.0
     */
    public List<String> lookupTxt(String domain) throws IOException {
        DnsMessage response = client.query(domain, RecordType.TXT);
        return response.answers().stream()
                .filter(r -> r.rdata() instanceof TxtRecord)
                .map(r -> ((TxtRecord) r.rdata()).text())
                .collect(Collectors.toList());
    }

    /**
     * Performs a reverse DNS lookup for an IP address.
     *
     * @param address the IP address to look up
     * @return list of PTR domain names
     * @throws IOException if the query fails
     * @since 1.0.0
     */
    public List<DnsName> reverseLookup(InetAddress address) throws IOException {
        String ptrName = buildPtrName(address);
        DnsMessage response = client.query(ptrName, RecordType.PTR);
        return response.answers().stream()
                .filter(r -> r.rdata() instanceof PtrRecord)
                .map(r -> ((PtrRecord) r.rdata()).domainName())
                .collect(Collectors.toList());
    }

    private String buildPtrName(InetAddress address) {
        byte[] addr = address.getAddress();
        if (addr.length == 4) {
            // IPv4: reverse octets + .in-addr.arpa
            return String.format("%d.%d.%d.%d.in-addr.arpa",
                    addr[3] & 0xFF, addr[2] & 0xFF, addr[1] & 0xFF, addr[0] & 0xFF);
        } else {
            // IPv6: reverse nibbles + .ip6.arpa
            StringBuilder sb = new StringBuilder();
            for (int i = addr.length - 1; i >= 0; i--) {
                int b = addr[i] & 0xFF;
                sb.append(Integer.toHexString(b & 0x0F)).append('.');
                sb.append(Integer.toHexString((b >> 4) & 0x0F)).append('.');
            }
            sb.append("ip6.arpa");
            return sb.toString();
        }
    }
}
