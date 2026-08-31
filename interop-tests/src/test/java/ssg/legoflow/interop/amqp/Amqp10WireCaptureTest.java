package ssg.legoflow.interop.amqp;

import org.junit.jupiter.api.*;
import ssg.legoflow.service.passthrough.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Captures raw wire traffic from Artemis CLI (AMQP 1.0 reference client) against
 * Artemis (AMQP 1.0 reference server) using {@link PassThroughConnection} + {@link WireCaptureInterceptor}.
 *
 * Both sides are verified AMQP 1.0 compliant (ISO/IEC 19464-1).
 * The client runs as a separate JVM process — black-box capture only.
 *
 * Scenario: producer sends a message → consumer receives it.
 * Results saved to test resources for later comparison with lego-flow.
 */
public class Amqp10WireCaptureTest {

    private static final String ARTEMIS_HOST = "localhost";
    private static final int ARTEMIS_PORT = 5675; // AMQP 1.0 acceptor
    private static final String ARTEMIS_USER = "guest";
    private static final String ARTEMIS_PASS = "guest";
    private static final String QUEUE = "wire-capture-queue-10";

    private static PassThroughConnection proxy;
    private static WireCaptureInterceptor capture;
    private static int proxyPort;
    private static Path artemisHome;

    @BeforeAll
    static void setUpProxy() throws Exception {
        capture = new WireCaptureInterceptor();
        proxy = new PassThroughConnection();
        proxy.addInterceptor(capture);
        proxyPort = PassThroughConnection.findFreePort();
        proxy.addRoute(proxyPort, new InetSocketAddress(ARTEMIS_HOST, ARTEMIS_PORT));
        proxy.start();
        Thread.sleep(500);
        System.out.println("[SETUP] Proxy on " + proxyPort + " -> " + ARTEMIS_HOST + ":" + ARTEMIS_PORT);

        // Resolve Artemis CLI path
        artemisHome = Paths.get("src/test/resources/artemis-cli");
        if (!Files.exists(artemisHome)) {
            throw new IllegalStateException("Artemis CLI not found at " + artemisHome
                    + " — run: docker cp artemis-test:/opt/artemis src/test/resources/artemis-cli");
        }
    }

    @AfterAll
    static void tearDownProxy() {
        proxy.stop();
    }

    @Test
    @Order(1)
    void captureProducerTraffic() throws Exception {
        System.out.println("\n=== AMQP 1.0 Wire Capture: Artemis CLI → Artemis (producer) ===\n");

        String[] cmd = {
            artemisHome.resolve("bin/artemis").toString(),
            "producer",
            "--url=tcp://localhost:" + proxyPort,
            "--user=" + ARTEMIS_USER,
            "--password=" + ARTEMIS_PASS,
            "--destination=queue://" + QUEUE,
            "--message=Hello from Artemis CLI AMQP 1.0 reference client",
            "--protocol=AMQP",
            "--message-count=1",
            "--verbose"
        };
        runArtemis(cmd);

        // Let connection close fully
        Thread.sleep(1000);

        saveCapture("amqp10-reference-capture-artemis-producer.txt");
    }

    @Test
    @Order(2)
    void captureConsumerTraffic() throws Exception {
        System.out.println("\n=== AMQP 1.0 Wire Capture: Artemis CLI → Artemis (consumer) ===\n");

        String[] cmd = {
            artemisHome.resolve("bin/artemis").toString(),
            "consumer",
            "--url=tcp://localhost:" + proxyPort,
            "--user=" + ARTEMIS_USER,
            "--password=" + ARTEMIS_PASS,
            "--destination=queue://" + QUEUE,
            "--protocol=AMQP",
            "--message-count=1",
            "--time=1"
        };

        // Try with --time, fall back to --message-count only
        try {
            runArtemis(cmd);
        } catch (Exception e) {
            // Fallback: run without --time (it times out after consuming)
            String[] fallback = {
                artemisHome.resolve("bin/artemis").toString(),
                "consumer",
                "--url=tcp://localhost:" + proxyPort,
                "--user=" + ARTEMIS_USER,
                "--password=" + ARTEMIS_PASS,
                "--destination=queue://" + QUEUE,
                "--protocol=AMQP",
                "--message-count=1"
            };
            runArtemisWithTimeout(fallback, 10, TimeUnit.SECONDS);
        }

        Thread.sleep(1000);

        saveCapture("amqp10-reference-capture-artemis-consumer.txt");
    }

