package ssg.legoflow.network.dns.server;

import ssg.legoflow.network.dns.protocol.*;
import ssg.legoflow.network.dns.rdata.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
/**
 * Authoritative DNS zone containing resource records.
 *
 * <p>A zone is defined by its origin (domain name) and SOA record.
 * Records are indexed by owner name and type for efficient lookup.
 *
 * @since 0.1.0
 */
public final class AuthoritativeZone {

    private final DnsName origin;
    private final SoaRecord soa;
    private final Map<DnsName, Map<RecordType, List<DnsRecord>>> records = new ConcurrentHashMap<>();

    /**
     * Creates an authoritative zone.
     *
     * @param origin the zone origin domain name
     * @param soa    the SOA record for the zone
     * @since 0.1.0
     */
    public AuthoritativeZone(DnsName origin, SoaRecord soa) {
        this.origin = Objects.requireNonNull(origin, "origin must not be null");
        this.soa = Objects.requireNonNull(soa, "soa must not be null");
        // Add SOA record
        addRecord(DnsRecord.of(origin, soa.minimum(), soa));
    }

    /**
     * Creates an authoritative zone from string parameters.
     *
     * @param origin  the zone origin
     * @param mname   primary name server
     * @param rname   responsible person
     * @param serial  zone serial
     * @param refresh refresh interval
     * @param retry   retry interval
     * @param expire  expiration
     * @param minimum minimum TTL
     * @return the zone
     * @since 0.1.0
     */
    public static AuthoritativeZone create(String origin, String mname, String rname,
                                            long serial, int refresh, int retry,
                                            int expire, int minimum) {
        SoaRecord soa = SoaRecord.of(mname, rname, serial, refresh, retry, expire, minimum);
        return new AuthoritativeZone(DnsName.of(origin), soa);
    }

    /**
     * Returns the zone origin.
     *
     * @return the origin domain name
     * @since 0.1.0
     */
    public DnsName origin() {
        return origin;
    }

    /**
     * Returns the SOA record.
     *
     * @return the SOA record
     * @since 0.1.0
     */
    public SoaRecord soa() {
        return soa;
    }

