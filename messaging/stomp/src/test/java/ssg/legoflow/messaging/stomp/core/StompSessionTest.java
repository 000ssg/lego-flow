package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link StompSession}.
 *
 * @since 1.0.0
 */
class StompSessionTest {

    @Test
    void testInitialState() {
        var session = new StompSession("test-1");
        assertThat(session.getSessionId()).isEqualTo("test-1");
        assertThat(session.getState()).isEqualTo(StompSession.State.CONNECTING);
        assertThat(session.isConnected()).isFalse();
    }

    @Test
    void testStateTransitions() {
        var session = new StompSession("test-1");
        session.setState(StompSession.State.CONNECTED);
        assertThat(session.isConnected()).isTrue();

        session.setState(StompSession.State.DISCONNECTING);
        assertThat(session.isConnected()).isFalse();

        session.setState(StompSession.State.DISCONNECTED);
        assertThat(session.isConnected()).isFalse();
    }

    @Test
    void testSubscriptions() {
        var session = new StompSession("test-1");
        session.addSubscription("sub-1", "/topic/news");
        session.addSubscription("sub-2", "/queue/work");

        assertThat(session.getSubscriptions()).hasSize(2);
        assertThat(session.getSubscriptionDestination("sub-1")).isEqualTo("/topic/news");
        assertThat(session.getSubscriptionDestination("sub-2")).isEqualTo("/queue/work");

        String removed = session.removeSubscription("sub-1");
        assertThat(removed).isEqualTo("/topic/news");
        assertThat(session.getSubscriptions()).hasSize(1);
    }

    @Test
    void testTransactions() {
        var session = new StompSession("test-1");
        session.beginTransaction("tx-1");
        assertThat(session.hasTransaction("tx-1")).isTrue();
        assertThat(session.getActiveTransactions()).contains("tx-1");

        boolean ended = session.endTransaction("tx-1");
        assertThat(ended).isTrue();
        assertThat(session.hasTransaction("tx-1")).isFalse();
    }

    @Test
    void testReceipts() {
        var session = new StompSession("test-1");
        session.addPendingReceipt("r-1");
        assertThat(session.getPendingReceipts()).contains("r-1");

        boolean confirmed = session.confirmReceipt("r-1");
        assertThat(confirmed).isTrue();
        assertThat(session.getPendingReceipts()).doesNotContain("r-1");
    }

    @Test
    void testMessageIdGeneration() {
        var session = new StompSession("test-1");
        String id1 = session.nextMessageId();
        String id2 = session.nextMessageId();
        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1).startsWith("test-1-");
    }

    @Test
    void testHeartbeat() {
        var session = new StompSession("test-1");
        session.setClientHeartbeat(10000, 10000);
        assertThat(session.getClientSendInterval()).isEqualTo(10000);
        assertThat(session.getClientReceiveInterval()).isEqualTo(10000);

        session.setServerHeartbeat(5000, 5000);
        assertThat(session.getServerSendInterval()).isEqualTo(5000);
        assertThat(session.getServerReceiveInterval()).isEqualTo(5000);
    }

    @Test
    void testClear() {
        var session = new StompSession("test-1");
        session.setState(StompSession.State.CONNECTED);
        session.addSubscription("sub-1", "/topic/test");
        session.beginTransaction("tx-1");
        session.addPendingReceipt("r-1");

        session.clear();

        assertThat(session.getState()).isEqualTo(StompSession.State.DISCONNECTED);
        assertThat(session.getSubscriptions()).isEmpty();
        assertThat(session.getActiveTransactions()).isEmpty();
        assertThat(session.getPendingReceipts()).isEmpty();
    }

    @Test
    void testLoginAndServer() {
        var session = new StompSession("test-1");
        session.setLogin("user");
        session.setServerName("TestBroker/1.0");
        session.setNegotiatedVersion("1.2");

        assertThat(session.getLogin()).isEqualTo("user");
        assertThat(session.getServerName()).isEqualTo("TestBroker/1.0");
        assertThat(session.getNegotiatedVersion()).isEqualTo("1.2");
    }

    @Test
    void testToString() {
        var session = new StompSession("test-1");
        assertThat(session.toString()).contains("test-1").contains("CONNECTING");
    }
}
