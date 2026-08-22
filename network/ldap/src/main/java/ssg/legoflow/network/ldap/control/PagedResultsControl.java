package ssg.legoflow.network.ldap.control;

import ssg.legoflow.network.common.asn1.Asn1Integer;
import ssg.legoflow.network.common.asn1.Asn1OctetString;
import ssg.legoflow.network.common.asn1.Asn1Sequence;
import ssg.legoflow.network.common.asn1.Asn1Type;
import ssg.legoflow.network.common.ber.BerDecoder;
import ssg.legoflow.network.common.ber.BerEncoder;
import java.nio.ByteBuffer;
/**
 * Simple Paged Results control as defined in RFC 2696.
 *
 * <p>Allows a client to retrieve search results in pages of a specified size.
 * The server returns a cookie that the client sends back to request the next page.
 *
 * <p>OID: {@value #OID}
 *
 * @since 0.1.0
 */
public final class PagedResultsControl {

    /** The OID for the paged results control. */
    public static final String OID = "1.2.840.113556.1.4.319";

    private PagedResultsControl() {}

    /**
     * Creates a paged results request control.
     *
     * @param pageSize the requested page size
     * @param cookie   the cookie from a previous response (empty array for first request)
     * @return the LDAP control
     */
    public static LdapControl request(int pageSize, byte[] cookie) {
        Asn1Sequence seq = Asn1Sequence.of(
                Asn1Integer.of(pageSize),
                new Asn1OctetString(cookie != null ? cookie : new byte[0])
        );
        ByteBuffer encoded = BerEncoder.encode(seq);
        byte[] value = new byte[encoded.remaining()];
        encoded.get(value);
        return LdapControl.of(OID, true, value);
    }

    /**
     * Creates a paged results request control for the first page.
     *
     * @param pageSize the requested page size
     * @return the LDAP control
     */
    public static LdapControl request(int pageSize) {
        return request(pageSize, new byte[0]);
    }

    /**
     * Decodes the page size from a paged results control value.
     *
     * @param controlValue the raw control value
     * @return the page size
     */
    public static int decodePageSize(byte[] controlValue) {
        Asn1Type decoded = BerDecoder.decode(controlValue);
        if (decoded instanceof Asn1Sequence seq && !seq.elements().isEmpty()) {
            if (seq.elements().getFirst() instanceof Asn1Integer pageSize) {
                return pageSize.value().intValueExact();
            }
        }
        throw new IllegalArgumentException("Invalid paged results control value");
    }

    /**
     * Decodes the cookie from a paged results control value.
     *
     * @param controlValue the raw control value
     * @return the cookie (empty array if no more pages)
     */
    public static byte[] decodeCookie(byte[] controlValue) {
        Asn1Type decoded = BerDecoder.decode(controlValue);
        if (decoded instanceof Asn1Sequence seq && seq.elements().size() >= 2) {
            if (seq.elements().get(1) instanceof Asn1OctetString cookie) {
                return cookie.value();
            }
        }
        throw new IllegalArgumentException("Invalid paged results control value");
    }
}
