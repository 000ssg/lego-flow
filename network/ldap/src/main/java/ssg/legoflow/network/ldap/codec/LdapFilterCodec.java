package ssg.legoflow.network.ldap.codec;

import ssg.legoflow.network.common.asn1.*;
import ssg.legoflow.network.ldap.filter.SearchFilter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * BER encoder/decoder for LDAP search filters (RFC 4511 Section 4.5.1).
 *
 * <p>Filters use CONTEXT-SPECIFIC tags (0-9) in their BER encoding.
 *
 * <p>This codec is stateless and thread-safe.
 *
 * @since 0.1.0
 */
public final class LdapFilterCodec {

    private LdapFilterCodec() {}

    /**
     * Encodes a search filter to an ASN.1 type.
     *
     * @param filter the filter to encode
     * @return the encoded ASN.1 type
     */
    public static Asn1Type encode(SearchFilter filter) {
        return switch (filter) {
            case SearchFilter.And and -> {
                Asn1Sequence.Builder builder = Asn1Sequence.builder();
                for (SearchFilter f : and.filters()) {
                    builder.add(encode(f));
                }
                yield Asn1ContextSpecific.implicitConstructed(0, builder.build());
            }
            case SearchFilter.Or or -> {
                Asn1Sequence.Builder builder = Asn1Sequence.builder();
                for (SearchFilter f : or.filters()) {
                    builder.add(encode(f));
                }
                yield Asn1ContextSpecific.implicitConstructed(1, builder.build());
            }
            case SearchFilter.Not not ->
                    Asn1ContextSpecific.explicit(2, encode(not.filter()));
            case SearchFilter.EqualityMatch eq ->
                    Asn1ContextSpecific.implicitConstructed(3, encodeAVA(eq.attribute(), eq.value()));
            case SearchFilter.Substrings sub -> {
                Asn1Sequence.Builder inner = Asn1Sequence.builder();
                inner.add(Asn1OctetString.of(sub.attribute()));
                Asn1Sequence.Builder subsSeq = Asn1Sequence.builder();
                if (sub.initial() != null) {
                    subsSeq.add(Asn1ContextSpecific.implicit(0,
                            sub.initial().getBytes(StandardCharsets.UTF_8)));
                }
                for (String any : sub.any()) {
                    subsSeq.add(Asn1ContextSpecific.implicit(1,
                            any.getBytes(StandardCharsets.UTF_8)));
                }
                if (sub.finalStr() != null) {
                    subsSeq.add(Asn1ContextSpecific.implicit(2,
                            sub.finalStr().getBytes(StandardCharsets.UTF_8)));
                }
                inner.add(subsSeq.build());
                yield Asn1ContextSpecific.implicitConstructed(4, inner.build());
            }
            case SearchFilter.GreaterOrEqual ge ->
                    Asn1ContextSpecific.implicitConstructed(5, encodeAVA(ge.attribute(), ge.value()));
            case SearchFilter.LessOrEqual le ->
                    Asn1ContextSpecific.implicitConstructed(6, encodeAVA(le.attribute(), le.value()));
            case SearchFilter.Present p ->
                    Asn1ContextSpecific.implicit(7, p.attribute().getBytes(StandardCharsets.UTF_8));
            case SearchFilter.ApproxMatch am ->
                    Asn1ContextSpecific.implicitConstructed(8, encodeAVA(am.attribute(), am.value()));
            case SearchFilter.ExtensibleMatch em -> {
                Asn1Sequence.Builder inner = Asn1Sequence.builder();
                if (em.matchingRule() != null) {
                    inner.add(Asn1ContextSpecific.implicit(1,
                            em.matchingRule().getBytes(StandardCharsets.UTF_8)));
                }
                if (em.attribute() != null) {
                    inner.add(Asn1ContextSpecific.implicit(2,
                            em.attribute().getBytes(StandardCharsets.UTF_8)));
                }
                inner.add(Asn1ContextSpecific.implicit(3, em.matchValue()));
                if (em.dnAttributes()) {
                    inner.add(Asn1ContextSpecific.implicit(4, new byte[]{(byte) 0xFF}));
                }
                yield Asn1ContextSpecific.implicitConstructed(9, inner.build());
            }
        };
    }

