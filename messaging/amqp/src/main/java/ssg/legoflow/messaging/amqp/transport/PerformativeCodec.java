package ssg.legoflow.messaging.amqp.transport;

import ssg.legoflow.messaging.amqp.common.AmqpError;
import ssg.legoflow.messaging.amqp.common.AmqpException;
import ssg.legoflow.messaging.amqp.types.AmqpType;
import ssg.legoflow.messaging.amqp.types.Descriptors;
import ssg.legoflow.messaging.amqp.types.TypeCodec;
import java.util.*;
/**
 * Encodes and decodes AMQP 1.0 performatives to/from described AMQP type values.
 *
 * <p>Each performative is encoded as a described list where the descriptor is a ulong
 * identifying the performative type and the described value is a list of fields
 * in the order defined by the specification.
 *
 * @since 0.1.0
 */
public final class PerformativeCodec {

    private PerformativeCodec() {}

    /**
     * Encodes a performative into a described AMQP type.
     *
     * @param performative the performative to encode
     * @return the described type (descriptor + list)
     */
    public static AmqpType.Described encode(Performative performative) {
        return switch (performative) {
            case Performative.Open o -> encodeOpen(o);
            case Performative.Begin b -> encodeBegin(b);
            case Performative.Attach a -> encodeAttach(a);
            case Performative.Flow f -> encodeFlow(f);
            case Performative.Transfer t -> encodeTransfer(t);
            case Performative.Disposition d -> encodeDisposition(d);
            case Performative.Detach d -> encodeDetach(d);
            case Performative.End e -> encodeEnd(e);
            case Performative.Close c -> encodeClose(c);
        };
    }

    /**
     * Decodes a described AMQP type into a performative.
     *
     * @param described the described type
     * @return the decoded performative
     * @throws AmqpException if the descriptor is unknown or the fields are malformed
     */
    public static Performative decode(AmqpType.Described described) {
        long descriptor = TypeCodec.toLong(described.descriptor());
        var list = asList(described.described());
        if (descriptor == Descriptors.OPEN) return decodeOpen(list);
        if (descriptor == Descriptors.BEGIN) return decodeBegin(list);
        if (descriptor == Descriptors.ATTACH) return decodeAttach(list);
        if (descriptor == Descriptors.FLOW) return decodeFlow(list);
        if (descriptor == Descriptors.TRANSFER) return decodeTransfer(list);
        if (descriptor == Descriptors.DISPOSITION) return decodeDisposition(list);
        if (descriptor == Descriptors.DETACH) return decodeDetach(list);
        if (descriptor == Descriptors.END) return decodeEnd(list);
        if (descriptor == Descriptors.CLOSE) return decodeClose(list);
        throw new AmqpException(AmqpError.DECODE_ERROR,
                "Unknown performative descriptor: 0x" + Long.toHexString(descriptor));
    }

    /**
     * Returns the descriptor code for the given described type, or -1 if not a described type.
     *
     * @param type the AMQP type
     * @return the descriptor code, or -1
     */
    public static long descriptorOf(AmqpType type) {
        if (type instanceof AmqpType.Described desc) {
            return TypeCodec.toLong(desc.descriptor());
        }
        return -1;
    }

    // ---- Open ----

    private static AmqpType.Described encodeOpen(Performative.Open o) {
        var fields = new ArrayList<AmqpType>();
        fields.add(new AmqpType.AmqpString(o.containerId()));
        fields.add(o.hostname() != null ? new AmqpType.AmqpString(o.hostname()) : new AmqpType.Null());
        fields.add(new AmqpType.UInt(o.maxFrameSize()));
        fields.add(new AmqpType.UShort(o.channelMax()));
        fields.add(o.idleTimeout() > 0 ? new AmqpType.UInt(o.idleTimeout()) : new AmqpType.Null());
        fields.add(encodeSymbolArray(o.offeredCapabilities()));
        fields.add(encodeSymbolArray(o.desiredCapabilities()));
        fields.add(encodeFieldsMap(o.properties()));
        return described(Descriptors.OPEN, trimNulls(fields));
    }

