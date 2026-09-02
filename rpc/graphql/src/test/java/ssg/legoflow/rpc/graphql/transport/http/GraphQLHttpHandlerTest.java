package ssg.legoflow.rpc.graphql.transport.http;

import ssg.legoflow.http.core.*;
import ssg.legoflow.rpc.graphql.schema.GraphQLSchema;
import ssg.legoflow.rpc.graphql.schema.ObjectType;
import ssg.legoflow.rpc.graphql.schema.FieldDefinition;
import ssg.legoflow.rpc.graphql.schema.ScalarType;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class GraphQLHttpHandlerTest {

    private GraphQLSchema schema;
    private GraphQLHttpHandler handler;

    @BeforeEach
    void setup() {
        var query = ObjectType.of("Query", List.of(
                FieldDefinition.of("hello", ScalarType.STRING),
                FieldDefinition.of("id", ScalarType.ID)));
        schema = GraphQLSchema.newSchema().query(query).build();
        handler = new GraphQLHttpHandler(schema);
    }

    @Test void testConstructorWithSchema() {
        assertThat(handler).isNotNull();
    }

    @Test void testConstructorWithContext() {
        GraphQLHttpHandler h = new GraphQLHttpHandler(schema, new Object());
        assertThat(h).isNotNull();
    }

    @Test void testHandlePostRequest() throws Exception {
        HttpRequest request = HttpRequest.of(HttpMethod.POST, "/graphql");
        HttpResponse response = handler.handle(null, request);
        assertThat(response).isNotNull();
    }

    @Test void testHandleGetRequest() throws Exception {
        HttpRequest request = HttpRequest.of(HttpMethod.GET, "/");
        HttpResponse response = handler.handle(null, request);
        assertThat(response).isNotNull();
    }

    @Test void testHandleOptionsRequest() throws Exception {
        HttpRequest request = HttpRequest.of(HttpMethod.OPTIONS, "/");
        HttpResponse response = handler.handle(null, request);
        assertThat(response).isNotNull();
    }

    @Test void testHandleWithNullHttpContextDoesNotThrow() throws Exception {
        HttpRequest request = HttpRequest.of(HttpMethod.POST, "/graphql");
        handler.handle(null, request);
    }
}
