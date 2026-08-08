package ssg.legoflow.messaging.amqp.message;

import ssg.legoflow.messaging.amqp.common.AmqpError;
import ssg.legoflow.messaging.amqp.common.AmqpException;
import ssg.legoflow.messaging.amqp.types.AmqpType;
import ssg.legoflow.messaging.amqp.types.Descriptors;
import ssg.legoflow.messaging.amqp.types.TypeCodec;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Encodes and decodes AMQP 1.0 messages to/from binary wire format.
 *
 * <p>A message is a sequence of described sections, each identified by its
 * descriptor code. This codec handles all standard sections: header,
 * delivery-annotations, message-annotations, properties, application-properties,
 * body (data/amqp-sequence/amqp-value), and footer.
 *
 * @since 0.1.0
 */
public final class MessageCodec {

    private MessageCodec() {}

    /**
     * Encodes a message into a ByteBuffer.
     *
     * @param message the message to encode
     * @return the encoded bytes
     */
    public static ByteBuffer encode(AmqpMessage message) {
        var sections = new ArrayList<AmqpType>();

        // Header
        if (message.header() != null) {
            sections.add(encodeHeader(message.header()));
        }

        // Delivery annotations
        if (message.deliveryAnnotations() != null && !message.deliveryAnnotations().isEmpty()) {
            sections.add(encodeAnnotations(Descriptors.DELIVERY_ANNOTATIONS, message.deliveryAnnotations()));
        }

        // Message annotations
        if (message.messageAnnotations() != null && !message.messageAnnotations().isEmpty()) {
            sections.add(encodeAnnotations(Descriptors.MESSAGE_ANNOTATIONS, message.messageAnnotations()));
        }

        // Properties
        if (message.properties() != null) {
            sections.add(encodeProperties(message.properties()));
        }

        // Application properties
        if (message.applicationProperties() != null && !message.applicationProperties().isEmpty()) {
            sections.add(encodeApplicationProperties(message.applicationProperties()));
        }

        // Body
        if (message.body() != null) {
            long descriptor = switch (message.bodyType()) {
                case DATA -> Descriptors.DATA;
                case AMQP_SEQUENCE -> Descriptors.AMQP_SEQUENCE;
                case AMQP_VALUE -> Descriptors.AMQP_VALUE;
            };
            sections.add(new AmqpType.Described(new AmqpType.ULong(descriptor), message.body()));
        }

        // Footer
        if (message.footer() != null && !message.footer().isEmpty()) {
            sections.add(encodeAnnotations(Descriptors.FOOTER, message.footer()));
        }

        // Calculate total size
        int totalSize = 0;
        var encodedSections = new ArrayList<ByteBuffer>(sections.size());
        for (var section : sections) {
            ByteBuffer buf = TypeCodec.encode(section);
            encodedSections.add(buf);
            totalSize += buf.remaining();
        }

        ByteBuffer result = ByteBuffer.allocate(totalSize);
        for (var buf : encodedSections) {
            result.put(buf);
        }
        result.flip();
        return result;
    }

    /**
     * Decodes a message from a ByteBuffer.
     *
     * @param buf the buffer containing the encoded message
     * @return the decoded message
     */
    public static AmqpMessage decode(ByteBuffer buf) {
        var message = new AmqpMessage();

        while (buf.hasRemaining()) {
            AmqpType section = TypeCodec.decode(buf);
            if (!(section instanceof AmqpType.Described desc)) {
                throw new AmqpException(AmqpError.DECODE_ERROR,
                        "Expected described type for message section");
            }

            long descriptor = TypeCodec.toLong(desc.descriptor());
            if (descriptor == Descriptors.HEADER) {
                message.header(decodeHeader(asList(desc.described())));
            } else if (descriptor == Descriptors.DELIVERY_ANNOTATIONS) {
                message.deliveryAnnotations(decodeAnnotations(desc.described()));
            } else if (descriptor == Descriptors.MESSAGE_ANNOTATIONS) {
                message.messageAnnotations(decodeAnnotations(desc.described()));
            } else if (descriptor == Descriptors.PROPERTIES) {
                message.properties(decodeProperties(asList(desc.described())));
            } else if (descriptor == Descriptors.APPLICATION_PROPERTIES) {
                message.applicationProperties(decodeAppProperties(desc.described()));
            } else if (descriptor == Descriptors.DATA) {
                message.bodyData(((AmqpType.Binary) desc.described()).value());
            } else if (descriptor == Descriptors.AMQP_SEQUENCE) {
                if (desc.described() instanceof AmqpType.AmqpList list) {
                    message.bodySequence(list.elements());
                }
            } else if (descriptor == Descriptors.AMQP_VALUE) {
                message.bodyValue(desc.described());
            } else if (descriptor == Descriptors.FOOTER) {
                message.footer(decodeAnnotations(desc.described()));
            }
            // Skip unknown sections
        }

        return message;
    }

