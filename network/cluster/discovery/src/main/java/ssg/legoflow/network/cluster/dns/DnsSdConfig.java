package ssg.legoflow.network.cluster.dns;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for DNS-SD service registration and discovery.
 *
 * <p>Controls the service type, instance name, port, TXT metadata,
 * and multicast interface binding per RFC 8305 and RFC 6762.
 *
 * @param serviceType     the service type, e.g. "_http._tcp"
 * @param domain          the domain suffix, e.g. "local"
 * @param instanceName    the human-readable instance name
 * @param port            the service port
 * @param txtAttributes   key-value metadata
 * @param ttl             record time-to-live
 * @param bindAddress     network interface to bind (null for default)
 * @param probeCount      number of probes before announcement (RFC 6762 §8)
 * @param probeInterval   interval between probes
 * @since 0.2.0
 */
public record DnsSdConfig(
        String serviceType,
        String domain,
        String instanceName,
        int port,
        Map<String, String> txtAttributes,
        Duration ttl,
        InetAddress bindAddress,
        int probeCount,
        Duration probeInterval
) {

    /** Default service domain for mDNS. */
    public static final String DEFAULT_DOMAIN = "local";

    /** Default TTL in seconds. */
    public static final Duration DEFAULT_TTL = Duration.ofSeconds(120);

    /** Default probe count (RFC 6762 recommends 3). */
    public static final int DEFAULT_PROBE_COUNT = 3;

    /** Default probe interval (250 ms per RFC 6762). */
    public static final Duration DEFAULT_PROBE_INTERVAL = Duration.ofMillis(250);

    public DnsSdConfig {
        Objects.requireNonNull(serviceType, "serviceType must not be null");
        if (serviceType.isBlank())
            throw new IllegalArgumentException("serviceType must not be blank");
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(instanceName, "instanceName must not be null");
        if (instanceName.isBlank())
            throw new IllegalArgumentException("instanceName must not be blank");
        if (port < 0 || port > 65535)
            throw new IllegalArgumentException("port must be 0-65535");
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isNegative() || ttl.isZero())
            throw new IllegalArgumentException("ttl must be positive");
        if (probeCount < 1)
            throw new IllegalArgumentException("probeCount must be >= 1");
        Objects.requireNonNull(probeInterval, "probeInterval must not be null");
        if (probeInterval.isNegative() || probeInterval.isZero())
            throw new IllegalArgumentException("probeInterval must be positive");
    }

    /**
     * Creates a default configuration for the given service type and instance.
     *
     * @param serviceType  the service type (e.g. "_http._tcp")
     * @param instanceName the instance name
     * @param port         the service port
     * @return a default configuration
     * @since 0.2.0
     */
    public static DnsSdConfig defaultsFor(String serviceType, String instanceName, int port) {
        return new DnsSdConfig(serviceType, DEFAULT_DOMAIN, instanceName, port,
                Collections.emptyMap(), DEFAULT_TTL, null,
                DEFAULT_PROBE_COUNT, DEFAULT_PROBE_INTERVAL);
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder
     * @since 0.2.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the fully qualified service domain name (e.g. "_http._tcp.local.").
     *
     * @since 0.2.0
     */
    public String serviceDomain() {
        return serviceType + "." + domain;
    }

    /**
     * Returns the fully qualified instance name (e.g. "MyServer._http._tcp.local.").
     *
     * @since 0.2.0
     */
    public String instanceFqdn() {
        return instanceName + "." + serviceDomain();
    }

    /**
     * Builder for {@link DnsSdConfig}.
     *
     * @since 0.2.0
     */
    public static class Builder {
        private String serviceType;
        private String domain = DEFAULT_DOMAIN;
        private String instanceName;
        private int port;
        private final Map<String, String> txtAttributes = new LinkedHashMap<>();
        private Duration ttl = DEFAULT_TTL;
        private InetAddress bindAddress;
        private int probeCount = DEFAULT_PROBE_COUNT;
        private Duration probeInterval = DEFAULT_PROBE_INTERVAL;

        public Builder serviceType(String serviceType) { this.serviceType = serviceType; return this; }
        public Builder domain(String domain) { this.domain = domain; return this; }
        public Builder instanceName(String instanceName) { this.instanceName = instanceName; return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder txtAttributes(Map<String, String> attrs) { this.txtAttributes.putAll(attrs); return this; }
        public Builder addTxtAttribute(String key, String value) { this.txtAttributes.put(key, value); return this; }
        public Builder ttl(Duration ttl) { this.ttl = ttl; return this; }
        public Builder bindAddress(InetAddress addr) { this.bindAddress = addr; return this; }
        public Builder probeCount(int count) { this.probeCount = count; return this; }
        public Builder probeInterval(Duration interval) { this.probeInterval = interval; return this; }

        public DnsSdConfig build() {
            return new DnsSdConfig(serviceType, domain, instanceName, port,
                    Collections.unmodifiableMap(new LinkedHashMap<>(txtAttributes)),
                    ttl, bindAddress, probeCount, probeInterval);
        }
    }
}
