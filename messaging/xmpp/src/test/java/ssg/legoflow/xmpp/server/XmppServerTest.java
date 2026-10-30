package ssg.legoflow.xmpp.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.xmpp.core.MessageStanza;
import ssg.legoflow.xmpp.core.Stanza;
import ssg.legoflow.xmpp.stream.XmppCodec;
import ssg.legoflow.xmpp.transport.InMemoryXmppTransport;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the headless {@link XmppServer} core: lifecycle, the
 * {@link XmppServer#handleConnection} seam (in-memory transport injection), connection
 * bookkeeping, and close semantics. Mirrors the NATS server-core tests — the core never
 * touches sockets.
 */
class XmppServerTest {

    private ssg.legoflow.xmpp.transport.InMemoryXmppTransport[] pair;

    @AfterEach
    void tearDown() {
        if (pair != null) {
            try {
                pair[0].close();
                pair[1].close();
            } catch (Exception ignored) {
            }
        }
    }

    private static ByteBuffer bytes(String s) {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String messageXml(String from, String to, String body) {
        return "<message from='" + from + "' to='" + to + "' id='m1'>"
                + "<body>" + body + "</body></message>";
    }

    @Test
    void testLifecycleDefaults() {
        var server = new XmppServer(1234);
        assertThat(server.isRunning()).isFalse();
        assertThat(server.port()).isEqualTo(1234);
        assertThat(server.clientCount()).isZero();
        server.close();
    }

    @Test
    void testStartAndClose() {
        var server = new XmppServer();
        server.start();
        assertThat(server.isRunning()).isTrue();
        server.close();
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void testHandleConnectionTracksAndServesStanzas() throws Exception {
        var pair = InMemoryXmppTransport.createPair();
        this.pair = pair;
        var server = new XmppServer(0);
        server.start();
        try {
            var received = new CountDownLatch(1);
            var got = new AtomicReference<Stanza>();
            server.addStanzaHandler("echo", s -> {
                got.set(s);
                received.countDown();
            });

            server.handleConnection(pair[1]);
            assertThat(server.clientCount()).isEqualTo(1);

            var codec = new XmppCodec();
            pair[0].send(bytes(messageXml("alice@example", "bob@example", "ping")));

            assertThat(received.await(5, TimeUnit.SECONDS))
                    .as("server should decode and dispatch the stanza").isTrue();
            assertThat(got.get()).isInstanceOf(MessageStanza.class);
            assertThat(((MessageStanza) got.get()).body()).isEqualTo("ping");
        } finally {
            server.close();
        }
    }

    @Test
    void testHandleConnectionIsIdempotentForMultipleClients() throws Exception {
        var pair = InMemoryXmppTransport.createPair();
        this.pair = pair;
        var server = new XmppServer(0);
        server.start();
        var received = new CountDownLatch(2);
        server.addStanzaHandler("echo", s -> received.countDown());
        var pair2 = InMemoryXmppTransport.createPair();
        try {
            server.handleConnection(pair[1]);
            server.handleConnection(pair2[1]);
            assertThat(server.clientCount()).isEqualTo(2);

            pair[0].send(bytes(messageXml("a@example", "b@example", "1")));
            pair2[0].send(bytes(messageXml("c@example", "b@example", "2")));

            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            server.close();
            pair2[0].close();
            pair2[1].close();
        }
    }

    @Test
    void testCloseClosesActiveConnections() throws Exception {
        var pair = InMemoryXmppTransport.createPair();
        this.pair = pair;
        var server = new XmppServer(0);
        server.start();
        server.handleConnection(pair[1]);
        assertThat(server.clientCount()).isEqualTo(1);

        server.close();
        assertThat(server.clientCount()).isZero();
        assertThat(pair[1].isOpen()).isFalse();
    }
}
