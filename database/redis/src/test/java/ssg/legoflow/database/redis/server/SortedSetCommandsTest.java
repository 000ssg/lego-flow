package ssg.legoflow.database.redis.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.redis.client.RedisClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for sorted set commands.
 */
class SortedSetCommandsTest {

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
    void testZaddAndZrange() throws IOException {
        client.execute("ZADD", "zs", "1", "a", "2", "b", "3", "c");
        var r = RedisClient.extractStringList(client.execute("ZRANGE", "zs", "0", "-1"));
        assertThat(r).containsExactly("a", "b", "c");
    }

    @Test
    void testZaddReturnsAddedCount() throws IOException {
        var added = RedisClient.extractLong(client.execute("ZADD", "zs", "1", "a", "2", "b"));
        assertThat(added).isEqualTo(2);
        // Update existing
        added = RedisClient.extractLong(client.execute("ZADD", "zs", "3", "a", "4", "c"));
        assertThat(added).isEqualTo(1); // only c is new
    }

    @Test
    void testZaddNx() throws IOException {
        client.execute("ZADD", "zs", "1", "a");
        client.execute("ZADD", "zs", "NX", "2", "a", "3", "b");
        assertThat(RedisClient.extractString(client.execute("ZSCORE", "zs", "a"))).isEqualTo("1.0");
        assertThat(RedisClient.extractString(client.execute("ZSCORE", "zs", "b"))).isEqualTo("3.0");
    }

    @Test
    void testZrem() throws IOException {
        client.execute("ZADD", "zs", "1", "a", "2", "b", "3", "c");
        var removed = RedisClient.extractLong(client.execute("ZREM", "zs", "b", "d"));
        assertThat(removed).isEqualTo(1);
    }

    @Test
    void testZscore() throws IOException {
        client.execute("ZADD", "zs", "1.5", "a");
        var score = RedisClient.extractString(client.execute("ZSCORE", "zs", "a"));
        assertThat(Double.parseDouble(score)).isEqualTo(1.5);
    }

    @Test
    void testZscoreNonExistent() throws IOException {
        var r = client.execute("ZSCORE", "zs", "a");
        assertThat(r).isInstanceOf(ssg.legoflow.database.redis.protocol.RespType.BulkString.class);
    }

    @Test
    void testZrank() throws IOException {
        client.execute("ZADD", "zs", "1", "a", "2", "b", "3", "c");
        assertThat(RedisClient.extractLong(client.execute("ZRANK", "zs", "a"))).isEqualTo(0);
        assertThat(RedisClient.extractLong(client.execute("ZRANK", "zs", "c"))).isEqualTo(2);
    }

    @Test
    void testZrevrank() throws IOException {
        client.execute("ZADD", "zs", "1", "a", "2", "b", "3", "c");
        assertThat(RedisClient.extractLong(client.execute("ZREVRANK", "zs", "c"))).isEqualTo(0);
        assertThat(RedisClient.extractLong(client.execute("ZREVRANK", "zs", "a"))).isEqualTo(2);
    }

    @Test
    void testZrangeWithScores() throws IOException {
        client.execute("ZADD", "zs", "1", "a", "2", "b");
        var r = RedisClient.extractStringList(client.execute("ZRANGE", "zs", "0", "-1", "WITHSCORES"));
        assertThat(r).containsExactly("a", "1.0", "b", "2.0");
    }

    @Test
    void testZrangebyscore() throws IOException {
        client.execute("ZADD", "zs", "1", "a", "2", "b", "3", "c", "4", "d");
        var r = RedisClient.extractStringList(client.execute("ZRANGEBYSCORE", "zs", "2", "3"));
        assertThat(r).containsExactly("b", "c");
    }

    @Test
    void testZrangebyscoreExclusive() throws IOException {
        client.execute("ZADD", "zs", "1", "a", "2", "b", "3", "c");
        var r = RedisClient.extractStringList(client.execute("ZRANGEBYSCORE", "zs", "(1", "3"));
        assertThat(r).containsExactly("b", "c");
    }

    @Test
    void testZcard() throws IOException {
        client.execute("ZADD", "zs", "1", "a", "2", "b");
        assertThat(RedisClient.extractLong(client.execute("ZCARD", "zs"))).isEqualTo(2);
    }

    @Test
    void testZcount() throws IOException {
        client.execute("ZADD", "zs", "1", "a", "2", "b", "3", "c", "4", "d");
        assertThat(RedisClient.extractLong(client.execute("ZCOUNT", "zs", "2", "3"))).isEqualTo(2);
    }

    @Test
    void testZincrby() throws IOException {
        client.execute("ZADD", "zs", "5", "a");
        var r = RedisClient.extractString(client.execute("ZINCRBY", "zs", "3", "a"));
        assertThat(Double.parseDouble(r)).isEqualTo(8.0);
    }

    @Test
    void testZinterstore() throws IOException {
        client.execute("ZADD", "zs1", "1", "a", "2", "b", "3", "c");
        client.execute("ZADD", "zs2", "10", "b", "20", "c", "30", "d");
        var count = RedisClient.extractLong(client.execute("ZINTERSTORE", "out", "2", "zs1", "zs2"));
        assertThat(count).isEqualTo(2);
        var r = RedisClient.extractStringList(client.execute("ZRANGE", "out", "0", "-1", "WITHSCORES"));
        // b: 2+10=12, c: 3+20=23
        assertThat(r).containsExactly("b", "12.0", "c", "23.0");
    }

    @Test
    void testZunionstore() throws IOException {
        client.execute("ZADD", "zs1", "1", "a", "2", "b");
        client.execute("ZADD", "zs2", "3", "b", "4", "c");
        var count = RedisClient.extractLong(client.execute("ZUNIONSTORE", "out", "2", "zs1", "zs2"));
        assertThat(count).isEqualTo(3);
    }

    @Test
    void testZpopmin() throws IOException {
        client.execute("ZADD", "zs", "3", "c", "1", "a", "2", "b");
        var r = RedisClient.extractStringList(client.execute("ZPOPMIN", "zs"));
        assertThat(r.get(0)).isEqualTo("a");
        assertThat(Double.parseDouble(r.get(1))).isEqualTo(1.0);
    }

    @Test
    void testZpopmax() throws IOException {
        client.execute("ZADD", "zs", "3", "c", "1", "a", "2", "b");
        var r = RedisClient.extractStringList(client.execute("ZPOPMAX", "zs"));
        assertThat(r.get(0)).isEqualTo("c");
        assertThat(Double.parseDouble(r.get(1))).isEqualTo(3.0);
    }
}
