package ssg.legoflow.coap.protocol;

/**
 * CoAP protocol version identifiers as defined in RFC 7252.
 *
 * <p>Currently only version 1 is defined.
 *
 * @since 0.1.0
 */
public enum CoapVersion {

    /** CoAP version 1, the only version defined in RFC 7252. */
    V1(1);

    private final int versionNumber;

    CoapVersion(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    /**
     * Returns the numeric version identifier used in the CoAP message header.
     *
     * @return the version number (1 for V1)
     * @since 0.1.0
     */
    public int versionNumber() {
        return versionNumber;
    }

    /**
     * Resolves a {@code CoapVersion} from the given version number.
     *
     * @param number the version number from the message header
     * @return the matching version
     * @throws IllegalArgumentException if the version number is not recognized
     * @since 0.1.0
     */
    public static CoapVersion fromNumber(int number) {
        return switch (number) {
            case 1 -> V1;
            default -> throw new IllegalArgumentException("Unknown CoAP version: " + number);
        };
    }
}