    // ---- Header ----

    private static AmqpType.Described encodeHeader(Header h) {
        var fields = new ArrayList<AmqpType>();
        fields.add(new AmqpType.Bool(h.durable()));
        fields.add(new AmqpType.UByte(h.priority()));
        fields.add(h.ttl() > 0 ? new AmqpType.UInt(h.ttl()) : new AmqpType.Null());
        fields.add(new AmqpType.Bool(h.firstAcquirer()));
        fields.add(new AmqpType.UInt(h.deliveryCount()));
        return new AmqpType.Described(new AmqpType.ULong(Descriptors.HEADER),
                new AmqpType.AmqpList(trimNulls(fields)));
    }

    private static Header decodeHeader(AmqpType.AmqpList list) {
        boolean durable = optBool(list, 0, false);
        short priority = (short) optLong(list, 1, 4);
        long ttl = optLong(list, 2, 0);
        boolean firstAcquirer = optBool(list, 3, false);
        long deliveryCount = optLong(list, 4, 0);
        return new Header(durable, priority, ttl, firstAcquirer, deliveryCount);
    }

    // ---- Properties ----

    private static AmqpType.Described encodeProperties(Properties p) {
        var fields = new ArrayList<AmqpType>();
        fields.add(p.messageId() != null ? new AmqpType.AmqpString(p.messageId()) : new AmqpType.Null());
        fields.add(p.userId() != null ? new AmqpType.Binary(p.userId()) : new AmqpType.Null());
        fields.add(p.to() != null ? new AmqpType.AmqpString(p.to()) : new AmqpType.Null());
        fields.add(p.subject() != null ? new AmqpType.AmqpString(p.subject()) : new AmqpType.Null());
        fields.add(p.replyTo() != null ? new AmqpType.AmqpString(p.replyTo()) : new AmqpType.Null());
        fields.add(p.correlationId() != null ? new AmqpType.AmqpString(p.correlationId()) : new AmqpType.Null());
        fields.add(p.contentType() != null ? new AmqpType.Symbol(p.contentType()) : new AmqpType.Null());
        fields.add(p.contentEncoding() != null ? new AmqpType.Symbol(p.contentEncoding()) : new AmqpType.Null());
        fields.add(p.absoluteExpiryTime() > 0 ? new AmqpType.Timestamp(p.absoluteExpiryTime()) : new AmqpType.Null());
        fields.add(p.creationTime() > 0 ? new AmqpType.Timestamp(p.creationTime()) : new AmqpType.Null());
        fields.add(p.groupId() != null ? new AmqpType.AmqpString(p.groupId()) : new AmqpType.Null());
        fields.add(p.groupSequence() > 0 ? new AmqpType.UInt(p.groupSequence()) : new AmqpType.Null());
        fields.add(p.replyToGroupId() != null ? new AmqpType.AmqpString(p.replyToGroupId()) : new AmqpType.Null());
        return new AmqpType.Described(new AmqpType.ULong(Descriptors.PROPERTIES),
                new AmqpType.AmqpList(trimNulls(fields)));
    }

    private static Properties decodeProperties(AmqpType.AmqpList list) {
        return new Properties(
                optString(list, 0),
                optBinary(list, 1),
                optString(list, 2),
                optString(list, 3),
                optString(list, 4),
                optString(list, 5),
                optSymbol(list, 6),
                optSymbol(list, 7),
                optLong(list, 8, 0),
                optLong(list, 9, 0),
                optString(list, 10),
                optLong(list, 11, 0),
                optString(list, 12)
        );
    }

    // ---- Annotations / Application Properties ----

    private static AmqpType.Described encodeAnnotations(long descriptor, Map<String, Object> annotations) {
        var entries = new LinkedHashMap<AmqpType, AmqpType>();
        for (var entry : annotations.entrySet()) {
            entries.put(new AmqpType.Symbol(entry.getKey()), toAmqpValue(entry.getValue()));
        }
        return new AmqpType.Described(new AmqpType.ULong(descriptor), new AmqpType.AmqpMap(entries));
    }

