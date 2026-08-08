package ssg.legoflow.wamp.core.serialization;

/**
 * Factory for creating WAMP serializers by format name.
 * Supports JSON, MessagePack, and CBOR serialization formats
 * corresponding to WAMP WebSocket subprotocols.
 *
 * @since 0.1.0
 */
public class WampSerializerFactory {

    /** The WAMP v2 JSON subprotocol identifier. */
    public static final String JSON_SUBPROTOCOL = "wamp.2.json";

    /** The WAMP v2 MessagePack subprotocol identifier. */
    public static final String MSGPACK_SUBPROTOCOL = "wamp.2.msgpack";

    /** The WAMP v2 CBOR subprotocol identifier. */
    public static final String CBOR_SUBPROTOCOL = "wamp.2.cbor";

    /**
     * Creates a MessagePack serializer.
     *
     * @return a new MessagePack serializer
     */
    public static WampMessagePackSerializer createMessagePack() {
        return new WampMessagePackSerializer();
    }

    /**
     * Creates a CBOR serializer.
     *
     * @return a new CBOR serializer
     */
    public static WampCborSerializer createCbor() {
        return new WampCborSerializer();
    }

    /**
     * Returns the list of supported subprotocols.
     *
     * @return array of supported subprotocol names
     */
    public static String[] supportedSubprotocols() {
        return new String[]{JSON_SUBPROTOCOL, MSGPACK_SUBPROTOCOL, CBOR_SUBPROTOCOL};
    }

    /**
     * Checks whether the given subprotocol is a binary format.
     *
     * @param subprotocol the subprotocol name
     * @return {@code true} if the subprotocol uses binary framing
     */
    public static boolean isBinarySubprotocol(String subprotocol) {
        return MSGPACK_SUBPROTOCOL.equals(subprotocol) || CBOR_SUBPROTOCOL.equals(subprotocol);
    }
}
