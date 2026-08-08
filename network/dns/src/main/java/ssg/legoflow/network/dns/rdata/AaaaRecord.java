package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.RecordType;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * AAAA record RDATA: a 16-byte IPv6 address (RFC 3596).
 *
 * @param address the IPv6 address
 * @since 0.1.0
 */
public record AaaaRecord(Inet6Address address) implements RData {

    public AaaaRecord {
        Objects.requireNonNull(address, "address must not be null");
    }

    @Override
    public RecordType type() {
        return RecordType.AAAA;
    }

    /**
     * Creates an AAAA record from an IPv6 address string.
     *
     * @param ip the IPv6 address string
     * @return the AAAA record
     * @throws IllegalArgumentException if the address is invalid
     * @since 0.1.0
     */
    public static AaaaRecord of(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr instanceof Inet6Address v6) {
                return new AaaaRecord(v6);
            }
            throw new IllegalArgumentException("Not an IPv6 address: " + ip);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IPv6 address: " + ip, e);
        }
    }

    /**
     * Creates an AAAA record from raw 16-byte address data.
     *
     * @param bytes the 16-byte IPv6 address
     * @return the AAAA record
     * @throws IllegalArgumentException if the byte array is not 16 bytes
     * @since 0.1.0
     */
    public static AaaaRecord fromBytes(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("IPv6 address must be 16 bytes, got " + bytes.length);
        }
        try {
            return new AaaaRecord((Inet6Address) InetAddress.getByAddress(bytes));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IPv6 address bytes", e);
        }
    }
}
