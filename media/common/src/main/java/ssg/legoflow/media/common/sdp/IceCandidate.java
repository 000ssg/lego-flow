package ssg.legoflow.media.common.sdp;

import java.util.Objects;
import java.util.Optional;

/**
 * Parsed ICE candidate attribute ({@code a=candidate:}) as defined in RFC 5245.
 *
 * <p>Format: {@code candidate:<foundation> <component-id> <transport> <priority>
 * <connection-address> <port> typ <cand-type> [raddr <rel-addr> rport <rel-port>]}
 *
 * @param foundation  the candidate foundation string
 * @param componentId the component identifier (1 for RTP, 2 for RTCP)
 * @param transport   the transport protocol (typically "udp" or "tcp")
 * @param priority    the candidate priority
 * @param address     the connection address
 * @param port        the port number
 * @param type        the candidate type (host, srflx, prflx, relay)
 * @param relAddr     the related address (for reflexive/relay candidates)
 * @param relPort     the related port (for reflexive/relay candidates)
 * @param rawLine     the full raw candidate line for preservation
 * @since 0.1.0
 */
public record IceCandidate(
        String foundation,
        int componentId,
        String transport,
        long priority,
        String address,
        int port,
        String type,
        Optional<String> relAddr,
        Optional<Integer> relPort,
        String rawLine
) {

    /**
     * Creates an ICE candidate with validation.
     */
    public IceCandidate {
        Objects.requireNonNull(foundation, "foundation");
        Objects.requireNonNull(transport, "transport");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(relAddr, "relAddr");
        Objects.requireNonNull(relPort, "relPort");
        Objects.requireNonNull(rawLine, "rawLine");
    }

    /**
     * Parses an ICE candidate from the value part of an {@code a=candidate:} attribute.
     *
     * @param value the candidate value (after "candidate:")
     * @return the parsed ICE candidate
     * @throws IllegalArgumentException if the format is invalid
     */
    public static IceCandidate parse(String value) {
        String[] parts = value.split("\\s+");
        if (parts.length < 8) {
            throw new IllegalArgumentException("Invalid candidate, expected at least 8 fields: " + value);
        }
        String foundation = parts[0];
        int componentId = Integer.parseInt(parts[1]);
        String transport = parts[2];
        long priority = Long.parseLong(parts[3]);
        String address = parts[4];
        int port = Integer.parseInt(parts[5]);
        // parts[6] should be "typ"
        String type = parts[7];

        Optional<String> relAddr = Optional.empty();
        Optional<Integer> relPort = Optional.empty();

        for (int i = 8; i < parts.length - 1; i++) {
            if ("raddr".equals(parts[i])) {
                relAddr = Optional.of(parts[i + 1]);
            } else if ("rport".equals(parts[i])) {
                relPort = Optional.of(Integer.parseInt(parts[i + 1]));
            }
        }

        return new IceCandidate(foundation, componentId, transport, priority,
                address, port, type, relAddr, relPort, value);
    }

    /**
     * Formats this ICE candidate for use as the value of an {@code a=candidate:} attribute.
     *
     * @return the formatted candidate value
     */
    public String format() {
        var sb = new StringBuilder();
        sb.append(foundation).append(' ')
                .append(componentId).append(' ')
                .append(transport).append(' ')
                .append(priority).append(' ')
                .append(address).append(' ')
                .append(port).append(" typ ")
                .append(type);
        relAddr.ifPresent(ra -> sb.append(" raddr ").append(ra));
        relPort.ifPresent(rp -> sb.append(" rport ").append(rp));
        return sb.toString();
    }

    @Override
    public String toString() {
        return "a=candidate:" + format();
    }
}
