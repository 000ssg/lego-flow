package ssg.legoflow.interop.redis;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.database.redis.protocol.RespType;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Interoperability test: Lego Flow Redis client ↔ real Redis server.
 */
    @Tag("web-protocols")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisInteropTest {

    private final String host = System.getProperty("interop.redis.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.redis.port", "6379"));

    private RedisClient client;

    @BeforeAll
    void connect() throws IOException {
        this.client = new RedisClient(host, port);
        client.connect();
        // Clean up any leftover test keys
        try {
            client.execute("FLUSHDB");
        } catch (IOException ignored) {
        }
    }

    @AfterAll
    void disconnect() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testPingPong() throws IOException {
        RespType response = client.execute("PING");
        assertThat(response).isInstanceOf(RespType.SimpleString.class);
        assertThat(((RespType.SimpleString) response).value()).isEqualTo("PONG");
    }

    @Test
    void testSetAndGet() throws IOException {
        String key = "lego_flow:interop:test";
        String value = "hello-redis";

        RespType setResult = client.execute("SET", key, value);
        assertThat(setResult).isInstanceOf(RespType.SimpleString.class);
        assertThat(((RespType.SimpleString) setResult).value()).isEqualTo("OK");

        RespType getResponse = client.execute("GET", key);
        assertThat(getResponse).isInstanceOf(RespType.BulkString.class);
        // BulkString.value() returns byte[] — use asString() for String comparison
        assertThat(((RespType.BulkString) getResponse).asString()).isEqualTo(value);

        client.execute("DEL", key);
    }

    @Test
    void testIncrement() throws IOException {
        String key = "lego_flow:interop:counter";

        RespType setResult = client.execute("SET", key, "42");
        assertThat(setResult).isInstanceOf(RespType.SimpleString.class);

        RespType incrResponse = client.execute("INCR", key);
        assertThat(incrResponse).isInstanceOf(RespType.Integer.class);
        assertThat(((RespType.Integer) incrResponse).value()).isEqualTo(43L);

        RespType getResponse = client.execute("GET", key);
        // BulkString.value() returns byte[] — use asString() for String comparison
        assertThat(((RespType.BulkString) getResponse).asString()).isEqualTo("43");

        client.execute("DEL", key);
    }

    @Test
    void testHashOperations() throws IOException {
        String hashKey = "lego_flow:interop:user";

        RespType setResult = client.execute("HSET", hashKey, "name", "Alice", "age", "30");
        assertThat(setResult).isInstanceOf(RespType.Integer.class);

        RespType nameResponse = client.execute("HGET", hashKey, "name");
        assertThat(nameResponse).isInstanceOf(RespType.BulkString.class);
        assertThat(((RespType.BulkString) nameResponse).asString()).isEqualTo("Alice");

        RespType allResponse = client.execute("HGETALL", hashKey);
        assertThat(allResponse).isInstanceOf(RespType.Array.class);

        client.execute("DEL", hashKey);
    }

    @Test
    void testListOperations() throws IOException {
        String listKey = "lego_flow:interop:list";

        RespType pushResponse = client.execute("RPUSH", listKey, "alpha", "beta", "gamma");
        assertThat(pushResponse).isInstanceOf(RespType.Integer.class);
        assertThat(((RespType.Integer) pushResponse).value()).isEqualTo(3L);

        RespType lenResponse = client.execute("LLEN", listKey);
        assertThat(lenResponse).isInstanceOf(RespType.Integer.class);
        assertThat(((RespType.Integer) lenResponse).value()).isEqualTo(3L);

        RespType popResponse = client.execute("LPOP", listKey);
        assertThat(popResponse).isInstanceOf(RespType.BulkString.class);
        // BulkString.value() returns byte[] — use asString() for String comparison
        assertThat(((RespType.BulkString) popResponse).asString()).isEqualTo("alpha");

        client.execute("DEL", listKey);
    }

    @Test
    void testKeysPattern() throws IOException {
        client.execute("SET", "lego:foo:1", "a");
        client.execute("SET", "lego:bar:2", "b");

        RespType keysResponse = client.execute("KEYS", "lego:*");
        assertThat(keysResponse).isInstanceOf(RespType.Array.class);
        assertThat(((RespType.Array) keysResponse).elements()).hasSizeGreaterThanOrEqualTo(2);

        client.execute("DEL", "lego:foo:1", "lego:bar:2");
    }

    @Test
    void testTTL() throws IOException {
        String key = "lego_flow:interop:ttl";

        RespType setResult = client.execute("SET", key, "expires", "EX", "10");
        assertThat(setResult).isInstanceOf(RespType.SimpleString.class);

        RespType ttlResponse = client.execute("TTL", key);
        assertThat(ttlResponse).isInstanceOf(RespType.Integer.class);
        assertThat(((RespType.Integer) ttlResponse).value()).isGreaterThan(0L);

        client.execute("DEL", key);
    }
}
