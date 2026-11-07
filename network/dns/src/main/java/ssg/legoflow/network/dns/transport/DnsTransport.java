package ssg.legoflow.network.dns.transport;

import ssg.legoflow.service.channel.ByteTransport;

/**
 * Byte-level transport SPI for the DNS protocol.
 *
 * <p>Extends {@link ByteTransport} with DNS-specific methods.
 *
 * @since 0.3.0
 */
public interface DnsTransport extends ByteTransport {
}