    /**
     * Decodes a search filter from an ASN.1 type.
     *
     * @param element the ASN.1 element
     * @return the decoded filter
     * @throws LdapCodecException if the filter is malformed
     */
    public static SearchFilter decode(Asn1Type element) {
        if (!(element instanceof Asn1ContextSpecific ctx)) {
            throw new LdapCodecException("Expected context-specific tagged filter, got: " +
                    element.getClass().getSimpleName());
        }

        int tag = ctx.tagNumber();
        return switch (tag) {
            case 0 -> { // AND
                List<SearchFilter> filters = new ArrayList<>();
                for (Asn1Type child : getChildren(ctx)) {
                    filters.add(decode(child));
                }
                yield new SearchFilter.And(filters);
            }
            case 1 -> { // OR
                List<SearchFilter> filters = new ArrayList<>();
                for (Asn1Type child : getChildren(ctx)) {
                    filters.add(decode(child));
                }
                yield new SearchFilter.Or(filters);
            }
            case 2 -> { // NOT
                Asn1Type inner;
                if (ctx.value() instanceof Asn1Sequence seq && !seq.elements().isEmpty()) {
                    inner = seq.elements().getFirst();
                } else {
                    inner = ctx.value();
                }
                if (inner == null) throw new LdapCodecException("NOT filter has no inner filter");
                yield new SearchFilter.Not(decode(inner));
            }
            case 3 -> { // equalityMatch
                List<Asn1Type> ava = getChildren(ctx);
                if (ava.size() < 2) throw new LdapCodecException("EqualityMatch requires 2 elements");
                String attr = toString(ava.get(0));
                byte[] value = toBytes(ava.get(1));
                yield new SearchFilter.EqualityMatch(attr, value);
            }
            case 4 -> { // substrings
                List<Asn1Type> children = getChildren(ctx);
                if (children.size() < 2) throw new LdapCodecException("SubstringFilter requires 2 elements");
                String attr = toString(children.get(0));
                String initial = null;
                List<String> any = new ArrayList<>();
                String finalStr = null;
                Asn1Type subsElement = children.get(1);
                List<Asn1Type> subs = subsElement instanceof Asn1Sequence seq ?
                        seq.elements() : List.of(subsElement);
                for (Asn1Type sub : subs) {
                    if (sub instanceof Asn1ContextSpecific sc) {
                        String val = new String(sc.rawBytes(), StandardCharsets.UTF_8);
                        switch (sc.tagNumber()) {
                            case 0 -> initial = val;
                            case 1 -> any.add(val);
                            case 2 -> finalStr = val;
                        }
                    }
                }
                yield new SearchFilter.Substrings(attr, initial, any, finalStr);
            }
            case 5 -> { // greaterOrEqual
                List<Asn1Type> ava = getChildren(ctx);
                if (ava.size() < 2) throw new LdapCodecException("GreaterOrEqual requires 2 elements");
                yield new SearchFilter.GreaterOrEqual(toString(ava.get(0)), toBytes(ava.get(1)));
            }
            case 6 -> { // lessOrEqual
                List<Asn1Type> ava = getChildren(ctx);
                if (ava.size() < 2) throw new LdapCodecException("LessOrEqual requires 2 elements");
                yield new SearchFilter.LessOrEqual(toString(ava.get(0)), toBytes(ava.get(1)));
            }
            case 7 -> { // present
                if (ctx.rawBytes() != null) {
                    yield new SearchFilter.Present(new String(ctx.rawBytes(), StandardCharsets.UTF_8));
                }
                throw new LdapCodecException("Present filter has no content");
            }
            case 8 -> { // approxMatch
                List<Asn1Type> ava = getChildren(ctx);
                if (ava.size() < 2) throw new LdapCodecException("ApproxMatch requires 2 elements");
                yield new SearchFilter.ApproxMatch(toString(ava.get(0)), toBytes(ava.get(1)));
            }
            case 9 -> { // extensibleMatch
                List<Asn1Type> children = getChildren(ctx);
                String matchingRule = null;
                String attribute = null;
                byte[] matchValue = null;
                boolean dnAttributes = false;
                for (Asn1Type child : children) {
                    if (child instanceof Asn1ContextSpecific sc) {
                        switch (sc.tagNumber()) {
                            case 1 -> matchingRule = new String(sc.rawBytes(), StandardCharsets.UTF_8);
                            case 2 -> attribute = new String(sc.rawBytes(), StandardCharsets.UTF_8);
                            case 3 -> matchValue = sc.rawBytes();
                            case 4 -> dnAttributes = sc.rawBytes() != null && sc.rawBytes().length > 0
                                    && sc.rawBytes()[0] != 0;
                        }
                    }
                }
                if (matchValue == null) throw new LdapCodecException("ExtensibleMatch missing matchValue");
                yield new SearchFilter.ExtensibleMatch(matchingRule, attribute, matchValue, dnAttributes);
            }
            default -> throw new LdapCodecException("Unknown filter tag: " + tag);
        };
    }

    private static Asn1Sequence encodeAVA(String attribute, byte[] value) {
        return Asn1Sequence.of(
                Asn1OctetString.of(attribute),
                new Asn1OctetString(value)
        );
    }

    private static List<Asn1Type> getChildren(Asn1ContextSpecific ctx) {
        if (ctx.value() instanceof Asn1Sequence seq) {
            return seq.elements();
        }
        if (ctx.value() != null) {
            return List.of(ctx.value());
        }
        return List.of();
    }

    private static String toString(Asn1Type element) {
        if (element instanceof Asn1OctetString os) return os.asString();
        throw new LdapCodecException("Expected OCTET STRING, got: " + element.getClass().getSimpleName());
    }

    private static byte[] toBytes(Asn1Type element) {
        if (element instanceof Asn1OctetString os) return os.value();
        throw new LdapCodecException("Expected OCTET STRING, got: " + element.getClass().getSimpleName());
    }
}