    private static Performative.Open decodeOpen(AmqpType.AmqpList list) {
        String containerId = stringField(list, 0);
        String hostname = optStringField(list, 1);
        long maxFrameSize = optUintField(list, 2, 0xFFFFFFFFL);
        int channelMax = (int) optUintField(list, 3, 65535);
        long idleTimeout = optUintField(list, 4, 0);
        List<String> offered = symbolArrayField(list, 5);
        List<String> desired = symbolArrayField(list, 6);
        Map<String, Object> props = fieldsMapField(list, 7);
        return new Performative.Open(containerId, hostname, maxFrameSize, channelMax,
                idleTimeout, offered, desired, props);
    }

    // ---- Begin ----

    private static AmqpType.Described encodeBegin(Performative.Begin b) {
        var fields = new ArrayList<AmqpType>();
        fields.add(b.remoteChannel() != null ? new AmqpType.UShort(b.remoteChannel()) : new AmqpType.Null());
        fields.add(new AmqpType.UInt(b.nextOutgoingId()));
        fields.add(new AmqpType.UInt(b.incomingWindow()));
        fields.add(new AmqpType.UInt(b.outgoingWindow()));
        fields.add(new AmqpType.UInt(b.handleMax()));
        fields.add(encodeSymbolArray(b.offeredCapabilities()));
        fields.add(encodeSymbolArray(b.desiredCapabilities()));
        fields.add(encodeFieldsMap(b.properties()));
        return described(Descriptors.BEGIN, trimNulls(fields));
    }

    private static Performative.Begin decodeBegin(AmqpType.AmqpList list) {
        Integer remoteChannel = optUshortField(list, 0);
        long nextOutgoingId = uintField(list, 1);
        long incomingWindow = uintField(list, 2);
        long outgoingWindow = uintField(list, 3);
        long handleMax = optUintField(list, 4, 0xFFFFFFFFL);
        List<String> offered = symbolArrayField(list, 5);
        List<String> desired = symbolArrayField(list, 6);
        Map<String, Object> props = fieldsMapField(list, 7);
        return new Performative.Begin(remoteChannel, nextOutgoingId, incomingWindow, outgoingWindow,
                handleMax, offered, desired, props);
    }

    // ---- Attach ----

    private static AmqpType.Described encodeAttach(Performative.Attach a) {
        var fields = new ArrayList<AmqpType>();
        fields.add(new AmqpType.AmqpString(a.name()));                          // 0
        fields.add(new AmqpType.UInt(a.handle()));                              // 1
        fields.add(new AmqpType.Bool(a.role()));                                // 2
        fields.add(new AmqpType.UByte((short) a.sndSettleMode()));              // 3
        fields.add(new AmqpType.UByte((short) a.rcvSettleMode()));              // 4
        fields.add(a.source() != null ? a.source() : new AmqpType.Null());     // 5
        fields.add(a.target() != null ? a.target() : new AmqpType.Null());     // 6
        fields.add(new AmqpType.Null());                                        // 7 unsettled
        fields.add(new AmqpType.Bool(false));                                   // 8 incomplete-unsettled
        fields.add(a.initialDeliveryCount() != null                             // 9
                ? new AmqpType.UInt(a.initialDeliveryCount()) : new AmqpType.Null());
        fields.add(a.maxMessageSize() > 0                                       // 10
                ? new AmqpType.ULong(a.maxMessageSize()) : new AmqpType.Null());
        fields.add(encodeSymbolArray(a.offeredCapabilities()));                 // 11
        fields.add(encodeSymbolArray(a.desiredCapabilities()));                 // 12
        fields.add(encodeFieldsMap(a.properties()));                            // 13
        return described(Descriptors.ATTACH, trimNulls(fields));
    }

