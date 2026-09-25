package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.*;
import ssg.legoflow.messaging.stomp.core.transport.InMemoryStompTransport;
import ssg.legoflow.messaging.stomp.transport.StompFrameCodec;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

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
        Thread.startVirtualThread(() -> broker.accept(transports[0]));

        var codec = new StompFrameCodec(transports[1]);
        connectCodec(codec);
        // Subscribe (no receipt header, so no response expected)
        var headers = subHeaders("sub-1", "test/acl", "auto");
        codec.send(new StompFrame(StompCommand.SUBSCRIBE, headers));
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
        Thread.startVirtualThread(() -> broker.accept(transports[0]));

        var codec = new StompFrameCodec(transports[1]);
        connectWithLoginCodec(codec, "reader");
        var sendFrame = StompFrame.withText(StompCommand.SEND, destHeaders("test/acl"), "hello");
        codec.send(sendFrame);

        var response = codec.receive();
        assertThat(response.command()).isEqualTo(StompCommand.ERROR);
    }

    @Test
    void testAclCheckerDeniesSubscribe() throws Exception {
        var transports = InMemoryStompTransport.createPair();
        var config = StompBrokerConfig.defaults()
                .aclChecker((login, dest, action) -> !"subscribe".equals(action));
        broker = new StompBroker(config);
        broker.setHeartbeatCapability(0, 0);
        Thread.startVirtualThread(() -> broker.accept(transports[0]));

        var codec = new StompFrameCodec(transports[1]);
        connectWithLoginCodec(codec, "user");
        var subFrame = new StompFrame(StompCommand.SUBSCRIBE, subHeaders("sub-1", "test/data", "auto"));
        codec.send(subFrame);

        var response = codec.receive();
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
        Thread.startVirtualThread(() -> broker.accept(pubTransports[0]));
        Thread.startVirtualThread(() -> broker.accept(subTransports[0]));

        var pubCodec = new StompFrameCodec(pubTransports[1]);
        var subCodec = new StompFrameCodec(subTransports[1]);
        connectWithLoginCodec(pubCodec, "writer");
        connectWithLoginCodec(subCodec, "reader");

        // Reader tries to send → denied
        var sendFrame = StompFrame.withText(StompCommand.SEND, destHeaders("test/t"), "msg");
        subCodec.send(sendFrame);
        var err = subCodec.receive();
        assertThat(err.command()).isEqualTo(StompCommand.ERROR);

        // Writer tries to subscribe → denied
        var subFrame = new StompFrame(StompCommand.SUBSCRIBE, subHeaders("s1", "test/t", "auto"));
        pubCodec.send(subFrame);
        err = pubCodec.receive();
        assertThat(err.command()).isEqualTo(StompCommand.ERROR);
    }

    private void connectCodec(StompFrameCodec codec) throws Exception {
        codec.send(new StompFrame(StompCommand.CONNECT, new StompHeaders()));
        codec.receive();
    }

    private void connectWithLoginCodec(StompFrameCodec codec, String login) throws Exception {
        var headers = new StompHeaders();
        headers.put(StompHeaders.LOGIN, login);
        codec.send(new StompFrame(StompCommand.CONNECT, headers));
        codec.receive();
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
