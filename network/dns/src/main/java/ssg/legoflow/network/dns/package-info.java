/**
 * DNS protocol implementation (RFC 1034/1035) with DNSSEC, DoH, and DoT.
 *
 * <p>Provides a complete DNS stack including:
 * <ul>
 *   <li>{@link ssg.legoflow.network.dns.protocol} — Wire format codec, message types, and domain names</li>
 *   <li>{@link ssg.legoflow.network.dns.rdata} — Typed RDATA for all supported record types</li>
 *   <li>{@link ssg.legoflow.network.dns.server} — Authoritative DNS server with zone management</li>
 *   <li>{@link ssg.legoflow.network.dns.resolver} — Recursive and stub resolvers with caching</li>
 *   <li>{@link ssg.legoflow.network.dns.client} — DNS client and high-level lookup API</li>
 *   <li>{@link ssg.legoflow.network.dns.transport} — UDP, TCP, DNS-over-HTTPS, DNS-over-TLS</li>
 * </ul>
 *
 * @since 1.0.0
 */
package ssg.legoflow.network.dns;
