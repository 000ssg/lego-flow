package ssg.legoflow.messaging.stomp.demo;

import ssg.legoflow.messaging.stomp.core.StompBroker;
import ssg.legoflow.messaging.stomp.core.StompClient;
import ssg.legoflow.messaging.stomp.core.StompFrame;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates STOMP transactional messaging.
 *
 * <p>Shows BEGIN/SEND/COMMIT and BEGIN/SEND/ABORT flows. Messages within
 * a committed transaction are delivered; messages within an aborted
 * transaction are discarded.
 *
 * @since 0.1.0
 */
public class TransactionalDemo {

    private final StompBroker broker;
    private final StompClient sender;
    private final StompClient receiver;
    private final List<StompFrame> receivedMessages = new CopyOnWriteArrayList<>();

    /**
     * Creates and connects the demo components.
     */
    public TransactionalDemo() {
        broker = new StompBroker();

        var sndPair = InMemoryStompTransport.createPair();
        var rcvPair = InMemoryStompTransport.createPair();

        broker.accept(sndPair[1]);
        broker.accept(rcvPair[1]);

        sender = new StompClient(sndPair[0]);
        receiver = new StompClient(rcvPair[0]);
    }

    /**
     * Sends messages within a committed transaction.
     *
     * @param destination the destination
     * @param messages    the messages to send
     * @return the received messages after commit
     * @throws InterruptedException if interrupted
     */
    public List<StompFrame> runCommit(String destination, List<String> messages) throws InterruptedException {
        sender.connect("localhost");
        receiver.connect("localhost");

        var latch = new CountDownLatch(messages.size());
        receiver.subscribe(destination, msg -> {
            receivedMessages.add(msg);
            latch.countDown();
        });

        Thread.sleep(50);

        // Begin transaction, send messages, commit
        String txId = "tx-commit-1";
        sender.begin(txId);
        for (String msg : messages) {
            sender.send(destination, msg, "text/plain", txId);
        }
        sender.commit(txId);

        latch.await(5, TimeUnit.SECONDS);
        return receivedMessages;
    }

    /**
     * Sends messages within an aborted transaction.
     *
     * @param destination the destination
     * @param messages    the messages to send
     * @return the received messages (should be empty after abort)
     * @throws InterruptedException if interrupted
     */
    public List<StompFrame> runAbort(String destination, List<String> messages) throws InterruptedException {
        sender.connect("localhost");
        receiver.connect("localhost");

        receiver.subscribe(destination, msg -> {
            receivedMessages.add(msg);
        });

        Thread.sleep(50);

        // Begin transaction, send messages, abort
        String txId = "tx-abort-1";
        sender.begin(txId);
        for (String msg : messages) {
            sender.send(destination, msg, "text/plain", txId);
        }
        sender.abort(txId);

        // Wait a bit to confirm no messages arrive
        Thread.sleep(200);
        return receivedMessages;
    }

    /**
     * Returns the received messages.
     *
     * @return the received messages
     */
    public List<StompFrame> getReceivedMessages() {
        return receivedMessages;
    }

    /**
     * Closes all resources.
     */
    public void close() {
        sender.close();
        receiver.close();
        broker.close();
    }
}
