package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.stomp.core.transport.InMemoryStompTransport;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link StompBroker} — connection, subscription, delivery, ack modes,
 * transactions, receipts, error handling.
 *
 * @since 1.0.0
 */
class StompBrokerTest {

    private StompBroker broker;

    @BeforeEach
    void setUp() {
        broker = new StompBroker();
    }

    @AfterEach
    void tearDown() {
        broker.close();
    }

    private StompClient connectClient() throws InterruptedException {
        var pair = InMemoryStompTransport.createPair();
        broker.accept(pair[1]);
        var client = new StompClient(pair[0]);
        client.connect("localhost");
        Thread.sleep(20);
        return client;
    }

    // --- Connection tests ---

    @Test
    void testClientConnect() throws Exception {
        var pair = InMemoryStompTransport.createPair();
        broker.accept(pair[1]);

        var client = new StompClient(pair[0]);
        var connected = client.connect("localhost");

        assertThat(connected.command()).isEqualTo(StompCommand.CONNECTED);
        assertThat(connected.header(StompHeaders.VERSION)).isNotNull();
        assertThat(connected.header(StompHeaders.SESSION)).isNotNull();
        assertThat(client.isConnected()).isTrue();

        client.close();
    }

    @Test
    void testVersionNegotiation12() throws Exception {
        var pair = InMemoryStompTransport.createPair();
        broker.accept(pair[1]);

        var client = new StompClient(pair[0]);
        var connected = client.connect("localhost");

        assertThat(connected.header(StompHeaders.VERSION)).isEqualTo("1.2");
        client.close();
    }

    @Test
    void testMultipleClients() throws Exception {
        var client1 = connectClient();
        var client2 = connectClient();
        var client3 = connectClient();

        assertThat(client1.isConnected()).isTrue();
        assertThat(client2.isConnected()).isTrue();
        assertThat(client3.isConnected()).isTrue();

        client1.close();
        client2.close();
        client3.close();
    }

    // --- Pub/Sub tests ---

    @Test
    void testSimplePubSub() throws Exception {
        var publisher = connectClient();
        var subscriber = connectClient();

        var received = new CopyOnWriteArrayList<StompFrame>();
        var latch = new CountDownLatch(1);

        subscriber.subscribe("/topic/test", msg -> {
            received.add(msg);
            latch.countDown();
        });
        Thread.sleep(50);

        publisher.send("/topic/test", "Hello!", "text/plain");
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(received).hasSize(1);
        assertThat(received.getFirst().bodyAsText()).isEqualTo("Hello!");
        assertThat(received.getFirst().header(StompHeaders.DESTINATION)).isEqualTo("/topic/test");

        publisher.close();
        subscriber.close();
    }

    @Test
    void testMultipleSubscribers() throws Exception {
        var publisher = connectClient();
        var sub1 = connectClient();
        var sub2 = connectClient();

        var received1 = new CopyOnWriteArrayList<StompFrame>();
        var received2 = new CopyOnWriteArrayList<StompFrame>();
        var latch = new CountDownLatch(2);

        sub1.subscribe("/topic/broadcast", msg -> {
            received1.add(msg);
            latch.countDown();
        });
        sub2.subscribe("/topic/broadcast", msg -> {
            received2.add(msg);
            latch.countDown();
        });
        Thread.sleep(50);

        publisher.send("/topic/broadcast", "Broadcast!", "text/plain");
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(received1).hasSize(1);
        assertThat(received2).hasSize(1);

        publisher.close();
        sub1.close();
        sub2.close();
    }

    @Test
    void testMultipleDestinations() throws Exception {
        var publisher = connectClient();
        var sub = connectClient();

        var topicA = new CopyOnWriteArrayList<StompFrame>();
        var topicB = new CopyOnWriteArrayList<StompFrame>();
        var latch = new CountDownLatch(2);

        sub.subscribe("/topic/a", msg -> {
            topicA.add(msg);
            latch.countDown();
        });
        sub.subscribe("/topic/b", msg -> {
            topicB.add(msg);
            latch.countDown();
        });
        Thread.sleep(50);

        publisher.send("/topic/a", "MessageA", "text/plain");
        publisher.send("/topic/b", "MessageB", "text/plain");
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(topicA).hasSize(1);
        assertThat(topicB).hasSize(1);
        assertThat(topicA.getFirst().bodyAsText()).isEqualTo("MessageA");
        assertThat(topicB.getFirst().bodyAsText()).isEqualTo("MessageB");

        publisher.close();
        sub.close();
    }

