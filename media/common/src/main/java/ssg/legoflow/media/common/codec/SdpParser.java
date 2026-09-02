package ssg.legoflow.media.common.codec;

import ssg.legoflow.media.common.sdp.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
/**
 * Parses SDP text into a {@link SessionDescription}.
 *
 * <p>Implements parsing of all SDP line types defined in RFC 4566.
 * Unknown line types are silently ignored. Attributes are parsed into
 * specific types where possible (rtpmap, fmtp, candidate, fingerprint, direction)
 * and preserved as generic {@link Attribute} instances.
 *
 * @since 0.1.0
 */
public final class SdpParser {

    private SdpParser() {
    }

    /**
     * Parses SDP text into a session description.
     *
     * @param sdp the complete SDP text
     * @return the parsed session description
     * @throws IllegalArgumentException if required fields are missing or malformed
     */
    public static SessionDescription parse(String sdp) {
        String[] lines = sdp.split("\\r?\\n");

        int version = 0;
        Origin origin = null;
        String sessionName = null;
        Optional<String> sessionInfo = Optional.empty();
        Optional<String> uri = Optional.empty();
        Optional<String> email = Optional.empty();
        Optional<String> phone = Optional.empty();
        Optional<ConnectionInfo> connectionInfo = Optional.empty();
        List<Bandwidth> bandwidths = new ArrayList<>();
        List<Timing> timings = new ArrayList<>();
        List<RepeatTime> repeatTimes = new ArrayList<>();
        Optional<String> timezoneAdjustments = Optional.empty();
        Optional<String> encryptionKey = Optional.empty();
        List<Attribute> sessionAttributes = new ArrayList<>();
        List<MediaDescription> mediaDescriptions = new ArrayList<>();

        // Track current media description being built
        MediaDescriptionBuilder currentMedia = null;

        for (String line : lines) {
            line = line.stripLeading();
            // Strip trailing CR only (preserve spaces in values like "s= ")
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty() || line.length() < 2 || line.charAt(1) != '=') {
                continue;
            }
            char type = line.charAt(0);
            String value = line.substring(2);

            if (type == 'm') {
                // Finalize previous media description
                if (currentMedia != null) {
                    mediaDescriptions.add(currentMedia.build());
                }
                currentMedia = new MediaDescriptionBuilder(value);
                continue;
            }

            if (currentMedia != null) {
                // Media-level line
                parseMediaLine(currentMedia, type, value);
            } else {
                // Session-level line
                switch (type) {
                    case 'v' -> version = Integer.parseInt(value.trim());
                    case 'o' -> origin = Origin.parse(value);
                    case 's' -> sessionName = value;
                    case 'i' -> sessionInfo = Optional.of(value);
                    case 'u' -> uri = Optional.of(value);
                    case 'e' -> email = Optional.of(value);
                    case 'p' -> phone = Optional.of(value);
                    case 'c' -> connectionInfo = Optional.of(ConnectionInfo.parse(value));
                    case 'b' -> bandwidths.add(Bandwidth.parse(value));
                    case 't' -> timings.add(Timing.parse(value));
                    case 'r' -> repeatTimes.add(RepeatTime.parse(value));
                    case 'z' -> timezoneAdjustments = Optional.of(value);
                    case 'k' -> encryptionKey = Optional.of(value);
                    case 'a' -> sessionAttributes.add(Attribute.parse(value));
                    default -> { /* unknown line type, ignore */ }
                }
            }
        }

        // Finalize last media description
        if (currentMedia != null) {
            mediaDescriptions.add(currentMedia.build());
        }

        if (origin == null) {
            throw new IllegalArgumentException("Missing required o= line");
        }
        if (sessionName == null) {
            throw new IllegalArgumentException("Missing required s= line");
        }

        return new SessionDescription(
                version, origin, sessionName, sessionInfo, uri, email, phone,
                connectionInfo, bandwidths, timings, repeatTimes,
                timezoneAdjustments, encryptionKey, sessionAttributes, mediaDescriptions
        );
    }

    private static void parseMediaLine(MediaDescriptionBuilder media, char type, String value) {
        switch (type) {
            case 'i' -> media.title = Optional.of(value);
            case 'c' -> media.connectionInfo = Optional.of(ConnectionInfo.parse(value));
            case 'b' -> media.bandwidths.add(Bandwidth.parse(value));
            case 'k' -> { /* encryption key at media level, ignored */ }
            case 'a' -> {
                Attribute attr = Attribute.parse(value);
                media.attributes.add(attr);
                parseMediaAttribute(media, attr);
            }
            default -> { /* unknown, ignore */ }
        }
    }

    private static void parseMediaAttribute(MediaDescriptionBuilder media, Attribute attr) {
        switch (attr.name()) {
            case "rtpmap" -> attr.value().ifPresent(v -> media.rtpMaps.add(RtpMap.parse(v)));
            case "fmtp" -> attr.value().ifPresent(v -> media.formatParameters.add(FormatParameters.parse(v)));
            case "candidate" -> attr.value().ifPresent(v -> media.iceCandidates.add(IceCandidate.parse(v)));
            case "fingerprint" -> attr.value().ifPresent(v -> media.fingerprint = Optional.of(Fingerprint.parse(v)));
            case "sendrecv", "sendonly", "recvonly", "inactive" ->
                    media.direction = Direction.fromToken(attr.name());
            default -> { /* generic attribute, already added */ }
        }
    }

    /**
     * Internal builder for accumulating media description fields during parsing.
     */
    private static final class MediaDescriptionBuilder {
        final MediaType mediaType;
        final int port;
        final int portCount;
        final TransportProtocol protocol;
        final List<Integer> formats;
        Optional<String> title = Optional.empty();
        Optional<ConnectionInfo> connectionInfo = Optional.empty();
        final List<Bandwidth> bandwidths = new ArrayList<>();
        Direction direction = Direction.SENDRECV;
        final List<RtpMap> rtpMaps = new ArrayList<>();
        final List<FormatParameters> formatParameters = new ArrayList<>();
        final List<IceCandidate> iceCandidates = new ArrayList<>();
        Optional<Fingerprint> fingerprint = Optional.empty();
        final List<Attribute> attributes = new ArrayList<>();

        MediaDescriptionBuilder(String mLine) {
            String[] parts = mLine.split("\\s+");
            if (parts.length < 4) {
                throw new IllegalArgumentException("Invalid m= line, expected at least 4 fields: " + mLine);
            }
            this.mediaType = MediaType.fromToken(parts[0]);

            String portStr = parts[1];
            int slash = portStr.indexOf('/');
            if (slash >= 0) {
                this.port = Integer.parseInt(portStr.substring(0, slash));
                this.portCount = Integer.parseInt(portStr.substring(slash + 1));
            } else {
                this.port = Integer.parseInt(portStr);
                this.portCount = 1;
            }

            this.protocol = TransportProtocol.fromToken(parts[2]);

            this.formats = new ArrayList<>();
            for (int i = 3; i < parts.length; i++) {
                this.formats.add(Integer.parseInt(parts[i]));
            }
        }

        MediaDescription build() {
            return new MediaDescription(
                    mediaType, port, portCount, protocol, formats,
                    title, connectionInfo, bandwidths, direction,
                    rtpMaps, formatParameters, iceCandidates, fingerprint, attributes
            );
        }
    }
}
