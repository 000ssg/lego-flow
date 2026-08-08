package ssg.legoflow.wamp.core.serialization;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampMessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class for binary WAMP serializers that convert between {@link WampMessage}
 * and binary byte arrays. Subclasses provide the encoder/decoder for the specific
 * binary format (MessagePack or CBOR).
 *
 * <p>WAMP messages are encoded as arrays: {@code [type_code, ...fields]}.</p>
 *
 * @since 0.1.0
 */
abstract class WampBinarySerializer {

    /**
     * Converts a WAMP message to a list representation for encoding.
     *
     * @param msg the message to convert
     * @return the list representation
     */
    protected List<Object> messageToList(WampMessage msg) {
        var list = new ArrayList<Object>();
        list.add(msg.type().code());
        switch (msg) {
            case WampMessage.Hello m -> {
                list.add(m.realm());
                list.add(m.details());
            }
            case WampMessage.Welcome m -> {
                list.add(m.sessionId());
                list.add(m.details());
            }
            case WampMessage.Abort m -> {
                list.add(m.details());
                list.add(m.reason());
            }
            case WampMessage.Challenge m -> {
                list.add(m.authMethod());
                list.add(m.extra());
            }
            case WampMessage.Authenticate m -> {
                list.add(m.signature());
                list.add(m.extra());
            }
            case WampMessage.Goodbye m -> {
                list.add(m.details());
                list.add(m.reason());
            }
            case WampMessage.Error m -> {
                list.add(m.requestType());
                list.add(m.requestId());
                list.add(m.details());
                list.add(m.error());
            }
            case WampMessage.Publish m -> {
                list.add(m.requestId());
                list.add(m.options());
                list.add(m.topic());
                if (m.args() != null && !m.args().isEmpty()) {
                    list.add(m.args());
                }
            }
            case WampMessage.Published m -> {
                list.add(m.requestId());
                list.add(m.publicationId());
            }
            case WampMessage.Subscribe m -> {
                list.add(m.requestId());
                list.add(m.options());
                list.add(m.topic());
            }
            case WampMessage.Subscribed m -> {
                list.add(m.requestId());
                list.add(m.subscriptionId());
            }
            case WampMessage.Unsubscribe m -> {
                list.add(m.requestId());
                list.add(m.subscriptionId());
            }
            case WampMessage.Unsubscribed m -> {
                list.add(m.requestId());
            }
            case WampMessage.Event m -> {
                list.add(m.subscriptionId());
                list.add(m.publicationId());
                list.add(m.details());
                if (m.args() != null && !m.args().isEmpty()) {
                    list.add(m.args());
                }
            }
            case WampMessage.Call m -> {
                list.add(m.requestId());
                list.add(m.options());
                list.add(m.procedure());
                if (m.args() != null && !m.args().isEmpty()) {
                    list.add(m.args());
                }
            }
            case WampMessage.Cancel m -> {
                list.add(m.requestId());
                list.add(m.options());
            }
            case WampMessage.Result m -> {
                list.add(m.requestId());
                list.add(m.details());
                if (m.args() != null && !m.args().isEmpty()) {
                    list.add(m.args());
                }
            }
            case WampMessage.Register m -> {
                list.add(m.requestId());
                list.add(m.options());
                list.add(m.procedure());
            }
            case WampMessage.Registered m -> {
                list.add(m.requestId());
                list.add(m.registrationId());
            }
            case WampMessage.Unregister m -> {
                list.add(m.requestId());
                list.add(m.registrationId());
            }
            case WampMessage.Unregistered m -> {
                list.add(m.requestId());
            }
            case WampMessage.Invocation m -> {
                list.add(m.requestId());
                list.add(m.registrationId());
                list.add(m.details());
                if (m.args() != null && !m.args().isEmpty()) {
                    list.add(m.args());
                }
            }
            case WampMessage.Interrupt m -> {
                list.add(m.requestId());
                list.add(m.options());
            }
            case WampMessage.Yield m -> {
                list.add(m.requestId());
                list.add(m.options());
                if (m.args() != null && !m.args().isEmpty()) {
                    list.add(m.args());
                }
            }
        }
        return list;
    }

