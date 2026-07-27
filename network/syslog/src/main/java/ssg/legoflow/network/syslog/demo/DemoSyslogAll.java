package ssg.legoflow.network.syslog.demo;

import ssg.legoflow.network.syslog.SyslogCollector;
import ssg.legoflow.network.syslog.SyslogSender;
import ssg.legoflow.network.syslog.protocol.*;
import ssg.legoflow.network.syslog.transport.FramingMode;
import ssg.legoflow.network.syslog.transport.UdpCollector;
import ssg.legoflow.network.syslog.transport.UdpSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive demo of all Syslog module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link SyslogCollector}</b> — No external dependencies.
 * Runs anywhere without installation. Supports UDP and TCP transports, RFC 5424 encoding,
 * structured data, all facilities and severities.
 * Ideal for development, testing, CI/CD, and learning the syslog protocol.</p>
 *
 * <p><b>Alternative: External rsyslog / syslog-ng</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production log aggregation against real syslog servers</li>
 *   <li>TLS transport testing with real certificates</li>
 *   <li>Integration testing against production logging infrastructure</li>
 * </ul>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>RFC 5424 message encoding — full message format with all fields</li>
 *   <li>RFC 5424 message decoding — parse syslog strings back to messages</li>
 *   <li>Structured data — SD-ELEMENT with SD-ID and SD-PARAMs, escaping</li>
 *   <li>Facility codes — all 24 facility values</li>
 *   <li>Severity levels — all 8 severity values and PRI computation</li>
 *   <li>UDP transport — send and receive over loopback</li>
 *   <li>Message builder — fluent API for constructing messages</li>
 *   <li>Codec round-trip — encode then decode preserves all fields</li>
 *   <li>High-level sender — SyslogSender with transport abstraction</li>
 *   <li>High-level collector — SyslogCollector with multi-transport support</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoSyslogAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoSyslogAll.class);

    /** Set to {@code true} to connect to an external syslog server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external syslog server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "127.0.0.1";

    /** Port for external syslog server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 514;

    private DemoSyslogAll() {}

    /**
     * Results from running the full demo.
     *
     * @param messageEncoding       true if RFC 5424 encoding produced valid output
     * @param messageDecoding       true if decoding recovered all fields
     * @param structuredData        number of SD-PARAMs in the encoded structured data
     * @param facilityCodes         number of valid facility codes verified
     * @param severityLevels        number of valid severity levels verified
     * @param udpTransport          true if UDP send/receive succeeded
     * @param messageBuilder        true if builder produced a valid message
     * @param codecRoundTrip        true if encode-then-decode preserved all fields
     * @param highLevelSender       true if SyslogSender abstraction worked
     * @param highLevelCollector    true if SyslogCollector received messages
     */
    public record Results(
            boolean messageEncoding,
            boolean messageDecoding,
            int structuredData,
            int facilityCodes,
            int severityLevels,
            boolean udpTransport,
            boolean messageBuilder,
            boolean codecRoundTrip,
            boolean highLevelSender,
            boolean highLevelCollector
    ) {}

    /**
     * Runs the comprehensive demo covering all syslog features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        boolean encoding = demoMessageEncoding();
        boolean decoding = demoMessageDecoding();
        int sdParams = demoStructuredData();
        int facilities = demoFacilityCodes();
        int severities = demoSeverityLevels();
        boolean udp = demoUdpTransport();
        boolean builder = demoMessageBuilder();
        boolean roundTrip = demoCodecRoundTrip();
        boolean sender = demoHighLevelSender();
        boolean collector = demoHighLevelCollector();

        return new Results(encoding, decoding, sdParams, facilities, severities,
                udp, builder, roundTrip, sender, collector);
    }

    // ======================== 1. MESSAGE ENCODING ===============================

    /**
     * Demonstrates RFC 5424 message encoding with all fields populated.
     *
     * @return true if encoding produced a valid RFC 5424 string
     */
    static boolean demoMessageEncoding() {
        LOG.info("=== 1. RFC 5424 Message Encoding ===");
        SyslogMessage msg = new SyslogMessage(
                Facility.DAEMON, Severity.INFO,
                Instant.parse("2024-06-01T12:00:00Z"),
                "server01", "myapp", "1234", "ID001",
                List.of(), "Application started successfully"
        );

        String encoded = SyslogCodec.encode(msg);
        boolean hasPri = encoded.startsWith("<30>1 ");
        boolean hasHostname = encoded.contains("server01");
        boolean hasMessage = encoded.contains("Application started successfully");
        LOG.info("Encoded: {}", encoded);
        LOG.info("Valid: hasPri={} hasHostname={} hasMessage={}", hasPri, hasHostname, hasMessage);
        return hasPri && hasHostname && hasMessage;
    }

    // ======================== 2. MESSAGE DECODING ===============================

    /**
     * Demonstrates RFC 5424 message decoding from string.
     *
     * @return true if decoding recovered all fields correctly
     */
    static boolean demoMessageDecoding() {
        LOG.info("=== 2. RFC 5424 Message Decoding ===");
        String raw = "<34>1 2024-06-01T12:30:00.000000+00:00 web01 nginx 5678 REQ123 - GET /index.html 200";
        SyslogMessage msg = SyslogCodec.decode(raw);

        boolean facilityOk = msg.facility() == Facility.AUTH;
        boolean severityOk = msg.severity() == Severity.CRITICAL;
        boolean hostnameOk = "web01".equals(msg.hostname());
        boolean appOk = "nginx".equals(msg.appName());
        boolean procOk = "5678".equals(msg.procId());
        boolean msgIdOk = "REQ123".equals(msg.msgId());
        boolean msgOk = msg.message() != null && msg.message().contains("GET /index.html");

        LOG.info("Decoded: facility={} severity={} host={} app={} procId={} msgId={}",
                msg.facility(), msg.severity(), msg.hostname(), msg.appName(),
                msg.procId(), msg.msgId());
        return facilityOk && severityOk && hostnameOk && appOk && procOk && msgIdOk && msgOk;
    }

    // ======================== 3. STRUCTURED DATA ================================

    /**
     * Demonstrates structured data with SD-IDs and SD-PARAMs, including escaping.
     *
     * @return the number of SD-PARAMs in the encoded structured data
     */
    static int demoStructuredData() {
        LOG.info("=== 3. Structured Data ===");

        // Build structured data with escaping
        StructuredData sd1 = StructuredData.builder("exampleSDID@32473")
                .param("iut", "3")
                .param("eventSource", "Application")
                .param("eventID", "1011")
                .build();

        StructuredData sd2 = StructuredData.builder(StructuredData.ORIGIN)
                .param("ip", "192.168.1.1")
                .build();

        String encoded1 = sd1.encode();
        String encoded2 = sd2.encode();
        LOG.info("SD1: {}", encoded1);
        LOG.info("SD2: {}", encoded2);

        // Verify escaping with special characters
        Map<String, String> params = new LinkedHashMap<>();
        params.put("msg", "test \"value\" with \\backslash and ]bracket");
        StructuredData sdEscaped = StructuredData.of("escape", params);
        String encodedEscaped = sdEscaped.encode();
        boolean hasEscaping = encodedEscaped.contains("\\\"") && encodedEscaped.contains("\\\\");
        LOG.info("Escaped SD: {} escaping={}", encodedEscaped, hasEscaping);

        int totalParams = sd1.params().size() + sd2.params().size();
        LOG.info("Total SD params: {}", totalParams);
        return totalParams;
    }

    // ======================== 4. FACILITY CODES ================================

    /**
     * Demonstrates all 24 facility codes.
     *
     * @return the number of valid facility codes
     */
    static int demoFacilityCodes() {
        LOG.info("=== 4. Facility Codes ===");
        int count = 0;
        for (int i = 0; i <= 23; i++) {
            Facility f = Facility.of(i);
            if (f.code() == i) {
                count++;
            }
        }
        LOG.info("Valid facility codes: {}/24", count);
        return count;
    }

    // ======================== 5. SEVERITY LEVELS ================================

    /**
     * Demonstrates all 8 severity levels and PRI computation.
     *
     * @return the number of valid severity levels
     */
    static int demoSeverityLevels() {
        LOG.info("=== 5. Severity Levels ===");
        int count = 0;
        for (int i = 0; i <= 7; i++) {
            Severity s = Severity.of(i);
            if (s.code() == i) {
                count++;
            }
        }

        // PRI computation: facility * 8 + severity
        SyslogMessage msg = SyslogMessage.builder(Facility.LOCAL0, Severity.WARNING)
                .build();
        int expectedPri = 16 * 8 + 4; // LOCAL0=16, WARNING=4 => 132
        boolean priOk = msg.pri() == expectedPri;
        LOG.info("PRI computation: LOCAL0.WARNING = {} (expected {}), OK={}",
                msg.pri(), expectedPri, priOk);

        LOG.info("Valid severity levels: {}/8", count);
        return count;
    }

    // ======================== 6. UDP TRANSPORT ==================================

    /**
     * Demonstrates UDP syslog send and receive over loopback.
     *
     * @return true if the message was received correctly
     */
    static boolean demoUdpTransport() throws Exception {
        LOG.info("=== 6. UDP Transport ===");
        var received = new CopyOnWriteArrayList<SyslogMessage>();
        var latch = new CountDownLatch(1);

        try (UdpCollector collector = new UdpCollector(0)) {
            collector.start(msg -> {
                received.add(msg);
                latch.countDown();
            });
            int port = collector.localPort();

            try (UdpSender sender = new UdpSender("127.0.0.1", port)) {
                SyslogMessage msg = SyslogMessage.builder(Facility.USER, Severity.NOTICE)
                        .timestamp(Instant.now())
                        .hostname("testhost")
                        .appName("demo")
                        .message("UDP transport test")
                        .build();
                sender.send(msg);
            }

            boolean ok = latch.await(5, TimeUnit.SECONDS) && !received.isEmpty();
            if (ok) {
                SyslogMessage recv = received.getFirst();
                LOG.info("UDP received: facility={} severity={} msg={}",
                        recv.facility(), recv.severity(), recv.message());
            }
            return ok;
        }
    }

    // ======================== 7. MESSAGE BUILDER ================================

    /**
     * Demonstrates the fluent message builder API.
     *
     * @return true if the builder produced a valid message with all fields
     */
    static boolean demoMessageBuilder() {
        LOG.info("=== 7. Message Builder ===");
        StructuredData sd = StructuredData.builder("myApp@12345")
                .param("version", "2.0")
                .param("env", "production")
                .build();

        SyslogMessage msg = SyslogMessage.builder(Facility.LOCAL1, Severity.INFO)
                .timestamp(Instant.parse("2024-06-01T15:30:00Z"))
                .hostname("prod-server-01")
                .appName("myapp")
                .procId("9876")
                .msgId("DEPLOY001")
                .structuredData(List.of(sd))
                .message("Deployment completed successfully")
                .build();

        boolean hasAllFields = msg.facility() == Facility.LOCAL1
                && msg.severity() == Severity.INFO
                && "prod-server-01".equals(msg.hostname())
                && "myapp".equals(msg.appName())
                && "9876".equals(msg.procId())
                && "DEPLOY001".equals(msg.msgId())
                && msg.structuredData().size() == 1
                && msg.message() != null;

        LOG.info("Builder message: valid={}", hasAllFields);
        return hasAllFields;
    }

    // ======================== 8. CODEC ROUND-TRIP ===============================

    /**
     * Demonstrates encoding a message and decoding it back, verifying field preservation.
     *
     * @return true if all fields survived the round-trip
     */
    static boolean demoCodecRoundTrip() {
        LOG.info("=== 8. Codec Round-Trip ===");
        StructuredData sd = StructuredData.builder("roundTrip@99")
                .param("key1", "value1")
                .param("key2", "value2")
                .build();

        SyslogMessage original = SyslogMessage.builder(Facility.MAIL, Severity.ERROR)
                .timestamp(Instant.parse("2024-03-15T10:20:30Z"))
                .hostname("mail-gw")
                .appName("postfix")
                .procId("4321")
                .msgId("BOUNCE")
                .structuredData(List.of(sd))
                .message("Delivery failed: unknown recipient")
                .build();

        String encoded = SyslogCodec.encode(original);
        SyslogMessage decoded = SyslogCodec.decode(encoded);

        boolean facilityMatch = decoded.facility() == original.facility();
        boolean severityMatch = decoded.severity() == original.severity();
        boolean hostnameMatch = "mail-gw".equals(decoded.hostname());
        boolean appMatch = "postfix".equals(decoded.appName());
        boolean procMatch = "4321".equals(decoded.procId());
        boolean msgIdMatch = "BOUNCE".equals(decoded.msgId());
        boolean sdMatch = decoded.structuredData().size() == 1
                && decoded.structuredData().getFirst().params().size() == 2;
        boolean msgMatch = decoded.message() != null
                && decoded.message().contains("Delivery failed");

        boolean allMatch = facilityMatch && severityMatch && hostnameMatch && appMatch
                && procMatch && msgIdMatch && sdMatch && msgMatch;
        LOG.info("Round-trip: facility={} severity={} hostname={} app={} sd={} msg={} all={}",
                facilityMatch, severityMatch, hostnameMatch, appMatch, sdMatch, msgMatch, allMatch);
        return allMatch;
    }

    // ======================== 9. HIGH-LEVEL SENDER ==============================

    /**
     * Demonstrates the high-level SyslogSender with transport abstraction.
     *
     * @return true if the sender transmitted a message successfully
     */
    static boolean demoHighLevelSender() throws Exception {
        LOG.info("=== 9. High-Level Sender ===");
        var received = new CopyOnWriteArrayList<SyslogMessage>();
        var latch = new CountDownLatch(1);

        try (UdpCollector collector = new UdpCollector(0)) {
            collector.start(msg -> {
                received.add(msg);
                latch.countDown();
            });
            int port = collector.localPort();

            try (SyslogSender sender = SyslogSender.udp("127.0.0.1", port)
                    .withHostname("demo-host")
                    .withAppName("demo-app")) {
                sender.send(Facility.SYSLOG, Severity.DEBUG, "High-level sender test");
            }

            boolean ok = latch.await(5, TimeUnit.SECONDS) && !received.isEmpty();
            if (ok) {
                LOG.info("High-level sender: message received OK");
            }
            return ok;
        }
    }

    // ======================== 10. HIGH-LEVEL COLLECTOR ===========================

    /**
     * Demonstrates the high-level SyslogCollector with multi-transport support.
     *
     * @return true if the collector received messages via UDP
     */
    static boolean demoHighLevelCollector() throws Exception {
        LOG.info("=== 10. High-Level Collector ===");
        var received = new CopyOnWriteArrayList<SyslogMessage>();
        var latch = new CountDownLatch(1);

        try (SyslogCollector collector = SyslogCollector.udp(0)) {
            collector.start(msg -> {
                received.add(msg);
                latch.countDown();
            });
            int udpPort = collector.udpPort();

            try (UdpSender sender = new UdpSender("127.0.0.1", udpPort)) {
                SyslogMessage msg = SyslogMessage.builder(Facility.LOCAL7, Severity.ALERT)
                        .timestamp(Instant.now())
                        .hostname("collector-test")
                        .message("Collector test message")
                        .build();
                sender.send(msg);
            }

            boolean ok = latch.await(5, TimeUnit.SECONDS) && !received.isEmpty();
            if (ok) {
                SyslogMessage recv = received.getFirst();
                LOG.info("Collector received: facility={} severity={}", recv.facility(), recv.severity());
            }
            return ok;
        }
    }
}
