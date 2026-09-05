package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.*;
import ssg.legoflow.messaging.stomp.core.transport.InMemoryStompTransport;
import ssg.legoflow.messaging.stomp.core.transport.StompTransport;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for STOMP selectors, priority, and max-size.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StompSelectorTest {

    private StompBroker broker;

    @AfterAll
    void cleanup() {
        if (broker != null) broker.close();
    }

    @Test
    void testSelectorFiltersMessages() throws Exception {
        var subTransports = InMemoryStompTransport.createPair();
        broker = new StompBroker(StompBrokerConfig.defaults());
        broker.setHeartbeatCapability(0, 0);
        broker.accept(subTransports[0]);

        connect(subTransports[1]);
        var headers = new StompHeaders();
        headers.put(StompHeaders.ID, "sub-1");
        headers.put(StompHeaders.DESTINATION, "test/selector");
        headers.put(StompHeaders.ACK, "auto");
        headers.put("selector", "priority > 5");
        subTransports[1].send(new StompFrame(StompCommand.SUBSCRIBE, headers));
        // SUBSCRIBE with no receipt → no response, don't block on receive()

        var latch = new CountDownLatch(1);
        broker.setListener((type, data1, data2) -> {
            if (type == StompEventListener.EventType.MESSAGE_DELIVERED) latch.countDown();
        });

        var sendHeaders = new StompHeaders();
        sendHeaders.put(StompHeaders.DESTINATION, "test/selector");
        sendHeaders.put("priority", "8");
        subTransports[1].send(StompFrame.withText(StompCommand.SEND, sendHeaders, "high"));

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void testSelectorExcludesLowPriority() throws Exception {
        var subTransports = InMemoryStompTransport.createPair();
        broker = new StompBroker(StompBrokerConfig.defaults());
        broker.setHeartbeatCapability(0, 0);
        broker.accept(subTransports[0]);

        connect(subTransports[1]);
        var headers = new StompHeaders();
        headers.put(StompHeaders.ID, "sub-1");
        headers.put(StompHeaders.DESTINATION, "test/filter");
        headers.put(StompHeaders.ACK, "auto");
        headers.put("selector", "priority > 5");
        subTransports[1].send(new StompFrame(StompCommand.SUBSCRIBE, headers));

        var deliveredLatch = new CountDownLatch(1);
        broker.setListener((type, data1, data2) -> {
            if (type == StompEventListener.EventType.MESSAGE_DELIVERED) deliveredLatch.countDown();
        });

        var sendHeaders = new StompHeaders();
        sendHeaders.put(StompHeaders.DESTINATION, "test/filter");
        sendHeaders.put("priority", "2");
        subTransports[1].send(StompFrame.withText(StompCommand.SEND, sendHeaders, "low"));

        Thread.sleep(500);
        // No message should have been delivered
        assertThat(deliveredLatch.getCount()).isEqualTo(1);
    }

    @Test
    void testSelectorWithStringComparison() throws Exception {
        var subTransports = InMemoryStompTransport.createPair();
        broker = new StompBroker(StompBrokerConfig.defaults());
        broker.setHeartbeatCapability(0, 0);
        broker.accept(subTransports[0]);

        connect(subTransports[1]);
        var headers = new StompHeaders();
        headers.put(StompHeaders.ID, "sub-1");
        headers.put(StompHeaders.DESTINATION, "test/type");
        headers.put(StompHeaders.ACK, "auto");
        headers.put("selector", "type = 'alert'");
        subTransports[1].send(new StompFrame(StompCommand.SUBSCRIBE, headers));

        var latch = new CountDownLatch(1);
        broker.setListener((type, data1, data2) -> {
            if (type == StompEventListener.EventType.MESSAGE_DELIVERED) latch.countDown();
        });

        var sendHeaders = new StompHeaders();
        sendHeaders.put(StompHeaders.DESTINATION, "test/type");
        sendHeaders.put("type", "alert");
        subTransports[1].send(StompFrame.withText(StompCommand.SEND, sendHeaders, "alert-msg"));

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void testMaxSizeLimitsQueue() throws Exception {
        var subTransports = InMemoryStompTransport.createPair();
        var config = StompBrokerConfig.defaults().defaultMaxQueueSize(2);
        broker = new StompBroker(config);
        broker.setHeartbeatCapability(0, 0);
        broker.accept(subTransports[0]);

        connect(subTransports[1]);
        var headers = new StompHeaders();
        headers.put(StompHeaders.ID, "sub-1");
        headers.put(StompHeaders.DESTINATION, "test/max");
        headers.put(StompHeaders.ACK, "auto");
        subTransports[1].send(new StompFrame(StompCommand.SUBSCRIBE, headers));

        var deliveredLatch = new CountDownLatch(3);
        broker.setListener((type, data1, data2) -> {
            if (type == StompEventListener.EventType.MESSAGE_DELIVERED) deliveredLatch.countDown();
        });

        for (int i = 0; i < 3; i++) {
            var sendHeaders = new StompHeaders();
            sendHeaders.put(StompHeaders.DESTINATION, "test/max");
            subTransports[1].send(StompFrame.withText(StompCommand.SEND, sendHeaders, "msg-" + i));
            Thread.sleep(50);
        }

        deliveredLatch.await(5, TimeUnit.SECONDS);
        // With max-size 2, only 2 messages should be delivered (1 blocked)
        assertThat(deliveredLatch.getCount()).isEqualTo(1);
    }

    @Test
    void testPersistenceAdapterStoresMessages() throws Exception {
        var persistence = new InMemoryStompPersistenceAdapter();
        var subTransports = InMemoryStompTransport.createPair();
        var config = StompBrokerConfig.defaults().persistenceAdapter(persistence);
        broker = new StompBroker(config);
        broker.setHeartbeatCapability(0, 0);
        broker.accept(subTransports[0]);

        connect(subTransports[1]);
        var headers = new StompHeaders();
        headers.put(StompHeaders.ID, "sub-1");
        headers.put(StompHeaders.DESTINATION, "test/persist");
        headers.put(StompHeaders.ACK, "auto");
        subTransports[1].send(new StompFrame(StompCommand.SUBSCRIBE, headers));

        var sendHeaders = new StompHeaders();
        sendHeaders.put(StompHeaders.DESTINATION, "test/persist");
        subTransports[1].send(StompFrame.withText(StompCommand.SEND, sendHeaders, "persisted"));

        Thread.sleep(300);

        var queued = persistence.loadQueuedMessages("test/persist");
        assertThat(queued).isNotEmpty();
        persistence.close();
    }

    private void connect(StompTransport transport) throws Exception {
        transport.send(new StompFrame(StompCommand.CONNECT, new StompHeaders()));
        transport.receive();
    }
}
