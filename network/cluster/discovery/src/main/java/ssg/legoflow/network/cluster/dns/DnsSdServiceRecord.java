package ssg.legoflow.network.cluster.dns;

import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.DnsRecord;
import ssg.legoflow.network.dns.protocol.RecordClass;
import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.rdata.ARecord;
import ssg.legoflow.network.dns.rdata.PtrRecord;
import ssg.legoflow.network.dns.rdata.SrvRecord;
import ssg.legoflow.network.dns.rdata.TxtRecord;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A DNS-SD service record as defined in RFC 8305.
 *
 * <p>Encapsulates the three-record chain required for service discovery:
 * <ul>
 *   <li>PTR: service domain → instance FQDN</li>
 *   <li>SRV: instance FQDN → target hostname, port, priority, weight</li>
 *   <li>A/AAAA: hostname → IP address</li>
 * </ul>
 * Plus an optional TXT record for metadata.
 *
 * <p>Instances are immutable and carry the TTL applied to all records.
 *
 * @param serviceType     the service type, e.g. "_http._tcp"
 * @param domain          the domain, e.g. "local"
 * @param instanceName    the instance name
 * @param targetHostname  the target hostname
 * @param targetAddress   the target IP address
 * @param port            the service port
 * @param priority        SRV priority
 * @param weight          SRV weight
 * @param txtAttributes   TXT metadata key-value pairs
 * @param ttl             record TTL
 * @since 0.2.0
 */
public final class DnsSdServiceRecord {

    private final String serviceType;
    private final String domain;
    private final String instanceName;
    private final String targetHostname;
    private final InetAddress targetAddress;
    private final int port;
    private final int priority;
    private final int weight;
    private final Map<String, String> txtAttributes;
    private final Duration ttl;

    /**
     * Constructs the service record.
     *
     * @since 0.2.0
     */
    public DnsSdServiceRecord(String serviceType, String domain, String instanceName,
            String targetHostname, InetAddress targetAddress, int port,
            int priority, int weight, Map<String, String> txtAttributes, Duration ttl) {
        Objects.requireNonNull(serviceType);
        if (serviceType.isBlank()) throw new IllegalArgumentException("serviceType must not be blank");
        Objects.requireNonNull(domain);
        Objects.requireNonNull(instanceName);
        if (instanceName.isBlank()) throw new IllegalArgumentException("instanceName must not be blank");
        Objects.requireNonNull(targetHostname);
        Objects.requireNonNull(targetAddress);
        if (port < 0 || port > 65535) throw new IllegalArgumentException("port must be 0-65535");
        if (priority < 0 || priority > 65535) throw new IllegalArgumentException("priority must be 0-65535");
        if (weight < 0 || weight > 65535) throw new IllegalArgumentException("weight must be 0-65535");
        this.txtAttributes = Map.copyOf(Objects.requireNonNull(txtAttributes));
        Objects.requireNonNull(ttl);
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("ttl must be positive");

        this.serviceType = serviceType;
        this.domain = domain;
        this.instanceName = instanceName;
        this.targetHostname = targetHostname;
        this.targetAddress = targetAddress;
        this.port = port;
        this.priority = priority;
        this.weight = weight;
        this.ttl = ttl;
    }

    /**
     * Returns the fully qualified service domain name (e.g. "_http._tcp.local.").
     */
    public String serviceDomain() {
        return serviceType + "." + domain;
    }

    /**
     * Returns the fully qualified instance name (e.g. "MyServer._http._tcp.local.").
     */
    public String instanceFqdn() {
        return instanceName + "." + serviceDomain();
    }

    public String serviceType() { return serviceType; }
    public String domain() { return domain; }
    public String instanceName() { return instanceName; }
    public String targetHostname() { return targetHostname; }
    public InetAddress targetAddress() { return targetAddress; }
    public int port() { return port; }
    public int priority() { return priority; }
    public int weight() { return weight; }
    public Map<String, String> txtAttributes() { return txtAttributes; }
    public Duration ttl() { return ttl; }

