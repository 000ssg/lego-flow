package ssg.legoflow.media.common.sdp;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * SDP connection information ({@code c=}) field as defined in RFC 4566 section 5.7.
 *
 * <p>Format: {@code c=<nettype> <addrtype> <connection-address>}
 * where connection-address may include {@code /ttl} for IPv4 multicast
 * and {@code /ttl/count} for multiple multicast addresses.
 *
 * @param netType  network type, typically "IN"
 * @param addrType address type, "IP4" or "IP6"
 * @param address  the connection address
 * @param ttl      TTL for IPv4 multicast, empty for unicast
 * @param count    number of contiguous multicast addresses, empty if not specified
 * @since 1.0.0
 */
public record ConnectionInfo(
        String netType,
        String addrType,
        String address,
        OptionalInt ttl,
        OptionalInt count
) {

    /**
     * Creates connection info with validation.
     */
    public ConnectionInfo {
        Objects.requireNonNull(netType, "netType");
        Objects.requireNonNull(addrType, "addrType");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(ttl, "ttl");
        Objects.requireNonNull(count, "count");
    }

    /**
     * Creates unicast connection info.
     *
     * @param netType  network type
     * @param addrType address type
     * @param address  the connection address
     * @return unicast connection info
     */
    public static ConnectionInfo unicast(String netType, String addrType, String address) {
        return new ConnectionInfo(netType, addrType, address, OptionalInt.empty(), OptionalInt.empty());
    }

    /**
     * Creates multicast connection info with TTL.
     *
     * @param netType  network type
     * @param addrType address type
     * @param address  the multicast address
     * @param ttl      time-to-live
     * @return multicast connection info
     */
    public static ConnectionInfo multicast(String netType, String addrType, String address, int ttl) {
        return new ConnectionInfo(netType, addrType, address, OptionalInt.of(ttl), OptionalInt.empty());
    }

    /**
     * Creates multicast connection info with TTL and address count.
     *
     * @param netType  network type
     * @param addrType address type
     * @param address  the base multicast address
     * @param ttl      time-to-live
     * @param count    number of contiguous addresses
     * @return multicast connection info with count
     */
    public static ConnectionInfo multicast(String netType, String addrType, String address, int ttl, int count) {
        return new ConnectionInfo(netType, addrType, address, OptionalInt.of(ttl), OptionalInt.of(count));
    }

    /**
     * Parses connection info from a {@code c=} line value.
     *
     * @param line the connection info text (after {@code c=})
     * @return the parsed connection info
     * @throws IllegalArgumentException if the format is invalid
     */
    public static ConnectionInfo parse(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid c= line, expected 3 fields: " + line);
        }
        String addr = parts[2];
        int slash1 = addr.indexOf('/');
        if (slash1 < 0) {
            return unicast(parts[0], parts[1], addr);
        }
        int slash2 = addr.indexOf('/', slash1 + 1);
        String baseAddr = addr.substring(0, slash1);
        if (slash2 < 0) {
            int ttlVal = Integer.parseInt(addr.substring(slash1 + 1));
            return multicast(parts[0], parts[1], baseAddr, ttlVal);
        }
        int ttlVal = Integer.parseInt(addr.substring(slash1 + 1, slash2));
        int countVal = Integer.parseInt(addr.substring(slash2 + 1));
        return multicast(parts[0], parts[1], baseAddr, ttlVal, countVal);
    }

    /**
     * Formats this connection info for SDP output (without the {@code c=} prefix).
     *
     * @return the formatted connection info string
     */
    public String format() {
        var sb = new StringBuilder();
        sb.append(netType).append(' ').append(addrType).append(' ').append(address);
        ttl.ifPresent(t -> {
            sb.append('/').append(t);
            count.ifPresent(c -> sb.append('/').append(c));
        });
        return sb.toString();
    }

    @Override
    public String toString() {
        return "c=" + format();
    }
}
