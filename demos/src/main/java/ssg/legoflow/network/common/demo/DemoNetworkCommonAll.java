package ssg.legoflow.network.common.demo;

import ssg.legoflow.network.common.asn1.*;
import ssg.legoflow.network.common.ber.BerDecoder;
import ssg.legoflow.network.common.ber.BerEncoder;
import ssg.legoflow.network.common.ber.BerLength;
import ssg.legoflow.network.common.ber.BerTag;
import ssg.legoflow.network.common.der.DerEncoder;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import ssg.legoflow.network.common.oid.OidRegistry;
import ssg.legoflow.network.common.oid.StandardOids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Comprehensive demo of all network-common (BER/ASN.1) module features.
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Primitive type encoding/decoding — Boolean, Integer, OctetString, Null, Enumerated</li>
 *   <li>String type encoding/decoding — UTF-8, PrintableString, IA5String, GeneralizedTime</li>
 *   <li>BitString encoding/decoding — with unused bits tracking</li>
 *   <li>OID encoding/decoding — ObjectIdentifier BER round-trip</li>
 *   <li>Sequence encoding/decoding — ordered constructed type</li>
 *   <li>Set encoding/decoding — unordered constructed type</li>
 *   <li>Context-specific tags — explicit and implicit tagging</li>
 *   <li>DER canonical encoding — SET element sorting</li>
 *   <li>OID operations — parsing, prefix matching, child creation, comparison</li>
 *   <li>OID registry — name lookup, registration, display names</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoNetworkCommonAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoNetworkCommonAll.class);

    /** Set to {@code true} to use an external ASN.1 toolkit instead of in-house codec. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external ASN.1 toolkit. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "127.0.0.1";

    /** Port for external ASN.1 toolkit. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 0;

    private DemoNetworkCommonAll() {}

    /**
     * Results from running the full demo.
     *
     * @param primitiveRoundTrip     number of primitive types successfully round-tripped
     * @param stringRoundTrip        number of string types successfully round-tripped
     * @param bitStringRoundTrip     true if BitString encode/decode preserved data and unused bits
     * @param oidRoundTrip           true if OID encode/decode preserved all arcs
     * @param sequenceRoundTrip      number of elements in the decoded sequence
     * @param setRoundTrip           number of elements in the decoded set
     * @param contextSpecificTags    true if both explicit and implicit context-specific tags worked
     * @param derCanonicalEncoding   true if DER sorted SET elements by tag
     * @param oidOperations          true if parsing, prefix, child, comparison all succeeded
     * @param oidRegistryLookups     number of successful OID registry lookups
     */
    public record Results(
            int primitiveRoundTrip,
            int stringRoundTrip,
            boolean bitStringRoundTrip,
            boolean oidRoundTrip,
            int sequenceRoundTrip,
            int setRoundTrip,
            boolean contextSpecificTags,
            boolean derCanonicalEncoding,
            boolean oidOperations,
            int oidRegistryLookups
    ) {}

    /**
     * Runs the comprehensive demo covering all BER/ASN.1 features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        int primitives = demoPrimitiveRoundTrip();
        int strings = demoStringRoundTrip();
        boolean bitString = demoBitStringRoundTrip();
        boolean oid = demoOidRoundTrip();
        int seqElements = demoSequenceRoundTrip();
        int setElements = demoSetRoundTrip();
        boolean ctxTags = demoContextSpecificTags();
        boolean derCanonical = demoDerCanonicalEncoding();
        boolean oidOps = demoOidOperations();
        int registryLookups = demoOidRegistryLookups();

        return new Results(primitives, strings, bitString, oid, seqElements, setElements,
                ctxTags, derCanonical, oidOps, registryLookups);
    }

    // ======================== 1. PRIMITIVE ROUND-TRIP ===========================

    /**
     * Demonstrates encoding and decoding of primitive ASN.1 types:
     * Boolean, Integer, OctetString, Null, Enumerated.
     *
     * @return the number of types successfully round-tripped
     */
    static int demoPrimitiveRoundTrip() {
        LOG.info("=== 1. Primitive Type Round-Trip ===");
        int count = 0;

        // Boolean
        Asn1Boolean boolTrue = Asn1Boolean.of(true);
        ByteBuffer encoded = BerEncoder.encode(boolTrue);
        Asn1Type decoded = BerDecoder.decode(encoded);
        if (decoded instanceof Asn1Boolean b && b.value()) {
            count++;
            LOG.info("Boolean(true) round-trip OK");
        }

        // Integer
        Asn1Integer bigInt = new Asn1Integer(BigInteger.valueOf(123456789L));
        encoded = BerEncoder.encode(bigInt);
        decoded = BerDecoder.decode(encoded);
        if (decoded instanceof Asn1Integer i && i.value().longValue() == 123456789L) {
            count++;
            LOG.info("Integer(123456789) round-trip OK");
        }

        // OctetString
        byte[] testData = {0x01, 0x02, 0x03, (byte) 0xFF};
        Asn1OctetString octetStr = new Asn1OctetString(testData);
        encoded = BerEncoder.encode(octetStr);
        decoded = BerDecoder.decode(encoded);
        if (decoded instanceof Asn1OctetString os && os.value().length == 4) {
            count++;
            LOG.info("OctetString(4 bytes) round-trip OK");
        }

        // Null
        encoded = BerEncoder.encode(Asn1Null.INSTANCE);
        decoded = BerDecoder.decode(encoded);
        if (decoded instanceof Asn1Null) {
            count++;
            LOG.info("Null round-trip OK");
        }

        // Enumerated
        Asn1Enumerated enumVal = Asn1Enumerated.of(42);
        encoded = BerEncoder.encode(enumVal);
        decoded = BerDecoder.decode(encoded);
        if (decoded instanceof Asn1Enumerated e && e.value() == 42) {
            count++;
            LOG.info("Enumerated(42) round-trip OK");
        }

        LOG.info("Primitive round-trips: {}/5", count);
        return count;
    }

    // ======================== 2. STRING ROUND-TRIP ==============================

    /**
     * Demonstrates encoding and decoding of ASN.1 string types:
     * UTF8String, PrintableString, IA5String, GeneralizedTime.
     *
     * @return the number of string types successfully round-tripped
     */
    static int demoStringRoundTrip() {
        LOG.info("=== 2. String Type Round-Trip ===");
        int count = 0;

        // UTF8String
        Asn1Utf8String utf8 = Asn1Utf8String.of("Hello, World!");
        ByteBuffer encoded = BerEncoder.encode(utf8);
        Asn1Type decoded = BerDecoder.decode(encoded);
        if (decoded instanceof Asn1Utf8String s && "Hello, World!".equals(s.value())) {
            count++;
            LOG.info("UTF8String round-trip OK");
        }

        // PrintableString
        Asn1PrintableString printable = Asn1PrintableString.of("Test Corp");
        encoded = BerEncoder.encode(printable);
        decoded = BerDecoder.decode(encoded);
        if (decoded instanceof Asn1PrintableString s && "Test Corp".equals(s.value())) {
            count++;
            LOG.info("PrintableString round-trip OK");
        }

        // IA5String
        Asn1IA5String ia5 = Asn1IA5String.of("admin@example.com");
        encoded = BerEncoder.encode(ia5);
        decoded = BerDecoder.decode(encoded);
        if (decoded instanceof Asn1IA5String s && "admin@example.com".equals(s.value())) {
            count++;
            LOG.info("IA5String round-trip OK");
        }

        // GeneralizedTime
        Asn1GeneralizedTime time = Asn1GeneralizedTime.of("20240101120000Z");
        encoded = BerEncoder.encode(time);
        decoded = BerDecoder.decode(encoded);
        if (decoded instanceof Asn1GeneralizedTime t && "20240101120000Z".equals(t.value())) {
            count++;
            LOG.info("GeneralizedTime round-trip OK");
        }

        LOG.info("String round-trips: {}/4", count);
        return count;
    }

    // ======================== 3. BITSTRING ROUND-TRIP ===========================

    /**
     * Demonstrates BitString encoding with unused bits tracking.
     *
     * @return true if the round-trip preserved data and unused bits count
     */
    static boolean demoBitStringRoundTrip() {
        LOG.info("=== 3. BitString Round-Trip ===");
        byte[] data = {(byte) 0b11010110, (byte) 0b10100000};
        Asn1BitString bitStr = new Asn1BitString(3, data); // 3 unused bits in last byte
        ByteBuffer encoded = BerEncoder.encode(bitStr);
        Asn1Type decoded = BerDecoder.decode(encoded);
        if (decoded instanceof Asn1BitString bs) {
            boolean ok = bs.unusedBits() == 3 && bs.data().length == 2;
            LOG.info("BitString round-trip: unusedBits={} dataLen={} OK={}",
                    bs.unusedBits(), bs.data().length, ok);
            return ok;
        }
        return false;
    }

    // ======================== 4. OID ROUND-TRIP =================================

    /**
     * Demonstrates ObjectIdentifier BER encoding and decoding.
     *
     * @return true if the OID round-trip preserved all arcs
     */
    static boolean demoOidRoundTrip() {
        LOG.info("=== 4. OID Round-Trip ===");
        ObjectIdentifier oid = ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0");
        Asn1ObjectIdentifier asn1Oid = new Asn1ObjectIdentifier(oid);
        ByteBuffer encoded = BerEncoder.encode(asn1Oid);
        Asn1Type decoded = BerDecoder.decode(encoded);
        if (decoded instanceof Asn1ObjectIdentifier decodedOid) {
            boolean matches = decodedOid.oid().equals(oid);
            LOG.info("OID round-trip: {} -> {} matches={}",
                    oid.toDottedString(), decodedOid.oid().toDottedString(), matches);
            return matches;
        }
        return false;
    }

    // ======================== 5. SEQUENCE ROUND-TRIP ============================

    /**
     * Demonstrates Sequence encoding and decoding with mixed element types.
     *
     * @return the number of elements in the decoded sequence
     */
    static int demoSequenceRoundTrip() {
        LOG.info("=== 5. Sequence Round-Trip ===");
        Asn1Sequence seq = Asn1Sequence.builder()
                .add(new Asn1Integer(BigInteger.valueOf(42)))
                .add(Asn1Utf8String.of("test-value"))
                .add(Asn1Boolean.of(false))
                .add(new Asn1ObjectIdentifier(ObjectIdentifier.parse("2.5.4.3")))
                .build();

        ByteBuffer encoded = BerEncoder.encode(seq);
        Asn1Type decoded = BerDecoder.decode(encoded);
        if (decoded instanceof Asn1Sequence decodedSeq) {
            int size = decodedSeq.elements().size();
            LOG.info("Sequence round-trip: {} elements", size);
            return size;
        }
        return 0;
    }

    // ======================== 6. SET ROUND-TRIP =================================

    /**
     * Demonstrates Set encoding and decoding.
     *
     * @return the number of elements in the decoded set
     */
    static int demoSetRoundTrip() {
        LOG.info("=== 6. Set Round-Trip ===");
        Asn1Set set = Asn1Set.builder()
                .add(Asn1PrintableString.of("US"))
                .add(new Asn1Integer(BigInteger.ONE))
                .add(Asn1Boolean.of(true))
                .build();

        ByteBuffer encoded = BerEncoder.encode(set);
        Asn1Type decoded = BerDecoder.decode(encoded);
        if (decoded instanceof Asn1Set decodedSet) {
            int size = decodedSet.elements().size();
            LOG.info("Set round-trip: {} elements", size);
            return size;
        }
        return 0;
    }

    // ======================== 7. CONTEXT-SPECIFIC TAGS ==========================

    /**
     * Demonstrates explicit and implicit context-specific tagging.
     *
     * @return true if both explicit and implicit tags round-tripped correctly
     */
    static boolean demoContextSpecificTags() {
        LOG.info("=== 7. Context-Specific Tags ===");

        // Explicit: wraps the inner value
        Asn1ContextSpecific explicit = Asn1ContextSpecific.explicit(0,
                new Asn1Integer(BigInteger.valueOf(99)));
        ByteBuffer encodedExplicit = BerEncoder.encode(explicit);
        Asn1Type decodedExplicit = BerDecoder.decode(encodedExplicit);
        boolean explicitOk = decodedExplicit instanceof Asn1ContextSpecific ctx
                && ctx.constructed()
                && ctx.value() instanceof Asn1Integer i
                && i.value().intValue() == 99;
        LOG.info("Explicit [0] INTEGER 99: OK={}", explicitOk);

        // Implicit: raw bytes
        byte[] rawData = {0x0A, 0x0B, 0x0C};
        Asn1ContextSpecific implicit = Asn1ContextSpecific.implicit(3, rawData);
        ByteBuffer encodedImplicit = BerEncoder.encode(implicit);
        Asn1Type decodedImplicit = BerDecoder.decode(encodedImplicit);
        boolean implicitOk = decodedImplicit instanceof Asn1ContextSpecific ctx
                && !ctx.constructed()
                && ctx.rawBytes() != null
                && ctx.rawBytes().length == 3;
        LOG.info("Implicit [3] raw bytes: OK={}", implicitOk);

        return explicitOk && implicitOk;
    }

    // ======================== 8. DER CANONICAL ENCODING =========================

    /**
     * Demonstrates DER canonical encoding: SET elements are sorted by tag value.
     *
     * @return true if DER sorted the SET elements differently from insertion order
     */
    static boolean demoDerCanonicalEncoding() {
        LOG.info("=== 8. DER Canonical Encoding ===");

        // Create a SET with elements in non-canonical order (higher tag first)
        Asn1Set set = Asn1Set.builder()
                .add(Asn1Utf8String.of("text"))         // tag 0x0C
                .add(Asn1Boolean.of(true))               // tag 0x01
                .add(new Asn1Integer(BigInteger.TEN))    // tag 0x02
                .build();

        // BER preserves insertion order; DER sorts by tag
        ByteBuffer berEncoded = BerEncoder.encode(set);
        ByteBuffer derEncoded = DerEncoder.encode(set);

        // The DER output should have Boolean first, then Integer, then UTF8String
        Asn1Type derDecoded = BerDecoder.decode(derEncoded);
        if (derDecoded instanceof Asn1Set derSet && derSet.elements().size() == 3) {
            boolean firstIsBoolean = derSet.elements().get(0) instanceof Asn1Boolean;
            boolean secondIsInteger = derSet.elements().get(1) instanceof Asn1Integer;
            boolean thirdIsString = derSet.elements().get(2) instanceof Asn1Utf8String;
            boolean sorted = firstIsBoolean && secondIsInteger && thirdIsString;
            LOG.info("DER SET sorting: Boolean={} Integer={} UTF8String={} sorted={}",
                    firstIsBoolean, secondIsInteger, thirdIsString, sorted);
            return sorted;
        }
        return false;
    }

    // ======================== 9. OID OPERATIONS ================================

    /**
     * Demonstrates OID operations: parsing, prefix matching, child creation,
     * and lexicographic comparison.
     *
     * @return true if all operations succeeded
     */
    static boolean demoOidOperations() {
        LOG.info("=== 9. OID Operations ===");

        ObjectIdentifier mib2 = ObjectIdentifier.parse("1.3.6.1.2.1");
        ObjectIdentifier sysDescr = ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0");

        // Prefix matching
        boolean isPrefix = sysDescr.startsWith(mib2);
        LOG.info("sysDescr startsWith mib-2: {}", isPrefix);

        // Child creation
        ObjectIdentifier system = mib2.child(1);
        boolean childOk = system.toDottedString().equals("1.3.6.1.2.1.1");
        LOG.info("mib-2.child(1) = {} correct={}", system, childOk);

        // Comparison
        ObjectIdentifier smaller = ObjectIdentifier.parse("1.3.6.1.2.1");
        ObjectIdentifier larger = ObjectIdentifier.parse("1.3.6.1.2.1.1");
        boolean cmpOk = smaller.compareTo(larger) < 0;
        LOG.info("Comparison {} < {}: {}", smaller, larger, cmpOk);

        // Size and arc access
        boolean sizeOk = mib2.size() == 6 && mib2.arc(0) == 1 && mib2.arc(5) == 1;
        LOG.info("Size and arc access: size={} arc(0)={} arc(5)={} OK={}",
                mib2.size(), mib2.arc(0), mib2.arc(5), sizeOk);

        boolean allOk = isPrefix && childOk && cmpOk && sizeOk;
        return allOk;
    }

    // ======================== 10. OID REGISTRY LOOKUPS ==========================

    /**
     * Demonstrates the OID registry: name-to-OID and OID-to-name lookups,
     * custom registration, and display names.
     *
     * @return the number of successful lookups
     */
    static int demoOidRegistryLookups() {
        LOG.info("=== 10. OID Registry Lookups ===");
        OidRegistry registry = OidRegistry.instance();
        int lookups = 0;

        // Lookup by OID
        var sysDescrName = registry.nameOf(StandardOids.SYS_DESCR);
        if (sysDescrName.isPresent()) {
            lookups++;
            LOG.info("OID {} -> {}", StandardOids.SYS_DESCR, sysDescrName.get());
        }

        // Lookup by name
        var internetOid = registry.oidOf("internet");
        if (internetOid.isPresent()) {
            lookups++;
            LOG.info("Name 'internet' -> {}", internetOid.get());
        }

        // Display name for known OID
        String display = registry.displayName(StandardOids.MIB_2);
        if (display.contains("mib-2")) {
            lookups++;
            LOG.info("Display name: {}", display);
        }

        // Registry has pre-registered entries
        if (registry.size() > 20) {
            lookups++;
            LOG.info("Registry size: {} (>20 pre-registered)", registry.size());
        }

        LOG.info("Successful lookups: {}", lookups);
        return lookups;
    }
}