    private static Performative.Attach decodeAttach(AmqpType.AmqpList list) {
        String name = stringField(list, 0);
        long handle = uintField(list, 1);
        boolean role = boolField(list, 2);
        int sndSettleMode = (int) optUbyteField(list, 3, 2);
        int rcvSettleMode = (int) optUbyteField(list, 4, 0);
        AmqpType source = TypeCodec.getField(list, 5);
        AmqpType target = TypeCodec.getField(list, 6);
        // field 7 = unsettled (skip)
        // field 8 = incomplete-unsettled (skip)
        Long initialDeliveryCount = optUintFieldNullable(list, 9);
        long maxMessageSize = optUlongField(list, 10, 0);
        List<String> offered = symbolArrayField(list, 11);
        List<String> desired = symbolArrayField(list, 12);
        Map<String, Object> props = fieldsMapField(list, 13);
        return new Performative.Attach(name, handle, role, sndSettleMode, rcvSettleMode,
                source, target, initialDeliveryCount, maxMessageSize, offered, desired, props);
    }

    // ---- Flow ----

    private static AmqpType.Described encodeFlow(Performative.Flow f) {
        var fields = new ArrayList<AmqpType>();
        fields.add(f.nextIncomingId() != null ? new AmqpType.UInt(f.nextIncomingId()) : new AmqpType.Null());
        fields.add(f.incomingWindow() != null ? new AmqpType.UInt(f.incomingWindow()) : new AmqpType.Null());
        fields.add(f.nextOutgoingId() != null ? new AmqpType.UInt(f.nextOutgoingId()) : new AmqpType.Null());
        fields.add(f.outgoingWindow() != null ? new AmqpType.UInt(f.outgoingWindow()) : new AmqpType.Null());
        fields.add(f.handle() != null ? new AmqpType.UInt(f.handle()) : new AmqpType.Null());
        fields.add(f.deliveryCount() != null ? new AmqpType.UInt(f.deliveryCount()) : new AmqpType.Null());
        fields.add(f.linkCredit() != null ? new AmqpType.UInt(f.linkCredit()) : new AmqpType.Null());
        fields.add(f.available() != null ? new AmqpType.UInt(f.available()) : new AmqpType.Null());
        fields.add(new AmqpType.Bool(f.drain()));
        fields.add(new AmqpType.Bool(f.echo()));
        fields.add(encodeFieldsMap(f.properties()));
        return described(Descriptors.FLOW, trimNulls(fields));
    }

    private static Performative.Flow decodeFlow(AmqpType.AmqpList list) {
        Long nextIncomingId = optUintFieldNullable(list, 0);
        Long incomingWindow = optUintFieldNullable(list, 1);
        Long nextOutgoingId = optUintFieldNullable(list, 2);
        Long outgoingWindow = optUintFieldNullable(list, 3);
        Long handle = optUintFieldNullable(list, 4);
        Long deliveryCount = optUintFieldNullable(list, 5);
        Long linkCredit = optUintFieldNullable(list, 6);
        Long available = optUintFieldNullable(list, 7);
        boolean drain = optBoolField(list, 8, false);
        boolean echo = optBoolField(list, 9, false);
        Map<String, Object> props = fieldsMapField(list, 10);
        return new Performative.Flow(nextIncomingId, incomingWindow, nextOutgoingId, outgoingWindow,
                handle, deliveryCount, linkCredit, available, drain, echo, props);
    }

    // ---- Transfer ----

    private static AmqpType.Described encodeTransfer(Performative.Transfer t) {
        var fields = new ArrayList<AmqpType>();
        fields.add(new AmqpType.UInt(t.handle()));                                 // 0
        fields.add(t.deliveryId() != null ? new AmqpType.UInt(t.deliveryId()) : new AmqpType.Null()); // 1
        fields.add(t.deliveryTag() != null ? new AmqpType.Binary(t.deliveryTag()) : new AmqpType.Null()); // 2
        fields.add(new AmqpType.UInt(t.messageFormat()));                          // 3
        fields.add(t.settled() ? new AmqpType.Bool(true) : new AmqpType.Null());   // 4
        fields.add(t.more() ? new AmqpType.Bool(true) : new AmqpType.Null());      // 5
        fields.add(t.rcvSettleMode() != null ? new AmqpType.UByte(t.rcvSettleMode().shortValue()) : new AmqpType.Null()); // 6
        fields.add(t.state() != null ? t.state() : new AmqpType.Null());           // 7
        fields.add(t.resume() ? new AmqpType.Bool(true) : new AmqpType.Null());    // 8
        fields.add(t.aborted() ? new AmqpType.Bool(true) : new AmqpType.Null());   // 9
        fields.add(t.batchable() ? new AmqpType.Bool(true) : new AmqpType.Null()); // 10
        return described(Descriptors.TRANSFER, trimNulls(fields));
    }

