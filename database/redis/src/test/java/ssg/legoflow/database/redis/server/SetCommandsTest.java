package ssg.legoflow.database.redis.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.redis.client.RedisClient;
import java.io.IOException;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for set commands.
 */
class SetCommandsTest {

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
    void testSaddAndSmembers() throws IOException {
        var added = RedisClient.extractLong(client.execute("SADD", "s", "a", "b", "c"));
        assertThat(added).isEqualTo(3);
        var members = new HashSet<>(RedisClient.extractStringList(client.execute("SMEMBERS", "s")));
        assertThat(members).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void testSaddDuplicates() throws IOException {
        client.execute("SADD", "s", "a", "b");
        var added = RedisClient.extractLong(client.execute("SADD", "s", "b", "c"));
        assertThat(added).isEqualTo(1);
    }

    @Test
    void testSrem() throws IOException {
        client.execute("SADD", "s", "a", "b", "c");
        var removed = RedisClient.extractLong(client.execute("SREM", "s", "b", "d"));
        assertThat(removed).isEqualTo(1);
    }

    @Test
    void testSismember() throws IOException {
        client.execute("SADD", "s", "a", "b");
        assertThat(RedisClient.extractLong(client.execute("SISMEMBER", "s", "a"))).isEqualTo(1);
        assertThat(RedisClient.extractLong(client.execute("SISMEMBER", "s", "z"))).isEqualTo(0);
    }

    @Test
    void testScard() throws IOException {
        client.execute("SADD", "s", "a", "b", "c");
        assertThat(RedisClient.extractLong(client.execute("SCARD", "s"))).isEqualTo(3);
    }

    @Test
    void testSinter() throws IOException {
        client.execute("SADD", "s1", "a", "b", "c");
        client.execute("SADD", "s2", "b", "c", "d");
        var result = new HashSet<>(RedisClient.extractStringList(client.execute("SINTER", "s1", "s2")));
        assertThat(result).containsExactlyInAnyOrder("b", "c");
    }

    @Test
    void testSunion() throws IOException {
        client.execute("SADD", "s1", "a", "b");
        client.execute("SADD", "s2", "b", "c");
        var result = new HashSet<>(RedisClient.extractStringList(client.execute("SUNION", "s1", "s2")));
        assertThat(result).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void testSdiff() throws IOException {
        client.execute("SADD", "s1", "a", "b", "c");
        client.execute("SADD", "s2", "b", "c", "d");
        var result = new HashSet<>(RedisClient.extractStringList(client.execute("SDIFF", "s1", "s2")));
        assertThat(result).containsExactly("a");
    }

    @Test
    void testSrandmember() throws IOException {
        client.execute("SADD", "s", "a", "b", "c");
        var r = RedisClient.extractString(client.execute("SRANDMEMBER", "s"));
        assertThat(r).isIn("a", "b", "c");
    }

    @Test
    void testSrandmemberCount() throws IOException {
        client.execute("SADD", "s", "a", "b", "c");
        var r = RedisClient.extractStringList(client.execute("SRANDMEMBER", "s", "2"));
        assertThat(r).hasSize(2);
    }

    @Test
    void testSpop() throws IOException {
        client.execute("SADD", "s", "a", "b", "c");
        var popped = RedisClient.extractString(client.execute("SPOP", "s"));
        assertThat(popped).isIn("a", "b", "c");
        assertThat(RedisClient.extractLong(client.execute("SCARD", "s"))).isEqualTo(2);
    }

    @Test
    void testSmove() throws IOException {
        client.execute("SADD", "src", "a", "b");
        client.execute("SADD", "dst", "c");
        var r = RedisClient.extractLong(client.execute("SMOVE", "src", "dst", "a"));
        assertThat(r).isEqualTo(1);
        assertThat(RedisClient.extractLong(client.execute("SISMEMBER", "dst", "a"))).isEqualTo(1);
        assertThat(RedisClient.extractLong(client.execute("SISMEMBER", "src", "a"))).isEqualTo(0);
    }

    @Test
    void testSmoveNonExistent() throws IOException {
        client.execute("SADD", "src", "a");
        var r = RedisClient.extractLong(client.execute("SMOVE", "src", "dst", "z"));
        assertThat(r).isEqualTo(0);
    }
}