    @Test
    void testUnsubscribe() throws Exception {
        var publisher = connectClient();
        var subscriber = connectClient();

        var received = new CopyOnWriteArrayList<StompFrame>();

        String subId = subscriber.subscribe("/topic/test", received::add);
        Thread.sleep(50);

        publisher.send("/topic/test", "Before unsub", "text/plain");
        Thread.sleep(100);
        assertThat(received).hasSize(1);

        subscriber.unsubscribe(subId);
        Thread.sleep(50);

        publisher.send("/topic/test", "After unsub", "text/plain");
        Thread.sleep(200);
        assertThat(received).hasSize(1); // No new messages

        publisher.close();
        subscriber.close();
    }

    @Test
    void testSendToUnsubscribedDestination() throws Exception {
        var publisher = connectClient();
        // Send to a destination nobody subscribes to — should not error
        publisher.send("/topic/nowhere", "Lost message", "text/plain");
        Thread.sleep(100);
        assertThat(publisher.isConnected()).isTrue();
        publisher.close();
    }

    // --- Ack mode tests ---

    @Test
    void testAckModeAuto() throws Exception {
        var publisher = connectClient();
        var subscriber = connectClient();

        var received = new CopyOnWriteArrayList<StompFrame>();
        var latch = new CountDownLatch(1);

        subscriber.subscribe("/topic/auto", "auto", msg -> {
            received.add(msg);
            latch.countDown();
        });
        Thread.sleep(50);

        publisher.send("/topic/auto", "auto message", "text/plain");
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
        // No ack header in auto mode
        assertThat(received.getFirst().header(StompHeaders.ACK)).isNull();

        publisher.close();
        subscriber.close();
    }

    @Test
    void testAckModeClientIndividual() throws Exception {
        var publisher = connectClient();
        var subscriber = connectClient();

        var received = new CopyOnWriteArrayList<StompFrame>();
        var latch = new CountDownLatch(2);

        subscriber.subscribe("/topic/ci", "client-individual", msg -> {
            received.add(msg);
            latch.countDown();
        });
        Thread.sleep(50);

        publisher.send("/topic/ci", "msg1", "text/plain");
        publisher.send("/topic/ci", "msg2", "text/plain");
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(received).hasSize(2);
        // Both should have ack headers
        assertThat(received.get(0).header(StompHeaders.ACK)).isNotNull();
        assertThat(received.get(1).header(StompHeaders.ACK)).isNotNull();

        // ACK them individually
        subscriber.ack(received.get(0).header(StompHeaders.ACK));
        subscriber.ack(received.get(1).header(StompHeaders.ACK));

        publisher.close();
        subscriber.close();
    }

    @Test
    void testAckModeClient() throws Exception {
        var publisher = connectClient();
        var subscriber = connectClient();

        var received = new CopyOnWriteArrayList<StompFrame>();
        var latch = new CountDownLatch(3);

        subscriber.subscribe("/topic/cl", "client", msg -> {
            received.add(msg);
            latch.countDown();
        });
        Thread.sleep(50);

        publisher.send("/topic/cl", "msg1", "text/plain");
        publisher.send("/topic/cl", "msg2", "text/plain");
        publisher.send("/topic/cl", "msg3", "text/plain");
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        // ACK the last one — cumulative, acknowledges all
        String lastAckId = received.get(2).header(StompHeaders.ACK);
        assertThat(lastAckId).isNotNull();
        subscriber.ack(lastAckId);

        publisher.close();
        subscriber.close();
    }

    @Test
    void testNack() throws Exception {
        var publisher = connectClient();
        var subscriber = connectClient();

        var received = new CopyOnWriteArrayList<StompFrame>();
        var latch = new CountDownLatch(1);

        subscriber.subscribe("/topic/nack", "client-individual", msg -> {
            received.add(msg);
            latch.countDown();
        });
        Thread.sleep(50);

        publisher.send("/topic/nack", "rejected", "text/plain");
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        String ackId = received.getFirst().header(StompHeaders.ACK);
        subscriber.nack(ackId);

        publisher.close();
        subscriber.close();
    }

    // --- Transaction tests ---

