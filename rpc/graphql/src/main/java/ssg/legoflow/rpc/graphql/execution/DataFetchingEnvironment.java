package ssg.legoflow.rpc.graphql.execution;

import ssg.legoflow.rpc.graphql.schema.FieldDefinition;
import ssg.legoflow.rpc.graphql.schema.GraphQLSchema;
import ssg.legoflow.rpc.graphql.schema.GraphQLType;
import java.util.Map;
/**
 * Environment passed to data fetchers (resolvers) during field resolution.
 *
 * <p>Provides access to the source object (parent value), field arguments,
 * the execution context, and schema information.
 *
 * @since 0.1.0
 */
public final class DataFetchingEnvironment {

    private final Object source;
    private final Map<String, Object> arguments;
    private final Map<String, Object> variables;
    private final Object context;
    private final FieldDefinition fieldDefinition;
    private final GraphQLType parentType;
    private final GraphQLSchema schema;

    /**
     * Creates a new data fetching environment.
     *
     * @param source          the source (parent) object
     * @param arguments       the field arguments
     * @param variables       the query variables
     * @param context         the user context object
     * @param fieldDefinition the field definition being resolved
     * @param parentType      the parent type
     * @param schema          the schema
     */
    public DataFetchingEnvironment(Object source, Map<String, Object> arguments,
                                   Map<String, Object> variables, Object context,
                                   FieldDefinition fieldDefinition,
                                   GraphQLType parentType, GraphQLSchema schema) {
        this.source = source;
        this.arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
        this.variables = variables != null ? Map.copyOf(variables) : Map.of();
        this.context = context;
        this.fieldDefinition = fieldDefinition;
        this.parentType = parentType;
        this.schema = schema;
    }

    /**
     * Returns the source (parent) object.
     *
     * @param <T> the expected type
     * @return the source object
     */
    @SuppressWarnings("unchecked")
    public <T> T getSource() { return (T) source; }

    /**
     * Returns the field arguments.
     *
     * @return the arguments map
     */
    public Map<String, Object> getArguments() { return arguments; }

    /**
     * Returns a specific argument value.
     *
     * @param name the argument name
     * @param <T>  the expected type
     * @return the argument value, or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getArgument(String name) { return (T) arguments.get(name); }

    /**
     * Returns the query variables.
     *
     * @return the variables map
     */
    public Map<String, Object> getVariables() { return variables; }

    /**
     * Returns the user context object.
     *
     * @param <T> the expected type
     * @return the context
     */
    @SuppressWarnings("unchecked")
    public <T> T getContext() { return (T) context; }

    /**
     * Returns the field definition being resolved.
     *
     * @return the field definition
     */
    public FieldDefinition getFieldDefinition() { return fieldDefinition; }

    /**
     * Returns the parent type.
     *
     * @return the parent type
     */
    public GraphQLType getParentType() { return parentType; }

    /**
     * Returns the schema.
     *
     * @return the schema
     */
    public GraphQLSchema getSchema() { return schema; }
}
