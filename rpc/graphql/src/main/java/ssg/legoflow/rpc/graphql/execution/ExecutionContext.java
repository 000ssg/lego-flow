package ssg.legoflow.rpc.graphql.execution;

import ssg.legoflow.rpc.graphql.language.FragmentDefinition;
import ssg.legoflow.rpc.graphql.language.OperationDefinition;
import ssg.legoflow.rpc.graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Per-execution context that holds state during query execution.
 *
 * <p>Contains the schema, operation, variables, fragment definitions,
 * user context, and accumulated errors.
 *
 * @since 1.0.0
 */
public final class ExecutionContext {

    private final GraphQLSchema schema;
    private final OperationDefinition operation;
    private final Map<String, Object> variables;
    private final Map<String, FragmentDefinition> fragments;
    private final Object userContext;
    private final List<ExecutionResult.GraphQLError> errors = Collections.synchronizedList(new ArrayList<>());

    /**
     * Creates a new execution context.
     *
     * @param schema      the schema
     * @param operation   the operation being executed
     * @param variables   the variable values
     * @param fragments   the fragment definitions
     * @param userContext the user context object
     */
    public ExecutionContext(GraphQLSchema schema, OperationDefinition operation,
                           Map<String, Object> variables,
                           Map<String, FragmentDefinition> fragments,
                           Object userContext) {
        this.schema = schema;
        this.operation = operation;
        this.variables = variables != null ? Map.copyOf(variables) : Map.of();
        this.fragments = fragments != null ? Map.copyOf(fragments) : Map.of();
        this.userContext = userContext;
    }

    public GraphQLSchema schema() { return schema; }
    public OperationDefinition operation() { return operation; }
    public Map<String, Object> variables() { return variables; }
    public Map<String, FragmentDefinition> fragments() { return fragments; }
    public Object userContext() { return userContext; }

    /**
     * Returns the fragment definition with the given name.
     *
     * @param name the fragment name
     * @return the fragment definition, or null
     */
    public FragmentDefinition getFragment(String name) {
        return fragments.get(name);
    }

    /**
     * Adds an error to the execution.
     *
     * @param error the error
     */
    public void addError(ExecutionResult.GraphQLError error) {
        errors.add(error);
    }

    /**
     * Returns the accumulated errors.
     *
     * @return the errors
     */
    public List<ExecutionResult.GraphQLError> errors() {
        return List.copyOf(errors);
    }
}
