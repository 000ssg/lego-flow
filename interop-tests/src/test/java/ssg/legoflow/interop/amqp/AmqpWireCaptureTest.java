package ssg.legoflow.interop.amqp;

import org.junit.jupiter.api.*;
import ssg.legoflow.service.passthrough.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Captures raw wire traffic from a reference AMQP 1.0 client (aiormq Python) against
 * RabbitMQ using {@link PassThroughConnection} + {@link WireCaptureInterceptor}.
 *
 * The client is run as a separate process — black-box capture only.
 * Results are saved to test resources for later comparison with lego-flow.
 */
public class AmqpWireCaptureTest {

    private static final String RABBITMQ_HOST = "localhost";
    private static final int RABBITMQ_PORT = 5672;

    private static PassThroughConnection proxy;
    private static WireCaptureInterceptor capture;
    private static int proxyPort;

    @BeforeAll
    static void setUpProxy() throws Exception {
        capture = new WireCaptureInterceptor();
        proxy = new PassThroughConnection();
        proxy.addInterceptor(capture);
        proxyPort = PassThroughConnection.findFreePort();
        proxy.addRoute(proxyPort, new InetSocketAddress(RABBITMQ_HOST, RABBITMQ_PORT));
        proxy.start();
        Thread.sleep(500);
        System.out.println("[SETUP] Proxy on " + proxyPort + " -> " + RABBITMQ_HOST + ":" + RABBITMQ_PORT);
    }

    @AfterAll
    static void tearDownProxy() {
        proxy.stop();
    }

    @Test
    void captureAiormqTraffic() throws Exception {
        System.out.println("\n=== AMQP 1.0 Wire Capture: aiormq Python client ===\n");

        // Resolve python3 path
        ProcessBuilder pb = new ProcessBuilder(
            "python3",
            "src/test/resources/amqp_capture_scenario.py",
            "localhost",
            String.valueOf(proxyPort)
        );
        pb.redirectErrorStream(true);

        // Wait for client to finish (max 30s)
        var done = new CountDownLatch(1);
        Process process = pb.start();
        var readerThread = Thread.ofVirtual().start(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[CLIENT] " + line);
                }
            } catch (IOException e) {
                // Ignore
            }
            done.countDown();
        });

        // Wait for completion
        boolean finished = done.await(30, TimeUnit.SECONDS);
        if (finished) {
            process.waitFor(5, TimeUnit.SECONDS);
            System.out.println("[CLIENT] Exit code: " + process.exitValue());
        } else {
            System.out.println("[CLIENT] Timed out after 30s");
            process.destroyForcibly();
        }

        // Let the connection fully close and propagate through the proxy
        Thread.sleep(2000);

        // Save capture
        saveCapture("amqp-reference-capture-rabbitmq-aiormq.txt");
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