    @Test
    @Order(3)
    void captureFullRoundTrip() throws Exception {
        System.out.println("\n=== AMQP 1.0 Wire Capture: Full send+receive round-trip ===\n");

        // Clear previous capture
        capture.clear();

        // Run producer first, then consumer — both through proxy sequentially
        // Producer
        String[] producerCmd = {
            artemisHome.resolve("bin/artemis").toString(),
            "producer",
            "--url=tcp://localhost:" + proxyPort,
            "--user=" + ARTEMIS_USER,
            "--password=" + ARTEMIS_PASS,
            "--destination=queue://" + QUEUE + "-roundtrip",
            "--message=Round-trip AMQP 1.0 test message",
            "--protocol=AMQP",
            "--message-count=3"
        };
        runArtemis(producerCmd);
        Thread.sleep(1000);

        // Consumer
        String[] consumerCmd = {
            artemisHome.resolve("bin/artemis").toString(),
            "consumer",
            "--url=tcp://localhost:" + proxyPort,
            "--user=" + ARTEMIS_USER,
            "--password=" + ARTEMIS_PASS,
            "--destination=queue://" + QUEUE + "-roundtrip",
            "--protocol=AMQP",
            "--message-count=3"
        };
        runArtemisWithTimeout(consumerCmd, 15, TimeUnit.SECONDS);

        Thread.sleep(1000);

        saveCapture("amqp10-reference-capture-artemis-roundtrip.txt");
    }

    private void runArtemis(String[] cmd) throws Exception {
        runArtemisWithTimeout(cmd, 30, TimeUnit.SECONDS);
    }

    private void runArtemisWithTimeout(String[] cmd, long timeout, TimeUnit unit) throws Exception {
        var pb = new ProcessBuilder(cmd);
        pb.environment().put("ARTEMIS_HOME", artemisHome.toString());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        var readerThread = Thread.ofVirtual().start(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[ARTEMIS] " + line);
                }
            } catch (IOException ignored) {}
        });

        boolean finished = process.waitFor(timeout, unit);
        if (!finished) {
            System.out.println("[ARTEMIS] Timed out after " + timeout + " " + unit + ", destroying");
            process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        }
        readerThread.join(5000);
        System.out.println("[ARTEMIS] Exit code: " + (finished ? process.exitValue() : "timeout"));
    }

    private void saveCapture(String filename) {
        System.out.println("\n=== Full Wire Capture ===\n");
        List<WireCaptureInterceptor.WireEntry> entries = capture.getEntries();

        // Print to console
        int seq = 0;
        for (var entry : entries) {
            String dir = entry.direction() == Direction.LOCAL_TO_REMOTE ? "→" : "←";
            byte[] data = entry.data();
            String hex = toHex(data);
            System.out.printf("%03d %-3s %4d bytes: %s%n", seq++, dir, data.length, hex);
        }

        // Save to test resources
        String resourcePath = "src/test/resources/" + filename;
        try {
            Files.createDirectories(Paths.get("src/test/resources"));
            try (var w = new PrintWriter(new FileWriter(resourcePath))) {
                // Header with metadata
                w.println("# AMQP 1.0 Wire Capture");
                w.println("# Client: Apache Artemis CLI (producer/consumer)");
                w.println("# Server: Apache Artemis " + ARTEMIS_HOST + ":" + ARTEMIS_PORT);
                w.println("# Protocol: AMQP 1.0 (ISO/IEC 19464-1)");
                w.println("# Auth: SASL PLAIN (" + ARTEMIS_USER + ")");
                w.println("# Queue: " + QUEUE);
                w.println("# Direction: → = client→server, ← = server→client");
                w.println("#");
                seq = 0;
                for (var entry : entries) {
                    String dir = entry.direction() == Direction.LOCAL_TO_REMOTE ? "→" : "←";
                    byte[] data = entry.data();
                    String hex = toHex(data);
                    w.printf("%03d %-3s %4d bytes: %s%n", seq++, dir, data.length, hex);
                }
            }
            System.out.println("\nSaved to " + resourcePath + " (" + entries.size() + " entries)\n");
        } catch (IOException e) {
            System.err.println("Failed to save capture: " + e.getMessage());
        }
    }

    private String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }
}