    private static Performative.Transfer decodeTransfer(AmqpType.AmqpList list) {
        long handle = uintField(list, 0);
        Long deliveryId = optUintFieldNullable(list, 1);
        byte[] deliveryTag = optBinaryField(list, 2);
        long messageFormat = optUintField(list, 3, 0);
        boolean settled = optBoolField(list, 4, false);
        boolean more = optBoolField(list, 5, false);
        Integer rcvSettleMode = optUbyteFieldNullable(list, 6);
        AmqpType state = TypeCodec.getField(list, 7);
        boolean resume = optBoolField(list, 8, false);
        boolean aborted = optBoolField(list, 9, false);
        boolean batchable = optBoolField(list, 10, false);
        return new Performative.Transfer(handle, deliveryId, deliveryTag, messageFormat,
                settled, more, rcvSettleMode, state, resume, aborted, batchable);
    }

    // ---- Disposition ----

    private static AmqpType.Described encodeDisposition(Performative.Disposition d) {
        var fields = new ArrayList<AmqpType>();
        fields.add(new AmqpType.Bool(d.role()));
        fields.add(new AmqpType.UInt(d.first()));
        fields.add(d.last() != null ? new AmqpType.UInt(d.last()) : new AmqpType.Null());
        fields.add(d.settled() ? new AmqpType.Bool(true) : new AmqpType.Null());
        fields.add(d.state() != null ? d.state() : new AmqpType.Null());
        fields.add(d.batchable() ? new AmqpType.Bool(true) : new AmqpType.Null());
        return described(Descriptors.DISPOSITION, trimNulls(fields));
    }

    private static Performative.Disposition decodeDisposition(AmqpType.AmqpList list) {
        boolean role = boolField(list, 0);
        long first = uintField(list, 1);
        Long last = optUintFieldNullable(list, 2);
        boolean settled = optBoolField(list, 3, false);
        AmqpType state = TypeCodec.getField(list, 4);
        boolean batchable = optBoolField(list, 5, false);
        return new Performative.Disposition(role, first, last, settled, state, batchable);
    }

    // ---- Detach ----

    private static AmqpType.Described encodeDetach(Performative.Detach d) {
        var fields = new ArrayList<AmqpType>();
        fields.add(new AmqpType.UInt(d.handle()));
        fields.add(d.closed() ? new AmqpType.Bool(true) : new AmqpType.Null());
        fields.add(d.error() != null ? d.error() : new AmqpType.Null());
        return described(Descriptors.DETACH, trimNulls(fields));
    }

    private static Performative.Detach decodeDetach(AmqpType.AmqpList list) {
        long handle = uintField(list, 0);
        boolean closed = optBoolField(list, 1, false);
        AmqpType error = TypeCodec.getField(list, 2);
        return new Performative.Detach(handle, closed, error);
    }

    // ---- End ----

    private static AmqpType.Described encodeEnd(Performative.End e) {
        var fields = new ArrayList<AmqpType>();
        fields.add(e.error() != null ? e.error() : new AmqpType.Null());
        return described(Descriptors.END, trimNulls(fields));
    }

    private static Performative.End decodeEnd(AmqpType.AmqpList list) {
        AmqpType error = TypeCodec.getField(list, 0);
        return new Performative.End(error);
    }

    // ---- Close ----

    private static AmqpType.Described encodeClose(Performative.Close c) {
        var fields = new ArrayList<AmqpType>();
        fields.add(c.error() != null ? c.error() : new AmqpType.Null());
        return described(Descriptors.CLOSE, trimNulls(fields));
    }

    private static Performative.Close decodeClose(AmqpType.AmqpList list) {
        AmqpType error = TypeCodec.getField(list, 0);
        return new Performative.Close(error);
    }

    // ---- Helpers ----

