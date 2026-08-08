package ssg.legoflow.rpc.graphql.schema;

import java.util.*;

/**
 * Represents a GraphQL input object type.
 *
 * <p>Input object types define structured input for arguments on fields
 * and directives. Unlike object types, input objects cannot have resolvers
 * and their fields are input fields.
 *
 * @since 0.1.0
 */
public final class InputObjectType implements GraphQLType {

    private final String name;
    private final String description;
    private final Map<String, InputFieldDefinition> fields;

    /**
     * Creates a new input object type.
     *
     * @param name        the type name
     * @param description the type description
     * @param fields      the input fields
     */
    public InputObjectType(String name, String description, List<InputFieldDefinition> fields) {
        this.name = Objects.requireNonNull(name);
        this.description = description;
        var fieldMap = new LinkedHashMap<String, InputFieldDefinition>();
        if (fields != null) {
            for (var field : fields) {
                fieldMap.put(field.name(), field);
            }
        }
        this.fields = Collections.unmodifiableMap(fieldMap);
    }

    /**
     * Creates a simple input object type.
     *
     * @param name   the type name
     * @param fields the input fields
     * @return a new input object type
     */
    public static InputObjectType of(String name, List<InputFieldDefinition> fields) {
        return new InputObjectType(name, null, fields);
    }

    @Override
    public String name() { return name; }
    public String description() { return description; }
    public Map<String, InputFieldDefinition> fields() { return fields; }

    /**
     * Returns the input field with the given name.
     *
     * @param name the field name
     * @return the input field definition, or null
     */
    public InputFieldDefinition getField(String name) { return fields.get(name); }

    @Override
    public String toString() { return name; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof InputObjectType t && name.equals(t.name));
    }

    @Override
    public int hashCode() { return name.hashCode(); }

    /**
     * Defines an input field on an input object type.
     *
     * @param name            the field name
     * @param description     the field description
     * @param type            the field type (must be an input type)
     * @param defaultValue    the default value
     * @param hasDefaultValue whether a default is provided
     * @since 0.1.0
     */
    public record InputFieldDefinition(String name, String description, GraphQLType type,
                                       Object defaultValue, boolean hasDefaultValue) {

        /**
         * Creates a required input field.
         *
         * @param name the field name
         * @param type the field type
         * @return a new input field definition
         */
        public static InputFieldDefinition of(String name, GraphQLType type) {
            return new InputFieldDefinition(name, null, type, null, false);
        }

        /**
         * Creates an optional input field with a default value.
         *
         * @param name         the field name
         * @param type         the field type
         * @param defaultValue the default value
         * @return a new input field definition
         */
        public static InputFieldDefinition of(String name, GraphQLType type, Object defaultValue) {
            return new InputFieldDefinition(name, null, type, defaultValue, true);
        }
    }
}
