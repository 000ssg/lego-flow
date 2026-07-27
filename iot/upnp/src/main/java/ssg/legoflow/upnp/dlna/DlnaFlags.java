package ssg.legoflow.upnp.dlna;

import java.util.EnumSet;
import java.util.Set;

/**
 * DLNA transport flags for the {@code DLNA.ORG_FLAGS} parameter.
 *
 * <p>These flags indicate transport capabilities and requirements for
 * DLNA content transfer. They are combined into a 32-bit hex string
 * followed by 24 zero bytes for the primary and remaining flags fields.
 *
 * @since 1.0.0
 */
public enum DlnaFlags {

    /** Sender controls the pace of data transfer. */
    SENDER_PACED(1 << 31),

    /** Limited Seek Operations: time-based seek supported. */
    LSOP_TIME_BASED_SEEK(1 << 30),

    /** Limited Seek Operations: byte-based seek supported. */
    LSOP_BYTE_BASED_SEEK(1 << 29),

    /** Play container support. */
    PLAY_CONTAINER(1 << 28),

    /** S0 (start of stream) increasing. */
    S0_INCREASING(1 << 27),

    /** SN (end of stream) increasing. */
    SN_INCREASING(1 << 26),

    /** RTSP pause support. */
    RTSP_PAUSE(1 << 25),

    /** Streaming transfer mode supported. */
    STREAMING_TRANSFER(1 << 24),

    /** Interactive transfer mode supported. */
    INTERACTIVE_TRANSFER(1 << 23),

    /** Background transfer mode supported. */
    BACKGROUND_TRANSFER(1 << 22),

    /** Connection stalling support. */
    CONNECTION_STALLING(1 << 21),

    /** DLNA version 1.5 support. */
    DLNA_V15(1 << 20);

    private final int bitMask;

    DlnaFlags(int bitMask) {
        this.bitMask = bitMask;
    }

    /**
     * Returns the bit mask value for this flag.
     *
     * @return the bit mask
     * @since 1.0.0
     */
    public int bitMask() {
        return bitMask;
    }

    /**
     * Combines the given flags into a DLNA flags hex string.
     *
     * <p>The result is a 32-character hex string: 8 hex digits for the primary flags
     * followed by 24 zero digits for the remaining flags field.
     *
     * @param flags the set of flags to combine
     * @return the hex flag string (e.g. "01700000000000000000000000000000")
     * @since 1.0.0
     */
    public static String toHexString(Set<DlnaFlags> flags) {
        int combined = 0;
        for (DlnaFlags flag : flags) {
            combined |= flag.bitMask;
        }
        return String.format("%08x000000000000000000000000", combined);
    }

    /**
     * Combines the given flags into a DLNA flags hex string.
     *
     * @param flags the flags to combine
     * @return the hex flag string
     * @since 1.0.0
     */
    public static String toHexString(DlnaFlags... flags) {
        return toHexString(flags.length == 0 ? EnumSet.noneOf(DlnaFlags.class) : EnumSet.of(flags[0], flags));
    }

    /**
     * Parses a DLNA flags hex string back into a set of flags.
     *
     * @param hexString the hex string (at least 8 hex digits)
     * @return the set of flags present in the string
     * @throws IllegalArgumentException if the hex string is invalid
     * @since 1.0.0
     */
    public static Set<DlnaFlags> fromHexString(String hexString) {
        if (hexString == null || hexString.length() < 8) {
            throw new IllegalArgumentException("Invalid DLNA flags hex string: " + hexString);
        }
        int primaryFlags = (int) Long.parseLong(hexString.substring(0, 8), 16);
        EnumSet<DlnaFlags> result = EnumSet.noneOf(DlnaFlags.class);
        for (DlnaFlags flag : values()) {
            if ((primaryFlags & flag.bitMask) != 0) {
                result.add(flag);
            }
        }
        return result;
    }

    /**
     * Creates a default flags string suitable for streaming media.
     *
     * @return the streaming flags hex string
     * @since 1.0.0
     */
    public static String streamingFlags() {
        return toHexString(EnumSet.of(
                STREAMING_TRANSFER,
                LSOP_TIME_BASED_SEEK,
                LSOP_BYTE_BASED_SEEK,
                DLNA_V15
        ));
    }

    /**
     * Creates a default flags string suitable for interactive image browsing.
     *
     * @return the interactive flags hex string
     * @since 1.0.0
     */
    public static String interactiveFlags() {
        return toHexString(EnumSet.of(
                INTERACTIVE_TRANSFER,
                CONNECTION_STALLING,
                DLNA_V15
        ));
    }
}
