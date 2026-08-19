package ssg.legoflow.network.dns.resolver;

import ssg.legoflow.network.dns.protocol.*;
import ssg.legoflow.network.dns.rdata.*;
import ssg.legoflow.network.dns.transport.UdpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
/**
 * Recursive DNS resolver that follows NS referrals from root servers.
 *
 * <p>Implements iterative resolution: starting from a set of root hints,
 * the resolver follows NS referrals down the delegation chain until
 * it receives an authoritative answer.
 *
 * @since 0.1.0
 */
public final class RecursiveResolver implements DnsResolver, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RecursiveResolver.class);

    /** Maximum number of referral hops to prevent infinite loops. */
    private static final int MAX_DEPTH = 20;

    private final List<InetSocketAddress> rootServers;
    private final DnsCache cache;
    private final Duration timeout;

    /**
     * Creates a recursive resolver with default root servers.
     *
     * @param timeout the query timeout per hop
     * @since 0.1.0
     */
    public RecursiveResolver(Duration timeout) {
        this(defaultRootServers(), timeout);
    }

    /**
     * Creates a recursive resolver with custom root servers.
     *
     * @param rootServers the root server addresses
     * @param timeout     the query timeout per hop
     * @since 0.1.0
     */
    public RecursiveResolver(List<InetSocketAddress> rootServers, Duration timeout) {
        this.rootServers = new ArrayList<>(rootServers);
        this.cache = new DnsCache();
        this.timeout = timeout;
    }

    /**
     * Creates a recursive resolver with custom root servers and cache.
     *
     * @param rootServers the root server addresses
     * @param timeout     the query timeout per hop
     * @param cache       the cache to use
     * @since 0.1.0
     */
    public RecursiveResolver(List<InetSocketAddress> rootServers, Duration timeout,
                              DnsCache cache) {
        this.rootServers = new ArrayList<>(rootServers);
        this.cache = Objects.requireNonNull(cache);
        this.timeout = timeout;
    }

    @Override
    public DnsMessage resolve(DnsMessage query) throws IOException {
        if (query.questions().isEmpty()) {
            return DnsMessage.responseFor(query, ResponseCode.FORMERR).build();
        }

        var q = query.questions().get(0);

        // Check cache
        List<DnsRecord> cached = cache.get(q.name(), q.type());
        if (!cached.isEmpty()) {
            LOG.debug("Cache hit for {} {}", q.name(), q.type());
            return DnsMessage.responseFor(query, ResponseCode.NOERROR)
                    .answers(cached)
                    .build();
        }

        try {
            DnsMessage response = resolveIterative(q, rootServers, 0);
            if (response != null) {
                cache.put(response);
                // Rebuild response with correct ID
                return DnsMessage.builder()
                        .id(query.header().id())
                        .qr(true)
                        .rd(true)
                        .ra(true)
                        .rCode(response.header().rCode())
                        .questions(query.questions())
                        .answers(response.answers())
                        .authorities(response.authority())
                        .additionals(response.additional())
                        .build();
            }
            return DnsMessage.responseFor(query, ResponseCode.SERVFAIL).build();
        } catch (IOException e) {
            LOG.warn("Resolution failed for {} {}: {}", q.name(), q.type(), e.getMessage());
            return DnsMessage.responseFor(query, ResponseCode.SERVFAIL).build();
        }
    }

    private DnsMessage resolveIterative(DnsQuestion question,
                                         List<InetSocketAddress> servers,
                                         int depth) throws IOException {
        if (depth >= MAX_DEPTH) {
            LOG.warn("Maximum recursion depth reached for {}", question.name());
            return null;
        }

        DnsMessage queryMsg = DnsMessage.builder()
                .id(new Random().nextInt(65536))
                .rd(false)
                .addQuestion(question)
                .build();

        for (InetSocketAddress server : servers) {
            try (UdpTransport udp = new UdpTransport(timeout)) {
                DnsMessage response = udp.send(queryMsg, server);

                // Got authoritative answer or NXDOMAIN
                if (response.isAuthoritative() || !response.answers().isEmpty()) {
                    return response;
                }

                // Check for NXDOMAIN
                if (response.header().rCode() == ResponseCode.NXDOMAIN) {
                    return response;
                }

                // Check for referral (NS records in authority section)
                List<InetSocketAddress> nextServers = extractReferralServers(response);
                if (!nextServers.isEmpty()) {
                    LOG.debug("Following referral to {} servers for {}",
                            nextServers.size(), question.name());
                    DnsMessage result = resolveIterative(question, nextServers, depth + 1);
                    if (result != null) {
                        return result;
                    }
                }
            } catch (IOException e) {
                LOG.debug("Failed to query {}: {}", server, e.getMessage());
                // Try next server
            }
        }

        return null;
    }

    private List<InetSocketAddress> extractReferralServers(DnsMessage response) {
        List<InetSocketAddress> servers = new ArrayList<>();

        // First try glue records in additional section
        for (DnsRecord additional : response.additional()) {
            if (additional.rdata() instanceof ARecord a) {
                servers.add(new InetSocketAddress(a.address(), 53));
            } else if (additional.rdata() instanceof AaaaRecord aaaa) {
                servers.add(new InetSocketAddress(aaaa.address(), 53));
            }
        }

        if (servers.isEmpty()) {
            // Try to resolve NS names from authority section
            for (DnsRecord auth : response.authority()) {
                if (auth.rdata() instanceof NsRecord ns) {
                    // Check cache for NS address
                    List<DnsRecord> cached = cache.get(ns.nameServer(), RecordType.A);
                    for (DnsRecord r : cached) {
                        if (r.rdata() instanceof ARecord a) {
                            servers.add(new InetSocketAddress(a.address(), 53));
                        }
                    }
                }
            }
        }

        return servers;
    }

    /**
     * Returns the cache used by this resolver.
     *
     * @return the cache
     * @since 0.1.0
     */
    public DnsCache cache() {
        return cache;
    }

    @Override
    public void close() {
        // No persistent resources
    }

    /**
     * Returns the default root server addresses.
     *
     * @return list of root server addresses
     * @since 0.1.0
     */
    public static List<InetSocketAddress> defaultRootServers() {
        return List.of(
                new InetSocketAddress("198.41.0.4", 53),      // a.root-servers.net
                new InetSocketAddress("170.247.170.2", 53),    // b.root-servers.net
                new InetSocketAddress("192.33.4.12", 53),      // c.root-servers.net
                new InetSocketAddress("199.7.91.13", 53),      // d.root-servers.net
                new InetSocketAddress("192.203.230.10", 53),   // e.root-servers.net
                new InetSocketAddress("192.5.5.241", 53),      // f.root-servers.net
                new InetSocketAddress("192.112.36.4", 53),     // g.root-servers.net
                new InetSocketAddress("198.97.190.53", 53),    // h.root-servers.net
                new InetSocketAddress("192.36.148.17", 53),    // i.root-servers.net
                new InetSocketAddress("192.58.128.30", 53),    // j.root-servers.net
                new InetSocketAddress("193.0.14.129", 53),     // k.root-servers.net
                new InetSocketAddress("199.7.83.42", 53),      // l.root-servers.net
                new InetSocketAddress("202.12.27.33", 53)      // m.root-servers.net
        );
    }
}
