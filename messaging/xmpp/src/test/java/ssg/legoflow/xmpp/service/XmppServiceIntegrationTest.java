package ssg.legoflow.xmpp.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import ssg.legoflow.xmpp.client.service.XmppClientService;
import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.core.MessageStanza;
import ssg.legoflow.xmpp.core.Stanza;
import ssg.legoflow.xmpp.server.service.XmppServerService;
import ssg.legoflow.xmpp.stream.XmppCodec;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration test: XMPP client service ↔ server service over real TCP through
 * the {@link SelectableChannelManager} (mirrors the NATS {@code NatsServiceIntegrationTest}).
 *
 * <p>Exercises the full production pipeline: SocketChannel → SelectableChannelManager →
 * TcpDataChannel → ChannelPipeline → {@link ssg.legoflow.xmpp.transport.PipelineXmppTransport}
 * → XMPP protocol layer. The protocol core itself never touches sockets — this test only
 * drives the service layer, which owns the (manager-managed) channels.
 *
 * <p>Round-trip: the client service connects over TCP, logs in (locally-simulated SASL),
 * sends a chat message that the server core decodes through the pipeline; the client
 * transport then receives a reply stanza (injected via {@code onRead}, exactly as the
 * selector thread would deliver server bytes) and dispatches it to the message listener.
 */
class XmppServiceIntegrationTest {

    private SelectableChannelManager manager;
    private DefaultServiceContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new DefaultServiceContext(ServiceUser.anonymous());
        manager = new SelectableChannelManager(ctx);
        ctx.setAttribute("channelManager", manager);
        manager.startEventLoop();
    }

    @AfterEach
    void tearDown() {
        manager.stopEventLoop();
        manager.close();
    }

    @Test
    void testClientAndServerServiceOverTcp() throws Exception {
        var server = XmppServerService.builder().port(0).build();
        server.connect(ctx);
        int port = server.getPort();
        assertThat(port).as("server should bind a real port").isGreaterThan(0);

        // Poll for the selector to register the server channel (service-layer sync point).
        int retries = 20;
        while (retries-- > 0) {
            if (server.getServer() != null && server.getServer().isRunning()) break;
            Thread.sleep(100);
        }

        var client = XmppClientService.builder("localhost", port).build();
        client.connect(ctx);
        assertThat(client.getClient()).as("client should be connected").isNotNull();
        assertThat(client.getClient().isConnected()).isTrue();

        // Inbound at the server core: the client's message, decoded through the pipeline.
        var serverLatch = new CountDownLatch(1);
        var serverMsg = new AtomicReference<Stanza>();
        server.getServer().addStanzaHandler("test", s -> {
            if (s instanceof MessageStanza) {
                serverMsg.set(s);
                serverLatch.countDown();
            }
        });

        // Outbound at the client: a reply stanza dispatched to the message listener.
        var clientLatch = new CountDownLatch(1);
        var clientMsg = new AtomicReference<MessageStanza>();
        client.getClient().addMessageListener(msg -> {
            clientMsg.set(msg);
            clientLatch.countDown();
        });

        // Give the pipeline a moment to settle, then send over real TCP.
        Thread.sleep(100);
        client.getClient().login("user", "pass").join();
        client.getClient().sendMessage(new JID("bob", "test.example", null), "hello-tcp");

        assertThat(serverLatch.await(5, TimeUnit.SECONDS))
                .as("server should receive the stanza over TCP").isTrue();
        assertThat(((MessageStanza) serverMsg.get()).body()).isEqualTo("hello-tcp");

        // Reply: inject the reply bytes into the client transport exactly as the selector
        // thread would deliver server-side bytes, and let the client read loop process it.
        var reply = MessageStanza.chat("r1", new JID("server", "test.example", null),
                client.getClient().getLocalJid(), "pong-tcp");
        var replyXml = new XmppCodec().encodeStanza(reply);
        client.getTransport().onRead(client.getDataChannel(), replyXml);

        assertThat(clientLatch.await(5, TimeUnit.SECONDS))
                .as("client should dispatch the reply stanza").isTrue();
        assertThat(clientMsg.get().body()).isEqualTo("pong-tcp");

        client.disconnect(ctx);
        server.disconnect(ctx);
    }

    @Test
    void testServerServiceLifecycleOnly() throws Exception {
        var server = XmppServerService.builder().port(0).build();
        server.connect(ctx);
        assertThat(server.getPort()).isGreaterThan(0);
        assertThat(server.getServer()).isNotNull();
        assertThat(server.getServer().isRunning()).isTrue();
        server.disconnect(ctx);
    }
}
