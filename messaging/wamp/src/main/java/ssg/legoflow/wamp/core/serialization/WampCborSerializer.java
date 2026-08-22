package ssg.legoflow.wamp.core.serialization;

import ssg.legoflow.wamp.core.WampMessage;
import java.util.List;
/**
 * Serializes and deserializes WAMP messages using CBOR (RFC 8949) binary format.
 * WAMP messages are encoded as CBOR arrays: {@code [type_code, ...fields]}.
 *
 * <p>Corresponds to the {@code wamp.2.cbor} WebSocket subprotocol.</p>
 *
 * @since 0.1.0
 */
public class WampCborSerializer extends WampBinarySerializer {

    private final CborEncoder encoder = new CborEncoder();
    private final CborDecoder decoder = new CborDecoder();

    /**
     * Serializes a WAMP message to CBOR bytes.
     *
     * @param msg the message to serialize
     * @return the CBOR encoded bytes
     */
    public byte[] serialize(WampMessage msg) {
        return encoder.encode(messageToList(msg));
    }

    /**
     * Deserializes CBOR bytes to a WAMP message.
     *
     * @param data the CBOR encoded bytes
     * @return the deserialized WAMP message
     */
    @SuppressWarnings("unchecked")
    public WampMessage deserialize(byte[] data) {
        var array = (List<Object>) decoder.decode(data);
        return listToMessage(array);
    }
}
