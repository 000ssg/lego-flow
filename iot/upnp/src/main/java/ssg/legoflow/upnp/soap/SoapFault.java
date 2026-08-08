package ssg.legoflow.upnp.soap;

/**
 * Represents a UPnP SOAP fault (error response).
 *
 * @param errorCode        the UPnP error code (e.g., 401, 402, 501)
 * @param errorDescription a human-readable description of the error
 * @since 0.1.0
 */
public record SoapFault(int errorCode, String errorDescription) {

    /**
     * Returns a descriptive string combining the error code and description.
     *
     * @return a formatted error string
     * @since 0.1.0
     */
    public String toErrorString() {
        return "UPnPError " + errorCode + ": " + errorDescription;
    }
}