    @Test
    void testTransactionCommit() throws Exception {
        var sender = connectClient();
        var receiver = connectClient();

        var received = new CopyOnWriteArrayList<StompFrame>();
        var latch = new CountDownLatch(2);

        receiver.subscribe("/topic/tx", msg -> {
            received.add(msg);
            latch.countDown();
        });
        Thread.sleep(50);

        sender.begin("tx-1");
        sender.send("/topic/tx", "tx-msg-1", "text/plain", "tx-1");
        sender.send("/topic/tx", "tx-msg-2", "text/plain", "tx-1");

        // Messages should not be delivered yet
        Thread.sleep(200);
        assertThat(received).isEmpty();

        sender.commit("tx-1");
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(2);

        sender.close();
        receiver.close();
    }

    @Test
    void testTransactionAbort() throws Exception {
        var sender = connectClient();
        var receiver = connectClient();

        var received = new CopyOnWriteArrayList<StompFrame>();

        receiver.subscribe("/topic/tx-abort", received::add);
        Thread.sleep(50);

        sender.begin("tx-2");
        sender.send("/topic/tx-abort", "should-not-deliver", "text/plain", "tx-2");
        sender.abort("tx-2");

        Thread.sleep(300);
        assertThat(received).isEmpty();

        sender.close();
        receiver.close();
    }

    // --- Receipt tests ---

    @Test
    void testReceiptForSend() throws Exception {
        var client = connectClient();

        // Subscribe to have a destination
        var received = new CopyOnWriteArrayList<StompFrame>();
        client.subscribe("/topic/receipt-test", received::add);
        Thread.sleep(50);

        var receiptFuture = client.sendWithReceipt("/topic/receipt-test", "test", "text/plain");
        var receipt = receiptFuture.get(5, TimeUnit.SECONDS);

        assertThat(receipt).isNotNull();
        assertThat(receipt.command()).isEqualTo(StompCommand.RECEIPT);
        assertThat(receipt.header(StompHeaders.RECEIPT_ID)).isNotNull();

        client.close();
    }

    @Test
    void testDisconnectReceipt() throws Exception {
        var client = connectClient();

        var disconnectFuture = client.disconnect();
        var receipt = disconnectFuture.get(5, TimeUnit.SECONDS);

        assertThat(receipt).isNotNull();
        assertThat(receipt.command()).isEqualTo(StompCommand.RECEIPT);

        client.close();
    }

    // --- Heart-beat tests ---

    @Test
    void testHeartbeatNegotiation() throws Exception {
        var pair = InMemoryStompTransport.createPair();
        broker.accept(pair[1]);

        var client = new StompClient(pair[0]);
        var connected = client.connect("localhost", null, null, 10000, 10000);

        assertThat(connected.header(StompHeaders.HEART_BEAT)).isNotNull();

        client.close();
    }

    // --- Error handling tests ---

    @Test
    void testMessageHeaderPresence() throws Exception {
        var publisher = connectClient();
        var subscriber = connectClient();

        var received = new CopyOnWriteArrayList<StompFrame>();
        var latch = new CountDownLatch(1);

        subscriber.subscribe("/topic/headers", msg -> {
            received.add(msg);
            latch.countDown();
        });
        Thread.sleep(50);

        publisher.send("/topic/headers", "test", "text/plain");
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        var msg = received.getFirst();
        assertThat(msg.command()).isEqualTo(StompCommand.MESSAGE);
        assertThat(msg.header(StompHeaders.DESTINATION)).isEqualTo("/topic/headers");
        assertThat(msg.header(StompHeaders.MESSAGE_ID)).isNotNull();
        assertThat(msg.header(StompHeaders.SUBSCRIPTION)).isNotNull();

        publisher.close();
        subscriber.close();
    }

    @Test
    void testContentTypePreserved() throws Exception {
        var publisher = connectClient();
        var subscriber = connectClient();

        var received = new CopyOnWriteArrayList<StompFrame>();
        var latch = new CountDownLatch(1);

        subscriber.subscribe("/topic/ct", msg -> {
            received.add(msg);
            latch.countDown();
        });
        Thread.sleep(50);

        publisher.send("/topic/ct", "{\"key\":\"value\"}", "application/json");
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(received.getFirst().header(StompHeaders.CONTENT_TYPE)).isEqualTo("application/json");

        publisher.close();
        subscriber.close();
    }

    @Test
    void testServerIdentification() throws Exception {
        var pair = InMemoryStompTransport.createPair();
        broker.accept(pair[1]);

        var client = new StompClient(pair[0]);
        var connected = client.connect("localhost");

        assertThat(connected.header(StompHeaders.SERVER)).contains("LegoFlow-STOMP");

        client.close();
    }
}
