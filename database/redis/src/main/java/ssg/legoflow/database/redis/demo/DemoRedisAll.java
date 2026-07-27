package ssg.legoflow.database.redis.demo;

import ssg.legoflow.database.redis.client.ClusterClient;
import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.database.redis.client.RedisPipeline;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.RedisServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive demo of all Redis module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link RedisServer}</b> — No external dependencies.
 * Runs anywhere without installation. Supports 16 databases, RESP2/RESP3 protocol,
 * all core data types (strings, lists, hashes, sets, sorted sets, streams),
 * pipelining, pub/sub, transactions (MULTI/EXEC/WATCH),
 * TTL expiration, and cluster-aware client with MOVED/ASK redirect handling.
 * Ideal for development, testing, CI/CD, and learning the Redis protocol.</p>
 *
 * <p><b>Alternative: External Redis, Valkey, KeyDB, or Dragonfly</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production load testing with persistence (RDB/AOF)</li>
 *   <li>Multi-node replication and Redis Cluster sharding</li>
 *   <li>Lua scripting and server-side functions (EVAL/EVALSHA)</li>
 *   <li>Integration testing against a real Redis deployment</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the server lifecycle (start/stop).
 * All client code (SET/GET, pipelining, pub/sub, transactions) uses the same API
 * regardless of backend. When {@code USE_EXTERNAL=true}, the demo skips server
 * creation and connects directly to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>SET/GET/DEL — basic string operations</li>
 *   <li>TTL expiration — key expiry with EX option</li>
 *   <li>Pipelining — batched command execution for reduced latency</li>
 *   <li>Pub/Sub — publish and subscribe messaging</li>
 *   <li>Data types — strings, lists, hashes, sets</li>
 *   <li>Transactions — MULTI/EXEC atomic command execution</li>
 *   <li>Cluster client — MOVED/ASK redirect handling with hash slot routing</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoRedisAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoRedisAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house RedisServer (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for Redis/Valkey
    // =========================================================================

    /** Set to {@code true} to connect to an external Redis server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external Redis server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "127.0.0.1";

    /** Port for external Redis server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 6379;

    private DemoRedisAll() {}

    /**
     * Results from running the full demo.
     *
     * @param setGetDel         true if SET/GET/DEL cycle succeeded
     * @param ttlExpiration     true if TTL was set and verified
     * @param pipelineResponses number of pipelined responses received
     * @param pubSubMessages    number of pub/sub messages received
     * @param dataTypeOps       true if list, hash, and set operations succeeded
     * @param transactionResult true if MULTI/EXEC transaction succeeded
     * @param clusterClient     true if cluster client operations succeeded
     * @param authentication    true if authentication demo succeeded
     * @param hyperLogLog       true if HyperLogLog demo succeeded
     * @param geoCommands       true if geo commands demo succeeded
     */
    public record Results(
            boolean setGetDel,
            boolean ttlExpiration,
            int pipelineResponses,
            int pubSubMessages,
            boolean dataTypeOps,
            boolean transactionResult,
            boolean clusterClient,
            boolean authentication,
            boolean hyperLogLog,
            boolean geoCommands
    ) {}

    /**
     * Runs the comprehensive demo covering all Redis features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runWithExternalServer(EXTERNAL_HOST, EXTERNAL_PORT);
        }

        // Run authentication demo with dedicated password-protected server
        boolean auth = demoAuthentication();

        try (var server = new RedisServer()) {
            server.start(0);
            int port = server.port();
            LOG.info("In-house RedisServer started on port {}", port);

            boolean setGetDel = demoSetGetDel("127.0.0.1", port);
            boolean ttl = demoTtlExpiration("127.0.0.1", port);
            int pipelineCount = demoPipelining("127.0.0.1", port);
            int pubSubCount = demoPubSub("127.0.0.1", port);
            boolean dataTypes = demoDataTypes("127.0.0.1", port);
            boolean txn = demoTransactions("127.0.0.1", port);
            boolean cluster = demoClusterClient("127.0.0.1", port);
            boolean hll = demoHyperLogLog("127.0.0.1", port);
            boolean geo = demoGeoCommands("127.0.0.1", port);

            return new Results(setGetDel, ttl, pipelineCount, pubSubCount,
                    dataTypes, txn, cluster, auth, hll, geo);
        }
    }

    private static Results runWithExternalServer(String host, int port) throws Exception {
        boolean setGetDel = demoSetGetDel(host, port);
        boolean ttl = demoTtlExpiration(host, port);
        int pipelineCount = demoPipelining(host, port);
        int pubSubCount = demoPubSub(host, port);
        boolean dataTypes = demoDataTypes(host, port);
        boolean txn = demoTransactions(host, port);
        boolean cluster = demoClusterClient(host, port);

        return new Results(setGetDel, ttl, pipelineCount, pubSubCount,
                dataTypes, txn, cluster, false, false, false);
    }

    // ======================== 1. SET/GET/DEL =================================

    /**
     * Demonstrates basic SET, GET, and DEL string operations.
     */
    static boolean demoSetGetDel(String host, int port) throws IOException {
        LOG.info("=== 1. SET/GET/DEL ===");
        try (var client = new RedisClient(host, port)) {
            client.connect();

            // SET and GET
            String setResult = client.set("demo:greeting", "Hello, Redis!");
            String value = client.get("demo:greeting");
            LOG.info("SET result={}, GET value={}", setResult, value);

            // DEL
            long deleted = client.del("demo:greeting");
            String afterDel = client.get("demo:greeting");
            LOG.info("DEL count={}, after DEL={}", deleted, afterDel);

            return "OK".equals(setResult) && "Hello, Redis!".equals(value)
                    && deleted == 1 && afterDel == null;
        }
    }

    // ======================== 2. TTL EXPIRATION ==============================

    /**
     * Demonstrates key TTL expiration using the EX option on SET.
     * <p>
     * Keys can be set with an expiration time in seconds (EX) or milliseconds (PX).
     * The TTL command reports remaining time-to-live.
     */
    static boolean demoTtlExpiration(String host, int port) throws IOException {
        LOG.info("=== 2. TTL Expiration ===");
        try (var client = new RedisClient(host, port)) {
            client.connect();

            // SET with EX (expire in 3600 seconds)
            client.execute("SET", "demo:session", "session-data", "EX", "3600");

            // Check TTL
            RespType ttlResp = client.execute("TTL", "demo:session");
            long ttl = RedisClient.extractLong(ttlResp);
            LOG.info("TTL for demo:session = {} seconds", ttl);

            // Verify key exists
            String value = client.get("demo:session");

            // Cleanup
            client.del("demo:session");

            return ttl > 0 && ttl <= 3600 && "session-data".equals(value);
        }
    }

    // ======================== 3. PIPELINING ==================================

    /**
     * Demonstrates pipelining: batch multiple commands in a single TCP write
     * and read all responses in order. Reduces round-trip latency.
     */
    static int demoPipelining(String host, int port) throws IOException {
        LOG.info("=== 3. Pipelining ===");
        try (var client = new RedisClient(host, port)) {
            client.connect();

            RedisPipeline pipeline = client.pipeline();
            pipeline.add("SET", "demo:p1", "value1");
            pipeline.add("SET", "demo:p2", "value2");
            pipeline.add("SET", "demo:p3", "value3");
            pipeline.add("GET", "demo:p1");
            pipeline.add("GET", "demo:p2");
            pipeline.add("GET", "demo:p3");
            pipeline.add("DEL", "demo:p1", "demo:p2", "demo:p3");

            List<RespType> responses = pipeline.execute();
            LOG.info("Pipeline returned {} responses", responses.size());

            for (int i = 0; i < responses.size(); i++) {
                LOG.info("  Pipeline response[{}]: {}", i, responses.get(i));
            }

            return responses.size();
        }
    }

    // ======================== 4. PUB/SUB ====================================

    /**
     * Demonstrates Redis pub/sub messaging.
     * <p>
     * One client subscribes to channels, another publishes messages.
     * The subscriber receives messages asynchronously via the RESP push protocol.
     */
    static int demoPubSub(String host, int port) throws Exception {
        LOG.info("=== 4. Pub/Sub ===");
        List<String> received = new ArrayList<>();
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch messagesReceived = new CountDownLatch(2);

        // Subscriber in a virtual thread
        Thread subscriberThread = Thread.startVirtualThread(() -> {
            try (var subscriber = new RedisClient(host, port)) {
                subscriber.connect();
                // SUBSCRIBE to 2 channels — the server sends one subscribe confirmation
                // per channel, but execute() only reads the first response. Read the
                // second subscribe confirmation before waiting for messages.
                subscriber.execute("SUBSCRIBE", "demo:news", "demo:alerts");
                subscriber.receive(); // second subscribe confirmation
                subscribed.countDown();

                // Read messages until we have 2
                while (received.size() < 2) {
                    var response = subscriber.receive();
                    if (response instanceof RespType.Array arr
                            && arr.elements() != null && arr.elements().size() >= 3) {
                        String type = RedisClient.extractString(arr.elements().get(0));
                        if ("message".equals(type)) {
                            String msg = RedisClient.extractString(arr.elements().get(2));
                            received.add(msg);
                            messagesReceived.countDown();
                        }
                    }
                }
            } catch (IOException e) {
                // ignore on close
            }
        });

        subscribed.await(2, TimeUnit.SECONDS);
        Thread.sleep(50);

        // Publisher
        try (var publisher = new RedisClient(host, port)) {
            publisher.connect();
            publisher.execute("PUBLISH", "demo:news", "Breaking news!");
            publisher.execute("PUBLISH", "demo:alerts", "System alert!");
        }

        messagesReceived.await(2, TimeUnit.SECONDS);
        LOG.info("Pub/sub received {} messages", received.size());
        return received.size();
    }

    // ======================== 5. DATA TYPES =================================

    /**
     * Demonstrates Redis data types: lists (LPUSH/LRANGE), hashes (HSET/HGET),
     * and sets (SADD/SMEMBERS).
     */
    static boolean demoDataTypes(String host, int port) throws IOException {
        LOG.info("=== 5. Data Types ===");
        try (var client = new RedisClient(host, port)) {
            client.connect();

            // Lists
            client.execute("LPUSH", "demo:list", "c", "b", "a");
            RespType listResp = client.execute("LRANGE", "demo:list", "0", "-1");
            List<String> listValues = RedisClient.extractStringList(listResp);
            LOG.info("List: {}", listValues);
            boolean listOk = listValues.size() == 3;

            // Hashes
            client.execute("HSET", "demo:hash", "name", "Alice", "age", "30");
            String hashName = RedisClient.extractString(
                    client.execute("HGET", "demo:hash", "name"));
            String hashAge = RedisClient.extractString(
                    client.execute("HGET", "demo:hash", "age"));
            LOG.info("Hash: name={}, age={}", hashName, hashAge);
            boolean hashOk = "Alice".equals(hashName) && "30".equals(hashAge);

            // Sets
            client.execute("SADD", "demo:set", "apple", "banana", "cherry");
            RespType membersResp = client.execute("SMEMBERS", "demo:set");
            List<String> members = RedisClient.extractStringList(membersResp);
            LOG.info("Set members: {}", members);
            boolean setOk = members.size() == 3;

            // Cleanup
            client.del("demo:list", "demo:hash", "demo:set");

            return listOk && hashOk && setOk;
        }
    }

    // ======================== 6. TRANSACTIONS ================================

    /**
     * Demonstrates Redis transactions using MULTI/EXEC.
     * <p>
     * Commands between MULTI and EXEC are queued and executed atomically.
     * All commands succeed or fail together.
     */
    static boolean demoTransactions(String host, int port) throws IOException {
        LOG.info("=== 6. Transactions (MULTI/EXEC) ===");
        try (var client = new RedisClient(host, port)) {
            client.connect();

            // Start transaction
            RespType multiResp = client.execute("MULTI");
            LOG.info("MULTI: {}", RedisClient.extractString(multiResp));

            // Queue commands (all return +QUEUED)
            client.execute("SET", "demo:tx1", "value1");
            client.execute("SET", "demo:tx2", "value2");
            client.execute("INCR", "demo:tx-counter");

            // Execute atomically
            RespType execResp = client.execute("EXEC");
            LOG.info("EXEC result: {}", execResp);

            // Verify
            String v1 = client.get("demo:tx1");
            String v2 = client.get("demo:tx2");
            String counter = client.get("demo:tx-counter");
            LOG.info("After EXEC: tx1={}, tx2={}, counter={}", v1, v2, counter);

            // Cleanup
            client.del("demo:tx1", "demo:tx2", "demo:tx-counter");

            return "value1".equals(v1) && "value2".equals(v2) && "1".equals(counter);
        }
    }

    // ======================== 7. CLUSTER CLIENT ===============================

    /**
     * Demonstrates the cluster-aware client with hash slot routing.
     * <p>
     * {@link ClusterClient} calculates CRC16 hash slots for keys and routes
     * commands to the correct node. It handles MOVED/ASK redirects automatically.
     * <p>
     * <b>Note:</b> With the in-house single-node server, all slots map to the
     * same node. The cluster client still validates routing logic and redirect handling.
     */
    static boolean demoClusterClient(String host, int port) throws IOException {
        LOG.info("=== 7. Cluster Client ===");
        try (var cluster = new ClusterClient(host, port)) {
            cluster.connect();

            // Execute commands through cluster client
            cluster.execute("SET", "demo:cluster-key", "cluster-value");
            RespType getResp = cluster.execute("GET", "demo:cluster-key");
            String value = RedisClient.extractString(getResp);
            LOG.info("Cluster GET: {}", value);

            // Cleanup
            cluster.execute("DEL", "demo:cluster-key");

            return "cluster-value".equals(value);
        }
    }

    // ======================== 8. AUTHENTICATION ================================

    /**
     * Demonstrates Redis AUTH command with password-protected server.
     * <p>
     * Creates a server with a password, verifies that commands are rejected
     * without authentication, authenticates, and then verifies commands work.
     */
    static boolean demoAuthentication() throws Exception {
        LOG.info("=== 8. Authentication (AUTH) ===");
        try (var server = new RedisServer("demo-password")) {
            server.start(0);
            int port = server.port();

            try (var client = new RedisClient("127.0.0.1", port)) {
                client.connect();

                // Command should fail without auth
                RespType noAuthResp = client.execute("SET", "key", "value");
                boolean rejected = noAuthResp instanceof RespType.Error err
                        && err.fullMessage().contains("NOAUTH");
                LOG.info("Command without auth rejected: {}", rejected);

                // Wrong password
                RespType wrongResp = client.execute("AUTH", "wrong-password");
                boolean wrongRejected = wrongResp instanceof RespType.Error;
                LOG.info("Wrong password rejected: {}", wrongRejected);

                // Correct password
                RespType authResp = client.execute("AUTH", "demo-password");
                boolean authOk = "OK".equals(RedisClient.extractString(authResp));
                LOG.info("Auth with correct password: {}", authOk);

                // Now commands should work
                String setResult = client.set("demo:auth-key", "secure-value");
                String value = client.get("demo:auth-key");
                boolean commandOk = "OK".equals(setResult) && "secure-value".equals(value);
                LOG.info("Command after auth: set={}, get={}", setResult, value);

                client.del("demo:auth-key");

                return rejected && wrongRejected && authOk && commandOk;
            }
        }
    }

    // ======================== 9. HYPERLOGLOG ====================================

    /**
     * Demonstrates HyperLogLog probabilistic cardinality estimation.
     * <p>
     * PFADD adds elements, PFCOUNT estimates cardinality, and PFMERGE
     * combines multiple HyperLogLog structures.
     */
    static boolean demoHyperLogLog(String host, int port) throws IOException {
        LOG.info("=== 9. HyperLogLog ===");
        try (var client = new RedisClient(host, port)) {
            client.connect();

            // PFADD elements to two sets
            client.execute("PFADD", "hll:set1", "a", "b", "c", "d", "e");
            client.execute("PFADD", "hll:set2", "d", "e", "f", "g", "h");

            // PFCOUNT single key
            long count1 = RedisClient.extractLong(client.execute("PFCOUNT", "hll:set1"));
            long count2 = RedisClient.extractLong(client.execute("PFCOUNT", "hll:set2"));
            LOG.info("HLL set1 count={}, set2 count={}", count1, count2);

            // PFMERGE
            String mergeResult = RedisClient.extractString(
                    client.execute("PFMERGE", "hll:merged", "hll:set1", "hll:set2"));
            long mergedCount = RedisClient.extractLong(
                    client.execute("PFCOUNT", "hll:merged"));
            LOG.info("PFMERGE result={}, merged count={}", mergeResult, mergedCount);

            // Cleanup
            client.del("hll:set1", "hll:set2", "hll:merged");

            // Union of {a,b,c,d,e} and {d,e,f,g,h} = {a,b,c,d,e,f,g,h} = 8
            return count1 == 5 && count2 == 5 && "OK".equals(mergeResult) && mergedCount == 8;
        }
    }

    // ======================== 10. GEO COMMANDS ==================================

    /**
     * Demonstrates geospatial commands: GEOADD, GEODIST, GEOPOS, GEOSEARCH.
     * <p>
     * Uses real city coordinates to demonstrate distance calculation and
     * radius-based search.
     */
    static boolean demoGeoCommands(String host, int port) throws IOException {
        LOG.info("=== 10. Geo Commands ===");
        try (var client = new RedisClient(host, port)) {
            client.connect();

            // GEOADD cities
            long added = RedisClient.extractLong(client.execute("GEOADD", "demo:cities",
                    "2.3522", "48.8566", "Paris",
                    "-0.1278", "51.5074", "London",
                    "13.4050", "52.5200", "Berlin"));
            LOG.info("GEOADD: added {} cities", added);

            // GEODIST Paris-London in km
            String distStr = RedisClient.extractString(
                    client.execute("GEODIST", "demo:cities", "Paris", "London", "km"));
            double dist = Double.parseDouble(distStr);
            LOG.info("GEODIST Paris-London: {} km", distStr);

            // GEOPOS Paris
            RespType posResp = client.execute("GEOPOS", "demo:cities", "Paris");
            LOG.info("GEOPOS Paris: {}", posResp);

            // GEOSEARCH within 500km of Paris
            RespType searchResp = client.execute("GEOSEARCH", "demo:cities",
                    "FROMLONLAT", "2.3522", "48.8566",
                    "BYRADIUS", "500", "km", "ASC");
            List<String> nearby = RedisClient.extractStringList(searchResp);
            LOG.info("GEOSEARCH 500km from Paris: {}", nearby);

            // Cleanup
            client.del("demo:cities");

            return added == 3 && dist > 300 && dist < 400 && !nearby.isEmpty();
        }
    }
}
