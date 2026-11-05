package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.*;
import ssg.legoflow.messaging.stomp.core.transport.InMemoryStompTransport;
import ssg.legoflow.messaging.stomp.transport.StompFrameCodec;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StompAuthTest {

    private StompBroker broker;

    @AfterAll
    void cleanup() {
        if (broker != null) broker.close();
    }

    @Test
    void testNoAuthenticatorAllowsAnyLogin() throws Exception {
        var transports = InMemoryStompTransport.createPair();
        var config = StompBrokerConfig.defaults();
        broker = new StompBroker(config);
        broker.setHeartbeatCapability(0, 0);
        Thread.startVirtualThread(() -> broker.accept(transports[0]));

        var codec = new StompFrameCodec(transports[1]);
        codec.send(new StompFrame(StompCommand.CONNECT, loginHeaders("guest", "secret")));

        var response = codec.receive();
        assertThat(response.command()).isEqualTo(StompCommand.CONNECTED);
    }

    @Test
    void testAuthenticatorValidLogin() throws Exception {
        var transports = InMemoryStompTransport.createPair();
        var config = StompBrokerConfig.defaults()
                .authenticator((login, passcode) -> "admin".equals(login) && "pass".equals(passcode));
        broker = new StompBroker(config);
        broker.setHeartbeatCapability(0, 0);
        Thread.startVirtualThread(() -> broker.accept(transports[0]));

        var codec = new StompFrameCodec(transports[1]);
        codec.send(new StompFrame(StompCommand.CONNECT, loginHeaders("admin", "pass")));

        var response = codec.receive();
        assertThat(response.command()).isEqualTo(StompCommand.CONNECTED);
    }

    @Test
    void testAuthenticatorRejectsBadLogin() throws Exception {
        var transports = InMemoryStompTransport.createPair();
        var config = StompBrokerConfig.defaults()
                .authenticator((login, passcode) -> "admin".equals(login) && "pass".equals(passcode));
        broker = new StompBroker(config);
        broker.setHeartbeatCapability(0, 0);
        Thread.startVirtualThread(() -> broker.accept(transports[0]));

        var codec = new StompFrameCodec(transports[1]);
        codec.send(new StompFrame(StompCommand.CONNECT, loginHeaders("admin", "wrong")));

        var response = codec.receive();
        assertThat(response.command()).isEqualTo(StompCommand.ERROR);
    }

    @Test
    void testAuthenticatorRejectsNullLogin() throws Exception {
        var transports = InMemoryStompTransport.createPair();
        var config = StompBrokerConfig.defaults()
                .authenticator((login, passcode) -> login != null && "admin".equals(login));
        broker = new StompBroker(config);
        broker.setHeartbeatCapability(0, 0);
        Thread.startVirtualThread(() -> broker.accept(transports[0]));

        var codec = new StompFrameCodec(transports[1]);
        codec.send(new StompFrame(StompCommand.CONNECT, loginHeaders("guest", null)));

        var response = codec.receive();
        assertThat(response.command()).isEqualTo(StompCommand.ERROR);
    }

    private StompHeaders loginHeaders(String login, String passcode) {
        var h = new StompHeaders();
        if (login != null) h.put(StompHeaders.LOGIN, login);
        if (passcode != null) h.put(StompHeaders.PASSCODE, passcode);
        return h;
    }
}
