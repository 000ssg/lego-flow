package ssg.legoflow.media.rtsp.protocol;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Parsed RTSP Transport header as defined in RFC 7826 section 18.54.
 *
 * <p>Format: {@code RTP/AVP[/UDP|/TCP];unicast|multicast;destination=addr;source=addr;
 * client_port=p1-p2;server_port=p1-p2;ssrc=SSRC;interleaved=ch1-ch2}
 *
 * @since 0.1.0
 */
public final class TransportHeader {

    /**
     * Transport protocol type.
     */
    public enum Protocol {
        /** RTP over UDP (default). */
        RTP_AVP_UDP,
        /** RTP over TCP (interleaved). */
        RTP_AVP_TCP
    }

    /**
     * Cast mode.
     */
    public enum CastMode {
        /** Unicast delivery. */
        UNICAST,
        /** Multicast delivery. */
        MULTICAST
    }

    private final Protocol protocol;
    private final CastMode castMode;
    private final Optional<String> destination;
    private final Optional<String> source;
    private final OptionalInt clientPortRtp;
    private final OptionalInt clientPortRtcp;
    private final OptionalInt serverPortRtp;
    private final OptionalInt serverPortRtcp;
    private final Optional<String> ssrc;
    private final OptionalInt interleavedRtp;
    private final OptionalInt interleavedRtcp;

    /**
     * Creates a transport header.
     *
     * @param protocol        the transport protocol
     * @param castMode        the cast mode
     * @param destination     optional destination address
     * @param source          optional source address
     * @param clientPortRtp   optional client RTP port
     * @param clientPortRtcp  optional client RTCP port
     * @param serverPortRtp   optional server RTP port
     * @param serverPortRtcp  optional server RTCP port
     * @param ssrc            optional synchronization source identifier
     * @param interleavedRtp  optional interleaved RTP channel
     * @param interleavedRtcp optional interleaved RTCP channel
     */
    public TransportHeader(
            Protocol protocol,
            CastMode castMode,
            Optional<String> destination,
            Optional<String> source,
            OptionalInt clientPortRtp,
            OptionalInt clientPortRtcp,
            OptionalInt serverPortRtp,
            OptionalInt serverPortRtcp,
            Optional<String> ssrc,
            OptionalInt interleavedRtp,
            OptionalInt interleavedRtcp
    ) {
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.castMode = Objects.requireNonNull(castMode, "castMode");
        this.destination = Objects.requireNonNull(destination, "destination");
        this.source = Objects.requireNonNull(source, "source");
        this.clientPortRtp = Objects.requireNonNull(clientPortRtp, "clientPortRtp");
        this.clientPortRtcp = Objects.requireNonNull(clientPortRtcp, "clientPortRtcp");
        this.serverPortRtp = Objects.requireNonNull(serverPortRtp, "serverPortRtp");
        this.serverPortRtcp = Objects.requireNonNull(serverPortRtcp, "serverPortRtcp");
        this.ssrc = Objects.requireNonNull(ssrc, "ssrc");
        this.interleavedRtp = Objects.requireNonNull(interleavedRtp, "interleavedRtp");
        this.interleavedRtcp = Objects.requireNonNull(interleavedRtcp, "interleavedRtcp");
    }

    /** Returns the transport protocol. */
    public Protocol protocol() { return protocol; }

    /** Returns the cast mode. */
    public CastMode castMode() { return castMode; }

    /** Returns the optional destination address. */
    public Optional<String> destination() { return destination; }

    /** Returns the optional source address. */
    public Optional<String> source() { return source; }

    /** Returns the optional client RTP port. */
    public OptionalInt clientPortRtp() { return clientPortRtp; }

    /** Returns the optional client RTCP port. */
    public OptionalInt clientPortRtcp() { return clientPortRtcp; }

    /** Returns the optional server RTP port. */
    public OptionalInt serverPortRtp() { return serverPortRtp; }

    /** Returns the optional server RTCP port. */
    public OptionalInt serverPortRtcp() { return serverPortRtcp; }

    /** Returns the optional SSRC. */
    public Optional<String> ssrc() { return ssrc; }

    /** Returns the optional interleaved RTP channel. */
    public OptionalInt interleavedRtp() { return interleavedRtp; }

    /** Returns the optional interleaved RTCP channel. */
    public OptionalInt interleavedRtcp() { return interleavedRtcp; }

