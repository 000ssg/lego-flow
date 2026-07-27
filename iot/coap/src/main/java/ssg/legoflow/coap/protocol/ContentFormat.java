package ssg.legoflow.coap.protocol;

/**
 * CoAP content format identifiers as defined in RFC 7252, Section 12.3.
 *
 * <p>Content formats are used in the Content-Format and Accept options
 * to indicate the representation format of the message payload.
 *
 * @since 1.0.0
 */
public enum ContentFormat {

    /** text/plain; charset=utf-8 */
    TEXT_PLAIN(0),

    /** application/link-format (CoRE Link Format, RFC 6690) */
    APPLICATION_LINK_FORMAT(40),

    /** application/xml */
    APPLICATION_XML(41),

    /** application/octet-stream */
    APPLICATION_OCTET_STREAM(42),

    /** application/exi (Efficient XML Interchange) */
    APPLICATION_EXI(47),

    /** application/json */
    APPLICATION_JSON(50),

    /** application/cbor (Concise Binary Object Representation, RFC 7049) */
    APPLICATION_CBOR(60),

    /** application/senml+json (SenML JSON, RFC 8428) */
    APPLICATION_SENML_JSON(110),

    /** application/senml+cbor (SenML CBOR, RFC 8428) */
    APPLICATION_SENML_CBOR(112);

    private final int value;

    ContentFormat(int value) {
        this.value = value;
    }

    /**
     * Returns the numeric content format identifier.
     *
     * @return the content format value
     * @since 1.0.0
     */
    public int value() {
        return value;
    }

    /**
     * Resolves a {@code ContentFormat} from the given numeric value.
     *
     * @param value the content format identifier
     * @return the matching content format
     * @throws IllegalArgumentException if the value is not recognized
     * @since 1.0.0
     */
    public static ContentFormat fromValue(int value) {
        for (var format : values()) {
            if (format.value == value) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown content format: " + value);
    }
}
