package ssg.legoflow.demos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Unified demo center for all Lego Flow protocol and framework demos.
 *
 * <p>Provides a single entry point to browse and run all demos
 * grouped by category. Supports:
 * <ul>
 *   <li>{@code --list} - list all demos with categories</li>
 *   <li>{@code --run category} - run all demos in a category</li>
 *   <li>{@code --run all} - run every demo</li>
 *   <li>{@code --help} - show usage</li>
 * </ul>
 */
public final class DemoCenter {

    private DemoCenter() {}

    /** A registered demo that can be run by name or category. */
    public record Demo(String category, String name, Supplier<Runnable> factory) {}

    private static final List<Demo> ALL_DEMOS = new ArrayList<>();

    static {
        // Blocks (no comprehensive demo, show key examples)
        register("blocks", "Bidirectional Pipe",
                () -> () -> System.out.println("BidirectionalPipe - see unit tests for behavior"));
        register("blocks", "Passthrough Processor",
                () -> () -> System.out.println("PassthroughProcessor - see unit tests for behavior"));

        // Service
        register("service", "Service Overview",
                () -> () -> { try { ssg.legoflow.service.demo.DemoServiceAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // HTTP
        register("http", "HTTP Overview",
                () -> () -> { try { ssg.legoflow.http.demo.DemoHttpAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // HTTP Auth
        register("http-auth", "Auth Core Overview",
                () -> () -> { try { ssg.legoflow.http.auth.demo.DemoHttpAuthCoreAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });
        register("http-auth", "Basic/Digest Auth",
                () -> () -> { try { ssg.legoflow.http.auth.basic.demo.DemoBasicDigestAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });
        register("http-auth", "OAuth2",
                () -> () -> { try { ssg.legoflow.http.auth.oauth2.demo.DemoOAuthAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });
        register("http-auth", "SSO",
                () -> () -> { try { ssg.legoflow.http.auth.sso.demo.DemoSsoAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });
        register("http-auth", "SPNEGO",
                () -> () -> { try { ssg.legoflow.http.auth.spnego.demo.DemoSpnegoAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // HTTP/2
        register("http2", "HTTP/2 Overview",
                () -> () -> { try { ssg.legoflow.http2.demo.DemoHttp2All.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // HTTP/3
        register("http3", "HTTP/3 Overview",
                () -> () -> { try { ssg.legoflow.http3.demo.DemoHttp3All.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // HTTP Proxy
        register("http-proxy", "Proxy Overview",
                () -> () -> { try { ssg.legoflow.http.proxy.demo.DemoHttpProxyAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // WebSocket
        register("ws", "WebSocket Services",
                () -> () -> { try { ssg.legoflow.ws.demo.DemoWebServicesAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // FTP
        register("ftp", "FTP Overview",
                () -> () -> { try { ssg.legoflow.ftp.demo.DemoFtpAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // SSH
        register("ssh", "SSH Overview",
                () -> () -> { try { ssg.legoflow.ssh.demo.DemoSshAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // Email
        register("email", "Email Common",
                () -> () -> { try { ssg.legoflow.email.common.demo.DemoEmailCommonAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });
        register("email", "SMTP Overview",
                () -> () -> { try { ssg.legoflow.email.smtp.demo.DemoSmtpAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });
        register("email", "IMAP Overview",
                () -> () -> { try { ssg.legoflow.email.imap.demo.DemoImapAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // MQTT
        register("mqtt", "MQTT Overview",
                () -> () -> { try { ssg.legoflow.messaging.mqtt.demo.DemoMqttAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // AMQP
        register("amqp", "AMQP Overview",
                () -> () -> { try { ssg.legoflow.messaging.amqp.demo.DemoAmqpAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // NATS
        register("nats", "NATS Overview",
                () -> () -> { try { ssg.legoflow.messaging.nats.demo.DemoNatsAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // STOMP
        register("stomp", "STOMP Overview",
                () -> () -> { try { ssg.legoflow.messaging.stomp.demo.DemoStompAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // Kafka
        register("kafka", "Kafka Overview",
                () -> () -> { try { ssg.legoflow.messaging.kafka.demo.DemoKafkaAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // WAMP
        register("wamp", "WAMP Overview",
                () -> () -> { try { ssg.legoflow.wamp.demo.base.DemoWampAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // XMPP
        register("xmpp", "XMPP Overview",
                () -> () -> { try { ssg.legoflow.xmpp.demo.DemoXmppAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // CoAP
        register("coap", "CoAP Overview",
                () -> () -> { try { ssg.legoflow.coap.demo.DemoCoapAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // RTP
        register("rtp", "RTP Overview",
                () -> () -> { try { ssg.legoflow.media.rtp.demo.DemoRtpAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // RTSP
        register("rtsp", "RTSP Overview",
                () -> () -> { try { ssg.legoflow.media.rtsp.demo.DemoRtspAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // SIP
        register("sip", "SIP Overview",
                () -> () -> { try { ssg.legoflow.media.sip.demo.DemoSipAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // SDP
        register("sdp", "SDP Overview",
                () -> () -> { try { ssg.legoflow.media.common.demo.DemoSdpAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // Database: Redis
        register("redis", "Redis Overview",
                () -> () -> { try { ssg.legoflow.database.redis.demo.DemoRedisAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // Database: PostgreSQL
        register("postgresql", "PostgreSQL Overview",
                () -> () -> { try { ssg.legoflow.database.postgresql.demo.DemoPostgreSqlAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // Database: MySQL
        register("mysql", "MySQL Overview",
                () -> () -> { try { ssg.legoflow.database.mysql.demo.DemoMysqlAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // GSSAPI / Kerberos
        register("gssapi", "GSSAPI/Kerberos Overview",
                () -> () -> { try { ssg.legoflow.auth.gssapi.demo.DemoGssapiAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // gRPC
        register("grpc", "gRPC Overview",
                () -> () -> { try { ssg.legoflow.rpc.grpc.demo.DemoGrpcAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // GraphQL
        register("graphql", "GraphQL Overview",
                () -> () -> { try { ssg.legoflow.rpc.graphql.demo.DemoGraphqlAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // DNS
        register("dns", "DNS Overview",
                () -> () -> { try { ssg.legoflow.network.dns.demo.DemoDnsAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // LDAP
        register("ldap", "LDAP Overview",
                () -> () -> { try { ssg.legoflow.network.ldap.demo.DemoLdapAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // SNMP
        register("snmp", "SNMP Overview",
                () -> () -> { try { ssg.legoflow.network.snmp.demo.DemoSnmpAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // Syslog
        register("syslog", "Syslog Overview",
                () -> () -> { try { ssg.legoflow.network.syslog.demo.DemoSyslogAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // Modbus
        register("modbus", "Modbus Overview",
                () -> () -> { try { ssg.legoflow.network.modbus.demo.DemoModbusAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // Network Common
        register("network", "Network Common",
                () -> () -> { try { ssg.legoflow.network.common.demo.DemoNetworkCommonAll.runAll(); } catch (Exception e) { throw new RuntimeException(e); } });

        // Telnet
        register("telnet", "Telnet Protocol",
                () -> () -> { try { ssg.legoflow.network.telnet.base.demo.TelnetDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("telnet", "Telnet Negotiation",
                () -> () -> { try { ssg.legoflow.network.telnet.negotiation.demo.NegotiationDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("telnet", "Telnet Gateway",
                () -> () -> { try { ssg.legoflow.network.telnet.gateway.demo.GatewayDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });

        // Terminal Emulators
        register("terminals", "Terminal Demo (vt100)",
                () -> () -> { try { ssg.legoflow.network.terminals.base.demo.TerminalDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("terminals", "VT52",
                () -> () -> { try { ssg.legoflow.network.terminals.vt52.demo.VT52Demo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("terminals", "VT100",
                () -> () -> { try { ssg.legoflow.network.terminals.vt100.demo.VT100Demo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("terminals", "VT200",
                () -> () -> { try { ssg.legoflow.network.terminals.vt200.demo.VT200Demo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("terminals", "VT400",
                () -> () -> { try { ssg.legoflow.network.terminals.vt400.demo.VT400Demo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("terminals", "VT500",
                () -> () -> { try { ssg.legoflow.network.terminals.vt500.demo.VT500Demo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("terminals", "ANSI",
                () -> () -> { try { ssg.legoflow.network.terminals.ansi.demo.ANSIDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("terminals", "XTERM",
                () -> () -> { try { ssg.legoflow.network.terminals.xterm.demo.XTERMDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });

        // Cluster
        register("cluster", "Cluster Runner (all)",
                () -> () -> { try { ssg.legoflow.demos.cluster.ClusterDemoRunner.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("cluster", "Cluster Simulation",
                () -> () -> { try { ssg.legoflow.demos.cluster.ClusterSimulationDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("cluster", "Auto-Discovering Web Cluster",
                () -> () -> { try { ssg.legoflow.demos.cluster.AutoDiscoveringWebClusterDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("cluster", "gRPC Microservice Cluster",
                () -> () -> { try { ssg.legoflow.demos.cluster.GrpcMicroserviceClusterDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("cluster", "Distributed Leader Election",
                () -> () -> { try { ssg.legoflow.demos.cluster.DistributedLeaderElectionDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("cluster", "Partition Tolerance",
                () -> () -> { try { ssg.legoflow.demos.cluster.PartitionToleranceDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("cluster", "DNS-SD Discovery",
                () -> () -> { try { ssg.legoflow.demos.cluster.DnsSdDiscoveryDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("cluster", "etcd Coordination",
                () -> () -> { try { ssg.legoflow.demos.cluster.EtcdCoordinationDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });

        // UPnP
        register("upnp", "Media Renderer",
                () -> () -> { try { ssg.legoflow.upnp.demo.SimpleMediaRendererDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("upnp", "Media Server",
                () -> () -> { try { ssg.legoflow.upnp.demo.SimpleMediaServerDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("upnp", "Media Controller",
                () -> () -> { try { ssg.legoflow.upnp.demo.MediaControllerDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("upnp", "DLNA Player",
                () -> () -> { try { ssg.legoflow.upnp.demo.DlnaPlayerDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("upnp", "Multi-Room",
                () -> () -> { try { ssg.legoflow.upnp.demo.MultiRoomDemo.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("upnp", "Media Control Center",
                () -> () -> { try { ssg.legoflow.upnp.demo.mcc.MediaControlCenterApp.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
        register("upnp", "MCC Web Server",
                () -> () -> { try { ssg.legoflow.upnp.demo.mccweb.MccWebApp.main(null); } catch (Exception e) { throw new RuntimeException(e); } });
    }

    private static void register(String category, String name, Supplier<Runnable> factory) {
        ALL_DEMOS.add(new Demo(category, name, factory));
    }

    /** Returns all registered demos. */
    public static List<Demo> allDemos() {
        return List.copyOf(ALL_DEMOS);
    }

    /** Returns demos filtered by category (case-insensitive). */
    public static List<Demo> byCategory(String category) {
        String lower = category.toLowerCase();
        return ALL_DEMOS.stream()
                .filter(d -> d.category().equalsIgnoreCase(lower))
                .toList();
    }

    /** Runs all demos, reporting timing and summary. */
    public static void runAll() throws Exception {
        runAll(null);
    }

    /** Runs all demos in the given category, or all if {@code null}. */
    public static void runAll(String categoryFilter) throws Exception {
        List<Demo> demos = categoryFilter != null
                ? byCategory(categoryFilter)
                : allDemos();

        int total = demos.size();
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        long startTime = System.nanoTime();

        System.out.println("Lego Flow - Demo Center Runner");
        System.out.println("Total: " + total + " demos");
        if (categoryFilter != null) {
            System.out.println("Category: " + categoryFilter);
        }
        System.out.println();

        for (int i = 0; i < total; i++) {
            Demo demo = demos.get(i);
            System.out.printf("[%d/%d] %-50s", i + 1, total, demo.name());

            try {
                long t0 = System.nanoTime();
                Runnable r = demo.factory().get();
                r.run();
                long ms = (System.nanoTime() - t0) / 1_000_000;
                System.out.printf(" OK (%d ms)%n", ms);
                passed++;
            } catch (NoClassDefFoundError e) {
                System.out.printf(" SKIPPED (missing deps)%n");
                skipped++;
            } catch (Exception e) {
                System.out.printf(" FAILED: %s%n", e.getMessage());
                failed++;
            }
        }

        long totalMs = (System.nanoTime() - startTime) / 1_000_000;
        System.out.println();
        System.out.println("Result: " + passed + " passed, " + failed + " failed, " + skipped + " skipped, " + total + " total");
        System.out.println("Time: " + totalMs + " ms");

        if (failed > 0) {
            System.exit(1);
        }
    }

    /** Main entry point for the DemoCenter. */
    public static void main(String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--list"))) {
            listDemos();
        } else if (args.length == 2 && args[0].equals("--run")) {
            try {
                runAll("all".equals(args[1]) ? null : args[1]);
            } catch (Exception e) {
                System.err.println("Demo failed: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        } else if (args.length == 1 && args[0].equals("--help")) {
            printUsage();
        } else {
            System.err.println("Unknown arguments: " + String.join(" ", args));
            printUsage();
            System.exit(1);
        }
    }

    private static void listDemos() {
        System.out.println("Lego Flow - Demo Center");
        System.out.println("Total demos: " + ALL_DEMOS.size());
        System.out.println();

        String currentCategory = null;
        for (Demo demo : ALL_DEMOS) {
            if (!demo.category().equals(currentCategory)) {
                currentCategory = demo.category();
                System.out.println("--- " + currentCategory.toUpperCase() + " ---");
            }
            System.out.println("  * " + demo.name());
        }

        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java ssg.legoflow.demos.DemoCenter --run all    # run every demo");
        System.out.println("  java ssg.legoflow.demos.DemoCenter --run <cat>  # run category demos");
        System.out.println("  java ssg.legoflow.demos.DemoCenter --list       # list all demos (default)");
        System.out.println("  java ssg.legoflow.demos.DemoCenter --help       # show this help");
    }

    private static void printUsage() {
        System.out.println("Lego Flow Demo Center - unified entry point for all demos");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java ssg.legoflow.demos.DemoCenter --run all    # run every demo");
        System.out.println("  java ssg.legoflow.demos.DemoCenter --run <cat>  # run category demos");
        System.out.println("  java ssg.legoflow.demos.DemoCenter --list       # list all demos (default)");
        System.out.println("  java ssg.legoflow.demos.DemoCenter --help       # show this help");
    }
}