    private static Map<String, Object> decodeAnnotations(AmqpType type) {
        if (type instanceof AmqpType.AmqpMap map) {
            var result = new LinkedHashMap<String, Object>();
            for (var entry : map.entries().entrySet()) {
                result.put(TypeCodec.toString(entry.getKey()), fromAmqpValue(entry.getValue()));
            }
            return result;
        }
        return Map.of();
    }

    private static AmqpType.Described encodeApplicationProperties(Map<String, Object> props) {
        var entries = new LinkedHashMap<AmqpType, AmqpType>();
        for (var entry : props.entrySet()) {
            entries.put(new AmqpType.AmqpString(entry.getKey()), toAmqpValue(entry.getValue()));
        }
        return new AmqpType.Described(new AmqpType.ULong(Descriptors.APPLICATION_PROPERTIES),
                new AmqpType.AmqpMap(entries));
    }

    private static Map<String, Object> decodeAppProperties(AmqpType type) {
        if (type instanceof AmqpType.AmqpMap map) {
            var result = new LinkedHashMap<String, Object>();
            for (var entry : map.entries().entrySet()) {
                String key = TypeCodec.toString(entry.getKey());
                result.put(key, fromAmqpValue(entry.getValue()));
            }
            return result;
        }
        return Map.of();
    }

    // ---- Helpers ----

    private static AmqpType toAmqpValue(Object value) {
        return switch (value) {
            case null -> new AmqpType.Null();
            case String s -> new AmqpType.AmqpString(s);
            case Boolean b -> new AmqpType.Bool(b);
            case Integer i -> new AmqpType.Int(i);
            case Long l -> new AmqpType.Long(l);
            case Float f -> new AmqpType.Float(f);
            case Double d -> new AmqpType.Double(d);
            case byte[] b -> new AmqpType.Binary(b);
            case AmqpType a -> a;
            default -> new AmqpType.AmqpString(value.toString());
        };
    }

    private static Object fromAmqpValue(AmqpType type) {
        return switch (type) {
            case AmqpType.Null _ -> null;
            case AmqpType.AmqpString s -> s.value();
            case AmqpType.Symbol s -> s.value();
            case AmqpType.Bool b -> b.value();
            case AmqpType.Int i -> i.value();
            case AmqpType.Long l -> l.value();
            case AmqpType.UInt ui -> ui.value();
            case AmqpType.ULong ul -> ul.value();
            case AmqpType.Float f -> f.value();
            case AmqpType.Double d -> d.value();
            case AmqpType.Binary bin -> bin.value();
            case AmqpType.Timestamp ts -> ts.millis();
            default -> type;
        };
    }

    private static AmqpType.AmqpList asList(AmqpType type) {
        if (type instanceof AmqpType.AmqpList list) return list;
        return new AmqpType.AmqpList(List.of());
    }

    private static String optString(AmqpType.AmqpList list, int index) {
        AmqpType f = TypeCodec.getField(list, index);
        return f != null ? TypeCodec.toString(f) : null;
    }

    private static String optSymbol(AmqpType.AmqpList list, int index) {
        AmqpType f = TypeCodec.getField(list, index);
        if (f instanceof AmqpType.Symbol s) return s.value();
        if (f instanceof AmqpType.AmqpString s) return s.value();
        return null;
    }

    private static byte[] optBinary(AmqpType.AmqpList list, int index) {
        AmqpType f = TypeCodec.getField(list, index);
        if (f instanceof AmqpType.Binary bin) return bin.value();
        return null;
    }

    private static boolean optBool(AmqpType.AmqpList list, int index, boolean defaultVal) {
        AmqpType f = TypeCodec.getField(list, index);
        return f != null ? TypeCodec.toBoolean(f) : defaultVal;
    }

    private static long optLong(AmqpType.AmqpList list, int index, long defaultVal) {
        AmqpType f = TypeCodec.getField(list, index);
        if (f == null) return defaultVal;
        if (f instanceof AmqpType.Timestamp ts) return ts.millis();
        return TypeCodec.toLong(f);
    }

    private static List<AmqpType> trimNulls(List<AmqpType> fields) {
        int last = fields.size() - 1;
        while (last >= 0 && fields.get(last) instanceof AmqpType.Null) {
            last--;
        }
        return fields.subList(0, last + 1);
    }
}
