package ssg.legoflow.network.dns.resolver;

import ssg.legoflow.network.dns.client.DnsClient;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.DnsRecord;
import ssg.legoflow.network.dns.protocol.RecordType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Stub resolver that forwards queries to an upstream DNS server.
 *
 * <p>Caches responses based on TTL values. This is the simplest resolver
 * implementation, equivalent to a forwarding/caching resolver.
 *
 * @since 1.0.0
 */
public final class StubResolver implements DnsResolver, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(StubResolver.class);

    private final DnsClient client;
    private final DnsCache cache;

    /**
     * Creates a stub resolver.
     *
     * @param upstream the upstream DNS server address
     * @param timeout  the query timeout
     * @since 1.0.0
     */
    public StubResolver(InetSocketAddress upstream, Duration timeout) {
        this.client = new DnsClient(upstream, timeout);
        this.cache = new DnsCache();
    }

    /**
     * Creates a stub resolver with a custom cache.
     *
     * @param upstream the upstream DNS server address
     * @param timeout  the query timeout
     * @param cache    the cache to use
     * @since 1.0.0
     */
    public StubResolver(InetSocketAddress upstream, Duration timeout, DnsCache cache) {
        this.client = new DnsClient(upstream, timeout);
        this.cache = Objects.requireNonNull(cache);
    }

    @Override
    public DnsMessage resolve(DnsMessage query) throws IOException {
        // Check cache first
        if (!query.questions().isEmpty()) {
            var q = query.questions().get(0);
            List<DnsRecord> cached = cache.get(q.name(), q.type());
            if (!cached.isEmpty()) {
                LOG.debug("Cache hit for {} {}", q.name(), q.type());
                return DnsMessage.responseFor(query, ssg.legoflow.network.dns.protocol.ResponseCode.NOERROR)
                        .answers(cached)
                        .build();
            }
        }

        DnsMessage response = client.query(query);
        cache.put(response);
        return response;
    }

    /**
     * Returns the cache used by this resolver.
     *
     * @return the cache
     * @since 1.0.0
     */
    public DnsCache cache() {
        return cache;
    }

    @Override
    public void close() {
        client.close();
    }
}
