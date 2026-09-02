package ssg.legoflow.database.redis.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.database.redis.protocol.RespType;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for HyperLogLog commands: PFADD, PFCOUNT, PFMERGE.
 */
class HyperLogLogTest {

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
    void testPfaddNewElements() throws IOException {
        long result = RedisClient.extractLong(client.execute("PFADD", "hll", "a", "b", "c"));
        assertThat(result).isEqualTo(1);
    }

    @Test
    void testPfaddDuplicateElements() throws IOException {
        client.execute("PFADD", "hll", "a", "b", "c");
        long result = RedisClient.extractLong(client.execute("PFADD", "hll", "a", "b", "c"));
        assertThat(result).isEqualTo(0);
    }

    @Test
    void testPfcountSingleKey() throws IOException {
        client.execute("PFADD", "hll", "a", "b", "c", "d", "e");
        long count = RedisClient.extractLong(client.execute("PFCOUNT", "hll"));
        assertThat(count).isEqualTo(5);
    }

    @Test
    void testPfcountNonexistentKey() throws IOException {
        long count = RedisClient.extractLong(client.execute("PFCOUNT", "nonexistent"));
        assertThat(count).isEqualTo(0);
    }

    @Test
    void testPfcountMultipleKeys() throws IOException {
        client.execute("PFADD", "hll1", "a", "b", "c");
        client.execute("PFADD", "hll2", "c", "d", "e");
        long count = RedisClient.extractLong(client.execute("PFCOUNT", "hll1", "hll2"));
        assertThat(count).isEqualTo(5); // union of {a,b,c} and {c,d,e}
    }

    @Test
    void testPfmerge() throws IOException {
        client.execute("PFADD", "hll1", "a", "b", "c");
        client.execute("PFADD", "hll2", "c", "d", "e");
        RespType mergeResult = client.execute("PFMERGE", "dest", "hll1", "hll2");
        assertThat(RedisClient.extractString(mergeResult)).isEqualTo("OK");

        long count = RedisClient.extractLong(client.execute("PFCOUNT", "dest"));
        assertThat(count).isEqualTo(5);
    }

    @Test
    void testPfmergeWithNonexistentSource() throws IOException {
        client.execute("PFADD", "hll1", "a", "b");
        client.execute("PFMERGE", "dest", "hll1", "nonexistent");
        long count = RedisClient.extractLong(client.execute("PFCOUNT", "dest"));
        assertThat(count).isEqualTo(2);
    }

    @Test
    void testPfaddEmptyElements() throws IOException {
        // PFADD with just the key and no elements should create empty HLL
        long result = RedisClient.extractLong(client.execute("PFADD", "hll"));
        assertThat(result).isEqualTo(0);
        long count = RedisClient.extractLong(client.execute("PFCOUNT", "hll"));
        assertThat(count).isEqualTo(0);
    }

    @Test
    void testPfcountAccuracyLargeSet() throws IOException {
        // Add 10000 unique elements
        int total = 10000;
        for (int i = 0; i < total; i += 50) {
            String[] args = new String[Math.min(52, total - i + 2)];
            args[0] = "PFADD";
            args[1] = "hll";
            for (int j = 0; j < args.length - 2 && i + j < total; j++) {
                args[j + 2] = "element:" + (i + j);
            }
            client.execute(args);
        }

        long count = RedisClient.extractLong(client.execute("PFCOUNT", "hll"));
        // Should be within 5% of actual count
        double errorRate = Math.abs(count - total) / (double) total;
        assertThat(errorRate)
                .as("HyperLogLog error rate for %d elements: estimated=%d, error=%.2f%%",
                        total, count, errorRate * 100)
                .isLessThan(0.05);
    }

    @Test
    void testPfaddReturnValueOnNewVsExisting() throws IOException {
        // First add should return 1 (changed)
        long r1 = RedisClient.extractLong(client.execute("PFADD", "hll", "x"));
        assertThat(r1).isEqualTo(1);

        // Adding new element should return 1
        long r2 = RedisClient.extractLong(client.execute("PFADD", "hll", "y"));
        assertThat(r2).isEqualTo(1);

        // Adding same element should return 0
        long r3 = RedisClient.extractLong(client.execute("PFADD", "hll", "x"));
        assertThat(r3).isEqualTo(0);
    }
}
