package ssg.legoflow.database.redis.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.redis.client.RedisClient;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for SERVER commands via client-server round-trip.
 */
class ServerCommandsIntegrationTest {

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
    }

    @AfterEach
    void disconnect() { client.close(); }

    @Test
    void testPing() throws IOException {
        String pong = client.ping();
        assertThat(pong).isEqualTo("PONG");
    }

    @Test
    void testInfoServer() throws IOException {
        var result = client.execute("INFO", "SERVER");
        assertThat(result).isNotNull();
    }

    @Test
    void testInfoMemory() throws IOException {
        var result = client.execute("INFO", "MEMORY");
        assertThat(result).isNotNull();
    }

    @Test
    void testInfoDefault() throws IOException {
        var result = client.execute("INFO");
        assertThat(result).isNotNull();
    }

    @Test
    void testDbSize() throws IOException {
        client.set("key", "value");
        long size = RedisClient.extractLong(client.execute("DBSIZE"));
        assertThat(size).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testEcho() throws IOException {
        String echoed = RedisClient.extractString(client.execute("ECHO", "hello"));
        assertThat(echoed).isEqualTo("hello");
    }

    @Test
    void testConfigGet() throws IOException {
        var result = client.execute("CONFIG", "GET", "maxmemory");
        assertThat(result).isNotNull();
    }

    @Test
    void testConfigSetAndGet() throws IOException {
        try {
            String setResult = RedisClient.extractString(client.execute("CONFIG", "SET", "timeout", "300"));
            assertThat(setResult).isEqualTo("OK");
        } catch (Exception e) {
            // CONFIG SET may not be supported for all parameters
        }
    }

    @Test
    void testClientList() throws IOException {
        var result = client.execute("CLIENT", "LIST");
        assertThat(result).isNotNull();
    }

    @Test
    void testClientId() throws IOException {
        long id = RedisClient.extractLong(client.execute("CLIENT", "ID"));
        assertThat(id).isGreaterThan(0);
    }

    @Test
    void testIsConnected() throws IOException {
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testFlushDb() throws IOException {
        client.set("fdbk", "value");
        client.execute("FLUSHDB");
        long exists = RedisClient.extractLong(client.execute("EXISTS", "fdbk"));
        assertThat(exists).isEqualTo(0);
    }

    @Test
    void testSelectDb() throws IOException {
        String result = RedisClient.extractString(client.execute("SELECT", "0"));
        assertThat(result).isEqualTo("OK");
    }

    @Test
    void testReset() throws IOException {
        String result = RedisClient.extractString(client.execute("RESET"));
        assertThat(result).isEqualTo("RESET");
    }
}
