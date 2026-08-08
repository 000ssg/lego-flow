package ssg.legoflow.database.redis.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.database.redis.protocol.RespType;

import java.io.IOException;
import static org.assertj.core.api.Assertions.*;

/**
 * Extended tests for Key commands (DEL, EXISTS, EXPIRE, KEYS, SCAN, RENAME, etc.)
 * covering uncovered code paths in KeyCommands implementation.
 */
class KeyCommandsExtendedTest {

    private static RedisServer server;
    private RedisClient client;

    @BeforeAll static void start() throws IOException {
        server = new RedisServer();
        server.start(0);
    }

    @AfterAll static void stop() { server.close(); }

    @BeforeEach void connectAndFlush() throws IOException {
        client = new RedisClient("127.0.0.1", server.port());
        client.connect();
        client.execute("FLUSHALL");
    }

    @AfterEach void disconnect() { client.close(); }

    // ===== DEL command with multiple keys =====

    @Test void testDelMultipleKeys() throws IOException {
        client.set("k1", "v1");
        client.set("k2", "v2");
        client.set("k3", "v3");
        
        var r = RedisClient.extractLong(client.execute("DEL", "k1", "k2", "nonexistent"));
        assertThat(r).isEqualTo(2);
    }

    @Test void testDelSingleKey() throws IOException {
        client.set("k", "v");
        var r = RedisClient.extractLong(client.execute("DEL", "k"));
        assertThat(r).isEqualTo(1);
        assertThat(client.get("k")).isNull();
    }

    // ===== EXISTS command =====

    @Test void testExistsMultipleKeys() throws IOException {
        client.set("a", "1");
        var r = RedisClient.extractLong(client.execute("EXISTS", "a", "b", "c"));
        assertThat(r).isEqualTo(1); // only 'a' exists
    }

    @Test void testExistsNoKeys() throws IOException {
        var r = RedisClient.extractLong(client.execute("EXISTS", "nonexistent"));
        assertThat(r).isEqualTo(0);
    }

    // ===== EXPIRE / TTL / PTTL =====

    @Test void testExpireNonExistentKey() throws IOException {
        var r = RedisClient.extractLong(client.execute("EXPIRE", "nope", "100"));
        assertThat(r).isEqualTo(0);
    }

    @Test void testExpireExistingKey() throws IOException {
        client.set("k", "v");
        var r = RedisClient.extractLong(client.execute("EXPIRE", "k", "3600"));
        assertThat(r).isEqualTo(1);
    }

    @Test void testTtlNonExistent() throws IOException {
        var r = RedisClient.extractLong(client.execute("TTL", "nonexistent"));
        assertThat(r).isEqualTo(-2);
    }

    @Test void testTtlWithoutExpiration() throws IOException {
        client.set("k", "v");
        var r = RedisClient.extractLong(client.execute("TTL", "k"));
        assertThat(r).isEqualTo(-1); // no TTL set
    }

    @Test void testPttlNonExistent() throws IOException {
        var r = RedisClient.extractLong(client.execute("PTTL", "nonexistent"));
        assertThat(r).isEqualTo(-2);
    }

    // ===== PERSIST =====

    @Test void testPersistWithExpiration() throws IOException {
        client.set("k", "v");
        client.execute("EXPIRE", "k", "3600");
        
        var r = RedisClient.extractLong(client.execute("PERSIST", "k"));
        assertThat(r).isEqualTo(1);
        var ttl = RedisClient.extractLong(client.execute("TTL", "k"));
        assertThat(ttl).isEqualTo(-1);
    }

    @Test void testPersistNonExistent() throws IOException {
        var r = RedisClient.extractLong(client.execute("PERSIST", "nope"));
        assertThat(r).isEqualTo(0);
    }

    // ===== TYPE command =====

    @Test void testTypeString() throws IOException {
        client.set("strkey", "value");
        var r = RedisClient.extractString(client.execute("TYPE", "strkey"));
        assertThat(r).isEqualTo("string");
    }

    @Test void testTypeList() throws IOException {
        client.execute("LPUSH", "listkey", "item");
        var r = RedisClient.extractString(client.execute("TYPE", "listkey"));
        assertThat(r).isEqualTo("list");
    }

    @Test void testTypeSet() throws IOException {
        client.execute("SADD", "setkey", "member");
        var r = RedisClient.extractString(client.execute("TYPE", "setkey"));
        assertThat(r).isEqualTo("set");
    }

    @Test void testTypeHash() throws IOException {
        client.execute("HSET", "hashkey", "field", "value");
        var r = RedisClient.extractString(client.execute("TYPE", "hashkey"));
        assertThat(r).isEqualTo("hash");
    }

    @Test void testTypeZset() throws IOException {
        client.execute("ZADD", "zkey", "1", "member");
        var r = RedisClient.extractString(client.execute("TYPE", "zkey"));
        assertThat(r).isEqualTo("zset");
    }

