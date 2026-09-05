package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.*;
import ssg.legoflow.messaging.stomp.core.transport.InMemoryStompTransport;
import ssg.legoflow.messaging.stomp.core.transport.StompTransport;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for STOMP ACL enforcement.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StompAclTest {

    private StompBroker broker;

    @AfterAll
    void cleanup() {
        if (broker != null) broker.close();
    }

    @Test
    void testNoAclCheckerAllowsAll() throws Exception {
        var transports = InMemoryStompTransport.createPair();
        broker = new StompBroker(StompBrokerConfig.defaults());
        broker.setHeartbeatCapability(0, 0);
        broker.accept(transports[0]);

        connect(transports[1]);
        // Subscribe (no receipt header, so no response expected)
        var headers = subHeaders("sub-1", "test/acl", "auto");
        transports[1].send(new StompFrame(StompCommand.SUBSCRIBE, headers));
        // With ack=auto and no receipt, there's no response. Just check the session has the subscription.
        Thread.sleep(100);
        assertThat(broker.getSessionCount()).isEqualTo(1);
        assertThat(broker.getSubscriptionCount("test/acl")).isEqualTo(1);
    }

    @Test
    void testAclCheckerDeniesSend() throws Exception {
        var transports = InMemoryStompTransport.createPair();
        var config = StompBrokerConfig.defaults()
                .aclChecker((login, dest, action) -> !"send".equals(action));
        broker = new StompBroker(config);
        broker.setHeartbeatCapability(0, 0);
        broker.accept(transports[0]);

        connectWithLogin(transports[1], "reader");
        // Try to send — ACL denies all sends
        var sendFrame = StompFrame.withText(StompCommand.SEND, destHeaders("test/acl"), "hello");
        transports[1].send(sendFrame);

        var response = transports[1].receive();
        assertThat(response.command()).isEqualTo(StompCommand.ERROR);
    }

    @Test
    void testAclCheckerDeniesSubscribe() throws Exception {
        var transports = InMemoryStompTransport.createPair();
        var config = StompBrokerConfig.defaults()
                .aclChecker((login, dest, action) -> !"subscribe".equals(action));
        broker = new StompBroker(config);
        broker.setHeartbeatCapability(0, 0);
        broker.accept(transports[0]);

        connectWithLogin(transports[1], "user");
        // Try to subscribe — ACL denies all subscribes
        var subFrame = new StompFrame(StompCommand.SUBSCRIBE, subHeaders("sub-1", "test/data", "auto"));
        transports[1].send(subFrame);

        var response = transports[1].receive();
        assertThat(response.command()).isEqualTo(StompCommand.ERROR);
    }

    @Test
    void testAclCheckerAllowsWriteButNotRead() throws Exception {
        var pubTransports = InMemoryStompTransport.createPair();
        var subTransports = InMemoryStompTransport.createPair();

        var config = StompBrokerConfig.defaults()
                .authenticator((l, p) -> true)
                .aclChecker((login, dest, action) -> {
                    if ("writer".equals(login)) return "send".equals(action);
                    if ("reader".equals(login)) return "subscribe".equals(action);
                    return false;
                });
        broker = new StompBroker(config);
        broker.setHeartbeatCapability(0, 0);
        broker.accept(pubTransports[0]);
        broker.accept(subTransports[0]);

        connectWithLogin(pubTransports[1], "writer");
        connectWithLogin(subTransports[1], "reader");

        // Reader tries to subscribe → denied (reader can only subscribe, but test says reader CAN subscribe)
        // Actually: reader CAN subscribe. So let's test reader trying to send.
        // Reader tries to send → denied (reader's ACL: subscribe only)
        var sendFrame = StompFrame.withText(StompCommand.SEND, destHeaders("test/t"), "msg");
        subTransports[1].send(sendFrame);
        var err = subTransports[1].receive();
        assertThat(err.command()).isEqualTo(StompCommand.ERROR);

        // Writer tries to subscribe → denied (writer's ACL: send only)
        var subFrame = new StompFrame(StompCommand.SUBSCRIBE, subHeaders("s1", "test/t", "auto"));
        pubTransports[1].send(subFrame);
        err = pubTransports[1].receive();
        assertThat(err.command()).isEqualTo(StompCommand.ERROR);
    }

    private void connect(StompTransport transport) throws Exception {
        transport.send(new StompFrame(StompCommand.CONNECT, new StompHeaders()));
        transport.receive();
    }

    private void connectWithLogin(StompTransport transport, String login) throws Exception {
        var headers = new StompHeaders();
        headers.put(StompHeaders.LOGIN, login);
        transport.send(new StompFrame(StompCommand.CONNECT, headers));
        transport.receive();
    }

    private CountDownLatch subscribe(StompTransport transport, String dest, String id) throws Exception {
        var latch = new CountDownLatch(1);
        var headers = subHeaders(id, dest, "auto");
        transport.send(new StompFrame(StompCommand.SUBSCRIBE, headers));
        transport.receive(); // RECEIPT
        return latch;
    }

    private StompHeaders destHeaders(String dest) {
        var h = new StompHeaders();
        h.put(StompHeaders.DESTINATION, dest);
        return h;
    }

    private StompHeaders subHeaders(String id, String dest, String ack) {
        var h = new StompHeaders();
        h.put(StompHeaders.ID, id);
        h.put(StompHeaders.DESTINATION, dest);
        h.put(StompHeaders.ACK, ack);
        return h;
    }
}
