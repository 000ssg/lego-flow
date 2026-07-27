package ssg.legoflow.network.dns.client;

import ssg.legoflow.network.dns.protocol.*;
import ssg.legoflow.network.dns.transport.TcpTransport;
import ssg.legoflow.network.dns.transport.UdpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * DNS client for sending queries and receiving responses.
 *
 * <p>Sends queries over UDP first, falling back to TCP if the response
 * is truncated (TC flag set). Supports custom server addresses and timeouts.
 *
 * @since 1.0.0
 */
public final class DnsClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DnsClient.class);

    /** Default DNS port. */
    public static final int DEFAULT_PORT = 53;

    private final InetSocketAddress server;
    private final Duration timeout;

    /**
     * Creates a DNS client targeting the given server.
     *
     * @param server  the DNS server address
     * @param timeout the query timeout
     * @since 1.0.0
     */
    public DnsClient(InetSocketAddress server, Duration timeout) {
        this.server = Objects.requireNonNull(server, "server must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    }

    /**
     * Creates a DNS client targeting the given server on the default port.
     *
     * @param host    the DNS server hostname or IP
     * @param timeout the query timeout
     * @since 1.0.0
     */
    public DnsClient(String host, Duration timeout) {
        this(new InetSocketAddress(host, DEFAULT_PORT), timeout);
    }

    /**
     * Sends a DNS query and returns the response.
     *
     * @param query the query message
     * @return the response message
     * @throws IOException if an I/O error occurs
     * @since 1.0.0
     */
    public DnsMessage query(DnsMessage query) throws IOException {
        // Try UDP first
        try (UdpTransport udp = new UdpTransport(timeout)) {
            DnsMessage response = udp.send(query, server);
            if (response.isTruncated()) {
                LOG.debug("Response truncated, retrying with TCP");
                return queryTcp(query);
            }
            return response;
        }
    }

    /**
     * Sends a query for a domain name and record type.
     *
     * @param name the domain name
     * @param type the record type
     * @return the response message
     * @throws IOException if an I/O error occurs
     * @since 1.0.0
     */
    public DnsMessage query(String name, RecordType type) throws IOException {
        return query(DnsMessage.query(name, type));
    }

    /**
     * Sends a query over TCP only.
     *
     * @param query the query message
     * @return the response message
     * @throws IOException if an I/O error occurs
     * @since 1.0.0
     */
    public DnsMessage queryTcp(DnsMessage query) throws IOException {
        try (TcpTransport tcp = new TcpTransport(timeout)) {
            return tcp.send(query, server);
        }
    }

    /**
     * Asynchronously sends a DNS query.
     *
     * @param query the query message
     * @return a future completing with the response
     * @since 1.0.0
     */
    public CompletableFuture<DnsMessage> queryAsync(DnsMessage query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return query(query);
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        });
    }

    @Override
    public void close() {
        // No persistent resources
    }
}