    /**
     * Builds the PTR record for this service instance.
     *
     * @return a DNS PTR record pointing from service domain to instance FQDN
     * @since 0.2.0
     */
    public DnsRecord ptrRecord() {
        DnsName serviceDomainName = DnsName.of(serviceDomain());
        DnsName instanceName = DnsName.of(instanceFqdn());
        return DnsRecord.of(serviceDomainName, ttl.getSeconds(), PtrRecord.of(instanceFqdn()));
    }

    /**
     * Builds the SRV record for this service instance.
     *
     * @return a DNS SRV record with target host, port, priority, weight
     * @since 0.2.0
     */
    public DnsRecord srvRecord() {
        DnsName instanceName = DnsName.of(instanceFqdn());
        SrvRecord srvData = SrvRecord.of(priority, weight, port, targetHostname + "." + domain + ".");
        return DnsRecord.of(instanceName, ttl.getSeconds(), srvData);
    }

    /**
     * Builds the A record for the target host.
     *
     * @return a DNS A record for the target IP address
     * @throws IllegalArgumentException if the target address is not IPv4
     * @since 0.2.0
     */
    public DnsRecord aRecord() {
        DnsName hostName = DnsName.of(targetHostname + "." + domain + ".");
        return DnsRecord.of(hostName, ttl.getSeconds(), ARecord.fromBytes(targetAddress.getAddress()));
    }

    /**
     * Builds the TXT record for service metadata.
     *
     * @return a DNS TXT record with key-value attributes
     * @since 0.2.0
     */
    public DnsRecord txtRecord() {
        DnsName instanceName = DnsName.of(instanceFqdn());
        List<String> parts = txtAttributes.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList();
        return DnsRecord.of(instanceName, ttl.getSeconds(), new TxtRecord(parts));
    }

    /**
     * Returns all DNS records for this service instance.
     *
     * @return list containing PTR, SRV, A, and TXT records
     * @since 0.2.0
     */
    public List<DnsRecord> allRecords() {
        return List.of(ptrRecord(), srvRecord(), aRecord(), txtRecord());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DnsSdServiceRecord that = (DnsSdServiceRecord) o;
        return instanceFqdn().equals(that.instanceFqdn());
    }

    @Override
    public int hashCode() {
        return instanceFqdn().hashCode();
    }

    @Override
    public String toString() {
        return "DnsSdServiceRecord{instance='" + instanceName + "', service='"
                + serviceDomain() + "', target=" + targetHostname + ":" + port + '}';
    }

    /**
     * Builder for {@link DnsSdServiceRecord}.
     *
     * @since 0.2.0
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String serviceType;
        private String domain = DnsSdConfig.DEFAULT_DOMAIN;
        private String instanceName;
        private String targetHostname = "localhost";
        private InetAddress targetAddress;
        private int port;
        private int priority = 0;
        private int weight = 50;
        private final Map<String, String> txtAttributes = new LinkedHashMap<>();
        private Duration ttl = DnsSdConfig.DEFAULT_TTL;

        public Builder serviceType(String st) { this.serviceType = st; return this; }
        public Builder domain(String d) { this.domain = d; return this; }
        public Builder instanceName(String n) { this.instanceName = n; return this; }
        public Builder targetHostname(String h) { this.targetHostname = h; return this; }
        public Builder targetAddress(InetAddress a) { this.targetAddress = a; return this; }
        public Builder port(int p) { this.port = p; return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public Builder weight(int w) { this.weight = w; return this; }
        public Builder txtAttributes(Map<String, String> attrs) { this.txtAttributes.putAll(attrs); return this; }
        public Builder addTxtAttribute(String k, String v) { this.txtAttributes.put(k, v); return this; }
        public Builder ttl(Duration t) { this.ttl = t; return this; }

        public DnsSdServiceRecord build() {
            return new DnsSdServiceRecord(serviceType, domain, instanceName,
                    targetHostname, targetAddress, port, priority, weight,
                    txtAttributes, ttl);
        }
    }
}
