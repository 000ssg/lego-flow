package ssg.legoflow.messaging.nats.protocol;

/**
 * NATS status codes used in header-based status messages.
 *
 * <p>These status codes are used in JetStream and other NATS features
 * to communicate request outcomes via the NATS/1.0 header status line.
 *
 * @since 0.1.0
 */
public enum NatsStatus {

    /** 100 — Idle heartbeat, no messages available. */
    IDLE_HEARTBEAT(100, "Idle Heartbeat"),

    /** 404 — No messages found or subject not found. */
    NO_MESSAGES(404, "No Messages"),

    /** 408 — Request timeout waiting for messages. */
    REQUEST_TIMEOUT(408, "Request Timeout"),

    /** 409 — Conflict, e.g., consumer already exists or exceeded max waiting. */
    CONFLICT(409, "Conflict"),

    /** 503 — No responders available for request. */
    NO_RESPONDERS(503, "No Responders");

    private final int code;
    private final String description;

    NatsStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Returns the numeric status code.
     *
     * @return the code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the default description.
     *
     * @return the description
     */
    public String description() {
        return description;
    }

    /**
     * Finds the status enum for the given numeric code.
     *
     * @param code the numeric code
     * @return the matching status, or null if not recognized
     */
    public static NatsStatus fromCode(int code) {
        for (NatsStatus s : values()) {
            if (s.code == code) return s;
        }
        return null;
    }

    /**
     * Returns whether this status indicates an error (4xx/5xx).
     *
     * @return true if error status
     */
    public boolean isError() {
        return code >= 400;
    }
}
