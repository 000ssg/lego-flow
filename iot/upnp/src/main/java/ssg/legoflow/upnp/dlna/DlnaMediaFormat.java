package ssg.legoflow.upnp.dlna;

/**
 * Enumeration of common DLNA media formats.
 *
 * <p>Each format maps a DLNA profile name to a MIME type, enabling
 * protocol info generation and content format negotiation between
 * DLNA devices.
 *
 * @since 0.1.0
 */
public enum DlnaMediaFormat {

    // --- Image formats ---

    /** JPEG Small — 160x160 or smaller. */
    JPEG_SM("image/jpeg", "JPEG_SM"),

    /** JPEG Medium — 1024x768 or smaller. */
    JPEG_MED("image/jpeg", "JPEG_MED"),

    /** JPEG Large — 4096x4096 or smaller. */
    JPEG_LRG("image/jpeg", "JPEG_LRG"),

    /** PNG Large — up to 4096x4096. */
    PNG_LRG("image/png", "PNG_LRG"),

    // --- Audio formats ---

    /** MP3 audio. */
    MP3("audio/mpeg", "MP3"),

    /** AAC ISO format audio. */
    AAC_ISO("audio/mp4", "AAC_ISO"),

    /** Linear PCM audio. */
    LPCM("audio/L16", "LPCM"),

    // --- Video formats ---

    /** AVC MP4 Baseline Level CIF15. */
    AVC_MP4_BL_CIF15("video/mp4", "AVC_MP4_BL_CIF15"),

    /** AVC MP4 Main Profile SD. */
    AVC_MP4_MP_SD("video/mp4", "AVC_MP4_MP_SD"),

    /** MPEG PS NTSC format. */
    MPEG_PS_NTSC("video/mpeg", "MPEG_PS_NTSC"),

    /** MPEG TS SD North America. */
    MPEG_TS_SD_NA("video/vnd.dlna.mpeg-tts", "MPEG_TS_SD_NA"),

    // --- Additional image formats ---

    /** GIF image. */
    GIF("image/gif", "GIF_LRG"),

    // --- Additional audio formats ---

    /** FLAC lossless audio. */
    FLAC("audio/flac", "FLAC"),

    /** WAV audio. */
    WAV("audio/wav", "WAV"),

    /** WMA base audio. */
    WMA_BASE("audio/x-ms-wma", "WMABASE"),

    /** WMA full audio. */
    WMA_FULL("audio/x-ms-wma", "WMAFULL"),

    /** OGG Vorbis audio. */
    OGG("audio/ogg", "OGG"),

    /** AAC ADTS audio. */
    AAC_ADTS("audio/aac", "AAC_ADTS"),

    // --- Additional video formats ---

    /** AVC MP4 High Profile HD. */
    AVC_MP4_HP_HD("video/mp4", "AVC_MP4_HP_HD_AAC"),

    /** AVC MP4 Main Profile HD. */
    AVC_MP4_MP_HD("video/mp4", "AVC_MP4_MP_HD_AAC"),

    /** AVC MKV Main Profile HD. */
    AVC_MKV_MP_HD("video/x-matroska", "AVC_MKV_MP_HD_AAC"),

    /** MPEG TS HD North America. */
    MPEG_TS_HD_NA("video/vnd.dlna.mpeg-tts", "MPEG_TS_HD_NA"),

    /** AVI format. */
    AVI("video/avi", "AVI"),

    /** WMV base video. */
    WMV_BASE("video/x-ms-wmv", "WMVMED_BASE");

    private final String mimeType;
    private final String dlnaProfileName;

    DlnaMediaFormat(String mimeType, String dlnaProfileName) {
        this.mimeType = mimeType;
        this.dlnaProfileName = dlnaProfileName;
    }

    /**
     * Returns the MIME type for this format.
     *
     * @return the MIME type string
     * @since 0.1.0
     */
    public String mimeType() {
        return mimeType;
    }

    /**
     * Returns the DLNA profile name (DLNA.ORG_PN value) for this format.
     *
     * @return the DLNA profile name
     * @since 0.1.0
     */
    public String dlnaProfileName() {
        return dlnaProfileName;
    }

    /**
     * Creates a {@link DlnaProtocolInfo} for HTTP GET access to content in this format.
     *
     * @return the protocol info
     * @since 0.1.0
     */
    public DlnaProtocolInfo toProtocolInfo() {
        return DlnaProtocolInfo.httpGet(mimeType, dlnaProfileName);
    }

    /**
     * Creates a {@link DlnaProtocolInfo} with the given DLNA flags.
     *
     * @param flags the DLNA.ORG_FLAGS hex string
     * @return the protocol info with flags
     * @since 0.1.0
     */
    public DlnaProtocolInfo toProtocolInfo(String flags) {
        return DlnaProtocolInfo.httpGetWithFlags(mimeType, dlnaProfileName, flags);
    }

    /**
     * Looks up a format by its DLNA profile name.
     *
     * @param profileName the DLNA profile name (e.g. "MP3")
     * @return the matching format
     * @throws IllegalArgumentException if the profile name is unknown
     * @since 0.1.0
     */
    public static DlnaMediaFormat fromProfileName(String profileName) {
        for (DlnaMediaFormat format : values()) {
            if (format.dlnaProfileName.equalsIgnoreCase(profileName)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown DLNA profile name: " + profileName);
    }
}