    /**
     * Adds a resource record to the zone.
     *
     * @param record the record to add
     * @throws IllegalArgumentException if the record name is not within the zone
     * @since 0.1.0
     */
    public void addRecord(DnsRecord record) {
        if (!record.name().isSubdomainOf(origin) && !record.name().equals(origin)) {
            throw new IllegalArgumentException(
                    "Record " + record.name() + " is not within zone " + origin);
        }
        records.computeIfAbsent(record.name(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(record.type(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(record);
    }

    /**
     * Adds an A record to the zone.
     *
     * @param name the owner name
     * @param ttl  the TTL
     * @param ip   the IPv4 address
     * @since 0.1.0
     */
    public void addA(String name, long ttl, String ip) {
        addRecord(DnsRecord.of(name, ttl, ARecord.of(ip)));
    }

    /**
     * Adds an AAAA record to the zone.
     *
     * @param name the owner name
     * @param ttl  the TTL
     * @param ip   the IPv6 address
     * @since 0.1.0
     */
    public void addAAAA(String name, long ttl, String ip) {
        addRecord(DnsRecord.of(name, ttl, AaaaRecord.of(ip)));
    }

    /**
     * Adds an NS record to the zone.
     *
     * @param name the owner name
     * @param ttl  the TTL
     * @param ns   the name server
     * @since 0.1.0
     */
    public void addNS(String name, long ttl, String ns) {
        addRecord(DnsRecord.of(name, ttl, NsRecord.of(ns)));
    }

    /**
     * Adds a CNAME record to the zone.
     *
     * @param name  the owner name
     * @param ttl   the TTL
     * @param cname the canonical name
     * @since 0.1.0
     */
    public void addCNAME(String name, long ttl, String cname) {
        addRecord(DnsRecord.of(name, ttl, CnameRecord.of(cname)));
    }

    /**
     * Adds an MX record to the zone.
     *
     * @param name       the owner name
     * @param ttl        the TTL
     * @param preference the preference
     * @param exchange   the mail exchange
     * @since 0.1.0
     */
    public void addMX(String name, long ttl, int preference, String exchange) {
        addRecord(DnsRecord.of(name, ttl, MxRecord.of(preference, exchange)));
    }

    /**
     * Adds a TXT record to the zone.
     *
     * @param name the owner name
     * @param ttl  the TTL
     * @param text the text strings
     * @since 0.1.0
     */
    public void addTXT(String name, long ttl, String... text) {
        addRecord(DnsRecord.of(name, ttl, TxtRecord.of(text)));
    }

    /**
     * Adds an SRV record to the zone.
     *
     * @param name     the owner name
     * @param ttl      the TTL
     * @param priority the priority
     * @param weight   the weight
     * @param port     the port
     * @param target   the target host
     * @since 0.1.0
     */
    public void addSRV(String name, long ttl, int priority, int weight, int port, String target) {
        addRecord(DnsRecord.of(name, ttl, SrvRecord.of(priority, weight, port, target)));
    }

    /**
     * Looks up records by name and type.
     *
     * @param name the owner name
     * @param type the record type
     * @return list of matching records
     * @since 0.1.0
     */
    public List<DnsRecord> lookup(DnsName name, RecordType type) {
        Map<RecordType, List<DnsRecord>> byType = records.get(name);
        if (byType == null) {
            // Try wildcard
            DnsName wildcard = DnsName.of("*." + name.parent());
            byType = records.get(wildcard);
            if (byType == null) {
                return List.of();
            }
        }

        if (type == RecordType.ANY) {
            return byType.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        }

        List<DnsRecord> result = byType.get(type);
        if (result == null) {
            // Check for CNAME
            List<DnsRecord> cnames = byType.get(RecordType.CNAME);
            if (cnames != null && !cnames.isEmpty()) {
                return new ArrayList<>(cnames);
            }
            return List.of();
        }
        return new ArrayList<>(result);
    }

    /**
     * Returns whether the given name exists in the zone.
     *
     * @param name the domain name
     * @return {@code true} if any records exist for the name
     * @since 0.1.0
     */
    public boolean nameExists(DnsName name) {
        return records.containsKey(name);
    }

    /**
     * Returns all records in the zone.
     *
     * @return all records
     * @since 0.1.0
     */
    public List<DnsRecord> allRecords() {
        return records.values().stream()
                .flatMap(m -> m.values().stream())
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Returns the NS records for the zone apex.
     *
     * @return the NS records
     * @since 0.1.0
     */
    public List<DnsRecord> nsRecords() {
        return lookup(origin, RecordType.NS);
    }

    /**
     * Handles a DNS query against this zone.
     *
     * @param query the DNS query
     * @return the DNS response
     * @since 0.1.0
     */
    public DnsMessage handleQuery(DnsMessage query) {
        if (query.questions().isEmpty()) {
            return DnsMessage.responseFor(query, ResponseCode.FORMERR).build();
        }

        DnsQuestion q = query.questions().get(0);

        // Check if name is within this zone
        if (!q.name().isSubdomainOf(origin) && !q.name().equals(origin)) {
            return DnsMessage.responseFor(query, ResponseCode.REFUSED).build();
        }

        List<DnsRecord> answers = lookup(q.name(), q.type());

        if (!answers.isEmpty()) {
            return DnsMessage.responseFor(query, ResponseCode.NOERROR)
                    .aa(true)
                    .answers(answers)
                    .authorities(nsRecords())
                    .build();
        }

        // Name exists but no matching type
        if (nameExists(q.name())) {
            return DnsMessage.responseFor(query, ResponseCode.NOERROR)
                    .aa(true)
                    .authorities(List.of(DnsRecord.of(origin, soa.minimum(), soa)))
                    .build();
        }

        // NXDOMAIN
        return DnsMessage.responseFor(query, ResponseCode.NXDOMAIN)
                .aa(true)
                .authorities(List.of(DnsRecord.of(origin, soa.minimum(), soa)))
                .build();
    }
}