    /**
     * Converts a decoded list representation back to a WAMP message.
     *
     * @param array the list decoded from binary format
     * @return the WAMP message
     */
    @SuppressWarnings("unchecked")
    protected WampMessage listToMessage(List<Object> array) {
        int typeCode = ((Number) array.get(0)).intValue();
        var type = WampMessageType.fromCode(typeCode);
        return switch (type) {
            case HELLO -> new WampMessage.Hello(
                    (String) array.get(1),
                    asMap(array.get(2)));
            case WELCOME -> new WampMessage.Welcome(
                    asLong(array.get(1)),
                    asMap(array.get(2)));
            case ABORT -> new WampMessage.Abort(
                    asMap(array.get(1)),
                    (String) array.get(2));
            case CHALLENGE -> new WampMessage.Challenge(
                    (String) array.get(1),
                    asMap(array.get(2)));
            case AUTHENTICATE -> new WampMessage.Authenticate(
                    (String) array.get(1),
                    asMap(array.get(2)));
            case GOODBYE -> new WampMessage.Goodbye(
                    asMap(array.get(1)),
                    (String) array.get(2));
            case ERROR -> new WampMessage.Error(
                    ((Number) array.get(1)).intValue(),
                    asLong(array.get(2)),
                    asMap(array.get(3)),
                    (String) array.get(4));
            case PUBLISH -> new WampMessage.Publish(
                    asLong(array.get(1)),
                    asMap(array.get(2)),
                    (String) array.get(3),
                    array.size() > 4 ? asList(array.get(4)) : List.of());
            case PUBLISHED -> new WampMessage.Published(
                    asLong(array.get(1)),
                    asLong(array.get(2)));
            case SUBSCRIBE -> new WampMessage.Subscribe(
                    asLong(array.get(1)),
                    asMap(array.get(2)),
                    (String) array.get(3));
            case SUBSCRIBED -> new WampMessage.Subscribed(
                    asLong(array.get(1)),
                    asLong(array.get(2)));
            case UNSUBSCRIBE -> new WampMessage.Unsubscribe(
                    asLong(array.get(1)),
                    asLong(array.get(2)));
            case UNSUBSCRIBED -> new WampMessage.Unsubscribed(
                    asLong(array.get(1)));
            case EVENT -> new WampMessage.Event(
                    asLong(array.get(1)),
                    asLong(array.get(2)),
                    asMap(array.get(3)),
                    array.size() > 4 ? asList(array.get(4)) : List.of());
            case CALL -> new WampMessage.Call(
                    asLong(array.get(1)),
                    asMap(array.get(2)),
                    (String) array.get(3),
                    array.size() > 4 ? asList(array.get(4)) : List.of());
            case CANCEL -> new WampMessage.Cancel(
                    asLong(array.get(1)),
                    asMap(array.get(2)));
            case RESULT -> new WampMessage.Result(
                    asLong(array.get(1)),
                    asMap(array.get(2)),
                    array.size() > 3 ? asList(array.get(3)) : List.of());
            case REGISTER -> new WampMessage.Register(
                    asLong(array.get(1)),
                    asMap(array.get(2)),
                    (String) array.get(3));
            case REGISTERED -> new WampMessage.Registered(
                    asLong(array.get(1)),
                    asLong(array.get(2)));
            case UNREGISTER -> new WampMessage.Unregister(
                    asLong(array.get(1)),
                    asLong(array.get(2)));
            case UNREGISTERED -> new WampMessage.Unregistered(
                    asLong(array.get(1)));
            case INVOCATION -> new WampMessage.Invocation(
                    asLong(array.get(1)),
                    asLong(array.get(2)),
                    asMap(array.get(3)),
                    array.size() > 4 ? asList(array.get(4)) : List.of());
            case INTERRUPT -> new WampMessage.Interrupt(
                    asLong(array.get(1)),
                    asMap(array.get(2)));
            case YIELD -> new WampMessage.Yield(
                    asLong(array.get(1)),
                    asMap(array.get(2)),
                    array.size() > 3 ? asList(array.get(3)) : List.of());
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object obj) {
        if (obj == null) return Map.of();
        return (Map<String, Object>) obj;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object obj) {
        if (obj == null) return List.of();
        return (List<Object>) obj;
    }

    private static long asLong(Object obj) {
        return ((Number) obj).longValue();
    }
}
