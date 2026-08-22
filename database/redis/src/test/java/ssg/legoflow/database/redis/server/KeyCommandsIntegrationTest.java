package ssg.legoflow.database.redis.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.database.redis.protocol.RespType;
import java.io.IOException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for KEY commands via client-server round-trip.
 */
class KeyCommandsIntegrationTest {

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
    void testExistsTrue() throws IOException {
        client.set("key1", "value1");
        long exists = RedisClient.extractLong(client.execute("EXISTS", "key1"));
        assertThat(exists).isEqualTo(1);
    }

    @Test
    void testExistsFalse() throws IOException {
        long exists = RedisClient.extractLong(client.execute("EXISTS", "nonexistent"));
        assertThat(exists).isEqualTo(0);
    }

    @Test
    void testExistsMultiple() throws IOException {
        client.set("e1", "v1");
        client.set("e2", "v2");
        long count = RedisClient.extractLong(client.execute("EXISTS", "e1", "e2", "e3"));
        assertThat(count).isEqualTo(2);
    }

    @Test
    void testDelSingleKey() throws IOException {
        client.set("todel", "data");
        long count = client.del("todel");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testDelNonExistent() throws IOException {
        long count = client.del("nonexistent");
        assertThat(count).isEqualTo(0);
    }

    @Test
    void testTypeString() throws IOException {
        client.set("strkey", "text");
        String type = RedisClient.extractString(client.execute("TYPE", "strkey"));
        assertThat(type.toLowerCase()).contains("string");
    }

    @Test
    void testTypeNonExistent() throws IOException {
        String type = RedisClient.extractString(client.execute("TYPE", "nonexistent"));
        assertThat(type.toLowerCase()).contains("none");
    }

    @Test
    void testExpireAndTtl() throws IOException {
        client.set("tempkey", "data");
        long ok = RedisClient.extractLong(client.execute("EXPIRE", "tempkey", "60"));
        assertThat(ok).isEqualTo(1);
        long ttl = RedisClient.extractLong(client.execute("TTL", "tempkey"));
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(60);
    }

    @Test
    void testExpireNonExistent() throws IOException {
        long ok = RedisClient.extractLong(client.execute("EXPIRE", "nonexistent", "60"));
        assertThat(ok).isEqualTo(0);
    }

    @Test
    void testTtlNoExpiry() throws IOException {
        client.set("key", "value");
        long ttl = RedisClient.extractLong(client.execute("TTL", "key"));
        assertThat(ttl).isEqualTo(-1);
    }

    @Test
    void testTtlNonExistent() throws IOException {
        long ttl = RedisClient.extractLong(client.execute("TTL", "nonexistent"));
        assertThat(ttl).isEqualTo(-2);
    }

    @Test
    void testPersist() throws IOException {
        client.set("key", "value");
        client.execute("EXPIRE", "key", "3600");
        long ok = RedisClient.extractLong(client.execute("PERSIST", "key"));
        assertThat(ok).isEqualTo(1);
        long ttl = RedisClient.extractLong(client.execute("TTL", "key"));
        assertThat(ttl).isEqualTo(-1);
    }

    @Test
    void testRandomKey() throws IOException {
        client.set("a", "1");
        client.set("b", "2");
        String key = RedisClient.extractString(client.execute("RANDOMKEY"));
        assertThat(key).isIn("a", "b");
    }

    @Test
    void testKeysPattern() throws IOException {
        client.set("user:1", "alice");
        client.set("user:2", "bob");
        client.set("post:1", "hello");
        
        List<String> keys = RedisClient.extractStringList(client.execute("KEYS", "user:*"));
        assertThat(keys).hasSize(2);
    }

    @Test
    void testKeysNoMatch() throws IOException {
        List<String> keys = RedisClient.extractStringList(client.execute("KEYS", "nomatch*"));
        assertThat(keys).isEmpty();
    }

    @Test
    void testRename() throws IOException {
        client.set("oldname", "data");
        String ok = RedisClient.extractString(client.execute("RENAME", "oldname", "newname"));
        assertThat(ok).isEqualTo("OK");
        assertThat(client.get("newname")).isEqualTo("data");
    }

    @Test
    void testPExpireAndPTtl() throws IOException {
        client.set("pkey", "pdata");
        long ok = RedisClient.extractLong(client.execute("PEXPIRE", "pkey", "60000"));
        assertThat(ok).isEqualTo(1);
        long pttl = RedisClient.extractLong(client.execute("PTTL", "pkey"));
        assertThat(pttl).isGreaterThan(0);
    }

    @Test
    void testScan() throws IOException {
        client.set("scan:a", "1");
        client.set("scan:b", "2");
        
        RespType result = client.execute("SCAN", "0", "COUNT", "10");
        assertThat(result).isNotNull();
    }

    @Test
    void testDelMultiple() throws IOException {
        client.set("dk1", "v1");
        client.set("dk2", "v2");
        long count = client.del("dk1", "dk2");
        assertThat(count).isEqualTo(2);
    }

    @Test
    void testPing() throws IOException {
        String pong = client.ping();
        assertThat(pong).isEqualTo("PONG");
    }

    @Test
    void testFlushAll() throws IOException {
        client.set("fk1", "v1");
        client.execute("FLUSHALL");
        long exists = RedisClient.extractLong(client.execute("EXISTS", "fk1"));
        assertThat(exists).isEqualTo(0);
    }

    @Test
    void testSetGetNull() throws IOException {
        assertThat(client.get("nonexistent")).isNull();
    }

    @Test
    void testSetOverwrite() throws IOException {
        client.set("ow", "first");
        String result = client.set("ow", "second");
        assertThat(result).isEqualTo("OK");
        assertThat(client.get("ow")).isEqualTo("second");
    }
}
