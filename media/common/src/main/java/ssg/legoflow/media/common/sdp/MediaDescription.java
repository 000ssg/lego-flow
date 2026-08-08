package ssg.legoflow.media.common.sdp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SDP media description ({@code m=}) field and associated media-level attributes
 * as defined in RFC 4566 section 5.14.
 *
 * <p>Format: {@code m=<media> <port>[/<number of ports>] <proto> <fmt> ...}
 *
 * @since 0.1.0
 */
public final class MediaDescription {

    private final MediaType mediaType;
    private final int port;
    private final int portCount;
    private final TransportProtocol protocol;
    private final List<Integer> formats;
    private final Optional<String> title;
    private final Optional<ConnectionInfo> connectionInfo;
    private final List<Bandwidth> bandwidths;
    private final Direction direction;
    private final List<RtpMap> rtpMaps;
    private final List<FormatParameters> formatParameters;
    private final List<IceCandidate> iceCandidates;
    private final Optional<Fingerprint> fingerprint;
    private final List<Attribute> attributes;

    /**
     * Creates a media description.
     *
     * @param mediaType       the media type
     * @param port            the transport port
     * @param portCount       the number of ports (1 if not specified)
     * @param protocol        the transport protocol
     * @param formats         the format list (payload type numbers)
     * @param title           optional media title (i= line)
     * @param connectionInfo  optional media-level connection info
     * @param bandwidths      bandwidth specifications
     * @param direction       the media direction
     * @param rtpMaps         RTP map attributes
     * @param formatParameters format parameter attributes
     * @param iceCandidates   ICE candidate attributes
     * @param fingerprint     optional DTLS fingerprint
     * @param attributes      all media-level attributes (including parsed ones)
     */
    public MediaDescription(
            MediaType mediaType,
            int port,
            int portCount,
            TransportProtocol protocol,
            List<Integer> formats,
            Optional<String> title,
            Optional<ConnectionInfo> connectionInfo,
            List<Bandwidth> bandwidths,
            Direction direction,
            List<RtpMap> rtpMaps,
            List<FormatParameters> formatParameters,
            List<IceCandidate> iceCandidates,
            Optional<Fingerprint> fingerprint,
            List<Attribute> attributes
    ) {
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
        this.port = port;
        this.portCount = portCount;
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.formats = List.copyOf(formats);
        this.title = Objects.requireNonNull(title, "title");
        this.connectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        this.bandwidths = List.copyOf(bandwidths);
        this.direction = Objects.requireNonNull(direction, "direction");
        this.rtpMaps = List.copyOf(rtpMaps);
        this.formatParameters = List.copyOf(formatParameters);
        this.iceCandidates = List.copyOf(iceCandidates);
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.attributes = List.copyOf(attributes);
    }

    /** Returns the media type. */
    public MediaType mediaType() { return mediaType; }

    /** Returns the transport port. */
    public int port() { return port; }

    /** Returns the number of ports. */
    public int portCount() { return portCount; }

    /** Returns the transport protocol. */
    public TransportProtocol protocol() { return protocol; }

    /** Returns the format list (payload type numbers). */
    public List<Integer> formats() { return formats; }

    /** Returns the optional media title. */
    public Optional<String> title() { return title; }

    /** Returns the optional media-level connection info. */
    public Optional<ConnectionInfo> connectionInfo() { return connectionInfo; }

    /** Returns bandwidth specifications. */
    public List<Bandwidth> bandwidths() { return bandwidths; }

    /** Returns the media direction. */
    public Direction direction() { return direction; }

    /** Returns the RTP map attributes. */
    public List<RtpMap> rtpMaps() { return rtpMaps; }

    /** Returns the format parameter attributes. */
    public List<FormatParameters> formatParameters() { return formatParameters; }

    /** Returns the ICE candidate attributes. */
    public List<IceCandidate> iceCandidates() { return iceCandidates; }

    /** Returns the optional DTLS fingerprint. */
    public Optional<Fingerprint> fingerprint() { return fingerprint; }

    /** Returns all media-level attributes. */
    public List<Attribute> attributes() { return attributes; }

    /**
     * Finds a specific attribute by name.
     *
     * @param name the attribute name
     * @return the first matching attribute, or empty
     */
    public Optional<Attribute> findAttribute(String name) {
        return attributes.stream()
                .filter(a -> a.name().equalsIgnoreCase(name))
                .findFirst();
    }

    /**
     * Finds all attributes with a given name.
     *
     * @param name the attribute name
     * @return list of matching attributes
     */
    public List<Attribute> findAttributes(String name) {
        return attributes.stream()
                .filter(a -> a.name().equalsIgnoreCase(name))
                .toList();
    }

    /**
     * Finds the RTP map for a given payload type.
     *
     * @param payloadType the payload type number
     * @return the RTP map, or empty
     */
    public Optional<RtpMap> findRtpMap(int payloadType) {
        return rtpMaps.stream()
                .filter(r -> r.payloadType() == payloadType)
                .findFirst();
    }

    /**
     * Formats the m= line for SDP output (without the {@code m=} prefix).
     *
     * @return the formatted media line
     */
    public String formatMediaLine() {
        var sb = new StringBuilder();
        sb.append(mediaType.token()).append(' ').append(port);
        if (portCount > 1) {
            sb.append('/').append(portCount);
        }
        sb.append(' ').append(protocol.token());
        for (int fmt : formats) {
            sb.append(' ').append(fmt);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "m=" + formatMediaLine();
    }
}
