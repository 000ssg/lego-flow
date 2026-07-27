package ssg.legoflow.coap.client;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.CoapType;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Represents a CoAP response received by a client.
 *
 * @param code    the response code
 * @param payload the response payload
 * @param options the response options
 * @param type    the message type
 * @since 1.0.0
 */
public record CoapResponse(CoapCode code, byte[] payload, List<CoapOption> options, CoapType type) {

    /**
     * Returns the Content-Format option value, or -1 if absent.
     *
     * @return the content format identifier, or -1
     * @since 1.0.0
     */
    public int getContentFormat() {
        for (var option : options) {
            if (option.number() == CoapOption.CONTENT_FORMAT) {
                return option.asInt();
            }
        }
        return -1;
    }

    /**
     * Returns the ETag option value, or {@code null} if absent.
     *
     * @return the entity tag bytes, or {@code null}
     * @since 1.0.0
     */
    public byte[] getETag() {
        for (var option : options) {
            if (option.number() == CoapOption.ETAG) {
                return option.value();
            }
        }
        return null;
    }

    /**
     * Returns the Location-Path assembled from Location-Path options.
     *
     * @return the location path, or an empty string if absent
     * @since 1.0.0
     */
    public String getLocationPath() {
        var sb = new StringBuilder();
        for (var option : options) {
            if (option.number() == CoapOption.LOCATION_PATH) {
                sb.append('/').append(option.asString());
            }
        }
        return sb.toString();
    }

    /**
     * Returns whether this response indicates success (class 2.xx).
     *
     * @return {@code true} if the response code indicates success
     * @since 1.0.0
     */
    public boolean isSuccess() {
        return code.isSuccess();
    }

    /**
     * Returns the payload as a UTF-8 string.
     *
     * @return the payload string
     * @since 1.0.0
     */
    public String getPayloadString() {
        return payload != null ? new String(payload, StandardCharsets.UTF_8) : "";
    }
}
