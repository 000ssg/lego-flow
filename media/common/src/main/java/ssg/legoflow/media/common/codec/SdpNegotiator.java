package ssg.legoflow.media.common.codec;

import ssg.legoflow.media.common.sdp.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * SDP offer/answer negotiator implementing RFC 3264.
 *
 * <p>Computes the intersection of media capabilities between an offer and
 * an answer's supported capabilities. For each media description in the offer,
 * the negotiator finds compatible formats from the answerer's capabilities.
 *
 * @since 0.1.0
 */
public final class SdpNegotiator {

    private SdpNegotiator() {
    }

    /**
     * Negotiates an answer from an offer and the answerer's capabilities.
     *
     * <p>For each media description in the offer, intersects the offered formats
     * with the answerer's supported formats. Media descriptions with no compatible
     * formats are included with port 0 (rejected). The direction is reversed:
     * sendonly becomes recvonly and vice versa.
     *
     * @param offer              the SDP offer
     * @param answererCapabilities the answerer's SDP capabilities (media descriptions
     *                            define what the answerer supports)
     * @return the negotiated answer, or empty if no media is compatible
     */
    public static Optional<SessionDescription> negotiate(
            SessionDescription offer,
            SessionDescription answererCapabilities
    ) {
        List<MediaDescription> answerMedia = new ArrayList<>();
        boolean anyAccepted = false;

        for (MediaDescription offeredMedia : offer.mediaDescriptions()) {
            Optional<MediaDescription> answererMedia = findCompatibleMedia(
                    offeredMedia, answererCapabilities.mediaDescriptions());

            if (answererMedia.isPresent()) {
                MediaDescription answer = intersectMedia(offeredMedia, answererMedia.get());
                if (!answer.formats().isEmpty()) {
                    answerMedia.add(answer);
                    anyAccepted = true;
                } else {
                    answerMedia.add(rejectMedia(offeredMedia));
                }
            } else {
                answerMedia.add(rejectMedia(offeredMedia));
            }
        }

        if (!anyAccepted) {
            return Optional.empty();
        }

        return Optional.of(new SessionDescription(
                0,
                answererCapabilities.origin(),
                answererCapabilities.sessionName(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                answererCapabilities.connectionInfo(),
                List.of(),
                List.of(Timing.PERMANENT),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                answerMedia
        ));
    }

    /**
     * Finds a compatible media description from the answerer's capabilities
     * matching the offered media's type and protocol.
     */
    private static Optional<MediaDescription> findCompatibleMedia(
            MediaDescription offered, List<MediaDescription> capabilities) {
        return capabilities.stream()
                .filter(c -> c.mediaType() == offered.mediaType())
                .filter(c -> c.protocol() == offered.protocol())
                .findFirst();
    }

    /**
     * Intersects the offered formats with the answerer's supported formats.
     */
    private static MediaDescription intersectMedia(
            MediaDescription offered, MediaDescription answerer) {

        // Build set of codec names the answerer supports (for dynamic types via rtpmap)
        Set<String> answererCodecs = new LinkedHashSet<>();
        Set<Integer> answererStaticFormats = new LinkedHashSet<>();
        for (int fmt : answerer.formats()) {
            Optional<RtpMap> rtpMap = answerer.findRtpMap(fmt);
            if (rtpMap.isPresent()) {
                answererCodecs.add(rtpMap.get().codec().toUpperCase() + "/" + rtpMap.get().clockRate());
            } else {
                answererStaticFormats.add(fmt);
            }
        }

        List<Integer> commonFormats = new ArrayList<>();
        List<RtpMap> commonRtpMaps = new ArrayList<>();
        List<FormatParameters> commonFmtp = new ArrayList<>();
        List<Attribute> commonAttrs = new ArrayList<>();

        for (int fmt : offered.formats()) {
            Optional<RtpMap> offeredRtpMap = offered.findRtpMap(fmt);
            if (offeredRtpMap.isPresent()) {
                String key = offeredRtpMap.get().codec().toUpperCase() + "/" + offeredRtpMap.get().clockRate();
                if (answererCodecs.contains(key)) {
                    commonFormats.add(fmt);
                    commonRtpMaps.add(offeredRtpMap.get());
                    // Include fmtp if present
                    offered.formatParameters().stream()
                            .filter(f -> f.payloadType() == fmt)
                            .findFirst()
                            .ifPresent(commonFmtp::add);
                }
            } else if (answererStaticFormats.contains(fmt)) {
                commonFormats.add(fmt);
            }
        }

        // Reverse direction
        Direction answerDirection = reverseDirection(offered.direction());
        commonAttrs.add(Attribute.property(answerDirection.token()));

        // Add rtpmap attributes
        for (RtpMap rm : commonRtpMaps) {
            commonAttrs.add(Attribute.of("rtpmap", rm.format()));
        }

        // Add fmtp attributes
        for (FormatParameters fp : commonFmtp) {
            commonAttrs.add(Attribute.of("fmtp", fp.format()));
        }

        return new MediaDescription(
                offered.mediaType(),
                answerer.port(),
                1,
                offered.protocol(),
                commonFormats,
                Optional.empty(),
                answerer.connectionInfo(),
                List.of(),
                answerDirection,
                commonRtpMaps,
                commonFmtp,
                List.of(),
                Optional.empty(),
                commonAttrs
        );
    }

    /**
     * Creates a rejected media description (port 0).
     */
    private static MediaDescription rejectMedia(MediaDescription offered) {
        return new MediaDescription(
                offered.mediaType(),
                0,
                1,
                offered.protocol(),
                offered.formats().isEmpty() ? List.of(0) : List.of(offered.formats().getFirst()),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Direction.INACTIVE,
                List.of(),
                List.of(),
                List.of(),
                Optional.empty(),
                List.of()
        );
    }

    /**
     * Reverses the direction for an answer.
     */
    private static Direction reverseDirection(Direction offered) {
        return switch (offered) {
            case SENDONLY -> Direction.RECVONLY;
            case RECVONLY -> Direction.SENDONLY;
            case SENDRECV -> Direction.SENDRECV;
            case INACTIVE -> Direction.INACTIVE;
        };
    }
}
