package ssg.legoflow.network.snmp.protocol;

import ssg.legoflow.network.common.asn1.*;
import ssg.legoflow.network.common.ber.BerDecoder;
import ssg.legoflow.network.common.ber.BerEncoder;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
/**
 * BER codec for SNMP messages, PDUs, and variable bindings.
 *
 * <p>Encodes and decodes SNMPv3 messages according to the BER encoding
 * rules defined in RFC 3412 (message format), RFC 3416 (PDU types),
 * and RFC 3414 (USM security parameters).
 *
 * <p>This codec is stateless and thread-safe.
 *
 * @since 0.1.0
 */
public final class SnmpCodec {

    /** Application tag for IpAddress (0x40). */
    private static final int TAG_IP_ADDRESS = 0;
    /** Application tag for Counter32 (0x41). */
    private static final int TAG_COUNTER32 = 1;
    /** Application tag for Gauge32/Unsigned32 (0x42). */
    private static final int TAG_GAUGE32 = 2;
    /** Application tag for TimeTicks (0x43). */
    private static final int TAG_TIMETICKS = 3;
    /** Application tag for Opaque (0x44). */
    private static final int TAG_OPAQUE = 4;
    /** Application tag for Counter64 (0x46). */
    private static final int TAG_COUNTER64 = 6;

    private SnmpCodec() {}

    // ── Message encoding ──

