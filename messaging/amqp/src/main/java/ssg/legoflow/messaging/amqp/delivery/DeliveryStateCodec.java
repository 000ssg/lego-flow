package ssg.legoflow.messaging.amqp.delivery;

import ssg.legoflow.messaging.amqp.common.AmqpError;
import ssg.legoflow.messaging.amqp.common.AmqpException;
import ssg.legoflow.messaging.amqp.types.AmqpType;
import ssg.legoflow.messaging.amqp.types.Descriptors;
import ssg.legoflow.messaging.amqp.types.TypeCodec;

import java.util.*;

/**
 * Encodes and decodes AMQP 1.0 delivery states to/from described types.
 *
 * @since 1.0.0
 */
public final class DeliveryStateCodec {

    private DeliveryStateCodec() {}

    /**
     * Encodes a delivery state into a described AMQP type.
     *
     * @param state the delivery state to encode
     * @return the described type
     */
    public static AmqpType.Described encode(DeliveryState state) {
        return switch (state) {
            case DeliveryState.Received r -> {
                var fields = List.<AmqpType>of(
                        new AmqpType.UInt(r.sectionNumber()),
                        new AmqpType.ULong(r.sectionOffset())
                );
                yield described(Descriptors.RECEIVED, fields);
            }
            case DeliveryState.Accepted _ -> described(Descriptors.ACCEPTED, List.of());
            case DeliveryState.Rejected r -> {
                var fields = new ArrayList<AmqpType>();
                if (r.errorCondition() != null) {
                    var errorFields = new ArrayList<AmqpType>();
                    errorFields.add(new AmqpType.Symbol(r.errorCondition()));
                    if (r.errorDescription() != null) {
                        errorFields.add(new AmqpType.AmqpString(r.errorDescription()));
                    }
                    fields.add(new AmqpType.Described(
                            new AmqpType.ULong(Descriptors.ERROR),
                            new AmqpType.AmqpList(errorFields)));
                }
                yield described(Descriptors.REJECTED, fields);
            }
            case DeliveryState.Released _ -> described(Descriptors.RELEASED, List.of());
            case DeliveryState.Modified m -> {
                var fields = new ArrayList<AmqpType>();
                fields.add(new AmqpType.Bool(m.deliveryFailed()));
                fields.add(new AmqpType.Bool(m.undeliverableHere()));
                if (m.messageAnnotations() != null && !m.messageAnnotations().isEmpty()) {
                    var entries = new LinkedHashMap<AmqpType, AmqpType>();
                    for (var entry : m.messageAnnotations().entrySet()) {
                        entries.put(new AmqpType.Symbol(entry.getKey()),
                                new AmqpType.AmqpString(Objects.toString(entry.getValue())));
                    }
                    fields.add(new AmqpType.AmqpMap(entries));
                }
                yield described(Descriptors.MODIFIED, fields);
            }
            case DeliveryState.TransactionalState ts -> {
                var fields = new ArrayList<AmqpType>();
                fields.add(new AmqpType.Binary(ts.txnId()));
                if (ts.outcome() != null) {
                    fields.add(encode(ts.outcome()));
                }
                yield described(Descriptors.TRANSACTIONAL_STATE, fields);
            }
        };
    }

    /**
     * Decodes a described AMQP type into a delivery state.
     *
     * @param described the described type
     * @return the decoded delivery state
     */
    public static DeliveryState decode(AmqpType.Described described) {
        long descriptor = TypeCodec.toLong(described.descriptor());
        var list = asList(described.described());
        if (descriptor == Descriptors.RECEIVED) {
            long sectionNumber = TypeCodec.toLong(list.elements().get(0));
            long sectionOffset = TypeCodec.toLong(list.elements().get(1));
            return new DeliveryState.Received(sectionNumber, sectionOffset);
        } else if (descriptor == Descriptors.ACCEPTED) {
            return new DeliveryState.Accepted();
        } else if (descriptor == Descriptors.REJECTED) {
            String condition = null;
            String description = null;
            if (!list.elements().isEmpty()) {
                AmqpType errorType = list.elements().getFirst();
                if (errorType instanceof AmqpType.Described errorDesc) {
                    var errorList = asList(errorDesc.described());
                    if (!errorList.elements().isEmpty()) {
                        condition = TypeCodec.toString(errorList.elements().get(0));
                    }
                    if (errorList.elements().size() > 1) {
                        AmqpType descField = errorList.elements().get(1);
                        if (!(descField instanceof AmqpType.Null)) {
                            description = TypeCodec.toString(descField);
                        }
                    }
                }
            }
            return new DeliveryState.Rejected(condition, description);
        } else if (descriptor == Descriptors.RELEASED) {
            return new DeliveryState.Released();
        } else if (descriptor == Descriptors.MODIFIED) {
            boolean deliveryFailed = !list.elements().isEmpty() && TypeCodec.toBoolean(list.elements().get(0));
            boolean undeliverableHere = list.elements().size() > 1 && TypeCodec.toBoolean(list.elements().get(1));
            Map<String, Object> annotations = Map.of();
            if (list.elements().size() > 2 && list.elements().get(2) instanceof AmqpType.AmqpMap map) {
                annotations = new LinkedHashMap<>();
                for (var entry : map.entries().entrySet()) {
                    annotations.put(TypeCodec.toString(entry.getKey()),
                            TypeCodec.toString(entry.getValue()));
                }
            }
            return new DeliveryState.Modified(deliveryFailed, undeliverableHere, annotations);
        } else if (descriptor == Descriptors.TRANSACTIONAL_STATE) {
            byte[] txnId = ((AmqpType.Binary) list.elements().get(0)).value();
            DeliveryState outcome = null;
            if (list.elements().size() > 1 && list.elements().get(1) instanceof AmqpType.Described outDesc) {
                outcome = decode(outDesc);
            }
            return new DeliveryState.TransactionalState(txnId, outcome);
        } else {
            throw new AmqpException(AmqpError.DECODE_ERROR,
                    "Unknown delivery state descriptor: 0x" + Long.toHexString(descriptor));
        }
    }

    /**
     * Checks whether the given AMQP type is a delivery state described type.
     *
     * @param type the type to check
     * @return true if it is a known delivery state descriptor
     */
    public static boolean isDeliveryState(AmqpType type) {
        if (type instanceof AmqpType.Described desc) {
            long d = TypeCodec.toLong(desc.descriptor());
            return d == Descriptors.RECEIVED || d == Descriptors.ACCEPTED
                    || d == Descriptors.REJECTED || d == Descriptors.RELEASED
                    || d == Descriptors.MODIFIED || d == Descriptors.TRANSACTIONAL_STATE;
        }
        return false;
    }

    private static AmqpType.Described described(long descriptor, List<AmqpType> fields) {
        return new AmqpType.Described(new AmqpType.ULong(descriptor), new AmqpType.AmqpList(fields));
    }

    private static AmqpType.AmqpList asList(AmqpType type) {
        if (type instanceof AmqpType.AmqpList list) return list;
        return new AmqpType.AmqpList(List.of());
    }
}
