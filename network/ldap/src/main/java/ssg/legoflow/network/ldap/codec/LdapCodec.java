package ssg.legoflow.network.ldap.codec;

import ssg.legoflow.network.common.asn1.*;
import ssg.legoflow.network.common.ber.BerDecoder;
import ssg.legoflow.network.common.ber.BerEncoder;
import ssg.legoflow.network.common.ber.BerLength;
import ssg.legoflow.network.common.ber.BerTag;
import ssg.legoflow.network.ldap.control.LdapControl;
import ssg.legoflow.network.ldap.filter.SearchFilter;
import ssg.legoflow.network.ldap.protocol.*;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * BER encoder/decoder for LDAP v3 protocol messages (RFC 4511).
 *
 * <p>Handles encoding and decoding of all LDAP protocol operations using
 * APPLICATION-tagged BER structures as defined in the LDAP ASN.1 schema.
 *
 * <p>This codec supports two usage modes:
 * <ul>
 *   <li><b>Static methods</b> ({@link #encode}, {@link #decode}, {@link #tryDecode}) —
 *       stateless, one-shot encode/decode for complete messages. Thread-safe.</li>
 *   <li><b>Instance methods</b> ({@link #decodeStream}) — stateful stream-oriented
 *       decoding with internal byte accumulation. An instance is <em>not</em> thread-safe
 *       and is intended to be owned by a single pipeline/connection. Partial data across
 *       reads is normal; the codec buffers incomplete BER TLVs internally and emits
 *       complete {@link LdapMessage}s when enough bytes have arrived.</li>
 * </ul>
 *
 * <p>The stream-oriented design follows the same accumulator pattern used by
 * {@code Http2FrameCodec}: an internal {@link ByteBuffer} accumulates leftover bytes,
 * {@link #combineWithAccumulator(ByteBuffer)} merges new input, and a parse loop extracts
 * complete protocol units while saving the remainder.
 *
 * @since 0.1.0
 */
public final class LdapCodec {

    /** Internal accumulator for partial BER data between {@link #decodeStream} calls. */
    private ByteBuffer accumulator;

    /**
     * Creates a new stateful {@code LdapCodec} instance for stream-oriented decoding.
     *
     * <p>Use static methods for one-shot encoding/decoding of complete messages.
     * Use an instance when data arrives incrementally over a network stream.
     */
    public LdapCodec() {}

    // ── Stream-oriented instance API ──

    /**
     * Decodes zero or more complete LDAP messages from an incremental byte stream.
     *
     * <p>Combines the supplied data with any bytes left over from previous calls,
     * then extracts as many complete BER-encoded LDAP messages as possible.
     * Any trailing bytes that do not form a complete message are saved in an
     * internal accumulator for the next invocation.
     *
     * <p>Callers should invoke this method each time new data is read from the
     * network. An empty list is a normal result — it means the accumulated data
     * does not yet contain a complete message.
     *
     * @param data the new data chunk (position is advanced to limit on return)
     * @return a list of fully decoded messages (may be empty, never null)
     * @throws LdapCodecException if a structurally complete TLV contains malformed content
     */
    public List<LdapMessage> decodeStream(ByteBuffer data) {
        ByteBuffer combined = combineWithAccumulator(data);
        List<LdapMessage> messages = new ArrayList<>();

        while (combined.hasRemaining()) {
            LdapMessage msg = tryDecode(combined);
            if (msg == null) {
                break; // incomplete — wait for more data
            }
            messages.add(msg);
        }

        // Save any remaining bytes for the next call
        if (combined.hasRemaining()) {
            accumulator = ByteBuffer.allocate(combined.remaining());
            accumulator.put(combined);
            accumulator.flip();
        } else {
            accumulator = null;
        }

        return messages;
    }

    /**
     * Returns {@code true} if the internal accumulator contains bytes from a
     * previous {@link #decodeStream} call that did not form a complete message.
     *
     * @return whether partial data is buffered
     */
    public boolean hasBufferedData() {
        return accumulator != null && accumulator.hasRemaining();
    }

    /**
     * Combines the internal accumulator (if any) with newly arrived data.
     *
     * @param data the fresh input buffer
     * @return a single buffer containing accumulated + new bytes, positioned at 0
     */
    private ByteBuffer combineWithAccumulator(ByteBuffer data) {
        int totalSize = (accumulator != null ? accumulator.remaining() : 0) + data.remaining();
        ByteBuffer combined = ByteBuffer.allocate(totalSize);
        if (accumulator != null) {
            combined.put(accumulator);
            accumulator = null;
        }
        combined.put(data);
        combined.flip();
        return combined;
    }

    // ── Encoding ──

    /**
     * Encodes an LDAP message to a ByteBuffer.
     *
     * <p>The message is encoded as a BER SEQUENCE containing the message ID,
     * protocol operation (APPLICATION-tagged), and optional controls.
     *
     * @param message the LDAP message to encode
     * @return a ByteBuffer positioned at 0 with the encoded bytes
     */
    public static ByteBuffer encode(LdapMessage message) {
        // Encode message ID
        byte[] msgIdBytes = encodeAsn1ToBytes(Asn1Integer.of(message.messageId()));

        // Encode protocol operation with APPLICATION tag
        byte[] opBytes = encodeProtocolOpBytes(message.protocolOp());

        // Encode controls if present
        byte[] controlsBytes = new byte[0];
        if (!message.controls().isEmpty()) {
            Asn1Sequence.Builder controlsSeq = Asn1Sequence.builder();
            for (LdapControl control : message.controls()) {
                controlsSeq.add(encodeControl(control));
            }
            byte[] innerControls = encodeAsn1ToBytes(controlsSeq.build());
            // Wrap in context-specific [0] constructed tag
            controlsBytes = wrapWithTag(new Asn1Tag(Asn1Tag.TagClass.CONTEXT_SPECIFIC, true, 0), innerControls);
        }

        // Build outer SEQUENCE
        int contentLength = msgIdBytes.length + opBytes.length + controlsBytes.length;
        Asn1Tag seqTag = Asn1Tag.SEQUENCE;
        int tagLen = BerTag.encodedLength(seqTag);
        int lenLen = BerLength.encodedLength(contentLength);
        ByteBuffer result = ByteBuffer.allocate(tagLen + lenLen + contentLength);
        BerTag.encode(seqTag, result);
        BerLength.encode(contentLength, result);
        result.put(msgIdBytes);
        result.put(opBytes);
        if (controlsBytes.length > 0) {
            result.put(controlsBytes);
        }
        result.flip();
        return result;
    }

    /**
     * Encodes an LDAP message to a byte array.
     *
     * @param message the LDAP message to encode
     * @return the encoded bytes
     */
    public static byte[] encodeToBytes(LdapMessage message) {
        ByteBuffer buf = encode(message);
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return bytes;
    }

    // ── Decoding ──

    /**
     * Decodes an LDAP message from a ByteBuffer.
     *
     * @param buffer the input buffer
     * @return the decoded LDAP message
     * @throws LdapCodecException if the data is malformed
     */
    public static LdapMessage decode(ByteBuffer buffer) {
        Asn1Type decoded = BerDecoder.decode(buffer);
        if (!(decoded instanceof Asn1Sequence seq)) {
            throw new LdapCodecException("Expected SEQUENCE for LDAPMessage, got: " + decoded.getClass().getSimpleName());
        }
        List<Asn1Type> elements = seq.elements();
        if (elements.size() < 2) {
            throw new LdapCodecException("LDAPMessage requires at least 2 elements, got: " + elements.size());
        }

        // Message ID
        int messageId = ((Asn1Integer) elements.get(0)).value().intValueExact();

        // Protocol operation (APPLICATION tagged — decoded as ContextSpecific)
        LdapProtocolOp protocolOp = decodeProtocolOp(elements.get(1));

        // Optional controls
        List<LdapControl> controls = List.of();
        if (elements.size() > 2) {
            Asn1Type controlsElement = elements.get(2);
            controls = decodeControls(controlsElement);
        }

        return new LdapMessage(messageId, protocolOp, controls);
    }

    /**
     * Decodes an LDAP message from a byte array.
     *
     * @param data the BER-encoded byte array
     * @return the decoded LDAP message
     * @throws LdapCodecException if the data is malformed
     */
    public static LdapMessage decode(byte[] data) {
        return decode(ByteBuffer.wrap(data));
    }

    /**
     * Reads a complete LDAP message from a ByteBuffer, returning null if
     * insufficient data is available.
     *
     * @param buffer the input buffer (position will advance past the message)
     * @return the decoded message, or null if more data is needed
     */
    public static LdapMessage tryDecode(ByteBuffer buffer) {
        int startPos = buffer.position();
        if (buffer.remaining() < 2) return null;

        try {
            buffer.mark();
            BerTag.decode(buffer);
            int length = BerLength.decode(buffer);
            int headerSize = buffer.position() - startPos;
            buffer.reset();

            if (length < 0 || buffer.remaining() < headerSize + length) {
                return null;
            }

            return decode(buffer);
        } catch (Exception e) {
            buffer.position(startPos);
            return null;
        }
    }

    // ── Protocol operation encoding (to raw bytes with APPLICATION tag) ──

    private static byte[] encodeProtocolOpBytes(LdapProtocolOp op) {
        return switch (op) {
            case BindRequest r -> encodeBindRequest(r);
            case BindResponse r -> encodeBindResponse(r);
            case UnbindRequest _ -> encodeUnbindRequest();
            case SearchRequest r -> encodeSearchRequest(r);
            case SearchResultEntry r -> encodeSearchResultEntry(r);
            case SearchResultDone r -> encodeResultOp(r.tagNumber(), r.result());
            case SearchResultReference r -> encodeSearchResultReference(r);
            case ModifyRequest r -> encodeModifyRequest(r);
            case ModifyResponse r -> encodeResultOp(r.tagNumber(), r.result());
            case AddRequest r -> encodeAddRequest(r);
            case AddResponse r -> encodeResultOp(r.tagNumber(), r.result());
            case DeleteRequest r -> encodeDeleteRequest(r);
            case DeleteResponse r -> encodeResultOp(r.tagNumber(), r.result());
            case ModifyDnRequest r -> encodeModifyDnRequest(r);
            case ModifyDnResponse r -> encodeResultOp(r.tagNumber(), r.result());
            case CompareRequest r -> encodeCompareRequest(r);
            case CompareResponse r -> encodeResultOp(r.tagNumber(), r.result());
            case AbandonRequest r -> encodeAbandonRequest(r);
            case ExtendedRequest r -> encodeExtendedRequest(r);
            case ExtendedResponse r -> encodeExtendedResponse(r);
            case IntermediateResponse r -> encodeIntermediateResponse(r);
        };
    }

    private static byte[] encodeBindRequest(BindRequest req) {
        Asn1Sequence.Builder inner = Asn1Sequence.builder();
        inner.add(Asn1Integer.of(req.version()));
        inner.add(Asn1OctetString.of(req.name()));

        switch (req.authentication()) {
            case BindRequest.AuthenticationChoice.Simple s ->
                    inner.add(Asn1ContextSpecific.implicit(0,
                            s.password().getBytes(StandardCharsets.UTF_8)));
            case BindRequest.AuthenticationChoice.Sasl s -> {
                Asn1Sequence.Builder sasl = Asn1Sequence.builder();
                sasl.add(Asn1OctetString.of(s.mechanism()));
                if (s.credentials() != null) {
                    sasl.add(new Asn1OctetString(s.credentials()));
                }
                inner.add(Asn1ContextSpecific.explicit(3, sasl.build()));
            }
        }

        return applicationConstructed(BindRequest.TAG, inner.build());
    }

    private static byte[] encodeBindResponse(BindResponse resp) {
        Asn1Sequence.Builder inner = Asn1Sequence.builder();
        addResultComponents(inner, resp.result());
        if (resp.serverSaslCreds() != null) {
            inner.add(Asn1ContextSpecific.implicit(7, resp.serverSaslCreds()));
        }
        return applicationConstructed(BindResponse.TAG, inner.build());
    }

    private static byte[] encodeUnbindRequest() {
        // UnbindRequest is APPLICATION 2 NULL — primitive with no content
        Asn1Tag tag = new Asn1Tag(Asn1Tag.TagClass.APPLICATION, false, UnbindRequest.TAG);
        int tagLen = BerTag.encodedLength(tag);
        ByteBuffer buf = ByteBuffer.allocate(tagLen + 1);
        BerTag.encode(tag, buf);
        BerLength.encode(0, buf);
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    private static byte[] encodeSearchRequest(SearchRequest req) {
        Asn1Sequence.Builder inner = Asn1Sequence.builder();
        inner.add(Asn1OctetString.of(req.baseObject()));
        inner.add(Asn1Enumerated.of(req.scope().value()));
        inner.add(Asn1Enumerated.of(req.derefAliases().value()));
        inner.add(Asn1Integer.of(req.sizeLimit()));
        inner.add(Asn1Integer.of(req.timeLimit()));
        inner.add(Asn1Boolean.of(req.typesOnly()));
        inner.add(LdapFilterCodec.encode(req.filter()));
        Asn1Sequence.Builder attrs = Asn1Sequence.builder();
        for (String attr : req.attributes()) {
            attrs.add(Asn1OctetString.of(attr));
        }
        inner.add(attrs.build());
        return applicationConstructed(SearchRequest.TAG, inner.build());
    }

    private static byte[] encodeSearchResultEntry(SearchResultEntry entry) {
        Asn1Sequence.Builder inner = Asn1Sequence.builder();
        inner.add(Asn1OctetString.of(entry.objectName()));
        inner.add(encodePartialAttributeList(entry.attributes()));
        return applicationConstructed(SearchResultEntry.TAG, inner.build());
    }

    private static byte[] encodeSearchResultReference(SearchResultReference ref) {
        Asn1Sequence.Builder inner = Asn1Sequence.builder();
        for (String uri : ref.uris()) {
            inner.add(Asn1OctetString.of(uri));
        }
        return applicationConstructed(SearchResultReference.TAG, inner.build());
    }

    private static byte[] encodeModifyRequest(ModifyRequest req) {
        Asn1Sequence.Builder inner = Asn1Sequence.builder();
        inner.add(Asn1OctetString.of(req.object()));
        Asn1Sequence.Builder changes = Asn1Sequence.builder();
        for (ModifyRequest.Change change : req.changes()) {
            Asn1Sequence.Builder changeSeq = Asn1Sequence.builder();
            changeSeq.add(Asn1Enumerated.of(change.operation().value()));
            changeSeq.add(encodePartialAttribute(change.modification()));
            changes.add(changeSeq.build());
        }
        inner.add(changes.build());
        return applicationConstructed(ModifyRequest.TAG, inner.build());
    }

    private static byte[] encodeAddRequest(AddRequest req) {
        Asn1Sequence.Builder inner = Asn1Sequence.builder();
        inner.add(Asn1OctetString.of(req.entry()));
        inner.add(encodePartialAttributeList(req.attributes()));
        return applicationConstructed(AddRequest.TAG, inner.build());
    }

    private static byte[] encodeDeleteRequest(DeleteRequest req) {
        // DeleteRequest is APPLICATION 10 primitive with DN as content
        byte[] dnBytes = req.entry().getBytes(StandardCharsets.UTF_8);
        Asn1Tag tag = new Asn1Tag(Asn1Tag.TagClass.APPLICATION, false, DeleteRequest.TAG);
        return wrapWithTag(tag, dnBytes);
    }

    private static byte[] encodeModifyDnRequest(ModifyDnRequest req) {
        Asn1Sequence.Builder inner = Asn1Sequence.builder();
        inner.add(Asn1OctetString.of(req.entry()));
        inner.add(Asn1OctetString.of(req.newRdn()));
        inner.add(Asn1Boolean.of(req.deleteOldRdn()));
        if (req.newSuperior() != null) {
            inner.add(Asn1ContextSpecific.implicit(0,
                    req.newSuperior().getBytes(StandardCharsets.UTF_8)));
        }
        return applicationConstructed(ModifyDnRequest.TAG, inner.build());
    }

    private static byte[] encodeCompareRequest(CompareRequest req) {
        Asn1Sequence.Builder inner = Asn1Sequence.builder();
        inner.add(Asn1OctetString.of(req.entry()));
        Asn1Sequence ava = Asn1Sequence.of(
                Asn1OctetString.of(req.attribute()),
                new Asn1OctetString(req.value())
        );
        inner.add(ava);
        return applicationConstructed(CompareRequest.TAG, inner.build());
    }

    private static byte[] encodeAbandonRequest(AbandonRequest req) {
        // AbandonRequest is APPLICATION 16 primitive with INTEGER content
        byte[] idBytes = BigInteger.valueOf(req.abandonedMessageId()).toByteArray();
        Asn1Tag tag = new Asn1Tag(Asn1Tag.TagClass.APPLICATION, false, AbandonRequest.TAG);
        return wrapWithTag(tag, idBytes);
    }

    private static byte[] encodeExtendedRequest(ExtendedRequest req) {
        Asn1Sequence.Builder inner = Asn1Sequence.builder();
        inner.add(Asn1ContextSpecific.implicit(0,
                req.requestName().getBytes(StandardCharsets.UTF_8)));
        if (req.requestValue() != null) {
            inner.add(Asn1ContextSpecific.implicit(1, req.requestValue()));
        }
        return applicationConstructed(ExtendedRequest.TAG, inner.build());
    }

    private static byte[] encodeExtendedResponse(ExtendedResponse resp) {
        Asn1Sequence.Builder inner = Asn1Sequence.builder();
        addResultComponents(inner, resp.result());
        if (resp.responseName() != null) {
            inner.add(Asn1ContextSpecific.implicit(10,
                    resp.responseName().getBytes(StandardCharsets.UTF_8)));
        }
        if (resp.responseValue() != null) {
            inner.add(Asn1ContextSpecific.implicit(11, resp.responseValue()));
        }
        return applicationConstructed(ExtendedResponse.TAG, inner.build());
    }

    private static byte[] encodeIntermediateResponse(IntermediateResponse resp) {
        Asn1Sequence.Builder inner = Asn1Sequence.builder();
        if (resp.responseName() != null) {
            inner.add(Asn1ContextSpecific.implicit(0,
                    resp.responseName().getBytes(StandardCharsets.UTF_8)));
        }
        if (resp.responseValue() != null) {
            inner.add(Asn1ContextSpecific.implicit(1, resp.responseValue()));
        }
        return applicationConstructed(IntermediateResponse.TAG, inner.build());
    }

    private static byte[] encodeResultOp(int tagNumber, LdapResult result) {
        Asn1Sequence.Builder inner = Asn1Sequence.builder();
        addResultComponents(inner, result);
        return applicationConstructed(tagNumber, inner.build());
    }

    // ── Protocol operation decoding ──

    private static LdapProtocolOp decodeProtocolOp(Asn1Type element) {
        if (!(element instanceof Asn1ContextSpecific ctx)) {
            throw new LdapCodecException("Expected APPLICATION-tagged protocol op, got: " +
                    element.getClass().getSimpleName());
        }

        int tag = ctx.tagNumber();
        return switch (tag) {
            case BindRequest.TAG -> decodeBindRequest(ctx);
            case BindResponse.TAG -> decodeBindResponse(ctx);
            case UnbindRequest.TAG -> UnbindRequest.INSTANCE;
            case SearchRequest.TAG -> decodeSearchRequest(ctx);
            case SearchResultEntry.TAG -> decodeSearchResultEntry(ctx);
            case SearchResultDone.TAG -> new SearchResultDone(decodeResult(getInnerElements(ctx)));
            case ModifyRequest.TAG -> decodeModifyRequest(ctx);
            case ModifyResponse.TAG -> new ModifyResponse(decodeResult(getInnerElements(ctx)));
            case AddRequest.TAG -> decodeAddRequest(ctx);
            case AddResponse.TAG -> new AddResponse(decodeResult(getInnerElements(ctx)));
            case DeleteRequest.TAG -> decodeDeleteRequest(ctx);
            case DeleteResponse.TAG -> new DeleteResponse(decodeResult(getInnerElements(ctx)));
            case ModifyDnRequest.TAG -> decodeModifyDnRequest(ctx);
            case ModifyDnResponse.TAG -> new ModifyDnResponse(decodeResult(getInnerElements(ctx)));
            case CompareRequest.TAG -> decodeCompareRequest(ctx);
            case CompareResponse.TAG -> new CompareResponse(decodeResult(getInnerElements(ctx)));
            case AbandonRequest.TAG -> decodeAbandonRequest(ctx);
            case SearchResultReference.TAG -> decodeSearchResultReference(ctx);
            case ExtendedRequest.TAG -> decodeExtendedRequest(ctx);
            case ExtendedResponse.TAG -> decodeExtendedResponse(ctx);
            case IntermediateResponse.TAG -> decodeIntermediateResponse(ctx);
            default -> throw new LdapCodecException("Unknown protocol operation tag: " + tag);
        };
    }

    private static BindRequest decodeBindRequest(Asn1ContextSpecific ctx) {
        List<Asn1Type> elements = getInnerElements(ctx);
        if (elements.size() < 3) throw new LdapCodecException("BindRequest requires 3 elements");

        int version = ((Asn1Integer) elements.get(0)).value().intValueExact();
        String name = octetStringToString(elements.get(1));
        BindRequest.AuthenticationChoice auth = decodeAuthChoice(elements.get(2));

        return new BindRequest(version, name, auth);
    }

    private static BindRequest.AuthenticationChoice decodeAuthChoice(Asn1Type element) {
        if (element instanceof Asn1ContextSpecific ctx) {
            if (ctx.tagNumber() == 0 && !ctx.constructed()) {
                return new BindRequest.AuthenticationChoice.Simple(
                        new String(ctx.rawBytes(), StandardCharsets.UTF_8));
            } else if (ctx.tagNumber() == 3) {
                List<Asn1Type> saslElements = getInnerElements(ctx);
                String mechanism = octetStringToString(saslElements.get(0));
                byte[] credentials = saslElements.size() > 1 ?
                        ((Asn1OctetString) saslElements.get(1)).value() : null;
                return new BindRequest.AuthenticationChoice.Sasl(mechanism, credentials);
            }
        }
        throw new LdapCodecException("Unknown authentication choice");
    }

    private static BindResponse decodeBindResponse(Asn1ContextSpecific ctx) {
        List<Asn1Type> elements = getInnerElements(ctx);
        LdapResult result = decodeResult(elements);
        byte[] saslCreds = null;
        for (Asn1Type e : elements) {
            if (e instanceof Asn1ContextSpecific c && c.tagNumber() == 7 && !c.constructed()) {
                saslCreds = c.rawBytes();
            }
        }
        return new BindResponse(result, saslCreds);
    }

    private static SearchRequest decodeSearchRequest(Asn1ContextSpecific ctx) {
        List<Asn1Type> elements = getInnerElements(ctx);
        if (elements.size() < 8) throw new LdapCodecException("SearchRequest requires 8 elements");

        String baseObject = octetStringToString(elements.get(0));
        SearchScope scope = SearchScope.of(((Asn1Enumerated) elements.get(1)).value());
        DerefAliases deref = DerefAliases.of(((Asn1Enumerated) elements.get(2)).value());
        int sizeLimit = ((Asn1Integer) elements.get(3)).value().intValueExact();
        int timeLimit = ((Asn1Integer) elements.get(4)).value().intValueExact();
        boolean typesOnly = ((Asn1Boolean) elements.get(5)).value();
        SearchFilter filter = LdapFilterCodec.decode(elements.get(6));
        List<String> attrs = new ArrayList<>();
        if (elements.get(7) instanceof Asn1Sequence attrSeq) {
            for (Asn1Type a : attrSeq.elements()) {
                attrs.add(octetStringToString(a));
            }
        }

        return new SearchRequest(baseObject, scope, deref, sizeLimit, timeLimit,
                typesOnly, filter, attrs);
    }

    private static SearchResultEntry decodeSearchResultEntry(Asn1ContextSpecific ctx) {
        List<Asn1Type> elements = getInnerElements(ctx);
        if (elements.size() < 2) throw new LdapCodecException("SearchResultEntry requires 2 elements");

        String objectName = octetStringToString(elements.get(0));
        List<LdapAttribute> attrs = decodePartialAttributeList(elements.get(1));

        return new SearchResultEntry(objectName, attrs);
    }

    private static SearchResultReference decodeSearchResultReference(Asn1ContextSpecific ctx) {
        List<Asn1Type> elements = getInnerElements(ctx);
        List<String> uris = new ArrayList<>();
        for (Asn1Type e : elements) {
            uris.add(octetStringToString(e));
        }
        return new SearchResultReference(uris);
    }

    private static ModifyRequest decodeModifyRequest(Asn1ContextSpecific ctx) {
        List<Asn1Type> elements = getInnerElements(ctx);
        if (elements.size() < 2) throw new LdapCodecException("ModifyRequest requires 2 elements");

        String object = octetStringToString(elements.get(0));
        List<ModifyRequest.Change> changes = new ArrayList<>();
        if (elements.get(1) instanceof Asn1Sequence changesSeq) {
            for (Asn1Type changeElem : changesSeq.elements()) {
                if (changeElem instanceof Asn1Sequence changeSeq && changeSeq.elements().size() >= 2) {
                    ModifyRequest.ModifyOperation op = ModifyRequest.ModifyOperation.of(
                            ((Asn1Enumerated) changeSeq.elements().get(0)).value());
                    LdapAttribute attr = decodePartialAttribute(changeSeq.elements().get(1));
                    changes.add(new ModifyRequest.Change(op, attr));
                }
            }
        }
        return new ModifyRequest(object, changes);
    }

    private static AddRequest decodeAddRequest(Asn1ContextSpecific ctx) {
        List<Asn1Type> elements = getInnerElements(ctx);
        if (elements.size() < 2) throw new LdapCodecException("AddRequest requires 2 elements");

        String entry = octetStringToString(elements.get(0));
        List<LdapAttribute> attrs = decodePartialAttributeList(elements.get(1));
        return new AddRequest(entry, attrs);
    }

    private static DeleteRequest decodeDeleteRequest(Asn1ContextSpecific ctx) {
        if (ctx.rawBytes() != null) {
            return new DeleteRequest(new String(ctx.rawBytes(), StandardCharsets.UTF_8));
        }
        throw new LdapCodecException("DeleteRequest expected primitive content");
    }

    private static ModifyDnRequest decodeModifyDnRequest(Asn1ContextSpecific ctx) {
        List<Asn1Type> elements = getInnerElements(ctx);
        if (elements.size() < 3) throw new LdapCodecException("ModifyDNRequest requires 3 elements");

        String entry = octetStringToString(elements.get(0));
        String newRdn = octetStringToString(elements.get(1));
        boolean deleteOldRdn = ((Asn1Boolean) elements.get(2)).value();
        String newSuperior = null;
        if (elements.size() > 3 && elements.get(3) instanceof Asn1ContextSpecific c && c.tagNumber() == 0) {
            newSuperior = new String(c.rawBytes(), StandardCharsets.UTF_8);
        }
        return new ModifyDnRequest(entry, newRdn, deleteOldRdn, newSuperior);
    }

    private static CompareRequest decodeCompareRequest(Asn1ContextSpecific ctx) {
        List<Asn1Type> elements = getInnerElements(ctx);
        if (elements.size() < 2) throw new LdapCodecException("CompareRequest requires 2 elements");

        String entry = octetStringToString(elements.get(0));
        if (elements.get(1) instanceof Asn1Sequence ava && ava.elements().size() >= 2) {
            String attribute = octetStringToString(ava.elements().get(0));
            byte[] value = ((Asn1OctetString) ava.elements().get(1)).value();
            return new CompareRequest(entry, attribute, value);
        }
        throw new LdapCodecException("Invalid AVA in CompareRequest");
    }

    private static AbandonRequest decodeAbandonRequest(Asn1ContextSpecific ctx) {
        if (ctx.rawBytes() != null) {
            int id = new BigInteger(ctx.rawBytes()).intValueExact();
            return new AbandonRequest(id);
        }
        throw new LdapCodecException("AbandonRequest expected primitive content");
    }

    private static ExtendedRequest decodeExtendedRequest(Asn1ContextSpecific ctx) {
        List<Asn1Type> elements = getInnerElements(ctx);
        String requestName = null;
        byte[] requestValue = null;
        for (Asn1Type e : elements) {
            if (e instanceof Asn1ContextSpecific c) {
                if (c.tagNumber() == 0) {
                    requestName = new String(c.rawBytes(), StandardCharsets.UTF_8);
                } else if (c.tagNumber() == 1) {
                    requestValue = c.rawBytes();
                }
            }
        }
        if (requestName == null) throw new LdapCodecException("ExtendedRequest missing requestName");
        return new ExtendedRequest(requestName, requestValue);
    }

    private static ExtendedResponse decodeExtendedResponse(Asn1ContextSpecific ctx) {
        List<Asn1Type> elements = getInnerElements(ctx);
        LdapResult result = decodeResult(elements);
        String responseName = null;
        byte[] responseValue = null;
        for (Asn1Type e : elements) {
            if (e instanceof Asn1ContextSpecific c) {
                if (c.tagNumber() == 10 && !c.constructed()) {
                    responseName = new String(c.rawBytes(), StandardCharsets.UTF_8);
                } else if (c.tagNumber() == 11 && !c.constructed()) {
                    responseValue = c.rawBytes();
                }
            }
        }
        return new ExtendedResponse(result, responseName, responseValue);
    }

    private static IntermediateResponse decodeIntermediateResponse(Asn1ContextSpecific ctx) {
        List<Asn1Type> elements = getInnerElements(ctx);
        String responseName = null;
        byte[] responseValue = null;
        for (Asn1Type e : elements) {
            if (e instanceof Asn1ContextSpecific c) {
                if (c.tagNumber() == 0 && !c.constructed()) {
                    responseName = new String(c.rawBytes(), StandardCharsets.UTF_8);
                } else if (c.tagNumber() == 1 && !c.constructed()) {
                    responseValue = c.rawBytes();
                }
            }
        }
        return new IntermediateResponse(responseName, responseValue);
    }

    // ── Helpers ──

    /**
     * Wraps SEQUENCE content with an APPLICATION constructed tag.
     * Encodes the inner sequence, then re-tags the content with an APPLICATION tag.
     */
    private static byte[] applicationConstructed(int tagNumber, Asn1Sequence inner) {
        // Encode the SEQUENCE content elements (not the SEQUENCE wrapper itself)
        byte[] seqEncoded = encodeAsn1ToBytes(inner);
        // Skip the SEQUENCE tag+length to get just the content bytes
        ByteBuffer buf = ByteBuffer.wrap(seqEncoded);
        BerTag.decode(buf);          // skip SEQUENCE tag
        int contentLen = BerLength.decode(buf);  // read content length
        byte[] contentBytes = new byte[contentLen];
        buf.get(contentBytes);

        // Wrap with APPLICATION constructed tag
        Asn1Tag appTag = new Asn1Tag(Asn1Tag.TagClass.APPLICATION, true, tagNumber);
        return wrapWithTag(appTag, contentBytes);
    }

    /**
     * Wraps raw bytes with a BER tag and length.
     */
    private static byte[] wrapWithTag(Asn1Tag tag, byte[] content) {
        int tagLen = BerTag.encodedLength(tag);
        int lenLen = BerLength.encodedLength(content.length);
        ByteBuffer buf = ByteBuffer.allocate(tagLen + lenLen + content.length);
        BerTag.encode(tag, buf);
        BerLength.encode(content.length, buf);
        buf.put(content);
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Encodes an Asn1Type to a byte array using BerEncoder.
     */
    private static byte[] encodeAsn1ToBytes(Asn1Type type) {
        ByteBuffer buf = BerEncoder.encode(type);
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return bytes;
    }

    private static void addResultComponents(Asn1Sequence.Builder builder, LdapResult result) {
        builder.add(Asn1Enumerated.of(result.resultCode().code()));
        builder.add(Asn1OctetString.of(result.matchedDn()));
        builder.add(Asn1OctetString.of(result.diagnosticMessage()));
        if (!result.referrals().isEmpty()) {
            Asn1Sequence.Builder refs = Asn1Sequence.builder();
            for (String ref : result.referrals()) {
                refs.add(Asn1OctetString.of(ref));
            }
            builder.add(Asn1ContextSpecific.explicit(3, refs.build()));
        }
    }

    private static LdapResult decodeResult(List<Asn1Type> elements) {
        if (elements.size() < 3) throw new LdapCodecException("LDAPResult requires 3 elements");

        LdapResultCode code = LdapResultCode.of(((Asn1Enumerated) elements.get(0)).value());
        String matchedDn = octetStringToString(elements.get(1));
        String diagnosticMessage = octetStringToString(elements.get(2));
        List<String> referrals = List.of();
        if (elements.size() > 3 && elements.get(3) instanceof Asn1ContextSpecific ctx && ctx.tagNumber() == 3) {
            List<Asn1Type> refElements = getInnerElements(ctx);
            referrals = new ArrayList<>();
            for (Asn1Type e : refElements) {
                referrals.add(octetStringToString(e));
            }
        }
        return new LdapResult(code, matchedDn, diagnosticMessage, referrals);
    }

    private static List<Asn1Type> getInnerElements(Asn1ContextSpecific ctx) {
        if (ctx.value() instanceof Asn1Sequence seq) {
            return seq.elements();
        }
        if (ctx.value() != null) {
            return List.of(ctx.value());
        }
        return List.of();
    }

    private static String octetStringToString(Asn1Type element) {
        if (element instanceof Asn1OctetString os) {
            return os.asString();
        }
        throw new LdapCodecException("Expected OCTET STRING, got: " + element.getClass().getSimpleName());
    }

    private static Asn1Type encodePartialAttribute(LdapAttribute attr) {
        Asn1Sequence.Builder builder = Asn1Sequence.builder();
        builder.add(Asn1OctetString.of(attr.type()));
        Asn1Set.Builder vals = Asn1Set.builder();
        for (byte[] v : attr.values()) {
            vals.add(new Asn1OctetString(v));
        }
        builder.add(vals.build());
        return builder.build();
    }

    private static Asn1Type encodePartialAttributeList(List<LdapAttribute> attributes) {
        Asn1Sequence.Builder builder = Asn1Sequence.builder();
        for (LdapAttribute attr : attributes) {
            builder.add(encodePartialAttribute(attr));
        }
        return builder.build();
    }

    static LdapAttribute decodePartialAttribute(Asn1Type element) {
        if (!(element instanceof Asn1Sequence seq) || seq.elements().size() < 2) {
            throw new LdapCodecException("Invalid partial attribute");
        }
        String type = octetStringToString(seq.elements().get(0));
        List<byte[]> values = new ArrayList<>();
        Asn1Type valSet = seq.elements().get(1);
        if (valSet instanceof Asn1Set set) {
            for (Asn1Type v : set.elements()) {
                if (v instanceof Asn1OctetString os) {
                    values.add(os.value());
                }
            }
        }
        return new LdapAttribute(type, values);
    }

    private static List<LdapAttribute> decodePartialAttributeList(Asn1Type element) {
        List<LdapAttribute> attributes = new ArrayList<>();
        if (element instanceof Asn1Sequence seq) {
            for (Asn1Type e : seq.elements()) {
                attributes.add(decodePartialAttribute(e));
            }
        }
        return attributes;
    }

    private static Asn1Type encodeControl(LdapControl control) {
        Asn1Sequence.Builder builder = Asn1Sequence.builder();
        builder.add(Asn1OctetString.of(control.oid()));
        if (control.criticality()) {
            builder.add(Asn1Boolean.TRUE);
        }
        if (control.value() != null) {
            builder.add(new Asn1OctetString(control.value()));
        }
        return builder.build();
    }

    private static List<LdapControl> decodeControls(Asn1Type element) {
        List<LdapControl> controls = new ArrayList<>();
        List<Asn1Type> elements;
        if (element instanceof Asn1ContextSpecific ctx) {
            elements = getInnerElements(ctx);
        } else if (element instanceof Asn1Sequence seq) {
            elements = seq.elements();
        } else {
            return controls;
        }

        for (Asn1Type e : elements) {
            if (e instanceof Asn1Sequence seq) {
                List<Asn1Type> controlElements = seq.elements();
                if (controlElements.isEmpty()) continue;
                String oid = octetStringToString(controlElements.get(0));
                boolean criticality = false;
                byte[] value = null;
                int idx = 1;
                if (idx < controlElements.size() && controlElements.get(idx) instanceof Asn1Boolean b) {
                    criticality = b.value();
                    idx++;
                }
                if (idx < controlElements.size() && controlElements.get(idx) instanceof Asn1OctetString os) {
                    value = os.value();
                }
                controls.add(new LdapControl(oid, criticality, value));
            }
        }
        return controls;
    }
}
