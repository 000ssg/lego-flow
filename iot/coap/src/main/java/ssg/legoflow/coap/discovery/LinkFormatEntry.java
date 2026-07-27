package ssg.legoflow.coap.discovery;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single entry in a CoRE Link Format document (RFC 6690).
 *
 * @param uri        the resource URI
 * @param attributes the link attributes (e.g. rt, if, ct, sz, obs, title)
 * @since 1.0.0
 */
public record LinkFormatEntry(String uri, Map<String, String> attributes) {

    /**
     * Compact constructor with defensive copy and validation.
     *
     * @param uri        the resource URI; must not be {@code null}
     * @param attributes the attributes; must not be {@code null}
     */
    public LinkFormatEntry {
        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");
        attributes = Collections.unmodifiableMap(attributes);
    }

    /**
     * Returns the resource type (rt attribute).
     *
     * @return the resource type, or {@code null} if absent
     * @since 1.0.0
     */
    public String getResourceType() {
        return attributes.get("rt");
    }

    /**
     * Returns the interface description (if attribute).
     *
     * @return the interface description, or {@code null} if absent
     * @since 1.0.0
     */
    public String getInterfaceDescription() {
        return attributes.get("if");
    }

    /**
     * Returns the content format (ct attribute).
     *
     * @return the content format string, or {@code null} if absent
     * @since 1.0.0
     */
    public String getContentFormat() {
        return attributes.get("ct");
    }

    /**
     * Returns whether the resource is observable.
     *
     * @return {@code true} if the obs attribute is present
     * @since 1.0.0
     */
    public boolean isObservable() {
        return attributes.containsKey("obs");
    }

    /**
     * Returns the title attribute.
     *
     * @return the title, or {@code null} if absent
     * @since 1.0.0
     */
    public String getTitle() {
        return attributes.get("title");
    }

    /**
     * Returns the maximum size estimate (sz attribute).
     *
     * @return the size estimate as a string, or {@code null} if absent
     * @since 1.0.0
     */
    public String getMaxSizeEstimate() {
        return attributes.get("sz");
    }
}
