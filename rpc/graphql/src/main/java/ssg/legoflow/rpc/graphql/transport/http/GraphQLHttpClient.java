package ssg.legoflow.rpc.graphql.transport.http;

import ssg.legoflow.rpc.graphql.execution.ExecutionResult;
import ssg.legoflow.rpc.graphql.transport.GraphQLTransport;
import ssg.legoflow.rpc.graphql.transport.JsonCodec;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * HTTP client for executing GraphQL queries against a remote server.
 *
 * <p>Sends queries as POST requests with application/json content type.
 *
 * @since 1.0.0
 */
public final class GraphQLHttpClient implements GraphQLTransport, AutoCloseable {

    private final String endpoint;
    private final HttpClient httpClient;
    private final Map<String, String> headers;

    /**
     * Creates a new GraphQL HTTP client.
     *
     * @param endpoint the GraphQL endpoint URL
     */
    public GraphQLHttpClient(String endpoint) {
        this(endpoint, Map.of());
    }

    /**
     * Creates a new GraphQL HTTP client with custom headers.
     *
     * @param endpoint the GraphQL endpoint URL
     * @param headers  additional HTTP headers
     */
    public GraphQLHttpClient(String endpoint, Map<String, String> headers) {
        this.endpoint = endpoint;
        this.httpClient = HttpClient.newHttpClient();
        this.headers = Map.copyOf(headers);
    }

    @Override
    public ExecutionResult execute(String query, String operationName, Map<String, Object> variables) {
        var body = new LinkedHashMap<String, Object>();
        body.put("query", query);
        if (operationName != null) body.put("operationName", operationName);
        if (variables != null && !variables.isEmpty()) body.put("variables", variables);

        var jsonBody = JsonCodec.encode(body);

        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Accept", "application/graphql+json, application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        for (var entry : headers.entrySet()) {
            requestBuilder.header(entry.getKey(), entry.getValue());
        }

        try {
            var response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            var responseBody = JsonCodec.decodeObject(response.body());
            if (responseBody == null) {
                return ExecutionResult.ofErrors(List.of(
                        ExecutionResult.GraphQLError.of("Invalid JSON response")));
            }

            var data = responseBody.get("data");
            @SuppressWarnings("unchecked")
            var errors = (List<Map<String, Object>>) responseBody.get("errors");

            var graphqlErrors = new ArrayList<ExecutionResult.GraphQLError>();
            if (errors != null) {
                for (var err : errors) {
                    graphqlErrors.add(ExecutionResult.GraphQLError.of(
                            (String) err.get("message")));
                }
            }

            return new ExecutionResult(data, graphqlErrors);

        } catch (IOException | InterruptedException e) {
            return ExecutionResult.ofErrors(List.of(
                    ExecutionResult.GraphQLError.of("HTTP request failed: " + e.getMessage())));
        }
    }

    @Override
    public String name() {
        return "http";
    }

    @Override
    public void close() {
        // HttpClient resources are cleaned up by GC
    }
}
