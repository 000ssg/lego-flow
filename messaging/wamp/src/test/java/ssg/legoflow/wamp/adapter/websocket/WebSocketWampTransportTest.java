package ssg.legoflow.wamp.adapter.websocket;

import ssg.legoflow.http.websocket.WebSocketFrame;
import ssg.legoflow.http.websocket.WebSocketSession;
import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampSerializer;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class WebSocketWampTransportTest {

    private final WampSerializer serializer = new WampSerializer();

    @Test
    void testSendSerializesToTextFrame() {
        var session = new WebSocketSession("test-1");
        var transport = new WebSocketWampTransport(session, serializer);

        var sentFrames = new ArrayList<WebSocketFrame>();
        transport.onFrame(sentFrames::add);

        var msg = new WampMessage.Hello("realm1", Map.of());
        transport.send(msg);

        assertThat(sentFrames).hasSize(1);
        var frame = sentFrames.getFirst();
        var json = frame.getPayloadText();
        assertThat(json).startsWith("[1,");
        assertThat(json).contains("\"realm1\"");
    }

    @Test
    void testReceiveDeserializesFromTextFrame() throws InterruptedException {
        var session = new WebSocketSession("test-2");
        var transport = new WebSocketWampTransport(session, serializer);

        var json = serializer.serialize(new WampMessage.Welcome(42L, Map.of()));
        var frame = WebSocketFrame.text(json);

        var latch = new CountDownLatch(1);
        Thread.startVirtualThread(() -> {
            var msg = transport.receive();
            assertThat(msg).isInstanceOf(WampMessage.Welcome.class);
            assertThat(((WampMessage.Welcome) msg).sessionId()).isEqualTo(42L);
            latch.countDown();
        });

        transport.injectFrame(frame);
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void testTryReceiveReturnsNullWhenEmpty() {
        var session = new WebSocketSession("test-3");
        var transport = new WebSocketWampTransport(session, serializer);

        assertThat(transport.tryReceive()).isNull();
    }

    @Test
    void testCloseStopsTransport() {
        var session = new WebSocketSession("test-4");
        var transport = new WebSocketWampTransport(session, serializer);

        assertThat(transport.isOpen()).isTrue();

        transport.close();

        assertThat(transport.isOpen()).isFalse();
        assertThatThrownBy(() -> transport.send(new WampMessage.Hello("realm", Map.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testRoundTripSerialization() {
        var session1 = new WebSocketSession("sender");
        var session2 = new WebSocketSession("receiver");
        var sender = new WebSocketWampTransport(session1, serializer);
        var receiver = new WebSocketWampTransport(session2, serializer);

        sender.onFrame(receiver::injectFrame);

        var original = new WampMessage.Call(7L, Map.of(), "com.test.add", List.of(3, 5));
        sender.send(original);

        var received = receiver.tryReceive();
        assertThat(received).isInstanceOf(WampMessage.Call.class);
        var call = (WampMessage.Call) received;
        assertThat(call.requestId()).isEqualTo(7L);
        assertThat(call.procedure()).isEqualTo("com.test.add");
        assertThat(call.args()).hasSize(2);
    }

    @Test
    void testBidirectionalCommunication() {
        var sessionA = new WebSocketSession("a");
        var sessionB = new WebSocketSession("b");
        var transportA = new WebSocketWampTransport(sessionA, serializer);
        var transportB = new WebSocketWampTransport(sessionB, serializer);

        transportA.onFrame(transportB::injectFrame);
        transportB.onFrame(transportA::injectFrame);

        transportA.send(new WampMessage.Hello("realm1", Map.of()));
        var received = transportB.tryReceive();
        assertThat(received).isInstanceOf(WampMessage.Hello.class);

        transportB.send(new WampMessage.Welcome(1L, Map.of()));
        var reply = transportA.tryReceive();
        assertThat(reply).isInstanceOf(WampMessage.Welcome.class);
    }

    @Test
    void testGetSessionReturnsUnderlyingSession() {
        var session = new WebSocketSession("test-session");
        var transport = new WebSocketWampTransport(session, serializer);

        assertThat(transport.getSession()).isSameAs(session);
        assertThat(transport.getSession().getId()).isEqualTo("test-session");
    }

    @Test
    void testGetSerializerReturnsSerializer() {
        var session = new WebSocketSession("test-ser");
        var transport = new WebSocketWampTransport(session, serializer);

        assertThat(transport.getSerializer()).isSameAs(serializer);
    }

    @Test
    void testAllWampMessageTypesRoundTrip() {
        var sessionA = new WebSocketSession("a");
        var sessionB = new WebSocketSession("b");
        var a = new WebSocketWampTransport(sessionA, serializer);
        var b = new WebSocketWampTransport(sessionB, serializer);
        a.onFrame(b::injectFrame);

        List<WampMessage> messages = List.of(
                new WampMessage.Hello("realm", Map.of()),
                new WampMessage.Welcome(1L, Map.of()),
                new WampMessage.Abort(Map.of(), "wamp.error.test"),
                new WampMessage.Goodbye(Map.of(), "wamp.close.normal"),
                new WampMessage.Subscribe(1L, Map.of(), "topic"),
                new WampMessage.Subscribed(1L, 2L),
                new WampMessage.Publish(1L, Map.of(), "topic", List.of("data")),
                new WampMessage.Published(1L, 2L),
                new WampMessage.Register(1L, Map.of(), "proc"),
                new WampMessage.Registered(1L, 2L),
                new WampMessage.Call(1L, Map.of(), "proc", List.of("arg")),
                new WampMessage.Result(1L, Map.of(), List.of("res")),
                new WampMessage.Invocation(1L, 2L, Map.of(), List.of("arg")),
                new WampMessage.Yield(1L, Map.of(), List.of("res"))
        );

        for (var msg : messages) {
            a.send(msg);
            var received = b.tryReceive();
            assertThat(received).isNotNull();
            assertThat(received.type()).isEqualTo(msg.type());
        }
    }
}
