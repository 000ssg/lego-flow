package ssg.legoflow.rpc.graphql.schema;

import java.util.*;

/**
 * Represents a GraphQL interface type.
 *
 * <p>Interfaces define a set of fields that implementing object types must include.
 * When querying a field that returns an interface, inline fragments or named fragments
 * can be used to select fields on the concrete object types.
 *
 * @since 0.1.0
 */
public final class InterfaceType implements GraphQLType {

    private final String name;
    private final String description;
    private final Map<String, FieldDefinition> fields;
    private final List<ObjectType> implementations;

    /**
     * Creates a new interface type.
     *
     * @param name        the interface name
     * @param description the interface description
     * @param fields      the fields defined by this interface
     */
    public InterfaceType(String name, String description, List<FieldDefinition> fields) {
        this.name = Objects.requireNonNull(name);
        this.description = description;
        var fieldMap = new LinkedHashMap<String, FieldDefinition>();
        if (fields != null) {
            for (var field : fields) {
                fieldMap.put(field.name(), field);
            }
        }
        this.fields = Collections.unmodifiableMap(fieldMap);
        this.implementations = new ArrayList<>();
    }

    /**
     * Creates a simple interface type.
     *
     * @param name   the interface name
     * @param fields the fields
     * @return a new interface type
     */
    public static InterfaceType of(String name, List<FieldDefinition> fields) {
        return new InterfaceType(name, null, fields);
    }

    @Override
    public String name() { return name; }
    public String description() { return description; }
    public Map<String, FieldDefinition> fields() { return fields; }

    /**
     * Returns the field with the given name.
     *
     * @param name the field name
     * @return the field definition, or null
     */
    public FieldDefinition getField(String name) { return fields.get(name); }

    /**
     * Returns the object types that implement this interface.
     *
     * @return the implementations
     */
    public List<ObjectType> implementations() { return Collections.unmodifiableList(implementations); }

    /**
     * Registers an implementing object type.
     *
     * @param objectType the implementing object type
     */
    public void addImplementation(ObjectType objectType) {
        implementations.add(objectType);
    }

    @Override
    public String toString() { return name; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof InterfaceType t && name.equals(t.name));
    }

    @Override
    public int hashCode() { return name.hashCode(); }
}
