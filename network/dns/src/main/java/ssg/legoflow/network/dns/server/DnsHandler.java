package ssg.legoflow.network.dns.server;

import ssg.legoflow.network.dns.protocol.DnsMessage;

import java.net.SocketAddress;

/**
 * Handler interface for processing DNS queries on the server.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface DnsHandler {

    /**
     * Handles a DNS query and returns a response.
     *
     * @param query  the incoming DNS query
     * @param sender the address of the sender
     * @return the response message
     * @since 1.0.0
     */
    DnsMessage handle(DnsMessage query, SocketAddress sender);
}
