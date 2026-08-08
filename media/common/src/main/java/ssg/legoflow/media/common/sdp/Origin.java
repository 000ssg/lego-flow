package ssg.legoflow.media.common.sdp;

import java.util.Objects;

/**
 * SDP origin ({@code o=}) field as defined in RFC 4566 section 5.2.
 *
 * <p>Format: {@code o=<username> <sess-id> <sess-version> <nettype> <addrtype> <unicast-address>}
 *
 * @param username   the user's login, or "-" if unavailable
 * @param sessionId  numeric session identifier (NTP timestamp or random)
 * @param version    session version number, incremented on each modification
 * @param netType    network type, typically "IN" (Internet)
 * @param addrType   address type, "IP4" or "IP6"
 * @param address    the originator's unicast address
 * @since 0.1.0
 */
public record Origin(
        String username,
        long sessionId,
        long version,
        String netType,
        String addrType,
        String address
) {

    /**
     * Creates an origin with validation.
     */
    public Origin {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(netType, "netType");
        Objects.requireNonNull(addrType, "addrType");
        Objects.requireNonNull(address, "address");
    }

    /**
     * Parses an origin from an {@code o=} line value.
     *
     * @param line the origin text (after {@code o=})
     * @return the parsed origin
     * @throws IllegalArgumentException if the format is invalid
     */
    public static Origin parse(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length != 6) {
            throw new IllegalArgumentException("Invalid o= line, expected 6 fields: " + line);
        }
        return new Origin(
                parts[0],
                Long.parseLong(parts[1]),
                Long.parseLong(parts[2]),
                parts[3],
                parts[4],
                parts[5]
        );
    }

    /**
     * Formats this origin for SDP output (without the {@code o=} prefix).
     *
     * @return the formatted origin string
     */
    public String format() {
        return username + " " + sessionId + " " + version + " " + netType + " " + addrType + " " + address;
    }

    @Override
    public String toString() {
        return "o=" + format();
    }
}
