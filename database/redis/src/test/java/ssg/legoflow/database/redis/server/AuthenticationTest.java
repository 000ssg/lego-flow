package ssg.legoflow.database.redis.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.database.redis.protocol.RespType;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for AUTH command and password authentication.
 */
class AuthenticationTest {

    // ---- Tests with password-protected server ----

    @Test
    void testCommandRejectedWithoutAuth() throws IOException {
        try (var server = new RedisServer("secret123")) {
            server.start(0);
            try (var client = new RedisClient("127.0.0.1", server.port())) {
                client.connect();
                RespType resp = client.execute("SET", "key", "value");
                assertThat(resp).isInstanceOf(RespType.Error.class);
                assertThat(((RespType.Error) resp).fullMessage()).contains("NOAUTH");
            }
        }
    }

    @Test
    void testPingAllowedWithoutAuth() throws IOException {
        try (var server = new RedisServer("secret123")) {
            server.start(0);
            try (var client = new RedisClient("127.0.0.1", server.port())) {
                client.connect();
                String pong = client.ping();
                assertThat(pong).isEqualTo("PONG");
            }
        }
    }

    @Test
    void testAuthWithCorrectPassword() throws IOException {
        try (var server = new RedisServer("secret123")) {
            server.start(0);
            try (var client = new RedisClient("127.0.0.1", server.port())) {
                client.connect();

                RespType authResp = client.execute("AUTH", "secret123");
                assertThat(RedisClient.extractString(authResp)).isEqualTo("OK");

                // Now commands should work
                assertThat(client.set("key", "value")).isEqualTo("OK");
                assertThat(client.get("key")).isEqualTo("value");
            }
        }
    }

    @Test
    void testAuthWithWrongPassword() throws IOException {
        try (var server = new RedisServer("secret123")) {
            server.start(0);
            try (var client = new RedisClient("127.0.0.1", server.port())) {
                client.connect();

                RespType resp = client.execute("AUTH", "wrongpassword");
                assertThat(resp).isInstanceOf(RespType.Error.class);
                assertThat(((RespType.Error) resp).fullMessage()).contains("WRONGPASS");
            }
        }
    }

    @Test
    void testAuthWithoutPasswordSet() throws IOException {
        try (var server = new RedisServer()) {
            server.start(0);
            try (var client = new RedisClient("127.0.0.1", server.port())) {
                client.connect();

                RespType resp = client.execute("AUTH", "anypassword");
                assertThat(resp).isInstanceOf(RespType.Error.class);
                assertThat(((RespType.Error) resp).fullMessage()).contains("no password is set");
            }
        }
    }

    @Test
    void testQuitAllowedWithoutAuth() throws IOException {
        try (var server = new RedisServer("secret123")) {
            server.start(0);
            try (var client = new RedisClient("127.0.0.1", server.port())) {
                client.connect();
                RespType resp = client.execute("QUIT");
                assertThat(resp).isInstanceOf(RespType.SimpleString.class);
                assertThat(((RespType.SimpleString) resp).value()).isEqualTo("OK");
            }
        }
    }

    @Test
    void testMultipleCommandsAfterAuth() throws IOException {
        try (var server = new RedisServer("mypass")) {
            server.start(0);
            try (var client = new RedisClient("127.0.0.1", server.port())) {
                client.connect();
                client.execute("AUTH", "mypass");

                client.set("k1", "v1");
                client.set("k2", "v2");
                assertThat(client.get("k1")).isEqualTo("v1");
                assertThat(client.get("k2")).isEqualTo("v2");
                assertThat(client.del("k1", "k2")).isEqualTo(2);
            }
        }
    }

    @Test
    void testServerPasswordAccessor() {
        try (var server = new RedisServer("testpass")) {
            assertThat(server.password()).isEqualTo("testpass");
        }
        try (var server = new RedisServer()) {
            assertThat(server.password()).isNull();
        }
    }
}
