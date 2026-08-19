package ssg.legoflow.rpc.graphql.transport.websocket;

import ssg.legoflow.rpc.graphql.schema.GraphQLSchema;
import ssg.legoflow.rpc.graphql.schema.ObjectType;
import ssg.legoflow.rpc.graphql.schema.FieldDefinition;
import ssg.legoflow.rpc.graphql.schema.ScalarType;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class GraphQLWebSocketHandlerTest {

    private GraphQLSchema schema;
    private GraphQLWebSocketHandler handler;

    @BeforeEach
    void setup() {
        var query = ObjectType.of("Query", List.of(
                FieldDefinition.of("hello", ScalarType.STRING)));
        schema = GraphQLSchema.newSchema().query(query).build();
        handler = new GraphQLWebSocketHandler(schema);
    }

    @Test void testConstructorWithSchema() {
        assertThat(handler).isNotNull();
    }

    @Test void testActiveSubscriptionCountInitiallyZero() {
        assertThat(handler.activeSubscriptionCount()).isZero();
    }

    @Test void testHandleCloseDoesNotThrow() {
        // handleClose with null session should not throw
        handler.handleClose(null);
    }
}
