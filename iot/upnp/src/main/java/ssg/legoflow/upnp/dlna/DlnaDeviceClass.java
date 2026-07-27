package ssg.legoflow.upnp.dlna;

import java.util.Objects;

/**
 * Represents a DLNA device class with formal certification headers.
 *
 * <p>DLNA device classes are advertised in device description XML via the
 * {@code dlna:X_DLNADOC} element within the {@code urn:schemas-dlna-org:device-1-0}
 * namespace. This class generates the proper XML fragment and HTTP headers
 * for DLNA device certification.
 *
 * @since 1.0.0
 */
public final class DlnaDeviceClass {

    /** DLNA device description namespace. */
    public static final String DLNA_DEVICE_NS = "urn:schemas-dlna-org:device-1-0";

    /** DLNA namespace prefix used in XML. */
    public static final String DLNA_PREFIX = "dlna";

    private final DlnaProfile profile;
    private final String dlnaVersion;

    /**
     * Creates a new DLNA device class.
     *
     * @param profile     the DLNA device profile
     * @param dlnaVersion the DLNA guideline version (e.g. "1.50", "1.51", "3.0")
     * @since 1.0.0
     */
    public DlnaDeviceClass(DlnaProfile profile, String dlnaVersion) {
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.dlnaVersion = Objects.requireNonNull(dlnaVersion, "dlnaVersion must not be null");
    }

    /**
     * Creates a DLNA device class with the default version 1.50.
     *
     * @param profile the DLNA device profile
     * @since 1.0.0
     */
    public DlnaDeviceClass(DlnaProfile profile) {
        this(profile, "1.50");
    }

    /**
     * Returns the DLNA profile.
     *
     * @return the profile
     * @since 1.0.0
     */
    public DlnaProfile profile() {
        return profile;
    }

    /**
     * Returns the DLNA version.
     *
     * @return the version string
     * @since 1.0.0
     */
    public String dlnaVersion() {
        return dlnaVersion;
    }

    /**
     * Generates the {@code dlna:X_DLNADOC} XML element for inclusion in a
     * UPnP device description.
     *
     * @return the XML fragment (e.g. {@code <dlna:X_DLNADOC xmlns:dlna="...">DMS-1.50</dlna:X_DLNADOC>})
     * @since 1.0.0
     */
    public String toXmlElement() {
        return "<" + DLNA_PREFIX + ":X_DLNADOC xmlns:" + DLNA_PREFIX + "=\"" + DLNA_DEVICE_NS + "\">"
                + profile.code() + "-" + dlnaVersion
                + "</" + DLNA_PREFIX + ":X_DLNADOC>";
    }

    /**
     * Generates the DLNA device class value string (e.g. "DMS-1.50").
     *
     * @return the device class string
     * @since 1.0.0
     */
    public String toDeviceClassString() {
        return profile.code() + "-" + dlnaVersion;
    }

    /**
     * Returns the DLNA capability header value for HTTP responses.
     *
     * <p>This generates the value for the {@code X-DLNA-CAPS} custom header
     * used in some DLNA implementations for device capability announcement.
     *
     * @return the capability header value
     * @since 1.0.0
     */
    public String toCapabilityHeader() {
        return profile.code();
    }

    /**
     * Creates a DMS (Digital Media Server) device class.
     *
     * @return the device class for a media server
     * @since 1.0.0
     */
    public static DlnaDeviceClass mediaServer() {
        return new DlnaDeviceClass(DlnaProfile.DIGITAL_MEDIA_SERVER);
    }

    /**
     * Creates a DMR (Digital Media Renderer) device class.
     *
     * @return the device class for a media renderer
     * @since 1.0.0
     */
    public static DlnaDeviceClass mediaRenderer() {
        return new DlnaDeviceClass(DlnaProfile.DIGITAL_MEDIA_RENDERER);
    }

    /**
     * Creates a DMP (Digital Media Player) device class.
     *
     * @return the device class for a media player
     * @since 1.0.0
     */
    public static DlnaDeviceClass mediaPlayer() {
        return new DlnaDeviceClass(DlnaProfile.DIGITAL_MEDIA_PLAYER);
    }

    /**
     * Creates a DMC (Digital Media Controller) device class.
     *
     * @return the device class for a media controller
     * @since 1.0.0
     */
    public static DlnaDeviceClass mediaController() {
        return new DlnaDeviceClass(DlnaProfile.DIGITAL_MEDIA_CONTROLLER);
    }

    @Override
    public String toString() {
        return toDeviceClassString();
    }
}
