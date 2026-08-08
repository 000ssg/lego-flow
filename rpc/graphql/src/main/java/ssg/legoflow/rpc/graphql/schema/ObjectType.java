package ssg.legoflow.rpc.graphql.schema;

import java.util.*;

/**
 * Represents a GraphQL object type.
 *
 * <p>Object types have a collection of fields, each of which has its own type.
 * Object types can implement one or more interfaces.
 *
 * @since 0.1.0
 */
public final class ObjectType implements GraphQLType {

    private final String name;
    private final String description;
    private final Map<String, FieldDefinition> fields;
    private final List<InterfaceType> interfaces;

    /**
     * Creates a new object type.
     *
     * @param name        the type name
     * @param description the type description
     * @param fields      the fields on this type
     * @param interfaces  the interfaces this type implements
     */
    public ObjectType(String name, String description,
                      List<FieldDefinition> fields,
                      List<InterfaceType> interfaces) {
        this.name = Objects.requireNonNull(name);
        this.description = description;
        var fieldMap = new LinkedHashMap<String, FieldDefinition>();
        if (fields != null) {
            for (var field : fields) {
                fieldMap.put(field.name(), field);
            }
        }
        this.fields = Collections.unmodifiableMap(fieldMap);
        this.interfaces = interfaces != null ? List.copyOf(interfaces) : List.of();
    }

    /**
     * Creates a simple object type with no interfaces.
     *
     * @param name   the type name
     * @param fields the fields
     * @return a new object type
     */
    public static ObjectType of(String name, List<FieldDefinition> fields) {
        return new ObjectType(name, null, fields, List.of());
    }

    /**
     * Creates an object type with interfaces.
     *
     * @param name       the type name
     * @param fields     the fields
     * @param interfaces the interfaces
     * @return a new object type
     */
    public static ObjectType of(String name, List<FieldDefinition> fields,
                                List<InterfaceType> interfaces) {
        return new ObjectType(name, null, fields, interfaces);
    }

    @Override
    public String name() { return name; }
    public String description() { return description; }

    /**
     * Returns the fields on this object type.
     *
     * @return unmodifiable map of field name to definition
     */
    public Map<String, FieldDefinition> fields() { return fields; }

    /**
     * Returns the field with the given name.
     *
     * @param name the field name
     * @return the field definition, or null
     */
    public FieldDefinition getField(String name) { return fields.get(name); }

    /**
     * Returns the interfaces this type implements.
     *
     * @return the interface list
     */
    public List<InterfaceType> interfaces() { return interfaces; }

    @Override
    public String toString() { return name; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ObjectType t && name.equals(t.name));
    }

    @Override
    public int hashCode() { return name.hashCode(); }
}
