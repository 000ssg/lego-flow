package ssg.legoflow.network.common.ber;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.asn1.*;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import ssg.legoflow.network.common.oid.StandardOids;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests encoding/decoding real-world protocol structures (LDAP, SNMP).
 */
class RealWorldProtocolTest {

    // ── LDAP-like structures ──

    @Test
    void testLdapBindRequest() {
        // LDAP BindRequest: SEQUENCE { messageID INTEGER, BindRequest APPLICATION[0] { ... } }
        // Simplified: SEQUENCE { INTEGER(1), SEQUENCE { INTEGER(3), OCTET STRING("cn=admin"), OCTET STRING("password") } }
        var bindRequest = Asn1Sequence.of(
                Asn1Integer.of(1), // messageID
                Asn1Sequence.of(
                        Asn1Integer.of(3), // version
                        Asn1OctetString.of("cn=admin,dc=example,dc=com"), // bind DN
                        Asn1ContextSpecific.implicit(0, "secret".getBytes()) // simple auth
                )
        );

        ByteBuffer encoded = BerEncoder.encode(bindRequest);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isInstanceOf(Asn1Sequence.class);
        var seq = (Asn1Sequence) decoded;
        assertThat(seq.elements()).hasSize(2);
        assertThat(seq.elements().get(0)).isEqualTo(Asn1Integer.of(1));

        var innerSeq = (Asn1Sequence) seq.elements().get(1);
        assertThat(innerSeq.elements()).hasSize(3);
        assertThat(innerSeq.elements().get(0)).isEqualTo(Asn1Integer.of(3));
        assertThat(((Asn1OctetString) innerSeq.elements().get(1)).asString())
                .isEqualTo("cn=admin,dc=example,dc=com");
    }

    @Test
    void testLdapSearchRequest() {
        // Simplified LDAP SearchRequest structure
        var searchRequest = Asn1Sequence.of(
                Asn1Integer.of(2), // messageID
                Asn1Sequence.of(
                        Asn1OctetString.of("dc=example,dc=com"), // baseObject
                        Asn1Enumerated.of(2), // scope: wholeSubtree
                        Asn1Enumerated.of(0), // derefAliases: neverDerefAliases
                        Asn1Integer.of(0), // sizeLimit
                        Asn1Integer.of(0), // timeLimit
                        Asn1Boolean.FALSE, // typesOnly
                        // Filter: present "objectClass"
                        Asn1ContextSpecific.implicit(7, "objectClass".getBytes()),
                        // Attributes
                        Asn1Sequence.of(
                                Asn1OctetString.of("cn"),
                                Asn1OctetString.of("mail"),
                                Asn1OctetString.of("uid")
                        )
                )
        );

        ByteBuffer encoded = BerEncoder.encode(searchRequest);
        assertThat(encoded.remaining()).isGreaterThan(0);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isInstanceOf(Asn1Sequence.class);
        var seq = (Asn1Sequence) decoded;
        assertThat(seq.elements()).hasSize(2);
        assertThat(seq.elements().get(0)).isEqualTo(Asn1Integer.of(2));
    }

    @Test
    void testLdapSearchResultEntry() {
        // LDAP SearchResultEntry
        var entry = Asn1Sequence.of(
                Asn1Integer.of(2), // messageID
                Asn1Sequence.of(
                        Asn1OctetString.of("uid=jdoe,ou=people,dc=example,dc=com"),
                        Asn1Sequence.of( // attributes
                                Asn1Sequence.of( // attribute
                                        Asn1OctetString.of("cn"),
                                        Asn1Set.of(Asn1OctetString.of("John Doe"))
                                ),
                                Asn1Sequence.of(
                                        Asn1OctetString.of("mail"),
                                        Asn1Set.of(Asn1OctetString.of("jdoe@example.com"))
                                )
                        )
                )
        );

        ByteBuffer encoded = BerEncoder.encode(entry);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(entry);
    }

    // ── SNMP-like structures ──

