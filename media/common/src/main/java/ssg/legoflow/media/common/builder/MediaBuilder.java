package ssg.legoflow.media.common.builder;

import ssg.legoflow.media.common.sdp.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fluent builder for {@link MediaDescription}.
 *
 * <p>Usage example:
 * <pre>{@code
 * MediaDescription audio = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
 *         .format(0)
 *         .format(8)
 *         .rtpMap(RtpMap.of(96, "opus", 48000, 2))
 *         .format(96)
 *         .direction(Direction.SENDRECV)
 *         .build();
 * }</pre>
 *
 * @since 0.1.0
 */
public final class MediaBuilder {

    private final MediaType mediaType;
    private final int port;
    private int portCount = 1;
    private final TransportProtocol protocol;
    private final List<Integer> formats = new ArrayList<>();
    private Optional<String> title = Optional.empty();
    private Optional<ConnectionInfo> connectionInfo = Optional.empty();
    private final List<Bandwidth> bandwidths = new ArrayList<>();
    private Direction direction = Direction.SENDRECV;
    private final List<RtpMap> rtpMaps = new ArrayList<>();
    private final List<FormatParameters> formatParameters = new ArrayList<>();
    private final List<IceCandidate> iceCandidates = new ArrayList<>();
    private Optional<Fingerprint> fingerprint = Optional.empty();
    private final List<Attribute> attributes = new ArrayList<>();

    /**
     * Creates a media builder.
     *
     * @param mediaType the media type
     * @param port      the transport port
     * @param protocol  the transport protocol
     */
    public MediaBuilder(MediaType mediaType, int port, TransportProtocol protocol) {
        this.mediaType = mediaType;
        this.port = port;
        this.protocol = protocol;
    }

    /** Sets the number of ports. */
    public MediaBuilder portCount(int portCount) {
        this.portCount = portCount;
        return this;
    }

    /** Adds a format (payload type number). */
    public MediaBuilder format(int payloadType) {
        formats.add(payloadType);
        return this;
    }

    /** Sets the media title. */
    public MediaBuilder title(String title) {
        this.title = Optional.of(title);
        return this;
    }

    /** Sets the media-level connection info. */
    public MediaBuilder connectionInfo(ConnectionInfo ci) {
        this.connectionInfo = Optional.of(ci);
        return this;
    }

    /** Adds a bandwidth specification. */
    public MediaBuilder bandwidth(String modifier, int value) {
        bandwidths.add(new Bandwidth(modifier, value));
        return this;
    }

    /** Sets the media direction. */
    public MediaBuilder direction(Direction direction) {
        this.direction = direction;
        return this;
    }

    /** Adds an RTP map and its format number. */
    public MediaBuilder rtpMap(RtpMap rtpMap) {
        rtpMaps.add(rtpMap);
        if (!formats.contains(rtpMap.payloadType())) {
            formats.add(rtpMap.payloadType());
        }
        attributes.add(Attribute.of("rtpmap", rtpMap.format()));
        return this;
    }

    /** Adds format parameters. */
    public MediaBuilder formatParameters(FormatParameters fmtp) {
        formatParameters.add(fmtp);
        attributes.add(Attribute.of("fmtp", fmtp.format()));
        return this;
    }

    /** Adds an ICE candidate. */
    public MediaBuilder iceCandidate(IceCandidate candidate) {
        iceCandidates.add(candidate);
        attributes.add(Attribute.of("candidate", candidate.format()));
        return this;
    }

    /** Sets the DTLS fingerprint. */
    public MediaBuilder fingerprint(Fingerprint fp) {
        this.fingerprint = Optional.of(fp);
        attributes.add(Attribute.of("fingerprint", fp.format()));
        return this;
    }

    /** Adds a generic attribute (property-style). */
    public MediaBuilder attribute(String name) {
        attributes.add(Attribute.property(name));
        return this;
    }

    /** Adds a generic attribute (value-style). */
    public MediaBuilder attribute(String name, String value) {
        attributes.add(Attribute.of(name, value));
        return this;
    }

    /**
     * Builds the media description.
     *
     * @return the built media description
     */
    public MediaDescription build() {
        // Add direction attribute
        List<Attribute> allAttrs = new ArrayList<>(attributes);
        allAttrs.add(Attribute.property(direction.token()));

        return new MediaDescription(
                mediaType, port, portCount, protocol, formats,
                title, connectionInfo, bandwidths, direction,
                rtpMaps, formatParameters, iceCandidates, fingerprint, allAttrs
        );
    }
}
