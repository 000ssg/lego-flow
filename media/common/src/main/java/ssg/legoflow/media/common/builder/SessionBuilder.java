package ssg.legoflow.media.common.builder;

import ssg.legoflow.media.common.sdp.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fluent builder for {@link SessionDescription}.
 *
 * <p>Usage example:
 * <pre>{@code
 * SessionDescription sdp = new SessionBuilder()
 *         .origin("alice", 2890844526L, 2890842807L, "IN", "IP4", "10.0.0.1")
 *         .sessionName("Audio Call")
 *         .connectionInfo(ConnectionInfo.unicast("IN", "IP4", "10.0.0.1"))
 *         .timing(Timing.PERMANENT)
 *         .media(audioMedia)
 *         .build();
 * }</pre>
 *
 * @since 1.0.0
 */
public final class SessionBuilder {

    private int version = 0;
    private Origin origin;
    private String sessionName = " ";
    private Optional<String> sessionInfo = Optional.empty();
    private Optional<String> uri = Optional.empty();
    private Optional<String> email = Optional.empty();
    private Optional<String> phone = Optional.empty();
    private Optional<ConnectionInfo> connectionInfo = Optional.empty();
    private final List<Bandwidth> bandwidths = new ArrayList<>();
    private final List<Timing> timings = new ArrayList<>();
    private final List<RepeatTime> repeatTimes = new ArrayList<>();
    private Optional<String> timezoneAdjustments = Optional.empty();
    private Optional<String> encryptionKey = Optional.empty();
    private final List<Attribute> attributes = new ArrayList<>();
    private final List<MediaDescription> mediaDescriptions = new ArrayList<>();

    /**
     * Creates a session builder with defaults.
     */
    public SessionBuilder() {
    }

    /** Sets the protocol version. */
    public SessionBuilder version(int version) {
        this.version = version;
        return this;
    }

    /** Sets the origin. */
    public SessionBuilder origin(Origin origin) {
        this.origin = origin;
        return this;
    }

    /** Sets the origin from individual fields. */
    public SessionBuilder origin(String username, long sessionId, long ver,
                                  String netType, String addrType, String address) {
        this.origin = new Origin(username, sessionId, ver, netType, addrType, address);
        return this;
    }

    /** Sets the session name. */
    public SessionBuilder sessionName(String name) {
        this.sessionName = name;
        return this;
    }

    /** Sets the session info. */
    public SessionBuilder sessionInfo(String info) {
        this.sessionInfo = Optional.of(info);
        return this;
    }

    /** Sets the URI. */
    public SessionBuilder uri(String uri) {
        this.uri = Optional.of(uri);
        return this;
    }

    /** Sets the email. */
    public SessionBuilder email(String email) {
        this.email = Optional.of(email);
        return this;
    }

    /** Sets the phone. */
    public SessionBuilder phone(String phone) {
        this.phone = Optional.of(phone);
        return this;
    }

    /** Sets the session-level connection info. */
    public SessionBuilder connectionInfo(ConnectionInfo ci) {
        this.connectionInfo = Optional.of(ci);
        return this;
    }

    /** Adds a bandwidth specification. */
    public SessionBuilder bandwidth(String modifier, int value) {
        bandwidths.add(new Bandwidth(modifier, value));
        return this;
    }

    /** Adds a timing field. */
    public SessionBuilder timing(Timing timing) {
        timings.add(timing);
        return this;
    }

    /** Adds a repeat time. */
    public SessionBuilder repeatTime(RepeatTime repeatTime) {
        repeatTimes.add(repeatTime);
        return this;
    }

    /** Sets the timezone adjustments. */
    public SessionBuilder timezoneAdjustments(String tz) {
        this.timezoneAdjustments = Optional.of(tz);
        return this;
    }

    /** Sets the encryption key. */
    public SessionBuilder encryptionKey(String key) {
        this.encryptionKey = Optional.of(key);
        return this;
    }

    /** Adds a session-level attribute (property-style). */
    public SessionBuilder attribute(String name) {
        attributes.add(Attribute.property(name));
        return this;
    }

    /** Adds a session-level attribute (value-style). */
    public SessionBuilder attribute(String name, String value) {
        attributes.add(Attribute.of(name, value));
        return this;
    }

    /** Adds a media description. */
    public SessionBuilder media(MediaDescription media) {
        mediaDescriptions.add(media);
        return this;
    }

    /**
     * Builds the session description.
     *
     * @return the built session description
     * @throws IllegalStateException if required fields are missing
     */
    public SessionDescription build() {
        if (origin == null) {
            throw new IllegalStateException("Origin is required");
        }
        if (timings.isEmpty()) {
            timings.add(Timing.PERMANENT);
        }
        return new SessionDescription(
                version, origin, sessionName, sessionInfo, uri, email, phone,
                connectionInfo, bandwidths, timings, repeatTimes,
                timezoneAdjustments, encryptionKey, attributes, mediaDescriptions
        );
    }
}
