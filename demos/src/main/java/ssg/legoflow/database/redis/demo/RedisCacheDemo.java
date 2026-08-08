package ssg.legoflow.database.redis.demo;

import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.database.redis.server.RedisServer;

import java.io.IOException;

/**
 * Demonstrates basic Redis caching patterns: SET/GET with TTL,
 * atomic counters (INCR/DECR), and hash-based object storage.
 *
 * @since 0.1.0
 */
public final class RedisCacheDemo {

    private RedisCacheDemo() {}

    /**
     * Runs the cache demo against the given server.
     *
     * @param server the Redis server
     * @throws IOException if I/O fails
     */
    public static void run(RedisServer server) throws IOException {
        try (var client = new RedisClient("127.0.0.1", server.port())) {
            client.connect();

            // Basic SET/GET
            client.set("greeting", "Hello, Redis!");
            String greeting = client.get("greeting");

            // TTL-based caching
            client.execute("SET", "session:123", "user-data", "EX", "3600");
            client.execute("TTL", "session:123");

            // Atomic counter
            client.execute("SET", "page:views", "0");
            client.execute("INCR", "page:views");
            client.execute("INCR", "page:views");
            client.execute("INCR", "page:views");
            String views = client.get("page:views");

            // Hash-based object
            client.execute("HSET", "user:1", "name", "Alice", "email", "alice@example.com", "age", "30");
            client.execute("HGET", "user:1", "name");
            client.execute("HGETALL", "user:1");
            client.execute("HINCRBY", "user:1", "age", "1");

            // MSET/MGET for batch operations
            client.execute("MSET", "k1", "v1", "k2", "v2", "k3", "v3");
            client.execute("MGET", "k1", "k2", "k3");
        }
    }
}
