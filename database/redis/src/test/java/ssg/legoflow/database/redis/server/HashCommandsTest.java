package ssg.legoflow.database.redis.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.redis.client.RedisClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for hash commands.
 */
class HashCommandsTest {

    private static RedisServer server;
    private RedisClient client;

    @BeforeAll
    static void startServer() throws IOException {
        server = new RedisServer();
        server.start(0);
    }

    @AfterAll
    static void stopServer() { server.close(); }

    @BeforeEach
    void connect() throws IOException {
        client = new RedisClient("127.0.0.1", server.port());
        client.connect();
        client.execute("FLUSHALL");
    }

    @AfterEach
    void disconnect() { client.close(); }

    @Test
    void testHsetAndHget() throws IOException {
        client.execute("HSET", "h", "field1", "value1");
        assertThat(RedisClient.extractString(client.execute("HGET", "h", "field1"))).isEqualTo("value1");
    }

    @Test
    void testHsetMultipleFields() throws IOException {
        var added = RedisClient.extractLong(client.execute("HSET", "h", "f1", "v1", "f2", "v2", "f3", "v3"));
        assertThat(added).isEqualTo(3);
    }

    @Test
    void testHgetNonExistent() throws IOException {
        var r = client.execute("HGET", "h", "field");
        assertThat(r).isInstanceOf(ssg.legoflow.database.redis.protocol.RespType.BulkString.class);
        assertThat(((ssg.legoflow.database.redis.protocol.RespType.BulkString) r).asString()).isNull();
    }

    @Test
    void testHmsetAndHmget() throws IOException {
        client.execute("HMSET", "h", "f1", "v1", "f2", "v2");
        var r = RedisClient.extractStringList(client.execute("HMGET", "h", "f1", "f2", "f3"));
        assertThat(r).containsExactly("v1", "v2", null);
    }

    @Test
    void testHdel() throws IOException {
        client.execute("HSET", "h", "f1", "v1", "f2", "v2", "f3", "v3");
        var deleted = RedisClient.extractLong(client.execute("HDEL", "h", "f1", "f3", "f4"));
        assertThat(deleted).isEqualTo(2);
    }

    @Test
    void testHexists() throws IOException {
        client.execute("HSET", "h", "f1", "v1");
        assertThat(RedisClient.extractLong(client.execute("HEXISTS", "h", "f1"))).isEqualTo(1);
        assertThat(RedisClient.extractLong(client.execute("HEXISTS", "h", "f2"))).isEqualTo(0);
    }

    @Test
    void testHlen() throws IOException {
        client.execute("HSET", "h", "f1", "v1", "f2", "v2");
        assertThat(RedisClient.extractLong(client.execute("HLEN", "h"))).isEqualTo(2);
    }

    @Test
    void testHkeys() throws IOException {
        client.execute("HSET", "h", "f1", "v1", "f2", "v2");
        var keys = RedisClient.extractStringList(client.execute("HKEYS", "h"));
        assertThat(keys).containsExactlyInAnyOrder("f1", "f2");
    }

    @Test
    void testHvals() throws IOException {
        client.execute("HSET", "h", "f1", "v1", "f2", "v2");
        var vals = RedisClient.extractStringList(client.execute("HVALS", "h"));
        assertThat(vals).containsExactlyInAnyOrder("v1", "v2");
    }

    @Test
    void testHgetall() throws IOException {
        client.execute("HSET", "h", "f1", "v1", "f2", "v2");
        var r = RedisClient.extractStringList(client.execute("HGETALL", "h"));
        assertThat(r).hasSize(4);
        // Should contain f1,v1,f2,v2 in some order
        assertThat(r).contains("f1", "v1", "f2", "v2");
    }

    @Test
    void testHincrby() throws IOException {
        client.execute("HSET", "h", "counter", "10");
        var r = RedisClient.extractLong(client.execute("HINCRBY", "h", "counter", "5"));
        assertThat(r).isEqualTo(15);
    }

    @Test
    void testHincrbyNewField() throws IOException {
        var r = RedisClient.extractLong(client.execute("HINCRBY", "h", "counter", "5"));
        assertThat(r).isEqualTo(5);
    }

    @Test
    void testHincrbyfloat() throws IOException {
        client.execute("HSET", "h", "price", "10.50");
        var r = RedisClient.extractString(client.execute("HINCRBYFLOAT", "h", "price", "0.5"));
        assertThat(Double.parseDouble(r)).isEqualTo(11.0);
    }

    @Test
    void testHsetnx() throws IOException {
        var r1 = RedisClient.extractLong(client.execute("HSETNX", "h", "f1", "v1"));
        assertThat(r1).isEqualTo(1);
        var r2 = RedisClient.extractLong(client.execute("HSETNX", "h", "f1", "v2"));
        assertThat(r2).isEqualTo(0);
        assertThat(RedisClient.extractString(client.execute("HGET", "h", "f1"))).isEqualTo("v1");
    }
}
