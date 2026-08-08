package ssg.legoflow.database.redis.demo;

import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.database.redis.server.RedisServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates Redis Pub/Sub messaging patterns.
 *
 * @since 0.1.0
 */
public final class RedisPubSubDemo {

    private RedisPubSubDemo() {}

    /**
     * Runs the pub/sub demo against the given server.
     *
     * @param server the Redis server
     * @return list of received messages
     * @throws Exception if I/O or thread interruption
     */
    public static List<String> run(RedisServer server) throws Exception {
        List<String> received = new ArrayList<>();
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch messagesReceived = new CountDownLatch(2);

        // Subscriber thread
        Thread subscriberThread = Thread.startVirtualThread(() -> {
            try (var subscriber = new RedisClient("127.0.0.1", server.port())) {
                subscriber.connect();
                subscriber.execute("SUBSCRIBE", "news", "alerts");
                subscribed.countDown();

                // Read messages
                for (int i = 0; i < 2; i++) {
                    var response = subscriber.receive();
                    if (response instanceof ssg.legoflow.database.redis.protocol.RespType.Array arr
                            && arr.elements() != null && arr.elements().size() >= 3) {
                        String type = RedisClient.extractString(arr.elements().get(0));
                        if ("message".equals(type)) {
                            String msg = RedisClient.extractString(arr.elements().get(2));
                            received.add(msg);
                            messagesReceived.countDown();
                        }
                    }
                }
            } catch (IOException e) {
                // ignore on close
            }
        });

        // Wait for subscription to be established
        subscribed.await(2, TimeUnit.SECONDS);
        Thread.sleep(50);

        // Publisher
        try (var publisher = new RedisClient("127.0.0.1", server.port())) {
            publisher.connect();
            publisher.execute("PUBLISH", "news", "Breaking: Redis works!");
            publisher.execute("PUBLISH", "alerts", "System update available");
        }

        messagesReceived.await(2, TimeUnit.SECONDS);
        return received;
    }
}
