package ssg.legoflow.database.redis.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.database.redis.protocol.RespType;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for string commands via client-server round-trip.
 */
class StringCommandsTest {

    private static RedisServer server;
    private RedisClient client;

    @BeforeAll
    static void startServer() throws IOException {
        server = new RedisServer();
        server.start(0);
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @BeforeEach
    void connect() throws IOException {
        client = new RedisClient("127.0.0.1", server.port());
        client.connect();
        client.execute("FLUSHALL");
    }

    @AfterEach
    void disconnect() {
        client.close();
    }

    @Test
    void testSetAndGet() throws IOException {
        assertThat(client.set("key", "value")).isEqualTo("OK");
        assertThat(client.get("key")).isEqualTo("value");
    }

    @Test
    void testGetNonExistent() throws IOException {
        assertThat(client.get("nonexistent")).isNull();
    }

    @Test
    void testSetNx() throws IOException {
        var r1 = client.execute("SET", "k", "v1", "NX");
        assertThat(RedisClient.extractString(r1)).isEqualTo("OK");

        var r2 = client.execute("SET", "k", "v2", "NX");
        assertThat(r2).isInstanceOf(RespType.BulkString.class);
        assertThat(((RespType.BulkString) r2).asString()).isNull();

        assertThat(client.get("k")).isEqualTo("v1");
    }

    @Test
    void testSetXx() throws IOException {
        var r1 = client.execute("SET", "k", "v1", "XX");
        assertThat(r1).isInstanceOf(RespType.BulkString.class);

        client.set("k", "v1");
        var r2 = client.execute("SET", "k", "v2", "XX");
        assertThat(RedisClient.extractString(r2)).isEqualTo("OK");
        assertThat(client.get("k")).isEqualTo("v2");
    }

    @Test
    void testSetWithEx() throws IOException {
        client.execute("SET", "k", "v", "EX", "100");
        var ttl = RedisClient.extractLong(client.execute("TTL", "k"));
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(100);
    }

    @Test
    void testSetWithPx() throws IOException {
        client.execute("SET", "k", "v", "PX", "100000");
        var pttl = RedisClient.extractLong(client.execute("PTTL", "k"));
        assertThat(pttl).isGreaterThan(0);
    }

    @Test
    void testSetWithGet() throws IOException {
        client.set("k", "old");
        var r = client.execute("SET", "k", "new", "GET");
        assertThat(RedisClient.extractString(r)).isEqualTo("old");
        assertThat(client.get("k")).isEqualTo("new");
    }

    @Test
    void testMsetAndMget() throws IOException {
        client.execute("MSET", "k1", "v1", "k2", "v2", "k3", "v3");
        var r = client.execute("MGET", "k1", "k2", "k3", "nonexistent");
        var list = RedisClient.extractStringList(r);
        assertThat(list).containsExactly("v1", "v2", "v3", null);
    }

    @Test
    void testAppend() throws IOException {
        client.set("k", "hello");
        var len = RedisClient.extractLong(client.execute("APPEND", "k", " world"));
        assertThat(len).isEqualTo(11);
        assertThat(client.get("k")).isEqualTo("hello world");
    }

    @Test
    void testAppendToNonExistent() throws IOException {
        var len = RedisClient.extractLong(client.execute("APPEND", "k", "value"));
        assertThat(len).isEqualTo(5);
        assertThat(client.get("k")).isEqualTo("value");
    }

    @Test
    void testStrlen() throws IOException {
        client.set("k", "hello");
        var len = RedisClient.extractLong(client.execute("STRLEN", "k"));
        assertThat(len).isEqualTo(5);
    }

    @Test
    void testStrlenNonExistent() throws IOException {
        var len = RedisClient.extractLong(client.execute("STRLEN", "k"));
        assertThat(len).isEqualTo(0);
    }

    @Test
    void testIncr() throws IOException {
        client.set("counter", "10");
        var r = RedisClient.extractLong(client.execute("INCR", "counter"));
        assertThat(r).isEqualTo(11);
    }

    @Test
    void testIncrFromZero() throws IOException {
        var r = RedisClient.extractLong(client.execute("INCR", "counter"));
        assertThat(r).isEqualTo(1);
    }

    @Test
    void testDecr() throws IOException {
        client.set("counter", "10");
        var r = RedisClient.extractLong(client.execute("DECR", "counter"));
        assertThat(r).isEqualTo(9);
    }

    @Test
    void testIncrby() throws IOException {
        client.set("counter", "10");
        var r = RedisClient.extractLong(client.execute("INCRBY", "counter", "5"));
        assertThat(r).isEqualTo(15);
    }

    @Test
    void testDecrby() throws IOException {
        client.set("counter", "10");
        var r = RedisClient.extractLong(client.execute("DECRBY", "counter", "3"));
        assertThat(r).isEqualTo(7);
    }

    @Test
    void testIncrbyfloat() throws IOException {
        client.set("pi", "3.0");
        var r = RedisClient.extractString(client.execute("INCRBYFLOAT", "pi", "0.14"));
        assertThat(Double.parseDouble(r)).isCloseTo(3.14, within(0.01));
    }

    @Test
    void testIncrOnNonInteger() throws IOException {
        client.set("k", "not_a_number");
        var r = client.execute("INCR", "k");
        assertThat(r).isInstanceOf(RespType.Error.class);
    }

    @Test
    void testGetset() throws IOException {
        client.set("k", "old");
        var r = RedisClient.extractString(client.execute("GETSET", "k", "new"));
        assertThat(r).isEqualTo("old");
        assertThat(client.get("k")).isEqualTo("new");
    }

    @Test
    void testSetnx() throws IOException {
        var r1 = RedisClient.extractLong(client.execute("SETNX", "k", "v1"));
        assertThat(r1).isEqualTo(1);

        var r2 = RedisClient.extractLong(client.execute("SETNX", "k", "v2"));
        assertThat(r2).isEqualTo(0);

        assertThat(client.get("k")).isEqualTo("v1");
    }

    @Test
    void testSetex() throws IOException {
        client.execute("SETEX", "k", "10", "value");
        assertThat(client.get("k")).isEqualTo("value");
        var ttl = RedisClient.extractLong(client.execute("TTL", "k"));
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(10);
    }

    @Test
    void testPsetex() throws IOException {
        client.execute("PSETEX", "k", "10000", "value");
        assertThat(client.get("k")).isEqualTo("value");
        var pttl = RedisClient.extractLong(client.execute("PTTL", "k"));
        assertThat(pttl).isGreaterThan(0);
    }

    @Test
    void testGetrange() throws IOException {
        client.set("k", "Hello, World!");
        var r = RedisClient.extractString(client.execute("GETRANGE", "k", "0", "4"));
        assertThat(r).isEqualTo("Hello");
    }

    @Test
    void testGetrangeNegativeIndices() throws IOException {
        client.set("k", "Hello");
        var r = RedisClient.extractString(client.execute("GETRANGE", "k", "-5", "-1"));
        assertThat(r).isEqualTo("Hello");
    }

    @Test
    void testSetrange() throws IOException {
        client.set("k", "Hello World");
        var len = RedisClient.extractLong(client.execute("SETRANGE", "k", "6", "Redis"));
        assertThat(len).isEqualTo(11);
        assertThat(client.get("k")).isEqualTo("Hello Redis");
    }

    @Test
    void testGetdel() throws IOException {
        client.set("k", "value");
        var r = RedisClient.extractString(client.execute("GETDEL", "k"));
        assertThat(r).isEqualTo("value");
        assertThat(client.get("k")).isNull();
    }

    @Test
    void testGetdelNonExistent() throws IOException {
        var r = client.execute("GETDEL", "k");
        assertThat(r).isInstanceOf(RespType.BulkString.class);
        assertThat(((RespType.BulkString) r).asString()).isNull();
    }

    private static org.assertj.core.data.Offset<Double> within(double d) {
        return org.assertj.core.data.Offset.offset(d);
    }
}
