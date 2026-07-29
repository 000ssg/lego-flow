package ssg.legoflow.database.redis.demo;

import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.database.redis.server.RedisServer;

import java.io.IOException;

/**
 * Demonstrates Redis Streams with consumer groups.
 *
 * @since 1.0.0
 */
public final class RedisStreamDemo {

    private RedisStreamDemo() {}

    /**
     * Runs the stream demo against the given server.
     *
     * @param server the Redis server
     * @throws IOException if I/O fails
     */
    public static void run(RedisServer server) throws IOException {
        try (var client = new RedisClient("127.0.0.1", server.port())) {
            client.connect();

            // Add entries to a stream
            client.execute("XADD", "events", "*", "type", "login", "user", "alice");
            client.execute("XADD", "events", "*", "type", "purchase", "user", "bob", "amount", "42.50");
            client.execute("XADD", "events", "*", "type", "login", "user", "charlie");

            // Read stream length
            client.execute("XLEN", "events");

            // Range query
            client.execute("XRANGE", "events", "-", "+");

            // Create consumer group
            client.execute("XGROUP", "CREATE", "events", "processors", "0", "MKSTREAM");

            // Read as consumer in group
            client.execute("XREADGROUP", "GROUP", "processors", "worker-1",
                    "COUNT", "2", "STREAMS", "events", ">");

            // Acknowledge entries
            // In real usage, you'd extract the IDs from the read response
        }
    }
}