    private static AmqpType.Described described(long descriptor, List<AmqpType> fields) {
        return new AmqpType.Described(new AmqpType.ULong(descriptor), new AmqpType.AmqpList(fields));
    }

    private static AmqpType.AmqpList asList(AmqpType type) {
        if (type instanceof AmqpType.AmqpList list) return list;
        throw new AmqpException(AmqpError.DECODE_ERROR,
                "Expected list body for performative, got: " + type.getClass().getSimpleName());
    }

    /** Trim trailing null values from a field list. */
    private static List<AmqpType> trimNulls(List<AmqpType> fields) {
        int last = fields.size() - 1;
        while (last >= 0 && fields.get(last) instanceof AmqpType.Null) {
            last--;
        }
        return fields.subList(0, last + 1);
    }

    private static String stringField(AmqpType.AmqpList list, int index) {
        AmqpType f = TypeCodec.getField(list, index);
        if (f == null) throw new AmqpException(AmqpError.DECODE_ERROR, "Required field " + index + " is null");
        return TypeCodec.toString(f);
    }

    private static String optStringField(AmqpType.AmqpList list, int index) {
        AmqpType f = TypeCodec.getField(list, index);
        return f != null ? TypeCodec.toString(f) : null;
    }

    private static boolean boolField(AmqpType.AmqpList list, int index) {
        AmqpType f = TypeCodec.getField(list, index);
        if (f == null) throw new AmqpException(AmqpError.DECODE_ERROR, "Required bool field " + index + " is null");
        return TypeCodec.toBoolean(f);
    }

    private static boolean optBoolField(AmqpType.AmqpList list, int index, boolean defaultVal) {
        AmqpType f = TypeCodec.getField(list, index);
        return f != null ? TypeCodec.toBoolean(f) : defaultVal;
    }

    private static long uintField(AmqpType.AmqpList list, int index) {
        AmqpType f = TypeCodec.getField(list, index);
        if (f == null) throw new AmqpException(AmqpError.DECODE_ERROR, "Required uint field " + index + " is null");
        return TypeCodec.toLong(f);
    }

    private static long optUintField(AmqpType.AmqpList list, int index, long defaultVal) {
        AmqpType f = TypeCodec.getField(list, index);
        return f != null ? TypeCodec.toLong(f) : defaultVal;
    }

    private static Long optUintFieldNullable(AmqpType.AmqpList list, int index) {
        AmqpType f = TypeCodec.getField(list, index);
        return f != null ? TypeCodec.toLong(f) : null;
    }

    private static Integer optUshortField(AmqpType.AmqpList list, int index) {
        AmqpType f = TypeCodec.getField(list, index);
        return f != null ? (int) TypeCodec.toLong(f) : null;
    }

    private static long optUbyteField(AmqpType.AmqpList list, int index, long defaultVal) {
        AmqpType f = TypeCodec.getField(list, index);
        return f != null ? TypeCodec.toLong(f) : defaultVal;
    }

    private static Integer optUbyteFieldNullable(AmqpType.AmqpList list, int index) {
        AmqpType f = TypeCodec.getField(list, index);
        return f != null ? (int) TypeCodec.toLong(f) : null;
    }

    private static long optUlongField(AmqpType.AmqpList list, int index, long defaultVal) {
        AmqpType f = TypeCodec.getField(list, index);
        return f != null ? TypeCodec.toLong(f) : defaultVal;
    }

    private static byte[] optBinaryField(AmqpType.AmqpList list, int index) {
        AmqpType f = TypeCodec.getField(list, index);
        if (f instanceof AmqpType.Binary bin) return bin.value();
        return null;
    }

    private static AmqpType encodeSymbolArray(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return new AmqpType.Null();
        var elements = new ArrayList<AmqpType>(symbols.size());
        for (var sym : symbols) {
            elements.add(new AmqpType.Symbol(sym));
        }
        return new AmqpType.AmqpArray(elements);
    }

    private static List<String> symbolArrayField(AmqpType.AmqpList list, int index) {
        AmqpType f = TypeCodec.getField(list, index);
        if (f == null) return List.of();
        if (f instanceof AmqpType.AmqpArray arr) {
            var result = new ArrayList<String>(arr.elements().size());
            for (var elem : arr.elements()) {
                result.add(TypeCodec.toString(elem));
            }
            return result;
        }
        if (f instanceof AmqpType.Symbol sym) {
            return List.of(sym.value());
        }
        return List.of();
    }

