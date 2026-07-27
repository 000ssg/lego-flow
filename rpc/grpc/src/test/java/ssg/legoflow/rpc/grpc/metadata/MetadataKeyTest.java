package ssg.legoflow.rpc.grpc.metadata;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class MetadataKeyTest {

    @Test
    void testStringKey() {
        var key = MetadataKey.of("x-request-id");
        assertThat(key.name()).isEqualTo("x-request-id");
        assertThat(key.isBinary()).isFalse();
    }

    @Test
    void testStringKeySerialize() {
        var key = MetadataKey.of("x-custom");
        assertThat(key.serialize("hello")).isEqualTo("hello");
        assertThat(key.deserialize("hello")).isEqualTo("hello");
    }

    @Test
    void testStringKeyCaseInsensitive() {
        var key = MetadataKey.of("X-Custom-Header");
        assertThat(key.name()).isEqualTo("x-custom-header");
    }

    @Test
    void testStringKeyCannotEndWithBin() {
        assertThatThrownBy(() -> MetadataKey.of("data-bin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testBinaryKey() {
        var key = MetadataKey.binary("x-data-bin");
        assertThat(key.name()).isEqualTo("x-data-bin");
        assertThat(key.isBinary()).isTrue();
    }

    @Test
    void testBinaryKeySerialize() {
        var key = MetadataKey.binary("x-data-bin");
        byte[] data = {0x01, 0x02, 0x03};
        String serialized = key.serialize(data);
        byte[] deserialized = key.deserialize(serialized);
        assertThat(deserialized).containsExactly(data);
    }

    @Test
    void testBinaryKeyMustEndWithBin() {
        assertThatThrownBy(() -> MetadataKey.binary("x-data"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testBinaryKeyCaseInsensitive() {
        var key = MetadataKey.binary("X-Data-BIN");
        assertThat(key.name()).isEqualTo("x-data-bin");
    }

    @Test
    void testBinaryKeyBase64RoundTrip() {
        var key = MetadataKey.binary("token-bin");
        byte[] original = "secret-token".getBytes(StandardCharsets.UTF_8);
        String encoded = key.serialize(original);
        byte[] decoded = key.deserialize(encoded);
        assertThat(decoded).containsExactly(original);
    }
}
