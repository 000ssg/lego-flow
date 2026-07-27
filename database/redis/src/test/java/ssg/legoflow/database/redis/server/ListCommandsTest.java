package ssg.legoflow.database.redis.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.database.redis.protocol.RespType;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for list commands via client-server round-trip.
 */
class ListCommandsTest {

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
    void testLpushAndLrange() throws IOException {
        client.execute("LPUSH", "list", "c", "b", "a");
        var r = RedisClient.extractStringList(client.execute("LRANGE", "list", "0", "-1"));
        assertThat(r).containsExactly("a", "b", "c");
    }

    @Test
    void testRpush() throws IOException {
        client.execute("RPUSH", "list", "a", "b", "c");
        var r = RedisClient.extractStringList(client.execute("LRANGE", "list", "0", "-1"));
        assertThat(r).containsExactly("a", "b", "c");
    }

    @Test
    void testLpop() throws IOException {
        client.execute("RPUSH", "list", "a", "b", "c");
        var r = RedisClient.extractString(client.execute("LPOP", "list"));
        assertThat(r).isEqualTo("a");
    }

    @Test
    void testRpop() throws IOException {
        client.execute("RPUSH", "list", "a", "b", "c");
        var r = RedisClient.extractString(client.execute("RPOP", "list"));
        assertThat(r).isEqualTo("c");
    }

    @Test
    void testLpopEmpty() throws IOException {
        var r = client.execute("LPOP", "list");
        assertThat(r).isInstanceOf(RespType.BulkString.class);
        assertThat(((RespType.BulkString) r).asString()).isNull();
    }

    @Test
    void testLpopCount() throws IOException {
        client.execute("RPUSH", "list", "a", "b", "c", "d");
        var r = RedisClient.extractStringList(client.execute("LPOP", "list", "2"));
        assertThat(r).containsExactly("a", "b");
    }

    @Test
    void testLlen() throws IOException {
        client.execute("RPUSH", "list", "a", "b", "c");
        var len = RedisClient.extractLong(client.execute("LLEN", "list"));
        assertThat(len).isEqualTo(3);
    }

    @Test
    void testLlenEmpty() throws IOException {
        var len = RedisClient.extractLong(client.execute("LLEN", "list"));
        assertThat(len).isEqualTo(0);
    }

    @Test
    void testLindex() throws IOException {
        client.execute("RPUSH", "list", "a", "b", "c");
        assertThat(RedisClient.extractString(client.execute("LINDEX", "list", "0"))).isEqualTo("a");
        assertThat(RedisClient.extractString(client.execute("LINDEX", "list", "2"))).isEqualTo("c");
        assertThat(RedisClient.extractString(client.execute("LINDEX", "list", "-1"))).isEqualTo("c");
    }

    @Test
    void testLset() throws IOException {
        client.execute("RPUSH", "list", "a", "b", "c");
        client.execute("LSET", "list", "1", "B");
        var r = RedisClient.extractStringList(client.execute("LRANGE", "list", "0", "-1"));
        assertThat(r).containsExactly("a", "B", "c");
    }

    @Test
    void testLrem() throws IOException {
        client.execute("RPUSH", "list", "a", "b", "a", "c", "a");
        var removed = RedisClient.extractLong(client.execute("LREM", "list", "2", "a"));
        assertThat(removed).isEqualTo(2);
        var r = RedisClient.extractStringList(client.execute("LRANGE", "list", "0", "-1"));
        assertThat(r).containsExactly("b", "c", "a");
    }

    @Test
    void testLremNegativeCount() throws IOException {
        client.execute("RPUSH", "list", "a", "b", "a", "c", "a");
        var removed = RedisClient.extractLong(client.execute("LREM", "list", "-2", "a"));
        assertThat(removed).isEqualTo(2);
        var r = RedisClient.extractStringList(client.execute("LRANGE", "list", "0", "-1"));
        assertThat(r).containsExactly("a", "b", "c");
    }

    @Test
    void testLinsertBefore() throws IOException {
        client.execute("RPUSH", "list", "a", "c");
        client.execute("LINSERT", "list", "BEFORE", "c", "b");
        var r = RedisClient.extractStringList(client.execute("LRANGE", "list", "0", "-1"));
        assertThat(r).containsExactly("a", "b", "c");
    }

    @Test
    void testLinsertAfter() throws IOException {
        client.execute("RPUSH", "list", "a", "c");
        client.execute("LINSERT", "list", "AFTER", "a", "b");
        var r = RedisClient.extractStringList(client.execute("LRANGE", "list", "0", "-1"));
        assertThat(r).containsExactly("a", "b", "c");
    }

    @Test
    void testLtrim() throws IOException {
        client.execute("RPUSH", "list", "a", "b", "c", "d", "e");
        client.execute("LTRIM", "list", "1", "3");
        var r = RedisClient.extractStringList(client.execute("LRANGE", "list", "0", "-1"));
        assertThat(r).containsExactly("b", "c", "d");
    }

    @Test
    void testBlpopNonBlocking() throws IOException {
        client.execute("RPUSH", "list", "a", "b");
        var r = RedisClient.extractStringList(client.execute("BLPOP", "list", "0"));
        assertThat(r).containsExactly("list", "a");
    }

    @Test
    void testBrpopNonBlocking() throws IOException {
        client.execute("RPUSH", "list", "a", "b");
        var r = RedisClient.extractStringList(client.execute("BRPOP", "list", "0"));
        assertThat(r).containsExactly("list", "b");
    }

    @Test
    void testLmove() throws IOException {
        client.execute("RPUSH", "src", "a", "b", "c");
        var r = RedisClient.extractString(client.execute("LMOVE", "src", "dst", "LEFT", "RIGHT"));
        assertThat(r).isEqualTo("a");
        assertThat(RedisClient.extractStringList(client.execute("LRANGE", "src", "0", "-1")))
                .containsExactly("b", "c");
        assertThat(RedisClient.extractStringList(client.execute("LRANGE", "dst", "0", "-1")))
                .containsExactly("a");
    }

    @Test
    void testLrangeSubset() throws IOException {
        client.execute("RPUSH", "list", "a", "b", "c", "d", "e");
        var r = RedisClient.extractStringList(client.execute("LRANGE", "list", "1", "3"));
        assertThat(r).containsExactly("b", "c", "d");
    }

    @Test
    void testLrangeNegativeIndices() throws IOException {
        client.execute("RPUSH", "list", "a", "b", "c");
        var r = RedisClient.extractStringList(client.execute("LRANGE", "list", "-2", "-1"));
        assertThat(r).containsExactly("b", "c");
    }
}
