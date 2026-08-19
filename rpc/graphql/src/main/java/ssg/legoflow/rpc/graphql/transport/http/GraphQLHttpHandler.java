package ssg.legoflow.rpc.graphql.transport.http;

import ssg.legoflow.http.core.*;
import ssg.legoflow.rpc.graphql.execution.ExecutionEngine;
import ssg.legoflow.rpc.graphql.execution.ExecutionResult;
import ssg.legoflow.rpc.graphql.schema.GraphQLSchema;
import ssg.legoflow.rpc.graphql.transport.JsonCodec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
/**
 * HTTP request handler for GraphQL queries.
 *
 * <p>Handles GraphQL queries over HTTP per the GraphQL over HTTP spec:
 * <ul>
 *   <li>POST with application/json body containing query, variables, operationName</li>
 *   <li>POST with application/graphql body (query string only)</li>
 *   <li>GET with query params: query, variables (JSON), operationName</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class GraphQLHttpHandler implements HttpRequestHandler {

    private final ExecutionEngine engine;
    private final Object context;

    /**
     * Creates a new HTTP handler for GraphQL.
     *
     * @param schema the GraphQL schema
     */
    public GraphQLHttpHandler(GraphQLSchema schema) {
        this(schema, null);
    }

    /**
     * Creates a new HTTP handler with a user context.
     *
     * @param schema  the GraphQL schema
     * @param context the user context passed to resolvers
     */
    public GraphQLHttpHandler(GraphQLSchema schema, Object context) {
        this.engine = new ExecutionEngine(schema);
        this.context = context;
    }

    @Override
    public HttpResponse handle(HttpContext ctx, HttpRequest request) {
        try {
            String query = null;
            String operationName = null;
            Map<String, Object> variables = null;

            if (request.getMethod() == HttpMethod.POST) {
                var contentType = request.getHeaders().get(HttpHeaders.CONTENT_TYPE);
                if (contentType != null && contentType.contains("application/graphql")) {
                    query = request.getBodyAsString();
                } else {
                    // application/json
                    var body = request.getBodyAsString();
                    if (body != null) {
                        var json = JsonCodec.decodeObject(body);
                        if (json != null) {
                            query = (String) json.get("query");
                            operationName = (String) json.get("operationName");
                            var vars = json.get("variables");
                            if (vars instanceof Map<?, ?> m) {
                                @SuppressWarnings("unchecked")
                                var typedVars = (Map<String, Object>) m;
                                variables = typedVars;
                            }
                        }
                    }
                }
            } else if (request.getMethod() == HttpMethod.GET) {
                var params = request.getQueryParams();
                query = params.get("query");
                operationName = params.get("operationName");
                var varsStr = params.get("variables");
                if (varsStr != null) {
                    var decoded = URLDecoder.decode(varsStr, StandardCharsets.UTF_8);
                    var vars = JsonCodec.decodeObject(decoded);
                    if (vars != null) {
                        variables = vars;
                    }
                }
            }

            if (query == null || query.isBlank()) {
                return jsonResponse(HttpStatus.BAD_REQUEST,
                        ExecutionResult.ofErrors(java.util.List.of(
                                ExecutionResult.GraphQLError.of("Missing query parameter"))));
            }

            var result = engine.execute(query, operationName, variables, context);
            return jsonResponse(HttpStatus.OK, result);

        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    ExecutionResult.ofErrors(java.util.List.of(
                            ExecutionResult.GraphQLError.of(
                                    e.getMessage() != null ? e.getMessage() : "Internal error"))));
        }
    }

    private HttpResponse jsonResponse(HttpStatus status, ExecutionResult result) {
        var json = JsonCodec.encode(result.toMap());
        var response = HttpResponse.of(status, json);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/graphql+json; charset=utf-8");
        return response;
    }
}
