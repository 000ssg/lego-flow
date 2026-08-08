package ssg.legoflow.media.common.payload;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * RTP payload type definition.
 *
 * <p>Static payload types (0-95) are defined by RFC 3551. Dynamic payload types
 * (96-127) are negotiated via SDP.
 *
 * @param number    the payload type number (0-127)
 * @param codec     the encoding name
 * @param clockRate the clock rate in Hz
 * @param channels  the number of audio channels, or empty for video
 * @param mediaType the media type ("audio" or "video")
 * @since 0.1.0
 */
public record PayloadType(
        int number,
        String codec,
        int clockRate,
        OptionalInt channels,
        String mediaType
) {

    /** Minimum dynamic payload type number. */
    public static final int DYNAMIC_MIN = 96;

    /** Maximum payload type number. */
    public static final int MAX = 127;

    // --- Well-known static payload types (RFC 3551) ---

    /** PCMU (G.711 mu-law), 8 kHz, mono. */
    public static final PayloadType PCMU = new PayloadType(0, "PCMU", 8000, OptionalInt.of(1), "audio");

    /** GSM, 8 kHz, mono. */
    public static final PayloadType GSM = new PayloadType(3, "GSM", 8000, OptionalInt.of(1), "audio");

    /** G723, 8 kHz, mono. */
    public static final PayloadType G723 = new PayloadType(4, "G723", 8000, OptionalInt.of(1), "audio");

    /** DVI4, 8 kHz, mono. */
    public static final PayloadType DVI4_8000 = new PayloadType(5, "DVI4", 8000, OptionalInt.of(1), "audio");

    /** DVI4, 16 kHz, mono. */
    public static final PayloadType DVI4_16000 = new PayloadType(6, "DVI4", 16000, OptionalInt.of(1), "audio");

    /** LPC, 8 kHz, mono. */
    public static final PayloadType LPC = new PayloadType(7, "LPC", 8000, OptionalInt.of(1), "audio");

    /** PCMA (G.711 A-law), 8 kHz, mono. */
    public static final PayloadType PCMA = new PayloadType(8, "PCMA", 8000, OptionalInt.of(1), "audio");

    /** G722, 8 kHz, mono. */
    public static final PayloadType G722 = new PayloadType(9, "G722", 8000, OptionalInt.of(1), "audio");

    /** L16 stereo, 44.1 kHz. */
    public static final PayloadType L16_STEREO = new PayloadType(10, "L16", 44100, OptionalInt.of(2), "audio");

    /** L16 mono, 44.1 kHz. */
    public static final PayloadType L16_MONO = new PayloadType(11, "L16", 44100, OptionalInt.of(1), "audio");

    /** QCELP, 8 kHz, mono. */
    public static final PayloadType QCELP = new PayloadType(12, "QCELP", 8000, OptionalInt.of(1), "audio");

    /** CN (Comfort Noise), 8 kHz, mono. */
    public static final PayloadType CN = new PayloadType(13, "CN", 8000, OptionalInt.of(1), "audio");

    /** G729, 8 kHz, mono. */
    public static final PayloadType G729 = new PayloadType(18, "G729", 8000, OptionalInt.of(1), "audio");

    /** JPEG video, 90 kHz. */
    public static final PayloadType JPEG = new PayloadType(26, "JPEG", 90000, OptionalInt.empty(), "video");

    /** H261 video, 90 kHz. */
    public static final PayloadType H261 = new PayloadType(31, "H261", 90000, OptionalInt.empty(), "video");

    /** MPV (MPEG-1/2 video), 90 kHz. */
    public static final PayloadType MPV = new PayloadType(32, "MPV", 90000, OptionalInt.empty(), "video");

    /** MP2T (MPEG-2 Transport Stream), 90 kHz. */
    public static final PayloadType MP2T = new PayloadType(33, "MP2T", 90000, OptionalInt.empty(), "video");

    /** H263 video, 90 kHz. */
    public static final PayloadType H263 = new PayloadType(34, "H263", 90000, OptionalInt.empty(), "video");

    /**
     * Creates a payload type with validation.
     */
    public PayloadType {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(channels, "channels");
        Objects.requireNonNull(mediaType, "mediaType");
        if (number < 0 || number > MAX) {
            throw new IllegalArgumentException("Payload type number must be 0-127: " + number);
        }
    }

    /**
     * Returns whether this is a dynamic payload type (96-127).
     *
     * @return true if dynamic
     */
    public boolean isDynamic() {
        return number >= DYNAMIC_MIN;
    }

    /**
     * Returns whether this is a static payload type (0-95).
     *
     * @return true if static
     */
    public boolean isStatic() {
        return number < DYNAMIC_MIN;
    }
}