    @Test void testTypeNonExistent() throws IOException {
        var r = RedisClient.extractString(client.execute("TYPE", "nope"));
        assertThat(r).isEqualTo("none");
    }

    // ===== KEYS command =====

    @Test void testKeysPattern() throws IOException {
        client.set("user:1", "alice");
        client.set("user:2", "bob");
        client.set("other", "x");
        
        var r = RedisClient.extractStringList(client.execute("KEYS", "user:*"));
        assertThat(r).hasSize(2);
    }

    @Test void testKeysAll() throws IOException {
        client.set("a", "1");
        client.set("b", "2");
        
        var r = RedisClient.extractStringList(client.execute("KEYS", "*"));
        assertThat(r).hasSizeGreaterThanOrEqualTo(2);
    }

    // ===== SCAN command =====

    @Test void testScan() throws IOException {
        client.set("scan1", "v1");
        client.set("scan2", "v2");
        
        var r = client.execute("SCAN", "0");
        assertThat(r).isInstanceOf(RespType.Array.class);
    }

    @Test void testScanWithMatch() throws IOException {
        client.set("match_yes", "1");
        client.set("no_match", "2");
        
        var r = client.execute("SCAN", "0", "MATCH", "match*");
        assertThat(r).isInstanceOf(RespType.Array.class);
    }

    @Test void testScanWithCount() throws IOException {
        var r = client.execute("SCAN", "0", "COUNT", "100");
        assertThat(r).isInstanceOf(RespType.Array.class);
    }

    // ===== RENAME command =====

    @Test void testRenameExistingKey() throws IOException {
        client.set("oldname", "value");
        var r = RedisClient.extractString(client.execute("RENAME", "oldname", "newname"));
        assertThat(r).isEqualTo("OK");
        assertThat(client.get("newname")).isEqualTo("value");
        assertThat(client.get("oldname")).isNull();
    }

    @Test void testRenameNonExistentKey() throws IOException {
        var r = client.execute("RENAME", "nope", "other");
        assertThat(r).isInstanceOf(RespType.Error.class);
    }

    // ===== RANDOMKEY command =====

    @Test void testRandomKeyWithKeys() throws IOException {
        client.set("rk1", "v1");
        client.set("rk2", "v2");
        
        var r = RedisClient.extractString(client.execute("RANDOMKEY"));
        assertThat(r).isIn("rk1", "rk2");
    }

    @Test void testRandomKeyEmptyDatabase() throws IOException {
        var r = client.execute("RANDOMKEY");
        assertThat(((RespType.BulkString) r).asString()).isNull();
    }

    // ===== UNLINK command =====

    @Test void testUnlinkMultipleKeys() throws IOException {
        client.set("u1", "v1");
        client.set("u2", "v2");
        
        var r = RedisClient.extractLong(client.execute("UNLINK", "u1", "u2"));
        assertThat(r).isEqualTo(2);
    }

    // ===== EXPIREAT / PEXPIRE =====

    @Test void testExpireAt() throws IOException {
        client.set("k", "v");
        long futureTs = System.currentTimeMillis() / 1000 + 3600;
        var r = RedisClient.extractLong(client.execute("EXPIREAT", "k", String.valueOf(futureTs)));
        assertThat(r).isEqualTo(1);
    }

    @Test void testPexpire() throws IOException {
        client.set("k", "v");
        var r = RedisClient.extractLong(client.execute("PEXPIRE", "k", "3600000"));
        assertThat(r).isEqualTo(1);
    }

    // ===== TTL edge cases =====

    @Test void testTtlAfterExpiry() throws IOException {
        client.set("k", "v");
        client.execute("EXPIRE", "k", "1");
        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
        
        var r = RedisClient.extractLong(client.execute("TTL", "k"));
        assertThat(r).isEqualTo(-2); // key no longer exists
    }

    // ===== EXISTS after deletion =====

    @Test void testExistsAfterDelete() throws IOException {
        client.set("k", "v");
        var before = RedisClient.extractLong(client.execute("EXISTS", "k"));
        assertThat(before).isEqualTo(1);
        
        client.execute("DEL", "k");
        var after = RedisClient.extractLong(client.execute("EXISTS", "k"));
        assertThat(after).isEqualTo(0);
    }

    // ===== DEL non-existent key returns 0 =====

    @Test void testDelNonExistentReturnsZero() throws IOException {
        var r = RedisClient.extractLong(client.execute("DEL", "nonexistent_key_xyz"));
        assertThat(r).isEqualTo(0);
    }

    // ===== PERSIST without expiration =====

    @Test void testPersistNoExpirationSet() throws IOException {
        client.set("k", "v");
        var r = RedisClient.extractLong(client.execute("PERSIST", "k"));
        assertThat(r).isEqualTo(0); // no expiration to remove
    }
}
