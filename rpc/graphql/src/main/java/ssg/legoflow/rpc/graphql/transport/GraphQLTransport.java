package ssg.legoflow.rpc.graphql.transport;

import ssg.legoflow.rpc.graphql.execution.ExecutionResult;
import java.util.Map;
/**
 * SPI interface for GraphQL transport bindings.
 *
 * <p>Transports handle the mechanics of receiving GraphQL requests
 * and sending results, independent of the execution engine.
 *
 * @since 0.1.0
 */
public interface GraphQLTransport {

    /**
     * Executes a GraphQL request.
     *
     * @param query         the query string
     * @param operationName the operation name, or null
     * @param variables     the variable values, or null
     * @return the execution result
     */
    ExecutionResult execute(String query, String operationName, Map<String, Object> variables);

    /**
     * Returns the transport name (e.g., "http", "websocket").
     *
     * @return the transport name
     */
    String name();
}