    @Test
    void testSnmpGetRequest() {
        // SNMPv2c GetRequest PDU structure
        var message = Asn1Sequence.of(
                Asn1Integer.of(1), // version: SNMPv2c
                Asn1OctetString.of("public"), // community string
                Asn1Sequence.of( // GetRequest-PDU
                        Asn1Integer.of(12345), // request-id
                        Asn1Integer.of(0), // error-status
                        Asn1Integer.of(0), // error-index
                        Asn1Sequence.of( // variable-bindings
                                Asn1Sequence.of( // varbind
                                        new Asn1ObjectIdentifier(StandardOids.SYS_DESCR),
                                        Asn1Null.INSTANCE
                                ),
                                Asn1Sequence.of(
                                        new Asn1ObjectIdentifier(StandardOids.SYS_NAME),
                                        Asn1Null.INSTANCE
                                )
                        )
                )
        );

        ByteBuffer encoded = BerEncoder.encode(message);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void testSnmpGetResponse() {
        // SNMPv2c GetResponse
        var response = Asn1Sequence.of(
                Asn1Integer.of(1), // version
                Asn1OctetString.of("public"),
                Asn1Sequence.of(
                        Asn1Integer.of(12345), // request-id
                        Asn1Integer.of(0), // error-status: noError
                        Asn1Integer.of(0), // error-index
                        Asn1Sequence.of( // variable-bindings
                                Asn1Sequence.of(
                                        new Asn1ObjectIdentifier(StandardOids.SYS_DESCR),
                                        Asn1OctetString.of("Linux server 5.10.0")
                                ),
                                Asn1Sequence.of(
                                        new Asn1ObjectIdentifier(StandardOids.SYS_NAME),
                                        Asn1OctetString.of("myserver.example.com")
                                ),
                                Asn1Sequence.of(
                                        new Asn1ObjectIdentifier(StandardOids.SYS_UP_TIME),
                                        Asn1Integer.of(123456789)
                                )
                        )
                )
        );

        ByteBuffer encoded = BerEncoder.encode(response);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(response);
    }

    @Test
    void testSnmpTrapPdu() {
        // SNMPv2c Trap with OID varbinds
        var trap = Asn1Sequence.of(
                Asn1Integer.of(1),
                Asn1OctetString.of("public"),
                Asn1Sequence.of(
                        Asn1Integer.of(0), // request-id
                        Asn1Integer.of(0),
                        Asn1Integer.of(0),
                        Asn1Sequence.of(
                                Asn1Sequence.of(
                                        new Asn1ObjectIdentifier(StandardOids.SYS_UP_TIME),
                                        Asn1Integer.of(999)
                                ),
                                Asn1Sequence.of(
                                        Asn1ObjectIdentifier.of("1.3.6.1.6.3.1.1.4.1.0"),
                                        new Asn1ObjectIdentifier(ObjectIdentifier.parse("1.3.6.1.4.1.99.1"))
                                )
                        )
                )
        );

        ByteBuffer encoded = BerEncoder.encode(trap);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(trap);
    }

    // ── X.509-like structures ──

    @Test
    void testX509AttributeTypeAndValue() {
        // X.509 AttributeTypeAndValue: SEQUENCE { OID, value }
        var atv = Asn1Sequence.of(
                new Asn1ObjectIdentifier(StandardOids.ID_AT_COMMON_NAME),
                Asn1Utf8String.of("Example CA")
        );

        ByteBuffer encoded = BerEncoder.encode(atv);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(atv);
    }

    @Test
    void testX509RDNSequence() {
        // X.509 RDNSequence: SEQUENCE OF SET OF AttributeTypeAndValue
        var rdn = Asn1Sequence.of(
                Asn1Set.of(
                        Asn1Sequence.of(
                                new Asn1ObjectIdentifier(StandardOids.ID_AT_COUNTRY_NAME),
                                Asn1PrintableString.of("US")
                        )
                ),
                Asn1Set.of(
                        Asn1Sequence.of(
                                new Asn1ObjectIdentifier(StandardOids.ID_AT_ORGANIZATION_NAME),
                                Asn1Utf8String.of("Example Corp")
                        )
                ),
                Asn1Set.of(
                        Asn1Sequence.of(
                                new Asn1ObjectIdentifier(StandardOids.ID_AT_COMMON_NAME),
                                Asn1Utf8String.of("Example CA")
                        )
                )
        );

        ByteBuffer encoded = BerEncoder.encode(rdn);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(rdn);
    }

    @Test
    void testAlgorithmIdentifier() {
        // AlgorithmIdentifier: SEQUENCE { algorithm OID, parameters ANY }
        var algId = Asn1Sequence.of(
                new Asn1ObjectIdentifier(StandardOids.SHA256_WITH_RSA),
                Asn1Null.INSTANCE
        );

        ByteBuffer encoded = BerEncoder.encode(algId);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(algId);
    }

    // ── Complex nested structure ──

    @Test
    void testComplexNestedStructure() {
        var complex = Asn1Sequence.of(
                Asn1Integer.of(1),
                Asn1Sequence.of(
                        Asn1Boolean.TRUE,
                        Asn1Set.of(
                                Asn1OctetString.of("a"),
                                Asn1OctetString.of("b")
                        ),
                        Asn1ContextSpecific.explicit(0,
                                Asn1Sequence.of(
                                        Asn1ObjectIdentifier.of("1.3.6.1.2.1.1.1"),
                                        Asn1GeneralizedTime.of("20240615103000Z")
                                )
                        )
                ),
                Asn1ContextSpecific.implicit(1, new byte[]{0x01, 0x02}),
                Asn1BitString.of(new byte[]{(byte) 0xFF, (byte) 0x80})
        );

        ByteBuffer encoded = BerEncoder.encode(complex);
        Asn1Type decoded = BerDecoder.decode(encoded);

        assertThat(decoded).isInstanceOf(Asn1Sequence.class);
        var seq = (Asn1Sequence) decoded;
        assertThat(seq.elements()).hasSize(4);
        assertThat(seq.elements().get(0)).isEqualTo(Asn1Integer.of(1));
        assertThat(seq.elements().get(3)).isInstanceOf(Asn1BitString.class);
    }

    @Test
    void testLargeSequence() {
        // Sequence with many elements to test long-form length
        var builder = Asn1Sequence.builder();
        for (int i = 0; i < 50; i++) {
            builder.add(Asn1OctetString.of("element-" + i + "-with-some-padding-to-make-it-longer"));
        }
        var original = builder.build();

        ByteBuffer encoded = BerEncoder.encode(original);
        assertThat(encoded.remaining()).isGreaterThan(127); // should use long-form length
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }
}
