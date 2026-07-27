package ssg.legoflow.wamp.core.serialization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for WampSerializerFactory.
 */
class WampSerializerFactoryTest {

    @Test
    void testCreateMessagePack() {
        var serializer = WampSerializerFactory.createMessagePack();
        assertThat(serializer).isNotNull();
        assertThat(serializer).isInstanceOf(WampMessagePackSerializer.class);
    }

    @Test
    void testCreateCbor() {
        var serializer = WampSerializerFactory.createCbor();
        assertThat(serializer).isNotNull();
        assertThat(serializer).isInstanceOf(WampCborSerializer.class);
    }

    @Test
    void testSupportedSubprotocols() {
        var protos = WampSerializerFactory.supportedSubprotocols();
        assertThat(protos).contains("wamp.2.json", "wamp.2.msgpack", "wamp.2.cbor");
    }

    @Test
    void testIsBinarySubprotocol() {
        assertThat(WampSerializerFactory.isBinarySubprotocol("wamp.2.msgpack")).isTrue();
        assertThat(WampSerializerFactory.isBinarySubprotocol("wamp.2.cbor")).isTrue();
        assertThat(WampSerializerFactory.isBinarySubprotocol("wamp.2.json")).isFalse();
    }
}