    /** Returns true if this transport uses interleaved (RTP over TCP). */
    public boolean isInterleaved() { return interleavedRtp.isPresent(); }

    /**
     * Parses a Transport header value.
     *
     * @param value the Transport header value
     * @return the parsed transport header
     * @throws IllegalArgumentException if the format is invalid
     */
    public static TransportHeader parse(String value) {
        var parts = value.split(";");
        var protocol = Protocol.RTP_AVP_UDP;
        var castMode = CastMode.UNICAST;
        Optional<String> destination = Optional.empty();
        Optional<String> source = Optional.empty();
        OptionalInt clientPortRtp = OptionalInt.empty();
        OptionalInt clientPortRtcp = OptionalInt.empty();
        OptionalInt serverPortRtp = OptionalInt.empty();
        OptionalInt serverPortRtcp = OptionalInt.empty();
        Optional<String> ssrc = Optional.empty();
        OptionalInt interleavedRtp = OptionalInt.empty();
        OptionalInt interleavedRtcp = OptionalInt.empty();

        for (String part : parts) {
            String p = part.trim();
            String pLower = p.toLowerCase();
            if (pLower.startsWith("rtp/avp/tcp") || pLower.equals("rtp/avp/tcp")) {
                protocol = Protocol.RTP_AVP_TCP;
            } else if (pLower.equals("unicast")) {
                castMode = CastMode.UNICAST;
            } else if (pLower.equals("multicast")) {
                castMode = CastMode.MULTICAST;
            } else if (pLower.startsWith("destination=")) {
                destination = Optional.of(p.substring(12));
            } else if (pLower.startsWith("source=")) {
                source = Optional.of(p.substring(7));
            } else if (pLower.startsWith("client_port=")) {
                int[] ports = parsePorts(p.substring(12));
                clientPortRtp = OptionalInt.of(ports[0]);
                if (ports.length > 1) clientPortRtcp = OptionalInt.of(ports[1]);
            } else if (pLower.startsWith("server_port=")) {
                int[] ports = parsePorts(p.substring(12));
                serverPortRtp = OptionalInt.of(ports[0]);
                if (ports.length > 1) serverPortRtcp = OptionalInt.of(ports[1]);
            } else if (pLower.startsWith("ssrc=")) {
                ssrc = Optional.of(p.substring(5));
            } else if (pLower.startsWith("interleaved=")) {
                int[] channels = parsePorts(p.substring(12));
                interleavedRtp = OptionalInt.of(channels[0]);
                if (channels.length > 1) interleavedRtcp = OptionalInt.of(channels[1]);
            }
        }

        return new TransportHeader(protocol, castMode, destination, source,
                clientPortRtp, clientPortRtcp, serverPortRtp, serverPortRtcp,
                ssrc, interleavedRtp, interleavedRtcp);
    }

    private static int[] parsePorts(String value) {
        String[] parts = value.split("-");
        if (parts.length == 1) {
            return new int[]{Integer.parseInt(parts[0].trim())};
        }
        return new int[]{
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim())
        };
    }

    /**
     * Formats this transport header as a string value.
     *
     * @return the formatted Transport header value
     */
    public String format() {
        var sb = new StringBuilder();
        sb.append(protocol == Protocol.RTP_AVP_TCP ? "RTP/AVP/TCP" : "RTP/AVP");
        sb.append(';').append(castMode == CastMode.MULTICAST ? "multicast" : "unicast");
        destination.ifPresent(d -> sb.append(";destination=").append(d));
        source.ifPresent(s -> sb.append(";source=").append(s));
        if (clientPortRtp.isPresent()) {
            sb.append(";client_port=").append(clientPortRtp.getAsInt());
            clientPortRtcp.ifPresent(p -> sb.append('-').append(p));
        }
        if (serverPortRtp.isPresent()) {
            sb.append(";server_port=").append(serverPortRtp.getAsInt());
            serverPortRtcp.ifPresent(p -> sb.append('-').append(p));
        }
        ssrc.ifPresent(s -> sb.append(";ssrc=").append(s));
        if (interleavedRtp.isPresent()) {
            sb.append(";interleaved=").append(interleavedRtp.getAsInt());
            interleavedRtcp.ifPresent(c -> sb.append('-').append(c));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "Transport: " + format();
    }
}
