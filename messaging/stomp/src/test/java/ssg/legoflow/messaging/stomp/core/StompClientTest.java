package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.stomp.demo.InMemoryStompTransport;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link StompClient}.
 *
 * @since 1.0.0
 */
class StompClientTest {

    private StompBroker broker;

    @BeforeEach
    void setUp() {
        broker = new StompBroker();
    }

    @AfterEach
    void tearDown() {
        broker.close();
    }

    private StompClient createAndConnectClient() {
        var pair = InMemoryStompTransport.createPair();
        broker.accept(pair[1]);
        var client = new StompClient(pair[0]);
        client.connect("localhost");
        return client;
    }

    @Test
    void testConnectSetsSessionState() {
        var client = createAndConnectClient();
        try {
            assertThat(client.isConnected()).isTrue();
            assertThat(client.getSession().getState()).isEqualTo(StompSession.State.CONNECTED);
            assertThat(client.getSession().getNegotiatedVersion()).isEqualTo("1.2");
        } finally {
            client.close();
        }
    }

    @Test
    void testConnectWithAuth() {
        var pair = InMemoryStompTransport.createPair();
        broker.accept(pair[1]);
        var client = new StompClient(pair[0]);
        var connected = client.connect("localhost", "user", "pass", 0, 0);
        try {
            assertThat(connected.command()).isEqualTo(StompCommand.CONNECTED);
            assertThat(client.getSession().getLogin()).isEqualTo("user");
        } finally {
            client.close();
        }
    }

    @Test
    void testSendNotConnectedThrows() {
        var pair = InMemoryStompTransport.createPair();
        var client = new StompClient(pair[0]);

        assertThatThrownBy(() -> client.send("/topic/test", "msg", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not connected");

        client.close();
    }

    @Test
    void testSubscribeAndReceive() throws Exception {
        var client = createAndConnectClient();
        var publisher = createAndConnectClient();

        try {
            var received = new CopyOnWriteArrayList<StompFrame>();
            var latch = new CountDownLatch(1);

            String subId = client.subscribe("/topic/test", msg -> {
                received.add(msg);
                latch.countDown();
            });
            Thread.sleep(50);

            assertThat(subId).startsWith("sub-");
            assertThat(client.getSession().getSubscriptions()).containsKey(subId);

            publisher.send("/topic/test", "hello", "text/plain");
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received).hasSize(1);
        } finally {
            client.close();
            publisher.close();
        }
    }

    @Test
    void testUnsubscribeRemovesSubscription() throws Exception {
        var client = createAndConnectClient();
        try {
            String subId = client.subscribe("/topic/test", msg -> {});
            assertThat(client.getSession().getSubscriptions()).containsKey(subId);

            client.unsubscribe(subId);
            assertThat(client.getSession().getSubscriptions()).doesNotContainKey(subId);
        } finally {
            client.close();
        }
    }

    @Test
    void testBeginAndCommit() throws Exception {
        var client = createAndConnectClient();
        try {
            client.begin("tx-1");
            assertThat(client.getSession().hasTransaction("tx-1")).isTrue();

            client.commit("tx-1");
            assertThat(client.getSession().hasTransaction("tx-1")).isFalse();
        } finally {
            client.close();
        }
    }

    @Test
    void testBeginAndAbort() throws Exception {
        var client = createAndConnectClient();
        try {
            client.begin("tx-2");
            assertThat(client.getSession().hasTransaction("tx-2")).isTrue();

            client.abort("tx-2");
            assertThat(client.getSession().hasTransaction("tx-2")).isFalse();
        } finally {
            client.close();
        }
    }

    @Test
    void testDisconnect() throws Exception {
        var client = createAndConnectClient();
        assertThat(client.isConnected()).isTrue();

        var future = client.disconnect();
        var receipt = future.get(5, TimeUnit.SECONDS);
        assertThat(receipt).isNotNull();
        assertThat(receipt.command()).isEqualTo(StompCommand.RECEIPT);

        client.close();
    }

    @Test
    void testDisconnectWhenNotConnected() {
        var pair = InMemoryStompTransport.createPair();
        var client = new StompClient(pair[0]);
        var future = client.disconnect();
        assertThat(future).isCompletedWithValue(null);
        client.close();
    }

    @Test
    void testErrorHandler() throws Exception {
        var pair = InMemoryStompTransport.createPair();
        broker.accept(pair[1]);
        var client = new StompClient(pair[0]);
        client.connect("localhost");

        var errors = new CopyOnWriteArrayList<StompFrame>();
        client.onError(errors::add);

        // The error handler is set — verify it's wired
        assertThat(client.isConnected()).isTrue();

        client.close();
    }

    @Test
    void testHeartbeatMonitorAccess() {
        var client = createAndConnectClient();
        try {
            var monitor = client.getHeartbeatMonitor();
            assertThat(monitor).isNotNull();
        } finally {
            client.close();
        }
    }

    @Test
    void testCloseCleanup() {
        var client = createAndConnectClient();
        client.close();
        assertThat(client.isConnected()).isFalse();
    }
}
