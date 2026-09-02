package ssg.legoflow.rpc.graphql.language;

import ssg.legoflow.rpc.graphql.schema.Directive;
import java.util.List;
/**
 * Represents an operation definition (query, mutation, or subscription).
 *
 * @param operationType     the operation type
 * @param name              the operation name, or null for anonymous
 * @param variableDefinitions the variable definitions
 * @param directives        the directives on this operation
 * @param selectionSet      the operation's selection set
 * @since 0.1.0
 */
public record OperationDefinition(OperationType operationType,
                                  String name,
                                  List<VariableDefinition> variableDefinitions,
                                  List<Directive.DirectiveUsage> directives,
                                  SelectionSet selectionSet) {

    /**
     * The type of GraphQL operation.
     */
    public enum OperationType {
        QUERY, MUTATION, SUBSCRIPTION
    }

    @Override
    public String toString() {
        var sb = new StringBuilder(operationType.name().toLowerCase());
        if (name != null) sb.append(" ").append(name);
        sb.append(" { ... }");
        return sb.toString();
    }
}
