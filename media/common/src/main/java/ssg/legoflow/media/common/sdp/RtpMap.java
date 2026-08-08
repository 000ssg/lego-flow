package ssg.legoflow.media.common.sdp;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Parsed RTP map attribute ({@code a=rtpmap:}).
 *
 * <p>Format: {@code a=rtpmap:<payload type> <encoding name>/<clock rate>[/<encoding parameters>]}
 *
 * @param payloadType the RTP payload type number (0-127)
 * @param codec       the encoding name (e.g., "PCMU", "H264")
 * @param clockRate   the clock rate in Hz
 * @param channels    the number of channels (empty for video or default single channel)
 * @since 0.1.0
 */
public record RtpMap(int payloadType, String codec, int clockRate, OptionalInt channels) {

    /**
     * Creates an RTP map with validation.
     */
    public RtpMap {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(channels, "channels");
        if (payloadType < 0 || payloadType > 127) {
            throw new IllegalArgumentException("Payload type must be 0-127: " + payloadType);
        }
        if (clockRate <= 0) {
            throw new IllegalArgumentException("Clock rate must be positive: " + clockRate);
        }
    }

    /**
     * Creates an RTP map without explicit channel count.
     *
     * @param payloadType the payload type number
     * @param codec       the encoding name
     * @param clockRate   the clock rate in Hz
     * @return the RTP map
     */
    public static RtpMap of(int payloadType, String codec, int clockRate) {
        return new RtpMap(payloadType, codec, clockRate, OptionalInt.empty());
    }

    /**
     * Creates an RTP map with explicit channel count.
     *
     * @param payloadType the payload type number
     * @param codec       the encoding name
     * @param clockRate   the clock rate in Hz
     * @param channels    the number of channels
     * @return the RTP map
     */
    public static RtpMap of(int payloadType, String codec, int clockRate, int channels) {
        return new RtpMap(payloadType, codec, clockRate, OptionalInt.of(channels));
    }

    /**
     * Parses an RTP map from the value part of an {@code a=rtpmap:} attribute.
     *
     * @param value the rtpmap value (e.g., "96 H264/90000")
     * @return the parsed RTP map
     * @throws IllegalArgumentException if the format is invalid
     */
    public static RtpMap parse(String value) {
        int space = value.indexOf(' ');
        if (space < 0) {
            throw new IllegalArgumentException("Invalid rtpmap value, expected space: " + value);
        }
        int pt = Integer.parseInt(value.substring(0, space).trim());
        String encoding = value.substring(space + 1).trim();
        String[] encParts = encoding.split("/");
        if (encParts.length < 2) {
            throw new IllegalArgumentException("Invalid rtpmap encoding, expected codec/rate: " + encoding);
        }
        String codec = encParts[0];
        int rate = Integer.parseInt(encParts[1]);
        if (encParts.length >= 3) {
            int ch = Integer.parseInt(encParts[2]);
            return of(pt, codec, rate, ch);
        }
        return of(pt, codec, rate);
    }

    /**
     * Formats this RTP map for use as the value of an {@code a=rtpmap:} attribute.
     *
     * @return the formatted rtpmap value
     */
    public String format() {
        var sb = new StringBuilder();
        sb.append(payloadType).append(' ').append(codec).append('/').append(clockRate);
        channels.ifPresent(ch -> sb.append('/').append(ch));
        return sb.toString();
    }

    @Override
    public String toString() {
        return "a=rtpmap:" + format();
    }
}
