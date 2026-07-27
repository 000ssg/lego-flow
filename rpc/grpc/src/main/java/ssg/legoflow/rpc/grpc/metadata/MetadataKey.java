package ssg.legoflow.rpc.grpc.metadata;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A typed metadata key. gRPC metadata keys are case-insensitive ASCII strings.
 * Keys ending in "-bin" are binary and their values are base64-encoded.
 *
 * @param <T> the value type (String or byte[])
 */
public sealed interface MetadataKey<T> {

    String name();

    String serialize(T value);

    T deserialize(String serialized);

    boolean isBinary();

    /**
     * A string metadata key.
     */
    record StringKey(String name) implements MetadataKey<String> {
        public StringKey {
            name = name.toLowerCase();
            if (name.endsWith("-bin")) {
                throw new IllegalArgumentException(
                        "String key must not end with '-bin': " + name);
            }
        }

        @Override
        public String serialize(String value) {
            return value;
        }

        @Override
        public String deserialize(String serialized) {
            return serialized;
        }

        @Override
        public boolean isBinary() {
            return false;
        }
    }

    /**
     * A binary metadata key. Values are base64-encoded in transport.
     */
    record BinaryKey(String name) implements MetadataKey<byte[]> {
        public BinaryKey {
            name = name.toLowerCase();
            if (!name.endsWith("-bin")) {
                throw new IllegalArgumentException(
                        "Binary key must end with '-bin': " + name);
            }
        }

        @Override
        public String serialize(byte[] value) {
            return Base64.getEncoder().encodeToString(value);
        }

        @Override
        public byte[] deserialize(String serialized) {
            return Base64.getDecoder().decode(serialized);
        }

        @Override
        public boolean isBinary() {
            return true;
        }
    }

    /**
     * Creates a string metadata key.
     */
    static MetadataKey<String> of(String name) {
        return new StringKey(name);
    }

    /**
     * Creates a binary metadata key. The name must end with "-bin".
     */
    static MetadataKey<byte[]> binary(String name) {
        return new BinaryKey(name);
    }
}
