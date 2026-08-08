package ssg.legoflow.upnp.soap;

import java.util.Map;

/**
 * Represents the result of a UPnP SOAP action invocation.
 *
 * @param success         {@code true} if the action completed successfully
 * @param outputArguments the output arguments from a successful invocation; empty on failure
 * @param fault           the SOAP fault details on failure; {@code null} on success
 * @since 0.1.0
 */
public record SoapResponse(boolean success, Map<String, String> outputArguments, SoapFault fault) {

    /**
     * Creates a successful SOAP response with the given output arguments.
     *
     * @param outputArguments the output arguments
     * @return a successful response
     * @since 0.1.0
     */
    public static SoapResponse success(Map<String, String> outputArguments) {
        return new SoapResponse(true, Map.copyOf(outputArguments), null);
    }

    /**
     * Creates a failed SOAP response with the given fault.
     *
     * @param fault the SOAP fault details
     * @return a failed response
     * @since 0.1.0
     */
    public static SoapResponse failure(SoapFault fault) {
        return new SoapResponse(false, Map.of(), fault);
    }
}
