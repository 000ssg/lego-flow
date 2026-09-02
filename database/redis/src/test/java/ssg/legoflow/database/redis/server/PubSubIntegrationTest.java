package ssg.legoflow.database.redis.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.database.redis.protocol.RespType;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
class PubSubIntegrationTest {

    private static RedisServer server;
    private RedisClient client;

    @BeforeAll
    static void startServer() throws IOException {
        server = new RedisServer();
        server.start(0);
    }

    @AfterAll static void stopServer() { server.close(); }

    @BeforeEach void connect() throws IOException {
        client = new RedisClient("127.0.0.1", server.port());
        client.connect();
    }

    @AfterEach void disconnect() { client.close(); }

    @Test void testPublish() throws Exception {
        var result = client.execute("PUBLISH", "test_channel", "hello");
        assertThat(result).isInstanceOf(RespType.Integer.class);
    }

    @Test void testPubSubChannels() throws Exception {
        var result = client.execute("PUBSUB", "CHANNELS");
        assertThat(result).isInstanceOf(RespType.Array.class);
    }

    @Test void testPubSubNumSub() throws Exception {
        var result = client.execute("PUBSUB", "NUMSUB");
        assertThat(result).isInstanceOf(RespType.Array.class);
    }

    @Test void testPubSubNumPat() throws Exception {
        var result = client.execute("PUBSUB", "NUMPAT");
        assertThat(result).isInstanceOf(RespType.Integer.class);
    }

    @Test void testPublishMultipleChannels() throws Exception {
        client.execute("PUBLISH", "ch1", "msg1");
        client.execute("PUBLISH", "ch2", "msg2");
        
        var channels = client.execute("PUBSUB", "CHANNELS");
        assertThat(channels).isInstanceOf(RespType.Array.class);
    }
}
