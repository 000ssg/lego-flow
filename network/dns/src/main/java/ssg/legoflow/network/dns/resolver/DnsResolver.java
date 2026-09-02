package ssg.legoflow.network.dns.resolver;

import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.RecordType;
import java.io.IOException;
/**
 * Interface for DNS resolvers.
 *
 * @since 0.1.0
 */
public interface DnsResolver {

    /**
     * Resolves a DNS query.
     *
     * @param query the query message
     * @return the response message
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    DnsMessage resolve(DnsMessage query) throws IOException;

    /**
     * Resolves a domain name and record type.
     *
     * @param name the domain name
     * @param type the record type
     * @return the response message
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    default DnsMessage resolve(String name, RecordType type) throws IOException {
        return resolve(DnsMessage.query(name, type));
    }
}
