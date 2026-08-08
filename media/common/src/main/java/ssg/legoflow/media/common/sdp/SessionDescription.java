package ssg.legoflow.media.common.sdp;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Complete SDP session description as defined in RFC 4566.
 *
 * <p>Represents the full SDP document with all session-level and media-level fields.
 *
 * @since 0.1.0
 */
public final class SessionDescription {

    private final int version;
    private final Origin origin;
    private final String sessionName;
    private final Optional<String> sessionInfo;
    private final Optional<String> uri;
    private final Optional<String> email;
    private final Optional<String> phone;
    private final Optional<ConnectionInfo> connectionInfo;
    private final List<Bandwidth> bandwidths;
    private final List<Timing> timings;
    private final List<RepeatTime> repeatTimes;
    private final Optional<String> timezoneAdjustments;
    private final Optional<String> encryptionKey;
    private final List<Attribute> attributes;
    private final List<MediaDescription> mediaDescriptions;

    /**
     * Creates a session description.
     *
     * @param version              the protocol version (always 0)
     * @param origin               the origin
     * @param sessionName          the session name
     * @param sessionInfo          optional session information
     * @param uri                  optional URI
     * @param email                optional email
     * @param phone                optional phone
     * @param connectionInfo       optional session-level connection info
     * @param bandwidths           bandwidth specifications
     * @param timings              timing fields
     * @param repeatTimes          repeat time fields
     * @param timezoneAdjustments  optional timezone adjustments (z= line raw value)
     * @param encryptionKey        optional encryption key (k= line raw value)
     * @param attributes           session-level attributes
     * @param mediaDescriptions    media descriptions
     */
    public SessionDescription(
            int version,
            Origin origin,
            String sessionName,
            Optional<String> sessionInfo,
            Optional<String> uri,
            Optional<String> email,
            Optional<String> phone,
            Optional<ConnectionInfo> connectionInfo,
            List<Bandwidth> bandwidths,
            List<Timing> timings,
            List<RepeatTime> repeatTimes,
            Optional<String> timezoneAdjustments,
            Optional<String> encryptionKey,
            List<Attribute> attributes,
            List<MediaDescription> mediaDescriptions
    ) {
        this.version = version;
        this.origin = Objects.requireNonNull(origin, "origin");
        this.sessionName = Objects.requireNonNull(sessionName, "sessionName");
        this.sessionInfo = Objects.requireNonNull(sessionInfo, "sessionInfo");
        this.uri = Objects.requireNonNull(uri, "uri");
        this.email = Objects.requireNonNull(email, "email");
        this.phone = Objects.requireNonNull(phone, "phone");
        this.connectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
        this.bandwidths = List.copyOf(bandwidths);
        this.timings = List.copyOf(timings);
        this.repeatTimes = List.copyOf(repeatTimes);
        this.timezoneAdjustments = Objects.requireNonNull(timezoneAdjustments, "timezoneAdjustments");
        this.encryptionKey = Objects.requireNonNull(encryptionKey, "encryptionKey");
        this.attributes = List.copyOf(attributes);
        this.mediaDescriptions = List.copyOf(mediaDescriptions);
    }

    /** Returns the protocol version (always 0). */
    public int version() { return version; }

    /** Returns the origin. */
    public Origin origin() { return origin; }

    /** Returns the session name. */
    public String sessionName() { return sessionName; }

    /** Returns the optional session information. */
    public Optional<String> sessionInfo() { return sessionInfo; }

    /** Returns the optional URI. */
    public Optional<String> uri() { return uri; }

    /** Returns the optional email. */
    public Optional<String> email() { return email; }

    /** Returns the optional phone. */
    public Optional<String> phone() { return phone; }

    /** Returns the optional session-level connection info. */
    public Optional<ConnectionInfo> connectionInfo() { return connectionInfo; }

    /** Returns bandwidth specifications. */
    public List<Bandwidth> bandwidths() { return bandwidths; }

    /** Returns timing fields. */
    public List<Timing> timings() { return timings; }

    /** Returns repeat time fields. */
    public List<RepeatTime> repeatTimes() { return repeatTimes; }

    /** Returns the optional timezone adjustments raw value. */
    public Optional<String> timezoneAdjustments() { return timezoneAdjustments; }

    /** Returns the optional encryption key raw value. */
    public Optional<String> encryptionKey() { return encryptionKey; }

    /** Returns session-level attributes. */
    public List<Attribute> attributes() { return attributes; }

    /** Returns media descriptions. */
    public List<MediaDescription> mediaDescriptions() { return mediaDescriptions; }

    /**
     * Finds a session-level attribute by name.
     *
     * @param name the attribute name
     * @return the first matching attribute, or empty
     */
    public Optional<Attribute> findAttribute(String name) {
        return attributes.stream()
                .filter(a -> a.name().equalsIgnoreCase(name))
                .findFirst();
    }

    /**
     * Finds all session-level attributes with a given name.
     *
     * @param name the attribute name
     * @return list of matching attributes
     */
    public List<Attribute> findAttributes(String name) {
        return attributes.stream()
                .filter(a -> a.name().equalsIgnoreCase(name))
                .toList();
    }

    /**
     * Returns the effective connection info for a media description.
     * Media-level connection info overrides session-level.
     *
     * @param media the media description
     * @return the effective connection info, or empty
     */
    public Optional<ConnectionInfo> effectiveConnectionInfo(MediaDescription media) {
        return media.connectionInfo().or(() -> connectionInfo);
    }

    @Override
    public String toString() {
        return "SessionDescription[session=" + sessionName
                + ", origin=" + origin
                + ", media=" + mediaDescriptions.size() + "]";
    }
}