    private static AmqpType encodeFieldsMap(Map<String, Object> props) {
        if (props == null || props.isEmpty()) return new AmqpType.Null();
        var entries = new LinkedHashMap<AmqpType, AmqpType>();
        for (var entry : props.entrySet()) {
            entries.put(new AmqpType.Symbol(entry.getKey()), toAmqpType(entry.getValue()));
        }
        return new AmqpType.AmqpMap(entries);
    }

    private static Map<String, Object> fieldsMapField(AmqpType.AmqpList list, int index) {
        AmqpType f = TypeCodec.getField(list, index);
        if (f == null) return Map.of();
        if (f instanceof AmqpType.AmqpMap map) {
            var result = new LinkedHashMap<String, Object>();
            for (var entry : map.entries().entrySet()) {
                result.put(TypeCodec.toString(entry.getKey()), fromAmqpType(entry.getValue()));
            }
            return result;
        }
        return Map.of();
    }

    private static AmqpType toAmqpType(Object value) {
        return switch (value) {
            case null -> new AmqpType.Null();
            case String s -> new AmqpType.AmqpString(s);
            case Boolean b -> new AmqpType.Bool(b);
            case Integer i -> new AmqpType.Int(i);
            case Long l -> new AmqpType.Long(l);
            case byte[] b -> new AmqpType.Binary(b);
            case AmqpType a -> a;
            default -> new AmqpType.AmqpString(value.toString());
        };
    }

    private static Object fromAmqpType(AmqpType type) {
        return switch (type) {
            case AmqpType.Null _ -> null;
            case AmqpType.AmqpString s -> s.value();
            case AmqpType.Symbol s -> s.value();
            case AmqpType.Bool b -> b.value();
            case AmqpType.Int i -> i.value();
            case AmqpType.Long l -> l.value();
            case AmqpType.UInt ui -> ui.value();
            case AmqpType.ULong ul -> ul.value();
            case AmqpType.Binary bin -> bin.value();
            default -> type;
        };
    }

    /**
     * Encodes an AMQP error condition as a described list.
     *
     * @param condition   the error condition symbol
     * @param description the error description
     * @return the encoded error described type
     */
    public static AmqpType.Described encodeError(String condition, String description) {
        var fields = new ArrayList<AmqpType>();
        fields.add(new AmqpType.Symbol(condition));
        fields.add(description != null ? new AmqpType.AmqpString(description) : new AmqpType.Null());
        return new AmqpType.Described(new AmqpType.ULong(Descriptors.ERROR),
                new AmqpType.AmqpList(trimNulls(fields)));
    }

    /**
     * Encodes a source terminus as a described type.
     *
     * @param address the source address
     * @return the described source
     */
    public static AmqpType.Described encodeSource(String address) {
        if (address == null) return null;
        var fields = new ArrayList<AmqpType>();
        fields.add(new AmqpType.AmqpString(address));
        return new AmqpType.Described(new AmqpType.ULong(Descriptors.SOURCE),
                new AmqpType.AmqpList(trimNulls(fields)));
    }

    /**
     * Encodes a target terminus as a described type.
     *
     * @param address the target address
     * @return the described target, or null if address is null
     */
    public static AmqpType.Described encodeTarget(String address) {
        if (address == null) return null;
        var fields = new ArrayList<AmqpType>();
        fields.add(new AmqpType.AmqpString(address));
        return new AmqpType.Described(new AmqpType.ULong(Descriptors.TARGET),
                new AmqpType.AmqpList(trimNulls(fields)));
    }

    /**
     * Extracts the address from a source or target described type.
     *
     * @param terminus the source or target described type
     * @return the address string, or null
     */
    public static String extractAddress(AmqpType terminus) {
        if (terminus instanceof AmqpType.Described desc) {
            var list = asList(desc.described());
            return optStringField(list, 0);
        }
        return null;
    }
}