    /**
     * Encodes an SNMPv3 message to a byte array.
     *
     * @param msg the SNMP message
     * @return the BER-encoded bytes
     */
    public static byte[] encodeMessage(SnmpMessage msg) {
        Asn1Sequence message = Asn1Sequence.of(
                Asn1Integer.of(msg.msgVersion()),
                encodeHeaderData(msg),
                new Asn1OctetString(msg.securityParams()),
                encodeScopedPdu(msg.scopedPdu())
        );
        ByteBuffer buffer = BerEncoder.encode(message);
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    /**
     * Decodes an SNMPv3 message from a byte array.
     *
     * @param data the BER-encoded bytes
     * @return the decoded SNMP message
     * @throws SnmpCodecException if the data is malformed
     */
    public static SnmpMessage decodeMessage(byte[] data) {
        try {
            Asn1Type decoded = BerDecoder.decode(data);
            if (!(decoded instanceof Asn1Sequence seq)) {
                throw new SnmpCodecException("Expected SEQUENCE at top level");
            }
            List<Asn1Type> elements = seq.elements();
            if (elements.size() < 4) {
                throw new SnmpCodecException("Expected at least 4 elements in message SEQUENCE");
            }

            int version = asInt(elements.get(0));
            Asn1Sequence headerSeq = asSequence(elements.get(1));
            byte[] secParams = asOctetBytes(elements.get(2));

            int msgId = asInt(headerSeq.elements().get(0));
            int msgMaxSize = asInt(headerSeq.elements().get(1));
            byte[] flagsBytes = asOctetBytes(headerSeq.elements().get(2));
            int msgFlags = flagsBytes.length > 0 ? flagsBytes[0] & 0xFF : 0;
            int secModel = asInt(headerSeq.elements().get(3));

            ScopedPdu scopedPdu = decodeScopedPdu(elements.get(3));

            return new SnmpMessage(version, msgId, msgMaxSize, msgFlags,
                    secModel, secParams, scopedPdu);
        } catch (SnmpCodecException e) {
            throw e;
        } catch (Exception e) {
            throw new SnmpCodecException("Failed to decode SNMP message: " + e.getMessage(), e);
        }
    }

    // ── Scoped PDU ──

    /**
     * Encodes a scoped PDU to an ASN.1 SEQUENCE.
     *
     * @param scopedPdu the scoped PDU
     * @return the encoded SEQUENCE
     */
    public static Asn1Sequence encodeScopedPdu(ScopedPdu scopedPdu) {
        return Asn1Sequence.of(
                new Asn1OctetString(scopedPdu.contextEngineId()),
                Asn1OctetString.of(scopedPdu.contextName()),
                encodePdu(scopedPdu.pdu())
        );
    }

    /**
     * Encodes a scoped PDU to raw bytes.
     *
     * @param scopedPdu the scoped PDU
     * @return the BER-encoded bytes
     */
    public static byte[] encodeScopedPduBytes(ScopedPdu scopedPdu) {
        ByteBuffer buffer = BerEncoder.encode(encodeScopedPdu(scopedPdu));
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    /**
     * Decodes a scoped PDU from an ASN.1 type.
     *
     * @param type the ASN.1 type (must be a SEQUENCE)
     * @return the decoded scoped PDU
     */
    public static ScopedPdu decodeScopedPdu(Asn1Type type) {
        Asn1Sequence seq = asSequence(type);
        List<Asn1Type> elements = seq.elements();
        if (elements.size() < 3) {
            throw new SnmpCodecException("ScopedPDU must have at least 3 elements");
        }

        byte[] contextEngineId = asOctetBytes(elements.get(0));
        String contextName = asOctetString(elements.get(1));
        SnmpPdu pdu = decodePdu(elements.get(2));

        return new ScopedPdu(contextEngineId, contextName, pdu);
    }

    /**
     * Decodes a scoped PDU from raw bytes.
     *
     * @param data the BER-encoded bytes
     * @return the decoded scoped PDU
     */
    public static ScopedPdu decodeScopedPduBytes(byte[] data) {
        Asn1Type type = BerDecoder.decode(data);
        return decodeScopedPdu(type);
    }

    // ── PDU encoding/decoding ──

    /**
     * Encodes a PDU to a context-specific tagged ASN.1 type.
     *
     * @param pdu the PDU
     * @return the encoded context-specific type
     */
    public static Asn1ContextSpecific encodePdu(SnmpPdu pdu) {
        List<Asn1Type> elements = new ArrayList<>();
        elements.add(Asn1Integer.of(pdu.requestId()));

        if (pdu instanceof SnmpPdu.GetBulkRequest bulk) {
            elements.add(Asn1Integer.of(bulk.nonRepeaters()));
            elements.add(Asn1Integer.of(bulk.maxRepetitions()));
        } else {
            int errorStatus = switch (pdu) {
                case SnmpPdu.GetRequest r -> r.errorStatus();
                case SnmpPdu.GetNextRequest r -> r.errorStatus();
                case SnmpPdu.Response r -> r.errorStatus();
                case SnmpPdu.SetRequest r -> r.errorStatus();
                case SnmpPdu.InformRequest r -> r.errorStatus();
                case SnmpPdu.TrapV2 r -> r.errorStatus();
                case SnmpPdu.GetBulkRequest _ ->
                        throw new AssertionError("unreachable");
            };
            int errorIndex = switch (pdu) {
                case SnmpPdu.GetRequest r -> r.errorIndex();
                case SnmpPdu.GetNextRequest r -> r.errorIndex();
                case SnmpPdu.Response r -> r.errorIndex();
                case SnmpPdu.SetRequest r -> r.errorIndex();
                case SnmpPdu.InformRequest r -> r.errorIndex();
                case SnmpPdu.TrapV2 r -> r.errorIndex();
                case SnmpPdu.GetBulkRequest _ ->
                        throw new AssertionError("unreachable");
            };
            elements.add(Asn1Integer.of(errorStatus));
            elements.add(Asn1Integer.of(errorIndex));
        }

        elements.add(encodeVarBindList(pdu.varBindList()));

        Asn1Sequence inner = Asn1Sequence.of(elements);
        return Asn1ContextSpecific.implicitConstructed(pdu.tagNumber(), inner);
    }

    /**
     * Decodes a PDU from an ASN.1 type.
     *
     * @param type the ASN.1 type (must be context-specific)
     * @return the decoded PDU
     */
    public static SnmpPdu decodePdu(Asn1Type type) {
        if (!(type instanceof Asn1ContextSpecific ctx)) {
            throw new SnmpCodecException("Expected context-specific tag for PDU, got: " + type);
        }

        int tagNumber = ctx.tagNumber();
        Asn1Sequence inner;

        if (ctx.constructed() && ctx.value() != null) {
            inner = asSequence(ctx.value());
        } else {
            throw new SnmpCodecException("PDU must be constructed context-specific");
        }

        List<Asn1Type> elements = inner.elements();
        if (elements.size() < 4) {
            throw new SnmpCodecException("PDU must have at least 4 elements");
        }

        int requestId = asInt(elements.get(0));
        int field2 = asInt(elements.get(1));
        int field3 = asInt(elements.get(2));
        VarBindList varBindList = decodeVarBindList(elements.get(3));

        return switch (tagNumber) {
            case 0 -> new SnmpPdu.GetRequest(requestId, field2, field3, varBindList);
            case 1 -> new SnmpPdu.GetNextRequest(requestId, field2, field3, varBindList);
            case 2 -> new SnmpPdu.Response(requestId, field2, field3, varBindList);
            case 3 -> new SnmpPdu.SetRequest(requestId, field2, field3, varBindList);
            case 5 -> new SnmpPdu.GetBulkRequest(requestId, field2, field3, varBindList);
            case 6 -> new SnmpPdu.InformRequest(requestId, field2, field3, varBindList);
            case 7 -> new SnmpPdu.TrapV2(requestId, field2, field3, varBindList);
            default -> throw new SnmpCodecException("Unknown PDU tag number: " + tagNumber);
        };
    }

    // ── VarBindList encoding/decoding ──

    /**
     * Encodes a VarBindList to an ASN.1 SEQUENCE.
     *
     * @param varBindList the variable binding list
     * @return the encoded SEQUENCE of SEQUENCE pairs
     */
    public static Asn1Sequence encodeVarBindList(VarBindList varBindList) {
        List<Asn1Type> bindings = new ArrayList<>();
        for (VarBind vb : varBindList) {
            bindings.add(encodeVarBind(vb));
        }
        return Asn1Sequence.of(bindings);
    }

    /**
     * Decodes a VarBindList from an ASN.1 SEQUENCE.
     *
     * @param type the ASN.1 type
     * @return the decoded VarBindList
     */
    public static VarBindList decodeVarBindList(Asn1Type type) {
        Asn1Sequence seq = asSequence(type);
        List<VarBind> bindings = new ArrayList<>();
        for (Asn1Type element : seq.elements()) {
            bindings.add(decodeVarBind(element));
        }
        return VarBindList.of(bindings);
    }

    // ── VarBind encoding/decoding ──

    /**
     * Encodes a VarBind to an ASN.1 SEQUENCE.
     *
     * @param varBind the variable binding
     * @return the encoded SEQUENCE(OID, value)
     */
    public static Asn1Sequence encodeVarBind(VarBind varBind) {
        return Asn1Sequence.of(
                new Asn1ObjectIdentifier(varBind.oid()),
                encodeSnmpValue(varBind.value())
        );
    }

    /**
     * Decodes a VarBind from an ASN.1 SEQUENCE.
     *
     * @param type the ASN.1 type
     * @return the decoded VarBind
     */
    public static VarBind decodeVarBind(Asn1Type type) {
        Asn1Sequence seq = asSequence(type);
        if (seq.elements().size() < 2) {
            throw new SnmpCodecException("VarBind must have at least 2 elements");
        }
        ObjectIdentifier oid = asOid(seq.elements().get(0));
        SnmpValue value = decodeSnmpValue(seq.elements().get(1));
        return new VarBind(oid, value);
    }

    // ── SnmpValue encoding/decoding ──

    /**
     * Encodes an SNMP value to its ASN.1 representation.
     *
     * <p>Application-tagged values (Counter32, Gauge32, TimeTicks, IpAddress,
     * Opaque, Counter64) and exception values (noSuchObject, noSuchInstance,
     * endOfMibView) are encoded as {@link Asn1ContextSpecific} with the
     * appropriate tag number. The BER decoder will read them back the same way.
     *
     * @param value the SNMP value
     * @return the encoded ASN.1 type
     */
    public static Asn1Type encodeSnmpValue(SnmpValue value) {
        return switch (value) {
            case SnmpValue.Integer32 i -> Asn1Integer.of(i.value());
            case SnmpValue.OctetString s -> new Asn1OctetString(s.value());
            case SnmpValue.Oid o -> new Asn1ObjectIdentifier(o.value());
            case SnmpValue.Null _ -> Asn1Null.INSTANCE;
            case SnmpValue.Counter32 c -> encodeAppPrimitive(TAG_COUNTER32, unsignedBytes(c.value()));
            case SnmpValue.Gauge32 g -> encodeAppPrimitive(TAG_GAUGE32, unsignedBytes(g.value()));
            case SnmpValue.TimeTicks t -> encodeAppPrimitive(TAG_TIMETICKS, unsignedBytes(t.value()));
            case SnmpValue.Counter64 c -> encodeAppPrimitive(TAG_COUNTER64, unsignedBytes64(c.value()));
            case SnmpValue.IpAddress ip -> encodeAppPrimitive(TAG_IP_ADDRESS, ip.address());
            case SnmpValue.Opaque o -> encodeAppPrimitive(TAG_OPAQUE, o.value());
            case SnmpValue.NoSuchObject _ ->
                    Asn1ContextSpecific.implicit(0, new byte[0]);
            case SnmpValue.NoSuchInstance _ ->
                    Asn1ContextSpecific.implicit(1, new byte[0]);
            case SnmpValue.EndOfMibView _ ->
                    Asn1ContextSpecific.implicit(2, new byte[0]);
        };
    }

    /**
     * Decodes an SNMP value from its ASN.1 representation.
     *
     * @param type the ASN.1 type
     * @return the decoded SNMP value
     */
    public static SnmpValue decodeSnmpValue(Asn1Type type) {
        return switch (type) {
            case Asn1Integer i -> new SnmpValue.Integer32(i.value().intValue());
            case Asn1OctetString s -> new SnmpValue.OctetString(s.value());
            case Asn1ObjectIdentifier o -> new SnmpValue.Oid(o.oid());
            case Asn1Null _ -> SnmpValue.Null.INSTANCE;
            case Asn1ContextSpecific ctx -> decodeTaggedValue(ctx);
            default -> throw new SnmpCodecException("Unsupported SNMP value type: " + type.getClass().getSimpleName());
        };
    }

    // ── USM Security Parameters encoding/decoding ──

    /**
     * Encodes USM security parameters to a byte array.
     *
     * @param params the USM parameters
     * @return the BER-encoded bytes
     */
    public static byte[] encodeUsmSecurityParams(UsmSecurityParameters params) {
        Asn1Sequence seq = Asn1Sequence.of(
                new Asn1OctetString(params.engineId()),
                Asn1Integer.of(params.engineBoots()),
                Asn1Integer.of(params.engineTime()),
                Asn1OctetString.of(params.userName()),
                new Asn1OctetString(params.authParams()),
                new Asn1OctetString(params.privParams())
        );
        ByteBuffer buffer = BerEncoder.encode(seq);
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    /**
     * Decodes USM security parameters from a byte array.
     *
     * @param data the BER-encoded bytes
     * @return the decoded USM parameters
     */
    public static UsmSecurityParameters decodeUsmSecurityParams(byte[] data) {
        if (data == null || data.length == 0) {
            return new UsmSecurityParameters(new byte[0], 0, 0, "", new byte[0], new byte[0]);
        }
        try {
            Asn1Sequence seq = asSequence(BerDecoder.decode(data));
            List<Asn1Type> elements = seq.elements();
            if (elements.size() < 6) {
                throw new SnmpCodecException("USM security params must have 6 elements");
            }

            byte[] engineId = asOctetBytes(elements.get(0));
            int engineBoots = asInt(elements.get(1));
            int engineTime = asInt(elements.get(2));
            String userName = asOctetString(elements.get(3));
            byte[] authParams = asOctetBytes(elements.get(4));
            byte[] privParams = asOctetBytes(elements.get(5));

            return new UsmSecurityParameters(engineId, engineBoots, engineTime,
                    userName, authParams, privParams);
        } catch (SnmpCodecException e) {
            throw e;
        } catch (Exception e) {
            throw new SnmpCodecException("Failed to decode USM security params: " + e.getMessage(), e);
        }
    }

    // ── Private helpers ──

    private static Asn1Sequence encodeHeaderData(SnmpMessage msg) {
        return Asn1Sequence.of(
                Asn1Integer.of(msg.msgId()),
                Asn1Integer.of(msg.msgMaxSize()),
                new Asn1OctetString(new byte[]{(byte) msg.msgFlags()}),
                Asn1Integer.of(msg.msgSecurityModel())
        );
    }

    /**
     * Encodes an application-tagged primitive value. Since the common BER library
     * does not have an APPLICATION-class variant of Asn1ContextSpecific, we use
     * Asn1ContextSpecific.implicit which produces a CONTEXT_SPECIFIC tag. The
     * decoder maps tag numbers back to the correct SNMP application types.
     */
    private static Asn1ContextSpecific encodeAppPrimitive(int tagNumber, byte[] data) {
        return Asn1ContextSpecific.implicit(tagNumber, data);
    }

    private static byte[] unsignedBytes(long value) {
        return BigInteger.valueOf(value).toByteArray();
    }

    private static byte[] unsignedBytes64(long value) {
        if (value >= 0) {
            return BigInteger.valueOf(value).toByteArray();
        }
        // Unsigned 64-bit: use BigInteger unsigned interpretation
        byte[] raw = new byte[8];
        for (int i = 7; i >= 0; i--) {
            raw[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return new BigInteger(1, raw).toByteArray();
    }

    /**
     * Decodes a context-specific tagged value. In our encoding scheme, both
     * SNMP exception values (noSuchObject, noSuchInstance, endOfMibView) and
     * application-tagged values (Counter32, etc.) are encoded as context-specific
     * with different tag numbers. We distinguish them by tag number:
     * - 0: noSuchObject (exception) -- empty payload
     * - 1: noSuchInstance (exception) OR Counter32 (application) -- distinguished by payload
     * - 2: endOfMibView (exception) OR Gauge32 (application) -- distinguished by payload
     * - 3: TimeTicks (application)
     * - 4: Opaque (application)
     * - 6: Counter64 (application)
     *
     * For values with non-empty payload at tags 0-2, we check the context:
     * - Tag 0 with 4-byte payload: IpAddress (but could also be noSuchObject with data)
     * - Tags 1,2 with payload: Counter32/Gauge32
     * - Tags 0,1,2 with empty payload: exception values
     */
    private static SnmpValue decodeTaggedValue(Asn1ContextSpecific ctx) {
        int tagNumber = ctx.tagNumber();
        byte[] raw = ctx.rawBytes();

        // Empty payload = exception values
        if (raw == null || raw.length == 0) {
            return switch (tagNumber) {
                case 0 -> SnmpValue.NoSuchObject.INSTANCE;
                case 1 -> SnmpValue.NoSuchInstance.INSTANCE;
                case 2 -> SnmpValue.EndOfMibView.INSTANCE;
                default -> throw new SnmpCodecException("Unknown empty context-specific tag: " + tagNumber);
            };
        }

        // Non-empty payload = application-tagged values mapped by tag number
        return switch (tagNumber) {
            case TAG_IP_ADDRESS -> {
                if (raw.length == 4) {
                    yield new SnmpValue.IpAddress(raw);
                }
                // Fallback: treat as noSuchObject with data (unlikely)
                yield SnmpValue.NoSuchObject.INSTANCE;
            }
            case TAG_COUNTER32 -> new SnmpValue.Counter32(decodeUnsigned32(raw));
            case TAG_GAUGE32 -> new SnmpValue.Gauge32(decodeUnsigned32(raw));
            case TAG_TIMETICKS -> new SnmpValue.TimeTicks(decodeUnsigned32(raw));
            case TAG_OPAQUE -> new SnmpValue.Opaque(raw);
            case TAG_COUNTER64 -> new SnmpValue.Counter64(decodeUnsigned64(raw));
            default -> throw new SnmpCodecException("Unknown context-specific tag: " + tagNumber);
        };
    }

    private static long decodeUnsigned32(byte[] raw) {
        if (raw.length == 0) return 0;
        return new BigInteger(1, raw).longValue() & 0xFFFFFFFFL;
    }

    private static long decodeUnsigned64(byte[] raw) {
        if (raw.length == 0) return 0;
        return new BigInteger(1, raw).longValue();
    }

    private static int asInt(Asn1Type type) {
        if (type instanceof Asn1Integer i) {
            return i.value().intValue();
        }
        throw new SnmpCodecException("Expected INTEGER, got: " + type.getClass().getSimpleName());
    }

    private static Asn1Sequence asSequence(Asn1Type type) {
        if (type instanceof Asn1Sequence seq) {
            return seq;
        }
        throw new SnmpCodecException("Expected SEQUENCE, got: " + type.getClass().getSimpleName());
    }

    private static byte[] asOctetBytes(Asn1Type type) {
        if (type instanceof Asn1OctetString os) {
            return os.value();
        }
        throw new SnmpCodecException("Expected OCTET STRING, got: " + type.getClass().getSimpleName());
    }

    private static String asOctetString(Asn1Type type) {
        if (type instanceof Asn1OctetString os) {
            return os.asString();
        }
        throw new SnmpCodecException("Expected OCTET STRING, got: " + type.getClass().getSimpleName());
    }

    private static ObjectIdentifier asOid(Asn1Type type) {
        if (type instanceof Asn1ObjectIdentifier oid) {
            return oid.oid();
        }
        throw new SnmpCodecException("Expected OID, got: " + type.getClass().getSimpleName());
    }
}
