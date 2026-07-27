package ssg.legoflow.rpc.grpc.protobuf;

/**
 * A protobuf field tag consisting of a field number and wire type.
 * Tag = (field_number &lt;&lt; 3) | wire_type.
 */
public record FieldTag(int fieldNumber, WireType wireType) {

    public FieldTag {
        if (fieldNumber < 1) {
            throw new IllegalArgumentException("Field number must be >= 1, got: " + fieldNumber);
        }
    }

    /**
     * Encodes this tag as a varint-compatible integer.
     */
    public int encode() {
        return (fieldNumber << 3) | wireType.value();
    }

    /**
     * Decodes a tag integer into a FieldTag.
     */
    public static FieldTag decode(int tagValue) {
        int fieldNumber = tagValue >>> 3;
        int wireTypeValue = tagValue & 0x07;
        return new FieldTag(fieldNumber, WireType.fromValue(wireTypeValue));
    }
}
