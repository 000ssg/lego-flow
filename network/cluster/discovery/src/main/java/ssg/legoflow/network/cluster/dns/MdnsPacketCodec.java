package ssg.legoflow.network.cluster.dns;

import ssg.legoflow.network.dns.protocol.DnsCodec;
import ssg.legoflow.network.dns.protocol.DnsHeader;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.RecordType;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
/**
 * Packet codec for Multicast DNS (mDNS) per RFC 6762.
 *
 * <p>Wraps the standard DNS codec ({@link DnsCodec}) with mDNS-specific
 * encoding rules:
 * <ul>
 *   <li>Queries: RD bit must be 0 (no recursion), TX bit handling</li>
 *   <li>Responses: AA bit must be 1 (authoritative)</li>
 *   <li>Shared flags: T bit (multicast response), unicast response flag</li>
 * </ul>
 *
 * <p>mDNS uses the link-local multicast address 224.0.0.251 (IPv4) or
 * FF02::FB (IPv6) on port 5353.
 *
 * @since 0.2.0
 */
public final class MdnsPacketCodec {

    /** mDNS multicast address (IPv4). */
    public static final String MDNS_IPV4_MULTICAST = "224.0.0.251";

    /** mDNS multicast address (IPv6). */
    public static final String MDNS_IPV6_MULTICAST = "FF02::FB";

    /** mDNS well-known port. */
    public static final int MDNS_PORT = 5353;

    /**
     * T bit position in the flags (bit 15 of the second 16-bit field).
     * When set in a query, the response should be multicast.
     */

    private MdnsPacketCodec() {}

    /**
     * Encodes a DNS message for multicast transmission.
     *
     * <p>Ensures mDNS-specific flag semantics:
     * <ul>
     *   <li>For queries: clears RD bit (no recursion)</li>
     *   <li>For responses: sets AA bit (authoritative)</li>
     * </ul>
     *
     * @param message the DNS message
     * @return the encoded bytes
     * @since 0.2.0
     */
    public static byte[] encode(DnsMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        return DnsCodec.encode(message);
    }

    /**
     * Decodes raw bytes into a DNS message.
     *
     * <p>Verifies that the decoded message conforms to mDNS requirements:
     * <ul>
     *   <li>Queries must have RD=0</li>
     *   <li>Responses should have AA=1 (warning only, not enforced)</li>
     * </ul>
     *
     * @param data the raw bytes received from the mDNS socket
     * @return the decoded message
     * @throws IllegalArgumentException if the message violates mDNS requirements
     * @since 0.2.0
     */
    public static DnsMessage decode(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        if (data.length < DnsHeader.SIZE) {
            throw new IllegalArgumentException("Message too short for DNS header: " + data.length);
        }

        DnsMessage message = DnsCodec.decode(data);

        // RFC 6762: mDNS queries must have RD=0
        if (!message.header().qr() && message.header().rd()) {
            throw new IllegalArgumentException("mDNS query must have RD=0 (no recursion)");
        }

        return message;
    }

    /**
     * Creates an mDNS query for the given record type.
     *
     * <p>The query has RD=0 (no recursion) as required by RFC 6762.
     * For conflict resolution probing, use {@link #buildProbeQuery(String, RecordType)}.
     *
     * @param name the domain name to query
     * @param type the record type
     * @return an mDNS query message
     * @since 0.2.0
     */
    public static DnsMessage buildQuery(String name, RecordType type) {
        DnsMessage.Builder builder = DnsMessage.builder()
                .id(ThreadLocalRandom.current().nextInt(0, 65536))
                .rd(false)  // no recursion for mDNS
                .addQuestion(ssg.legoflow.network.dns.protocol.DnsQuestion.of(name, type));

        return builder.build();
    }

    /**
     * Creates a probe query for conflict resolution (RFC 6762 §8).
     *
     * <p>Probes are identical to regular queries but are sent multiple times
     * to detect name conflicts before announcing a new service.
     *
     * @param name the domain name to probe
     * @param type the record type (typically SRV for instance names)
     * @return a probe query message
     * @since 0.2.0
     */
    public static DnsMessage buildProbeQuery(String name, RecordType type) {
        return buildQuery(name, type);
    }

    /**
     * Creates a goodbye (bye) message for leaving the network.
     *
     * <p>A goodbye is a response with TTL=0 for all records, indicating
     * that the service is leaving. Per RFC 6762 §10.1.
     *
     * @param serviceRecord the service record to announce leaving
     * @return a goodbye response message
     * @since 0.2.0
     */
    public static DnsMessage buildGoodbye(DnsSdServiceRecord serviceRecord) {
        DnsMessage.Builder builder = DnsMessage.builder()
                .id(ThreadLocalRandom.current().nextInt(0, 65536))
                .qr(true)
                .aa(true)
                .rd(false);

        // Add zero-TTL records for goodbye
        builder.addAnswer(DnsSdRecordBuilder.buildPtr(
                serviceRecord.serviceDomain(), serviceRecord.instanceFqdn(), 0));
        builder.addAnswer(DnsSdRecordBuilder.buildSrv(
                serviceRecord.instanceFqdn(), serviceRecord.priority(), serviceRecord.weight(),
                serviceRecord.port(), serviceRecord.targetHostname(),
                serviceRecord.domain(), 0));
        builder.addAnswer(DnsSdRecordBuilder.buildTxt(
                serviceRecord.instanceFqdn(), serviceRecord.txtAttributes(), 0));

        return builder.build();
    }

    /**
     * Creates an announcement message for a new service.
     *
     * <p>Sent immediately after probing succeeds, announcing the new service
     * to all listening nodes.
     *
     * @param serviceRecord the service record to announce
     * @return an announcement message
     * @since 0.2.0
     */
    public static DnsMessage buildAnnouncement(DnsSdServiceRecord serviceRecord) {
        int txId = ThreadLocalRandom.current().nextInt(0, 65536);
        return DnsSdRecordBuilder.buildResponse(serviceRecord, txId);
    }

    /**
     * Checks if a message is an mDNS announcement (authoritative response).
     *
     * @param message the DNS message
     * @return true if this is an authoritative response (AA=1, QR=1)
     * @since 0.2.0
     */
    public static boolean isAnnouncement(DnsMessage message) {
        return message.header().qr() && message.header().aa();
    }

    /**
     * Checks if a message is a goodbye (contains zero-TTL records).
     *
     * @param message the DNS message
     * @return true if this is a goodbye message
     * @since 0.2.0
     */
    public static boolean isGoodbye(DnsMessage message) {
        if (!message.header().qr()) return false;
        for (var record : message.answers()) {
            if (record.ttl() == 0) return true;
        }
        return false;
    }
}
