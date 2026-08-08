package ssg.legoflow.wamp.core.serialization;

import ssg.legoflow.wamp.core.WampMessage;

import java.util.List;

/**
 * Serializes and deserializes WAMP messages using MessagePack binary format.
 * WAMP messages are encoded as MessagePack arrays: {@code [type_code, ...fields]}.
 *
 * <p>Corresponds to the {@code wamp.2.msgpack} WebSocket subprotocol.</p>
 *
 * @since 0.1.0
 */
public class WampMessagePackSerializer extends WampBinarySerializer {

    private final MessagePackEncoder encoder = new MessagePackEncoder();
    private final MessagePackDecoder decoder = new MessagePackDecoder();

    /**
     * Serializes a WAMP message to MessagePack bytes.
     *
     * @param msg the message to serialize
     * @return the MessagePack encoded bytes
     */
    public byte[] serialize(WampMessage msg) {
        return encoder.encode(messageToList(msg));
    }

    /**
     * Deserializes MessagePack bytes to a WAMP message.
     *
     * @param data the MessagePack encoded bytes
     * @return the deserialized WAMP message
     */
    @SuppressWarnings("unchecked")
    public WampMessage deserialize(byte[] data) {
        var array = (List<Object>) decoder.decode(data);
        return listToMessage(array);
    }
}
