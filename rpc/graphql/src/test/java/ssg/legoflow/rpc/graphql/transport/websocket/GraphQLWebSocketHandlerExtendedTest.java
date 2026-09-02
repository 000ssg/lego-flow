package ssg.legoflow.rpc.graphql.transport.websocket;

import ssg.legoflow.http.websocket.WebSocketFrame;
import ssg.legoflow.http.websocket.WebSocketSession;
import ssg.legoflow.rpc.graphql.execution.SubscriptionPublisher;
import ssg.legoflow.rpc.graphql.schema.GraphQLSchema;
import ssg.legoflow.rpc.graphql.schema.ObjectType;
import ssg.legoflow.rpc.graphql.schema.FieldDefinition;
import ssg.legoflow.rpc.graphql.schema.ScalarType;
import ssg.legoflow.rpc.graphql.transport.JsonCodec;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class GraphQLWebSocketHandlerExtendedTest {

    private GraphQLSchema schema;
    private GraphQLWebSocketHandler handler;

    @BeforeEach
    void setup() {
        var query = ObjectType.of("Query", List.of(
                FieldDefinition.of("hello", ScalarType.STRING),
                FieldDefinition.of("greeting", ScalarType.STRING)));
        schema = GraphQLSchema.newSchema().query(query).build();
        handler = new GraphQLWebSocketHandler(schema);
    }

    private static TestSession createSession() {
        return new TestSession("test-session");
    }

    private static class TestSession extends WebSocketSession {
        final List<String> sentMessages = Collections.synchronizedList(new ArrayList<>());

        TestSession(String id) { super(id); }

        @Override
        public void handleFrame(WebSocketFrame frame) {
            var text = frame.getPayloadText();
            if (text != null) sentMessages.add(text);
        }

        List<String> getSentTextMessages() { return sentMessages; }
    }

    @Test void testConnectionInitSendsAck() {
        var session = createSession();
        handler.handleMessage(session, JsonCodec.encode(Map.of("type", "connection_init")));
        assertThat(session.getSentTextMessages()).hasSize(1);
        var ackMsg = JsonCodec.decodeObject(session.getSentTextMessages().get(0));
        assertThat(ackMsg).containsEntry("type", "connection_ack");
    }

    @Test void testConnectionInitWithData() {
        var session = createSession();
        var initPayload = new LinkedHashMap<String, Object>();
        initPayload.put("type", "connection_init");
        initPayload.put("payload", Map.of("authToken", "secret"));
        handler.handleMessage(session, JsonCodec.encode(initPayload));
        assertThat(session.getSentTextMessages()).hasSize(1);
    }

    @Test void testSubscribeWithoutInitReturnsError() {
        var session = createSession();
        var subscribeMsg = new LinkedHashMap<String, Object>();
        subscribeMsg.put("id", "1");
        subscribeMsg.put("type", "subscribe");
        subscribeMsg.put("payload", Map.of("query", "{ hello }"));
        handler.handleMessage(session, JsonCodec.encode(subscribeMsg));
        assertThat(session.getSentTextMessages()).hasSize(1);
        var errorMsg = JsonCodec.decodeObject(session.getSentTextMessages().get(0));
        assertThat(errorMsg).containsEntry("type", "error");
    }

    @Test void testSubscribeWithValidQuerySendsNextAndComplete() {
        var session = createSession();
        handler.handleMessage(session, JsonCodec.encode(Map.of("type", "connection_init")));
        var subscribeMsg = new LinkedHashMap<String, Object>();
        subscribeMsg.put("id", "sub-1");
        subscribeMsg.put("type", "subscribe");
        subscribeMsg.put("payload", Map.of("query", "{ hello }"));
        handler.handleMessage(session, JsonCodec.encode(subscribeMsg));
        var messages = session.getSentTextMessages();
        assertThat(messages).hasSize(3);
        var nextMsg = JsonCodec.decodeObject(messages.get(1));
        assertThat(nextMsg).containsEntry("type", "next");
        assertThat(nextMsg).containsEntry("id", "sub-1");
        var completeMsg = JsonCodec.decodeObject(messages.get(2));
        assertThat(completeMsg).containsEntry("type", "complete");
    }

    @Test void testSubscribeWithQueryErrorSendsErrorMessage() {
        var session = createSession();
        handler.handleMessage(session, JsonCodec.encode(Map.of("type", "connection_init")));
        var subscribeMsg = new LinkedHashMap<String, Object>();
        subscribeMsg.put("id", "sub-2");
        subscribeMsg.put("type", "subscribe");
        subscribeMsg.put("payload", Map.of("query", "{ nonExistentField }"));
        handler.handleMessage(session, JsonCodec.encode(subscribeMsg));
        var messages = session.getSentTextMessages();
        assertThat(messages).hasSize(2);
        var errorMsg = JsonCodec.decodeObject(messages.get(1));
        assertThat(errorMsg).containsEntry("type", "error");
        assertThat(errorMsg).containsEntry("id", "sub-2");
    }

    @Test void testSubscribeWithOperationName() {
        var session = createSession();
        handler.handleMessage(session, JsonCodec.encode(Map.of("type", "connection_init")));
        var subscribeMsg = new LinkedHashMap<String, Object>();
        subscribeMsg.put("id", "sub-3");
        subscribeMsg.put("type", "subscribe");
        var payload = new LinkedHashMap<String, Object>();
        payload.put("query", "query MyQuery { hello }");
        payload.put("operationName", "MyQuery");
        subscribeMsg.put("payload", payload);
        handler.handleMessage(session, JsonCodec.encode(subscribeMsg));
        assertThat(session.getSentTextMessages()).hasSize(3);
    }

    @Test void testSubscribeWithVariables() {
        var session = createSession();
        handler.handleMessage(session, JsonCodec.encode(Map.of("type", "connection_init")));
        var subscribeMsg = new LinkedHashMap<String, Object>();
        subscribeMsg.put("id", "sub-4");
        subscribeMsg.put("type", "subscribe");
        var payload = new LinkedHashMap<String, Object>();
        payload.put("query", "{ hello }");
        payload.put("variables", Map.of("name", "test"));
        subscribeMsg.put("payload", payload);
        handler.handleMessage(session, JsonCodec.encode(subscribeMsg));
        assertThat(session.getSentTextMessages()).hasSize(3);
    }

    @Test void testSubscribeWithNullIdIsIgnored() {
        var session = createSession();
        handler.handleMessage(session, JsonCodec.encode(Map.of("type", "connection_init")));
        var subscribeMsg = new LinkedHashMap<String, Object>();
        subscribeMsg.put("type", "subscribe");
        subscribeMsg.put("payload", Map.of("query", "{ hello }"));
        handler.handleMessage(session, JsonCodec.encode(subscribeMsg));
        assertThat(session.getSentTextMessages()).hasSize(1);
    }

    @Test void testSubscribeWithNullPayloadIsIgnored() {
        var session = createSession();
        handler.handleMessage(session, JsonCodec.encode(Map.of("type", "connection_init")));
        var subscribeMsg = new LinkedHashMap<String, Object>();
        subscribeMsg.put("id", "sub-5");
        subscribeMsg.put("type", "subscribe");
        handler.handleMessage(session, JsonCodec.encode(subscribeMsg));
        assertThat(session.getSentTextMessages()).hasSize(1);
    }

    @Test void testCompleteWithNullIdDoesNothing() {
        var session = createSession();
        handler.handleMessage(session, JsonCodec.encode(Map.of("type", "complete")));
    }

    @Test void testPingSendsPong() {
        var session = createSession();
        handler.handleMessage(session, JsonCodec.encode(Map.of("type", "ping")));
        assertThat(session.getSentTextMessages()).hasSize(1);
        var pongMsg = JsonCodec.decodeObject(session.getSentTextMessages().get(0));
        assertThat(pongMsg).containsEntry("type", "pong");
    }

    @Test void testUnknownMessageTypeIsIgnored() {
        var session = createSession();
        handler.handleMessage(session, JsonCodec.encode(Map.of("type", "unknown_type")));
        assertThat(session.getSentTextMessages()).isEmpty();
    }

    @Test void testMessageWithNullTypeIsIgnored() {
        var session = createSession();
        handler.handleMessage(session, JsonCodec.encode(new LinkedHashMap<>()));
        assertThat(session.getSentTextMessages()).isEmpty();
    }

    @Test void testInvalidJsonThrowsException() {
        var session = createSession();
        assertThatThrownBy(() -> handler.handleMessage(session, "not valid json {{{"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void testHandleCloseWithNullSessionDoesNotThrow() {
        handler.handleClose(null);
    }

    @Test void testHandleCloseClearsSubscriptions() {
        var session = createSession();
        var publisher = new SubscriptionPublisher<String>();
        handler.registerSubscription(session, "sub-7", publisher);
        assertThat(handler.activeSubscriptionCount()).isEqualTo(1);
        handler.handleClose(session);
        assertThat(handler.activeSubscriptionCount()).isZero();
    }

    @Test void testRegisterSubscription() {
        var session = createSession();
        var publisher = new SubscriptionPublisher<String>();
        handler.registerSubscription(session, "sub-8", publisher);
        assertThat(handler.activeSubscriptionCount()).isEqualTo(1);
        publisher.publish("test-event");
    }

    @Test void testMultipleSubscriptions() {
        var session = createSession();
        handler.handleMessage(session, JsonCodec.encode(Map.of("type", "connection_init")));
        for (int i = 0; i < 5; i++) {
            var subscribeMsg = new LinkedHashMap<String, Object>();
            subscribeMsg.put("id", String.valueOf(i));
            subscribeMsg.put("type", "subscribe");
            subscribeMsg.put("payload", Map.of("query", "{ hello }"));
            handler.handleMessage(session, JsonCodec.encode(subscribeMsg));
        }
        assertThat(session.getSentTextMessages()).hasSize(1 + 5 * 2);
    }

    @Test void testSessionClosedStopsSending() {
        var session = createSession();
        session.close();
        handler.handleMessage(session, JsonCodec.encode(Map.of("type", "connection_init")));
        assertThat(session.getSentTextMessages()).isEmpty();
    }
}
