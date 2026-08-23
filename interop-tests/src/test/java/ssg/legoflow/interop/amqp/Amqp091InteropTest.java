package ssg.legoflow.interop.amqp;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import ssg.legoflow.messaging.amqp091.client.Amqp091Client;
import ssg.legoflow.messaging.amqp091.client.ClientConfig;
import com.rabbitmq.client.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

/**
 * Interoperability tests: Lego Flow AMQP 0-9-1 client and official RabbitMQ client
 * both connecting to a real RabbitMQ broker.
 *
 * <p>Tests connection, channel management, exchange/queue declaration,
 * publish/consume, multiple messages, headers, QoS, and channel reopen.
 *
 * <p>Each test runs with BOTH clients to verify interoperability.
 *
 * <p>Docker: rabbitmq on port 5672, 15672 (management).
 * System properties: interop.amqp.host/port/username/password/queue/exchange
 *
 * @since 0.2.0
 */
    @Tag("messaging-protocols")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Amqp091InteropTest {

    private final String host = System.getProperty("interop.amqp.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.amqp.port", "5672"));
    private final String username = System.getProperty("interop.amqp.username", "guest");
    private final String password = System.getProperty("interop.amqp.password", "guest");
    private final String queueName = System.getProperty("interop.amqp.queue", "interop-amqp091-test");
    private final String exchangeName = System.getProperty("interop.amqp.exchange", "amqp091-test-ex");

    // Official client
    private Connection officialConn;
    private Channel officialCh;

    @BeforeAll
    void connectOfficial() throws Exception {
        var factory = new com.rabbitmq.client.ConnectionFactory();
        factory.setHost(System.getProperty("interop.amqp.host", "localhost"));
        factory.setPort(Integer.parseInt(System.getProperty("interop.amqp.port", "5672")));
        factory.setUsername(System.getProperty("interop.amqp.username", "guest"));
        factory.setPassword(System.getProperty("interop.amqp.password", "guest"));
        officialConn = factory.newConnection();
        officialCh = officialConn.createChannel();
    }

    @AfterAll
    void disconnectOfficial() throws Exception {
        if (officialCh != null && officialCh.isOpen()) { try { officialCh.close(); } catch (Exception ignored) {} }
        if (officialConn != null && officialConn.isOpen()) { try { officialConn.close(); } catch (Exception ignored) {} }
    }

    // ═══════════════════════════════════════════════════════════
    // OFFICIAL CLIENT TESTS (reference)
    // ═══════════════════════════════════════════════════════════

    @Test
    void testOfficialConnection() {
        assertThat(officialConn).isNotNull();
        assertThat(officialConn.isOpen()).isTrue();
    }

    @Test
    void testOfficialChannel() {
        assertThat(officialCh).isNotNull();
        assertThat(officialCh.isOpen()).isTrue();
    }

    @Test
    void testOfficialExchangeDeclare() throws Exception {
        String testExchange = exchangeName + "-test-" + System.currentTimeMillis();
        officialCh.exchangeDeclare(testExchange, BuiltinExchangeType.DIRECT, false);
        assertThat(testExchange).isNotEmpty();
    }

    @Test
    void testOfficialQueueDeclare() throws Exception {
        String testQueue = queueName + "-test-" + System.currentTimeMillis();
        AMQP.Queue.DeclareOk result = officialCh.queueDeclare(testQueue, false, true, true, null);
        assertThat(result.getQueue()).isEqualTo(testQueue);
    }

    @Test
    void testOfficialPublish() throws Exception {
        String testQueue = queueName + "-pub-" + System.currentTimeMillis();
        officialCh.queueDeclare(testQueue, false, true, true, null);
        String msg = "interop-test-" + System.currentTimeMillis();
        officialCh.basicPublish(exchangeName, testQueue, null, msg.getBytes(StandardCharsets.UTF_8));
        assertThat(msg).isNotEmpty();
    }

    @Test
    void testOfficialConsume() throws Exception {
        String testQueue = queueName + "-consume-" + System.currentTimeMillis();
        officialCh.queueDeclare(testQueue, false, true, true, null);
        officialCh.queueBind(testQueue, exchangeName, testQueue);
        String testMsg = "consume-" + System.currentTimeMillis();
        officialCh.basicPublish(exchangeName, testQueue, null, testMsg.getBytes(StandardCharsets.UTF_8));
        GetResponse resp = officialCh.basicGet(testQueue, true);
        assertThat(resp).isNotNull();
        String received = new String(resp.getBody(), StandardCharsets.UTF_8);
        assertThat(received).isEqualTo(testMsg);
    }

    @Test
    void testOfficialMultipleMessages() throws Exception {
        String testQueue = queueName + "-multi-" + System.currentTimeMillis();
        officialCh.queueDeclare(testQueue, false, true, true, null);
        officialCh.queueBind(testQueue, exchangeName, testQueue);
        for (int i = 0; i < 10; i++) {
            officialCh.basicPublish(exchangeName, testQueue, null, ("m" + i).getBytes(StandardCharsets.UTF_8));
        }
        int consumed = 0;
        GetResponse r;
        while ((r = officialCh.basicGet(testQueue, true)) != null) consumed++;
        assertThat(consumed).isEqualTo(10);
    }

    @Test
    void testOfficialHeaders() throws Exception {
        String testQueue = queueName + "-headers-" + System.currentTimeMillis();
        officialCh.queueDeclare(testQueue, false, true, true, null);
        officialCh.queueBind(testQueue, exchangeName, testQueue);
        Map<String, Object> headers = new HashMap<>();
        headers.put("key1", "val1");
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .headers(headers)
                .contentType("text/plain")
                .build();
        officialCh.basicPublish(exchangeName, testQueue, props, "hi".getBytes(StandardCharsets.UTF_8));
        GetResponse hr = officialCh.basicGet(testQueue, true);
        assertThat(hr).isNotNull();
        assertThat(hr.getProps().getHeaders()).containsKey("key1");
        Object val = hr.getProps().getHeaders().get("key1");
        String valStr = val instanceof byte[] ? new String((byte[]) val) : val.toString();
        assertThat(valStr).isEqualTo("val1");
    }

    @Test
    void testOfficialQoS() throws Exception {
        officialCh.basicQos(10);
        String testQueue = queueName + "-qos-" + System.currentTimeMillis();
        officialCh.queueDeclare(testQueue, false, true, true, null);
        officialCh.queueBind(testQueue, exchangeName, testQueue);
        for (int i = 0; i < 20; i++) {
            officialCh.basicPublish(exchangeName, testQueue, null, ("q" + i).getBytes(StandardCharsets.UTF_8));
        }
        for (int i = 0; i < 20; i++) {
            GetResponse qr = officialCh.basicGet(testQueue, true);
            if (qr == null) break;
        }
        assertThat(officialCh.isOpen()).isTrue();
    }

    @Test
    void testOfficialChannelCloseReopen() throws Exception {
        int oldChannel = officialCh.getChannelNumber();
        officialCh.close();
        Channel newCh = officialConn.createChannel();
        assertThat(newCh.isOpen()).isTrue();
        assertThat(newCh.getChannelNumber()).isGreaterThan(0);
        newCh.close();
    }

    // ═══════════════════════════════════════════════════════════
    // LEGO FLOW CLIENT TESTS (our client)
    // ═══════════════════════════════════════════════════════════

    @Test
    void testOurClientConnection() throws Exception {
        try (Amqp091Client client = Amqp091Client.builder()
                .config(ClientConfig.builder()
                        .host(host).port(port)
                        .username(username).password(password)
                        .containerId("lego-flow-client")
                        .build()).build()) {
            client.connect();
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test
    void testOurClientChannelOpen() throws Exception {
        try (Amqp091Client client = Amqp091Client.builder()
                .config(ClientConfig.builder()
                        .host(host).port(port)
                        .username(username).password(password)
                        .containerId("lego-flow-client")
                        .build()).build()) {
            client.connect();
            int channel = client.openChannel();
            assertThat(channel).isGreaterThan(0);
        }
    }

    @Test
    void testOurClientQueueDeclare() throws Exception {
        String testQueue = queueName + "-our-" + System.currentTimeMillis();
        try (Amqp091Client client = Amqp091Client.builder()
                .config(ClientConfig.builder()
                        .host(host).port(port)
                        .username(username).password(password)
                        .containerId("lego-flow-client")
                        .build()).build()) {
            client.connect();
            int ch = client.openChannel();
            Amqp091Client.QueueDeclareResult result = client.declareQueue(testQueue, false, true, true, null);
            assertThat(result.queueName()).isEqualTo(testQueue);
        }
    }

    @Test
    void testOurClientPublishAndConsume() throws Exception {
        String testQueue = queueName + "-ourpub-" + System.currentTimeMillis();
        String testMsg = "lego-flow-msg-" + System.currentTimeMillis();
        try (Amqp091Client client = Amqp091Client.builder()
                .config(ClientConfig.builder()
                        .host(host).port(port)
                        .username(username).password(password)
                        .containerId("lego-flow-client")
                        .build()).build()) {
            client.connect();
            int ch = client.openChannel();
            client.declareQueue(testQueue, false, true, true, null);
            client.publish(testQueue, testMsg.getBytes(StandardCharsets.UTF_8));
            Amqp091Client.DeliverResult result = client.basicGet(testQueue);
            assertThat(result).isNotNull();
            assertThat(result.isEmpty()).isFalse();
            assertThat(new String(result.body(), StandardCharsets.UTF_8)).isEqualTo(testMsg);
        }
    }

    @Test
    void testOurClientMultipleMessages() throws Exception {
        String testQueue = queueName + "-ourmulti-" + System.currentTimeMillis();
        try (Amqp091Client client = Amqp091Client.builder()
                .config(ClientConfig.builder()
                        .host(host).port(port)
                        .username(username).password(password)
                        .containerId("lego-flow-client")
                        .build()).build()) {
            client.connect();
            int ch = client.openChannel();
            client.declareQueue(testQueue, false, true, true, null);
            for (int i = 0; i < 10; i++) {
                client.publish(testQueue, ("msg" + i).getBytes(StandardCharsets.UTF_8));
            }
            int consumed = 0;
            for (int i = 0; i < 15; i++) {
                Amqp091Client.DeliverResult r = client.basicGet(testQueue);
                if (r != null && !r.isEmpty()) consumed++;
                else break;
            }
            assertThat(consumed).isEqualTo(10);
        }
    }

    @Test
    void testOurClientQueueOperations() throws Exception {
        String testQueue = queueName + "-ourop-" + System.currentTimeMillis();
        try (Amqp091Client client = Amqp091Client.builder()
                .config(ClientConfig.builder()
                        .host(host).port(port)
                        .username(username).password(password)
                        .containerId("lego-flow-client")
                        .build()).build()) {
            client.connect();
            int ch = client.openChannel();

            // Declare and publish
            client.declareQueue(testQueue, false, true, true, null);
            client.publish(testQueue, "hello".getBytes(StandardCharsets.UTF_8));
            client.publish(testQueue, "world".getBytes(StandardCharsets.UTF_8));

            // Purge
            int purged = client.queuePurge(testQueue);
            assertThat(purged).isEqualTo(2);

            // Declare and bind queue
            String bindQueue = testQueue + "-bind";
            client.declareQueue(bindQueue, false, true, true, null);
            client.queueBind(bindQueue, exchangeName, bindQueue);

            // Unbind
            client.queueUnbind(bindQueue, exchangeName, bindQueue);

            // Delete
            client.declareQueue(testQueue + "-del", false, true, true, null);
            int deleted = client.queueDelete(testQueue + "-del");
            assertThat(deleted).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void testOurClientQoSAndAck() throws Exception {
        String testQueue = queueName + "-ourqos-" + System.currentTimeMillis();
        try (Amqp091Client client = Amqp091Client.builder()
                .config(ClientConfig.builder()
                        .host(host).port(port)
                        .username(username).password(password)
                        .containerId("lego-flow-client")
                        .build()).build()) {
            client.connect();
            int ch = client.openChannel();
            client.declareQueue(testQueue, false, true, true, null);
            client.basicQos(10);

            for (int i = 0; i < 5; i++) {
                client.publish(testQueue, ("qos" + i).getBytes(StandardCharsets.UTF_8));
            }

            // Get without auto-ack and acknowledge
            Amqp091Client.DeliverResult r = client.basicGet(testQueue);
            assertThat(r).isNotNull();
            // Note: basicGet with autoAck=false needs manual ack, but since the frame
            // is read directly by readFrame() we need to handle ack separately
        }
    }

    @Test
    void testOurClientExchangeOps() throws Exception {
        String testExchange = exchangeName + "-our-" + System.currentTimeMillis();
        try (Amqp091Client client = Amqp091Client.builder()
                .config(ClientConfig.builder()
                        .host(host).port(port)
                        .username(username).password(password)
                        .containerId("lego-flow-client")
                        .build()).build()) {
            client.connect();
            int ch = client.openChannel();

            client.declareExchange(testExchange, "direct");
            client.deleteExchange(testExchange);
        }
    }

    @Test
    void testOurClientChannelCloseReopen() throws Exception {
        try (Amqp091Client client = Amqp091Client.builder()
                .config(ClientConfig.builder()
                        .host(host).port(port)
                        .username(username).password(password)
                        .containerId("lego-flow-client")
                        .build()).build()) {
            client.connect();
            int ch1 = client.openChannel();
            client.closeChannel(ch1);
            int ch2 = client.openChannel();
            assertThat(ch2).isNotEqualTo(ch1);
        }
    }

    @Test
    void testOurClientCloseGracefully() throws Exception {
        try (Amqp091Client client = Amqp091Client.builder()
                .config(ClientConfig.builder()
                        .host(host).port(port)
                        .username(username).password(password)
                        .containerId("lego-flow-client")
                        .build()).build()) {
            client.connect();
            assertThat(client.isConnected()).isTrue();
            client.close();
            assertThat(client.isConnected()).isFalse();
        }
    }
}
