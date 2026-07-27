package ssg.legoflow.rpc.grpc.protobuf;

import java.util.*;

/**
 * Descriptor for a protobuf message type, providing field metadata for encoding/decoding.
 */
public class MessageDescriptor {

    private final String fullName;
    private final Map<Integer, FieldDescriptor> fieldsByNumber;
    private final Map<String, FieldDescriptor> fieldsByName;
    private final List<String> oneofNames;

    private MessageDescriptor(String fullName, Map<Integer, FieldDescriptor> fieldsByNumber,
                               Map<String, FieldDescriptor> fieldsByName, List<String> oneofNames) {
        this.fullName = fullName;
        this.fieldsByNumber = Collections.unmodifiableMap(fieldsByNumber);
        this.fieldsByName = Collections.unmodifiableMap(fieldsByName);
        this.oneofNames = List.copyOf(oneofNames);
    }

    public String fullName() {
        return fullName;
    }

    public FieldDescriptor field(int number) {
        return fieldsByNumber.get(number);
    }

    public FieldDescriptor field(String name) {
        return fieldsByName.get(name);
    }

    public Collection<FieldDescriptor> fields() {
        return fieldsByNumber.values();
    }

    public List<String> oneofNames() {
        return oneofNames;
    }

    /**
     * Returns all fields belonging to the given oneof group index.
     */
    public List<FieldDescriptor> oneofFields(int oneofIndex) {
        return fieldsByNumber.values().stream()
                .filter(f -> f.oneofIndex() == oneofIndex)
                .toList();
    }

    public static Builder builder(String fullName) {
        return new Builder(fullName);
    }

    public static class Builder {
        private final String fullName;
        private final Map<Integer, FieldDescriptor> fieldsByNumber = new LinkedHashMap<>();
        private final Map<String, FieldDescriptor> fieldsByName = new LinkedHashMap<>();
        private final List<String> oneofNames = new ArrayList<>();

        private Builder(String fullName) {
            this.fullName = fullName;
        }

        public Builder addField(FieldDescriptor field) {
            fieldsByNumber.put(field.number(), field);
            fieldsByName.put(field.name(), field);
            return this;
        }

        public Builder addOneof(String name) {
            oneofNames.add(name);
            return this;
        }

        public MessageDescriptor build() {
            return new MessageDescriptor(fullName, fieldsByNumber, fieldsByName, oneofNames);
        }
    }
}
