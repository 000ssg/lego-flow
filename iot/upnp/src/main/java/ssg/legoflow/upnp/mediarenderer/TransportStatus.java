package ssg.legoflow.upnp.mediarenderer;

/**
 * Enumeration of AVTransport transport statuses.
 *
 * @since 0.1.0
 */
public enum TransportStatus {

    /** Transport is operating normally. */
    OK("OK"),

    /** An error has occurred in the transport. */
    ERROR_OCCURRED("ERROR_OCCURRED");

    private final String value;

    TransportStatus(String value) {
        this.value = value;
    }

    /**
     * Returns the UPnP string value for this status.
     *
     * @return the status string
     * @since 0.1.0
     */
    public String value() {
        return value;
    }

    /**
     * Parses a transport status from its UPnP string value.
     *
     * @param value the status string
     * @return the transport status
     * @throws IllegalArgumentException if the value is unknown
     * @since 0.1.0
     */
    public static TransportStatus fromValue(String value) {
        for (TransportStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown transport status: " + value);
    }
}
