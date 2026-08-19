package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.RecordType;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
/**
 * A record RDATA: a 4-byte IPv4 address (RFC 1035).
 *
 * @param address the IPv4 address
 * @since 0.1.0
 */
public record ARecord(Inet4Address address) implements RData {

    public ARecord {
        Objects.requireNonNull(address, "address must not be null");
    }

    @Override
    public RecordType type() {
        return RecordType.A;
    }

    /**
     * Creates an A record from a dotted-quad string.
     *
     * @param ip the IPv4 address string (e.g., "192.0.2.1")
     * @return the A record
     * @throws IllegalArgumentException if the address is invalid
     * @since 0.1.0
     */
    public static ARecord of(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr instanceof Inet4Address v4) {
                return new ARecord(v4);
            }
            throw new IllegalArgumentException("Not an IPv4 address: " + ip);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip, e);
        }
    }

    /**
     * Creates an A record from raw 4-byte address data.
     *
     * @param bytes the 4-byte IPv4 address
     * @return the A record
     * @throws IllegalArgumentException if the byte array is not 4 bytes
     * @since 0.1.0
     */
    public static ARecord fromBytes(byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException("IPv4 address must be 4 bytes, got " + bytes.length);
        }
        try {
            return new ARecord((Inet4Address) InetAddress.getByAddress(bytes));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IPv4 address bytes", e);
        }
    }
}
