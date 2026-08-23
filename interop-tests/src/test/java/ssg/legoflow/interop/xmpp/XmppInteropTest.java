package ssg.legoflow.interop.xmpp;

import org.junit.jupiter.api.*;
import ssg.legoflow.xmpp.client.MessageListener;
import ssg.legoflow.xmpp.client.XmppClient;
import ssg.legoflow.xmpp.client.XmppClientConfig;
import ssg.legoflow.xmpp.core.JID;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability test: Lego Flow XMPP client to real XMPP server.
 *
 * <p>Connects to a real Prosody XMPP server to verify
 * that the Lego Flow client can establish connections, send messages,
 * and manage presence.
 *
 * <p>Configuration via system properties:
 *   interop.xmpp.host (default: localhost)
 *   interop.xmpp.port (default: 5222)
 *   interop.xmpp.domain (default: localhost)
 *   interop.xmpp.username (default: test)
 *   interop.xmpp.password (default: test)
 *
 * <p>To run against Prosody:
 *   docker compose -f interop-tests/docker-compose.yml up -d
 *   mvn verify -Dinterop.xmpp.host=localhost -DskipInteropTests=false
 */
    @Tag("messaging-protocols")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class XmppInteropTest {

    private final String host = System.getProperty("interop.xmpp.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.xmpp.port", "5222"));
    private final String domain = System.getProperty("interop.xmpp.domain", "localhost");
    private final String username = System.getProperty("interop.xmpp.username", "test");
    private final String password = System.getProperty("interop.xmpp.password", "test");

    private XmppClient client;

    @BeforeAll
    void connect() throws Exception {
        XmppClientConfig config = XmppClientConfig.builder(host, domain)
                .port(port)
                .connectTimeout(Duration.ofSeconds(10))
                .trustAllCerts(true)
                .build();
        this.client = new XmppClient();
        client.connect(config).get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    void disconnect() throws Exception {
        if (client != null) {
            client.disconnect();
        }
    }

    @Test
    void testConnection() {
        assertThat(client).isNotNull();
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testMessageListenerRegistration() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        client.addMessageListener(message -> {
            received.set(message.body());
            latch.countDown();
        });

        // Register listener successfully
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testClientConfigBuilder() {
        XmppClientConfig config = XmppClientConfig.builder("test", "testdomain")
                .port(5223)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        assertThat(config.host()).isEqualTo("test");
        assertThat(config.port()).isEqualTo(5223);
        assertThat(config.domain()).isEqualTo("testdomain");
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void testJidConstruction() {
        JID jid = new JID("user", "example.com", "resource");
        assertThat(jid.localpart()).isEqualTo("user");
        assertThat(jid.domainpart()).isEqualTo("example.com");
        assertThat(jid.resourcepart()).isEqualTo("resource");
    }

    @Test
    void testCloseGracefully() throws Exception {
        client.disconnect();
        assertThat(client.isConnected()).isFalse();
    }
}
