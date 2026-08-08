package ssg.legoflow.network.dns.protocol;

import java.util.Objects;

/**
 * A DNS question entry as defined in RFC 1035, Section 4.1.2.
 *
 * @param name       the domain name being queried
 * @param type       the record type requested
 * @param recordClass the class of the query
 * @since 0.1.0
 */
public record DnsQuestion(
        DnsName name,
        RecordType type,
        RecordClass recordClass
) {

    /**
     * Creates a question with default class IN.
     *
     * @param name the domain name
     * @param type the record type
     * @return the question
     * @since 0.1.0
     */
    public static DnsQuestion of(DnsName name, RecordType type) {
        return new DnsQuestion(
                Objects.requireNonNull(name, "name must not be null"),
                Objects.requireNonNull(type, "type must not be null"),
                RecordClass.IN
        );
    }

    /**
     * Creates a question from a string domain name with default class IN.
     *
     * @param name the domain name string
     * @param type the record type
     * @return the question
     * @since 0.1.0
     */
    public static DnsQuestion of(String name, RecordType type) {
        return of(DnsName.of(name), type);
    }
}
