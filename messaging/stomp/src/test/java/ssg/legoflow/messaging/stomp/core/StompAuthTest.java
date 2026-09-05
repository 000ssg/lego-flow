package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.*;
import ssg.legoflow.messaging.stomp.core.transport.InMemoryStompTransport;
import ssg.legoflow.messaging.stomp.core.transport.StompTransport;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for STOMP authentication.
 */
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
        broker.accept(transports[0]);

        // Connect with any credentials
        var headers = new StompHeaders();
        headers.put(StompHeaders.LOGIN, "guest");
        headers.put(StompHeaders.PASSCODE, "secret");
        transports[1].send(new StompFrame(StompCommand.CONNECT, headers));

        var response = transports[1].receive();
        assertThat(response.command()).isEqualTo(StompCommand.CONNECTED);
    }

    @Test
    void testAuthenticatorValidLogin() throws Exception {
        var transports = InMemoryStompTransport.createPair();
        var config = StompBrokerConfig.defaults()
                .authenticator((login, passcode) -> "admin".equals(login) && "pass".equals(passcode));
        broker = new StompBroker(config);
        broker.setHeartbeatCapability(0, 0);
        broker.accept(transports[0]);

        var headers = new StompHeaders();
        headers.put(StompHeaders.LOGIN, "admin");
        headers.put(StompHeaders.PASSCODE, "pass");
        transports[1].send(new StompFrame(StompCommand.CONNECT, headers));

        var response = transports[1].receive();
        assertThat(response.command()).isEqualTo(StompCommand.CONNECTED);
    }

    @Test
    void testAuthenticatorRejectsBadLogin() throws Exception {
        var transports = InMemoryStompTransport.createPair();
        var config = StompBrokerConfig.defaults()
                .authenticator((login, passcode) -> "admin".equals(login) && "pass".equals(passcode));
        broker = new StompBroker(config);
        broker.setHeartbeatCapability(0, 0);
        broker.accept(transports[0]);

        var headers = new StompHeaders();
        headers.put(StompHeaders.LOGIN, "admin");
        headers.put(StompHeaders.PASSCODE, "wrong");
        transports[1].send(new StompFrame(StompCommand.CONNECT, headers));

        var response = transports[1].receive();
        assertThat(response.command()).isEqualTo(StompCommand.ERROR);
    }

    @Test
    void testAuthenticatorRejectsNullLogin() throws Exception {
        var transports = InMemoryStompTransport.createPair();
        var config = StompBrokerConfig.defaults()
                .authenticator((login, passcode) -> login != null && "admin".equals(login));
        broker = new StompBroker(config);
        broker.setHeartbeatCapability(0, 0);
        broker.accept(transports[0]);

        var headers = new StompHeaders();
        headers.put(StompHeaders.LOGIN, "guest");
        transports[1].send(new StompFrame(StompCommand.CONNECT, headers));

        var response = transports[1].receive();
        assertThat(response.command()).isEqualTo(StompCommand.ERROR);
    }
}
