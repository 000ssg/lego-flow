package ssg.legoflow.database.redis.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.database.redis.protocol.RespType;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Redis server commands (PING, DBSIZE, SELECT, INFO, TIME, KEYS)
 * via client-server round-trip through the existing RedisClient API.
 */
class PubSubCommandsTest {

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
        try {
            if (client != null) client.close();
        } catch (Exception ignored) {}
    }

    @Test
    void testPing() throws Exception {
        String result = client.ping();
        assertThat(result).isEqualTo("PONG");
    }

    @Test
    void testSetAndGetAfterFlushAll() throws Exception {
        assertThat(client.set("flush:key", "value")).isEqualTo("OK");
        assertThat(client.get("flush:key")).isEqualTo("value");
    }

    @Test
    void testDelCommand() throws Exception {
        client.set("delkey", "val");
        long count = client.del("delkey");
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testPingResponse() throws Exception {
        RespType resp = client.execute("PING");
        assertThat(resp).isNotNull();
    }

    @Test
    void testExecuteWithArgs() throws Exception {
        // SET key value using the varargs execute method
        RespType resp = client.execute("SET", "exec:key", "exec:value");
        assertThat(resp).isNotNull();
        
        String val = RedisClient.extractString(client.execute("GET", "exec:key"));
        assertThat(val).isEqualTo("exec:value");
    }

    @Test
    void testReceiveWithoutSend() throws Exception {
        // Receive should handle empty state gracefully
    }

    @Test
    void testIsConnectedAfterConnect() throws Exception {
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testExecuteInfoCommand() throws Exception {
        RespType resp = client.execute("INFO");
        // Should return bulk string with server info
        assertThat(resp).isNotNull();
    }

    @Test
    void testExecuteTimeCommand() throws Exception {
        RespType resp = client.execute("TIME");
        assertThat(resp).isNotNull();
    }

    @Test
    void testSelectDbZero() throws Exception {
        RespType resp = client.execute("SELECT", "0");
        assertThat(resp).isNotNull();
    }

    @Test
    void testKeysCommand() throws Exception {
        client.set("ktest:a", "1");
        RespType keysResp = client.execute("KEYS", "*");
        // Should return array with at least our key
        assertThat(keysResp).isNotNull();
    }

    @Test
    void testDbsizeCommand() throws Exception {
        client.execute("FLUSHALL");
        RespType sizeResp = client.execute("DBSIZE");
        long size = RedisClient.extractLong(sizeResp);
        assertThat(size).isEqualTo(0);
        
        client.set("sizekey", "val");
        sizeResp = client.execute("DBSIZE");
        size = RedisClient.extractLong(sizeResp);
        assertThat(size).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testEchoCommand() throws Exception {
        RespType echoResp = client.execute("ECHO", "hello-redis");
        String echoed = RedisClient.extractString(echoResp);
        assertThat(echoed).isEqualTo("hello-redis");
    }

    @Test
    void testConnectedStateTransitions() throws Exception {
        assertThat(client.isConnected()).isTrue();
        client.close();
        assertThat(client.isConnected()).isFalse();
    }
}
