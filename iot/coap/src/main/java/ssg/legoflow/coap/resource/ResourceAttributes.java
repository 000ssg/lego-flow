package ssg.legoflow.coap.resource;

import ssg.legoflow.coap.protocol.ContentFormat;

import java.util.Objects;

/**
 * CoRE Link Format resource attributes as defined in RFC 6690.
 *
 * <p>These attributes describe a CoAP resource for discovery purposes
 * and are serialized in the link-format representation.
 *
 * @since 1.0.0
 */
public final class ResourceAttributes {

    private String title;
    private String resourceType;
    private String interfaceDescription;
    private int maxSizeEstimate = -1;
    private int contentFormat = -1;
    private boolean observable;

    /**
     * Creates empty resource attributes.
     *
     * @since 1.0.0
     */
    public ResourceAttributes() {
    }

    /**
     * Returns the human-readable title.
     *
     * @return the title, or {@code null}
     * @since 1.0.0
     */
    public String title() {
        return title;
    }

    /**
     * Sets the human-readable title.
     *
     * @param title the title
     * @return this instance for chaining
     * @since 1.0.0
     */
    public ResourceAttributes title(String title) {
        this.title = title;
        return this;
    }

    /**
     * Returns the resource type (rt=).
     *
     * @return the resource type, or {@code null}
     * @since 1.0.0
     */
    public String resourceType() {
        return resourceType;
    }

    /**
     * Sets the resource type (rt=).
     *
     * @param resourceType the resource type
     * @return this instance for chaining
     * @since 1.0.0
     */
    public ResourceAttributes resourceType(String resourceType) {
        this.resourceType = resourceType;
        return this;
    }

    /**
     * Returns the interface description (if=).
     *
     * @return the interface description, or {@code null}
     * @since 1.0.0
     */
    public String interfaceDescription() {
        return interfaceDescription;
    }

    /**
     * Sets the interface description (if=).
     *
     * @param interfaceDescription the interface description
     * @return this instance for chaining
     * @since 1.0.0
     */
    public ResourceAttributes interfaceDescription(String interfaceDescription) {
        this.interfaceDescription = interfaceDescription;
        return this;
    }

    /**
     * Returns the maximum size estimate in bytes (sz=).
     *
     * @return the max size estimate, or -1 if not set
     * @since 1.0.0
     */
    public int maxSizeEstimate() {
        return maxSizeEstimate;
    }

    /**
     * Sets the maximum size estimate (sz=).
     *
     * @param maxSizeEstimate the max size in bytes
     * @return this instance for chaining
     * @since 1.0.0
     */
    public ResourceAttributes maxSizeEstimate(int maxSizeEstimate) {
        this.maxSizeEstimate = maxSizeEstimate;
        return this;
    }

    /**
     * Returns the content format (ct=).
     *
     * @return the content format value, or -1 if not set
     * @since 1.0.0
     */
    public int contentFormat() {
        return contentFormat;
    }

    /**
     * Sets the content format (ct=).
     *
     * @param contentFormat the content format value
     * @return this instance for chaining
     * @since 1.0.0
     */
    public ResourceAttributes contentFormat(int contentFormat) {
        this.contentFormat = contentFormat;
        return this;
    }

    /**
     * Returns whether the resource is observable (obs).
     *
     * @return {@code true} if observable
     * @since 1.0.0
     */
    public boolean observable() {
        return observable;
    }

    /**
     * Sets whether the resource is observable (obs).
     *
     * @param observable whether the resource is observable
     * @return this instance for chaining
     * @since 1.0.0
     */
    public ResourceAttributes observable(boolean observable) {
        this.observable = observable;
        return this;
    }

    /**
     * Serializes these attributes to CoRE Link Format attribute string
     * (without the URI part).
     *
     * @return the attribute string (e.g. ";rt=temperature;obs;ct=0")
     * @since 1.0.0
     */
    public String toCoreLinkFormat() {
        var sb = new StringBuilder();
        if (resourceType != null) {
            sb.append(";rt=\"").append(resourceType).append('"');
        }
        if (interfaceDescription != null) {
            sb.append(";if=\"").append(interfaceDescription).append('"');
        }
        if (title != null) {
            sb.append(";title=\"").append(title).append('"');
        }
        if (contentFormat >= 0) {
            sb.append(";ct=").append(contentFormat);
        }
        if (maxSizeEstimate >= 0) {
            sb.append(";sz=").append(maxSizeEstimate);
        }
        if (observable) {
            sb.append(";obs");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ResourceAttributes{" + toCoreLinkFormat() + "}";
    }
}
