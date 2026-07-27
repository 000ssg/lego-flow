package ssg.legoflow.upnp.soap;

/**
 * Constants for SOAP messaging in UPnP action invocation.
 *
 * <p>Defines namespace URIs, content types, and standard UPnP error codes
 * used in SOAP-based service control.
 *
 * @since 1.0.0
 */
public final class SoapConstants {

    /**
     * SOAP 1.1 envelope namespace URI.
     *
     * @since 1.0.0
     */
    public static final String SOAP_ENVELOPE_NS = "http://schemas.xmlsoap.org/soap/envelope/";

    /**
     * SOAP 1.1 encoding namespace URI.
     *
     * @since 1.0.0
     */
    public static final String SOAP_ENCODING_NS = "http://schemas.xmlsoap.org/soap/encoding/";

    /**
     * UPnP control namespace URI.
     *
     * @since 1.0.0
     */
    public static final String UPNP_CONTROL_NS = "urn:schemas-upnp-org:control-1-0";

    /**
     * Content-Type header value for UPnP SOAP requests.
     *
     * @since 1.0.0
     */
    public static final String CONTENT_TYPE = "text/xml; charset=\"utf-8\"";

    // UPnP SOAP error codes

    /** Error 401: Invalid Action - the action name is not recognized. @since 1.0.0 */
    public static final int ERROR_INVALID_ACTION = 401;

    /** Error 402: Invalid Args - incorrect number or type of arguments. @since 1.0.0 */
    public static final int ERROR_INVALID_ARGS = 402;

    /** Error 501: Action Failed - the action could not be executed. @since 1.0.0 */
    public static final int ERROR_ACTION_FAILED = 501;

    /** Error 600: Argument Value Invalid - argument value is out of range. @since 1.0.0 */
    public static final int ERROR_ARGUMENT_VALUE_INVALID = 600;

    /** Error 601: Argument Value Out of Range. @since 1.0.0 */
    public static final int ERROR_ARGUMENT_VALUE_OUT_OF_RANGE = 601;

    /** Error 602: Optional Action Not Implemented. @since 1.0.0 */
    public static final int ERROR_OPTIONAL_ACTION_NOT_IMPLEMENTED = 602;

    /** Error 603: Out of Memory. @since 1.0.0 */
    public static final int ERROR_OUT_OF_MEMORY = 603;

    /** Error 604: Human Intervention Required. @since 1.0.0 */
    public static final int ERROR_HUMAN_INTERVENTION_REQUIRED = 604;

    /** Error 605: String Argument Too Long. @since 1.0.0 */
    public static final int ERROR_STRING_ARGUMENT_TOO_LONG = 605;

    private SoapConstants() {
        // Utility class
    }

    /**
     * Builds the SOAPAction header value for a UPnP action invocation.
     *
     * @param serviceType the service type URN
     * @param actionName  the action name
     * @return the SOAPAction header value in the format: "serviceType#actionName"
     * @since 1.0.0
     */
    public static String soapAction(String serviceType, String actionName) {
        return "\"" + serviceType + "#" + actionName + "\"";
    }
}
