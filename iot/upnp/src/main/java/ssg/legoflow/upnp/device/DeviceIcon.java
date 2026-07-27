package ssg.legoflow.upnp.device;

import java.util.Objects;

/**
 * Represents a UPnP device icon as described in the device description XML.
 *
 * @param mimetype the MIME type of the icon (e.g., "image/png")
 * @param width    the icon width in pixels
 * @param height   the icon height in pixels
 * @param depth    the colour depth in bits
 * @param url      the relative or absolute URL to the icon resource
 * @since 1.0.0
 */
public record DeviceIcon(String mimetype, int width, int height, int depth, String url) {

    /**
     * Creates a new {@code DeviceIcon} with validation.
     *
     * @throws NullPointerException     if {@code mimetype} or {@code url} is {@code null}
     * @throws IllegalArgumentException if dimensions or depth are not positive
     */
    public DeviceIcon {
        Objects.requireNonNull(mimetype, "mimetype must not be null");
        Objects.requireNonNull(url, "url must not be null");
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive: " + height);
        }
        if (depth <= 0) {
            throw new IllegalArgumentException("depth must be positive: " + depth);
        }
    }

    /**
     * Serializes this icon to UPnP device description XML fragment.
     *
     * @return the XML representation of this icon
     * @since 1.0.0
     */
    public String toXml() {
        return "<icon>" +
                "<mimetype>" + mimetype + "</mimetype>" +
                "<width>" + width + "</width>" +
                "<height>" + height + "</height>" +
                "<depth>" + depth + "</depth>" +
                "<url>" + url + "</url>" +
                "</icon>";
    }
}
