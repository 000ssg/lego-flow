package ssg.legoflow.network.cluster.dns;

import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.DnsRecord;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.protocol.ResponseCode;
import ssg.legoflow.network.dns.rdata.ARecord;
import ssg.legoflow.network.dns.rdata.PtrRecord;
import ssg.legoflow.network.dns.rdata.SrvRecord;
import ssg.legoflow.network.dns.rdata.TxtRecord;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/**
 * Builds DNS resource records for DNS-SD service registration per RFC 8305.
 *
 * <p>Constructs the PTR, SRV, A, and TXT records that constitute a complete
 * service instance advertisement. Also assembles these into a DNS builder
 * message suitable for multicast DNS announcements.
 *
 * @since 0.2.0
 */
public final class DnsSdRecordBuilder {

    private DnsSdRecordBuilder() {}

    /**
     * Builds a PTR record pointing from the service domain to the instance FQDN.
     *
     * @param serviceDomain  the service domain name (e.g. "_http._tcp.local.")
     * @param instanceFqdn   the instance fully qualified name
     * @param ttl            the TTL in seconds
     * @return the PTR record
     * @since 0.2.0
     */
    public static DnsRecord buildPtr(String serviceDomain, String instanceFqdn, long ttl) {
        DnsName service = DnsName.of(serviceDomain);
        PtrRecord rdata = PtrRecord.of(instanceFqdn);
        return DnsRecord.of(service, ttl, rdata);
    }

    /**
     * Builds an SRV record for the given instance.
     *
     * @param instanceFqdn the instance fully qualified name
     * @param priority     SRV priority (0-65535)
     * @param weight       SRV weight (0-65535)
     * @param port         service port
     * @param targetHost   target hostname
     * @param domain       domain suffix
     * @param ttl          TTL in seconds
     * @return the SRV record
     * @since 0.2.0
     */
    public static DnsRecord buildSrv(String instanceFqdn, int priority, int weight,
            int port, String targetHost, String domain, long ttl) {
        DnsName name = DnsName.of(instanceFqdn);
        String target = targetHost + "." + domain + ".";
        SrvRecord rdata = SrvRecord.of(priority, weight, port, target);
        return DnsRecord.of(name, ttl, rdata);
    }

    /**
     * Builds an A record for the target host.
     *
     * @param hostname   the host name
     * @param domain     the domain suffix
     * @param address    the IPv4 address
     * @param ttl        TTL in seconds
     * @return the A record
     * @since 0.2.0
     */
    public static DnsRecord buildA(String hostname, String domain, InetAddress address, long ttl) {
        DnsName name = DnsName.of(hostname + "." + domain + ".");
        ARecord rdata = ARecord.fromBytes(address.getAddress());
        return DnsRecord.of(name, ttl, rdata);
    }

    /**
     * Builds a TXT record from key-value attributes.
     *
     * <p>Each attribute is encoded as "key=value" and placed in a separate
     * TXT string segment per RFC 8305 §3.
     *
     * @param instanceFqdn the instance FQDN
     * @param attributes   the key-value pairs
     * @param ttl          TTL in seconds
     * @return the TXT record
     * @since 0.2.0
     */
    public static DnsRecord buildTxt(String instanceFqdn, Map<String, String> attributes, long ttl) {
        DnsName name = DnsName.of(instanceFqdn);
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
        }
        TxtRecord rdata = new TxtRecord(parts);
        return DnsRecord.of(name, ttl, rdata);
    }

    /**
     * Builds a complete DNS-SD response message from a service record.
     *
     * <p>The response includes PTR, SRV, A, and TXT records with the
     * authoritative answer flag set (per RFC 6762 §2.1).
     *
     * @param serviceRecord the service record
     * @param transactionId the DNS transaction ID to match the query
     * @return a DNS response message
     * @since 0.2.0
     */
    public static DnsMessage buildResponse(DnsSdServiceRecord serviceRecord, int transactionId) {
        DnsMessage.Builder builder = DnsMessage.responseFor(
                DnsMessage.query(serviceRecord.serviceDomain(), RecordType.PTR),
                ResponseCode.NOERROR);

        builder
                .id(transactionId)
                .aa(true)  // authoritative answer for mDNS
                .rd(false) // no recursion for mDNS
                .addAnswer(serviceRecord.ptrRecord())
                .addAnswer(serviceRecord.srvRecord())
                .addAnswer(serviceRecord.aRecord())
                .addAnswer(serviceRecord.txtRecord());

        return builder.build();
    }

    /**
     * Escapes a TXT string value per RFC 8305 conventions.
     * Backslashes and quotes are escaped.
     *
     * @param value the raw value
     * @return the escaped string
     * @since 0.2.0
     */
    public static String escapeTxtValue(String value) {
        Objects.requireNonNull(value);
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"");
    }

    /**
     * Unescapes a TXT string value.
     *
     * @param escaped the escaped value
     * @return the original string
     * @since 0.2.0
     */
    public static String unescapeTxtValue(String escaped) {
        Objects.requireNonNull(escaped);
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (escape) {
                if (c == '\\' || c == '"') {
                    sb.append(c);
                } else {
                    sb.append('\\');
                    sb.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else {
                sb.append(c);
            }
        }
        if (escape) sb.append('\\');
        return sb.toString();
    }
}
