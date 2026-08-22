package ssg.legoflow.messaging.stomp.adapter.tcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.stomp.core.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for TCP adapter: full client-server round-trip over real TCP sockets.
 *
 * @since 0.1.0
 */
class TcpStompAdapterTest {

    private StompBroker broker;
    private TcpStompServer server;

    @BeforeEach
    void setUp() throws Exception {
        broker = new StompBroker();
        server = new TcpStompServer(broker, 0); // auto-assign port
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.close();
        broker.close();
    }

    @Test
    void testServerStarts() {
        assertThat(server.isRunning()).isTrue();
        assertThat(server.getPort()).isGreaterThan(0);
    }

    @Test
    void testTcpConnect() throws Exception {
        var tcpClient = new TcpStompClient("localhost", server.getPort());
        var connected = tcpClient.connect("localhost");

        assertThat(connected.command()).isEqualTo(StompCommand.CONNECTED);
        assertThat(connected.header(StompHeaders.VERSION)).isEqualTo("1.2");
        assertThat(tcpClient.isConnected()).isTrue();

        tcpClient.close();
    }

    @Test
    void testTcpPubSub() throws Exception {
        var publisher = new TcpStompClient("localhost", server.getPort());
        var subscriber = new TcpStompClient("localhost", server.getPort());

        publisher.connect("localhost");
        subscriber.connect("localhost");

        var received = new CopyOnWriteArrayList<StompFrame>();
        var latch = new CountDownLatch(1);

        subscriber.getClient().subscribe("/topic/tcp-test", msg -> {
            received.add(msg);
            latch.countDown();
        });
        Thread.sleep(100);

        publisher.getClient().send("/topic/tcp-test", "TCP message!", "text/plain");

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.getFirst().bodyAsText()).isEqualTo("TCP message!");

        publisher.close();
        subscriber.close();
    }

    @Test
    void testTcpMultipleMessages() throws Exception {
        var publisher = new TcpStompClient("localhost", server.getPort());
        var subscriber = new TcpStompClient("localhost", server.getPort());

        publisher.connect("localhost");
        subscriber.connect("localhost");

        int messageCount = 10;
        var received = new CopyOnWriteArrayList<StompFrame>();
        var latch = new CountDownLatch(messageCount);

        subscriber.getClient().subscribe("/topic/tcp-multi", msg -> {
            received.add(msg);
            latch.countDown();
        });
        Thread.sleep(100);

        for (int i = 0; i < messageCount; i++) {
            publisher.getClient().send("/topic/tcp-multi", "Message " + i, "text/plain");
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(messageCount);

        publisher.close();
        subscriber.close();
    }

    @Test
    void testTcpDisconnect() throws Exception {
        var tcpClient = new TcpStompClient("localhost", server.getPort());
        tcpClient.connect("localhost");
        assertThat(tcpClient.isConnected()).isTrue();

        var receiptFuture = tcpClient.getClient().disconnect();
        var receipt = receiptFuture.get(5, TimeUnit.SECONDS);
        assertThat(receipt.command()).isEqualTo(StompCommand.RECEIPT);

        tcpClient.close();
    }

    @Test
    void testTcpTransaction() throws Exception {
        var sender = new TcpStompClient("localhost", server.getPort());
        var receiver = new TcpStompClient("localhost", server.getPort());

        sender.connect("localhost");
        receiver.connect("localhost");

        var received = new CopyOnWriteArrayList<StompFrame>();
        var latch = new CountDownLatch(2);

        receiver.getClient().subscribe("/topic/tcp-tx", msg -> {
            received.add(msg);
            latch.countDown();
        });
        Thread.sleep(100);

        sender.getClient().begin("tcp-tx-1");
        sender.getClient().send("/topic/tcp-tx", "tx-msg-1", "text/plain", "tcp-tx-1");
        sender.getClient().send("/topic/tcp-tx", "tx-msg-2", "text/plain", "tcp-tx-1");

        Thread.sleep(200);
        assertThat(received).isEmpty();

        sender.getClient().commit("tcp-tx-1");
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(2);

        sender.close();
        receiver.close();
    }

    @Test
    void testTcpMultipleClients() throws Exception {
        var clients = new TcpStompClient[5];
        for (int i = 0; i < clients.length; i++) {
            clients[i] = new TcpStompClient("localhost", server.getPort());
            clients[i].connect("localhost");
        }

        for (var client : clients) {
            assertThat(client.isConnected()).isTrue();
        }

        for (var client : clients) {
            client.close();
        }
    }

    @Test
    void testTcpReceipt() throws Exception {
        var tcpClient = new TcpStompClient("localhost", server.getPort());
        tcpClient.connect("localhost");

        tcpClient.getClient().subscribe("/topic/tcp-receipt", msg -> {});
        Thread.sleep(50);

        var future = tcpClient.getClient().sendWithReceipt("/topic/tcp-receipt", "test", "text/plain");
        var receipt = future.get(5, TimeUnit.SECONDS);

        assertThat(receipt).isNotNull();
        assertThat(receipt.command()).isEqualTo(StompCommand.RECEIPT);

        tcpClient.close();
    }
}
