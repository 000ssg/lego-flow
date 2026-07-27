package ssg.legoflow.messaging.amqp.sasl;

import ssg.legoflow.messaging.amqp.types.AmqpType;
import ssg.legoflow.messaging.amqp.types.Descriptors;
import ssg.legoflow.messaging.amqp.types.TypeCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Encodes and decodes SASL frames for AMQP 1.0 authentication negotiation.
 *
 * @since 1.0.0
 */
public final class SaslCodec {

    private SaslCodec() {}

    /**
     * Encodes a sasl-mechanisms frame listing available mechanisms.
     *
     * @param mechanisms the mechanism names
     * @return the described type
     */
    public static AmqpType.Described encodeMechanisms(List<String> mechanisms) {
        var symbols = new ArrayList<AmqpType>(mechanisms.size());
        for (var m : mechanisms) {
            symbols.add(new AmqpType.Symbol(m));
        }
        var fields = List.<AmqpType>of(new AmqpType.AmqpArray(symbols));
        return described(Descriptors.SASL_MECHANISMS, fields);
    }

    /**
     * Encodes a sasl-init frame.
     *
     * @param mechanism       the chosen mechanism name
     * @param initialResponse the initial response bytes (may be empty)
     * @param hostname        the hostname (may be null)
     * @return the described type
     */
    public static AmqpType.Described encodeInit(String mechanism, byte[] initialResponse, String hostname) {
        var fields = new ArrayList<AmqpType>();
        fields.add(new AmqpType.Symbol(mechanism));
        fields.add(initialResponse != null && initialResponse.length > 0
                ? new AmqpType.Binary(initialResponse) : new AmqpType.Null());
        fields.add(hostname != null ? new AmqpType.AmqpString(hostname) : new AmqpType.Null());
        return described(Descriptors.SASL_INIT, trimNulls(fields));
    }

    /**
     * Encodes a sasl-challenge frame.
     *
     * @param challenge the challenge bytes
     * @return the described type
     */
    public static AmqpType.Described encodeChallenge(byte[] challenge) {
        return described(Descriptors.SASL_CHALLENGE, List.of(new AmqpType.Binary(challenge)));
    }

    /**
     * Encodes a sasl-response frame.
     *
     * @param response the response bytes
     * @return the described type
     */
    public static AmqpType.Described encodeResponse(byte[] response) {
        return described(Descriptors.SASL_RESPONSE, List.of(new AmqpType.Binary(response)));
    }

    /**
     * Encodes a sasl-outcome frame.
     *
     * @param code           the outcome code (0=ok, 1=auth, 2=sys, 3=sys-perm, 4=sys-temp)
     * @param additionalData optional additional data
     * @return the described type
     */
    public static AmqpType.Described encodeOutcome(int code, byte[] additionalData) {
        var fields = new ArrayList<AmqpType>();
        fields.add(new AmqpType.UByte((short) code));
        if (additionalData != null && additionalData.length > 0) {
            fields.add(new AmqpType.Binary(additionalData));
        }
        return described(Descriptors.SASL_OUTCOME, fields);
    }

    /**
     * Extracts mechanism names from a sasl-mechanisms described type.
     *
     * @param described the sasl-mechanisms described type
     * @return the list of mechanism names
     */
    public static List<String> decodeMechanisms(AmqpType.Described described) {
        var list = asList(described.described());
        if (list.elements().isEmpty()) return List.of();
        AmqpType mechField = list.elements().getFirst();
        var result = new ArrayList<String>();
        if (mechField instanceof AmqpType.AmqpArray arr) {
            for (var elem : arr.elements()) {
                result.add(TypeCodec.toString(elem));
            }
        } else if (mechField instanceof AmqpType.Symbol sym) {
            result.add(sym.value());
        }
        return result;
    }

    /**
     * Extracts the mechanism name from a sasl-init described type.
     *
     * @param described the sasl-init described type
     * @return the mechanism name
     */
    public static String decodeInitMechanism(AmqpType.Described described) {
        var list = asList(described.described());
        return TypeCodec.toString(list.elements().getFirst());
    }

    /**
     * Extracts the initial response from a sasl-init described type.
     *
     * @param described the sasl-init described type
     * @return the initial response bytes, or empty array
     */
    public static byte[] decodeInitResponse(AmqpType.Described described) {
        var list = asList(described.described());
        if (list.elements().size() > 1) {
            AmqpType f = list.elements().get(1);
            if (f instanceof AmqpType.Binary bin) return bin.value();
        }
        return new byte[0];
    }

    /**
     * Extracts the outcome code from a sasl-outcome described type.
     *
     * @param described the sasl-outcome described type
     * @return the outcome code
     */
    public static int decodeOutcomeCode(AmqpType.Described described) {
        var list = asList(described.described());
        return (int) TypeCodec.toLong(list.elements().getFirst());
    }

    private static AmqpType.Described described(long descriptor, List<AmqpType> fields) {
        return new AmqpType.Described(new AmqpType.ULong(descriptor), new AmqpType.AmqpList(fields));
    }

    private static AmqpType.AmqpList asList(AmqpType type) {
        if (type instanceof AmqpType.AmqpList list) return list;
        return new AmqpType.AmqpList(List.of());
    }

    private static List<AmqpType> trimNulls(List<AmqpType> fields) {
        int last = fields.size() - 1;
        while (last >= 0 && fields.get(last) instanceof AmqpType.Null) {
            last--;
        }
        return new ArrayList<>(fields.subList(0, last + 1));
    }
}
