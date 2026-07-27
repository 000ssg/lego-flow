package ssg.legoflow.rpc.graphql.language;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a parsed GraphQL document (AST root).
 *
 * <p>A document contains one or more operation definitions and zero or more
 * fragment definitions.
 *
 * @param operations the operation definitions
 * @param fragments  the fragment definitions
 * @since 1.0.0
 */
public record Document(List<OperationDefinition> operations,
                       List<FragmentDefinition> fragments) {

    /**
     * Returns a map of fragment definitions by name.
     *
     * @return the fragment map
     */
    public Map<String, FragmentDefinition> fragmentMap() {
        return fragments.stream()
                .collect(Collectors.toMap(FragmentDefinition::name, f -> f, (a, b) -> a));
    }

    /**
     * Returns the single operation, or the operation with the given name.
     *
     * @param operationName the operation name, or null
     * @return the operation definition
     * @throws IllegalArgumentException if the operation is not found
     */
    public OperationDefinition getOperation(String operationName) {
        if (operationName == null) {
            if (operations.size() == 1) {
                return operations.getFirst();
            }
            throw new IllegalArgumentException(
                    "Must provide operation name when document contains multiple operations");
        }
        return operations.stream()
                .filter(op -> operationName.equals(op.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown operation: " + operationName));
    }
}
