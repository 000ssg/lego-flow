package ssg.legoflow.network.ldap.control;

import ssg.legoflow.network.common.asn1.*;
import ssg.legoflow.network.common.ber.BerDecoder;
import ssg.legoflow.network.common.ber.BerEncoder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-Side Sort controls as defined in RFC 2891.
 *
 * <p>Request control OID: {@value #REQUEST_OID}
 * <p>Response control OID: {@value #RESPONSE_OID}
 *
 * @since 1.0.0
 */
public final class SortControl {

    /** The OID for the sort request control. */
    public static final String REQUEST_OID = "1.2.840.113556.1.4.473";

    /** The OID for the sort response control. */
    public static final String RESPONSE_OID = "1.2.840.113556.1.4.474";

    private SortControl() {}

    /**
     * A sort key specifying an attribute and sort order.
     *
     * @param attributeType the attribute to sort by
     * @param orderingRule  the optional matching rule OID (null if default)
     * @param reverseOrder  true to sort in descending order
     * @since 1.0.0
     */
    public record SortKey(String attributeType, String orderingRule, boolean reverseOrder) {

        /**
         * Creates a sort key for ascending order.
         *
         * @param attributeType the attribute to sort by
         * @return the sort key
         */
        public static SortKey ascending(String attributeType) {
            return new SortKey(attributeType, null, false);
        }

        /**
         * Creates a sort key for descending order.
         *
         * @param attributeType the attribute to sort by
         * @return the sort key
         */
        public static SortKey descending(String attributeType) {
            return new SortKey(attributeType, null, true);
        }
    }

    /**
     * Creates a sort request control with the given sort keys.
     *
     * @param criticality whether the control is critical
     * @param sortKeys    the sort keys (at least one required)
     * @return the LDAP control
     */
    public static LdapControl request(boolean criticality, SortKey... sortKeys) {
        List<Asn1Type> keySequences = new ArrayList<>();
        for (SortKey key : sortKeys) {
            Asn1Sequence.Builder builder = Asn1Sequence.builder();
            builder.add(Asn1OctetString.of(key.attributeType()));
            if (key.orderingRule() != null) {
                builder.add(Asn1ContextSpecific.implicit(0,
                        key.orderingRule().getBytes(StandardCharsets.UTF_8)));
            }
            if (key.reverseOrder()) {
                builder.add(Asn1ContextSpecific.implicit(1, new byte[]{(byte) 0xFF}));
            }
            keySequences.add(builder.build());
        }
        Asn1Sequence seq = Asn1Sequence.of(keySequences);
        ByteBuffer encoded = BerEncoder.encode(seq);
        byte[] value = new byte[encoded.remaining()];
        encoded.get(value);
        return LdapControl.of(REQUEST_OID, criticality, value);
    }
}
