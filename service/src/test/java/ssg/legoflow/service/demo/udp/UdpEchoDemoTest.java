package ssg.legoflow.service.demo.udp;

import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.channel.DatagramPacketInfo;
import ssg.legoflow.service.channel.UdpDataChannel;
import ssg.legoflow.service.manager.UdpChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import static org.assertj.core.api.Assertions.*;

class UdpEchoDemoTest {

    private UdpChannelManager manager;
    private UdpEchoService echoService;
    private UdpDataChannel serverChannel;

    @BeforeEach
    void setUp() throws IOException {
        // Given: an echo service registered with the manager
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        manager = new UdpChannelManager(ctx);
        echoService = new UdpEchoService();
        manager.register(echoService);
        serverChannel = manager.bindAndRegister(echoService, new InetSocketAddress("127.0.0.1", 0));
        manager.getChannelPipeline(echoService).addLast(echoService);
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    @Test
    void testEchoSingleDatagram() throws Exception {
        // Given: a client channel
        var clientDc = DatagramChannel.open();
        var client = new UdpDataChannel(clientDc, manager.getSelector());
        client.bind(new InetSocketAddress("127.0.0.1", 0));

        var serverAddress = serverChannel.getLocalAddress();

        // When: sending a message to the echo service
        var message = "Hello Echo!";
        client.sendTo(ByteBuffer.wrap(message.getBytes()), serverAddress);
        Thread.sleep(50);

        // Simulate the event loop: receive datagram on server and dispatch to handler
        var packet = serverChannel.receiveDatagram();
        assertThat(packet).isNotNull();
        echoService.onDatagram(serverChannel, packet);
        Thread.sleep(50);

        // Then: the client receives the echoed response
        var response = client.receiveDatagram();
        assertThat(response).isNotNull();
        assertThat(new String(response.toByteArray())).isEqualTo(message);
        assertThat(echoService.getEchoCount()).isEqualTo(1);

        client.close();
    }

    @Test
    void testEchoMultipleDatagrams() throws Exception {
        // Given: a client channel
        var clientDc = DatagramChannel.open();
        var client = new UdpDataChannel(clientDc, manager.getSelector());
        client.bind(new InetSocketAddress("127.0.0.1", 0));

        var serverAddress = serverChannel.getLocalAddress();

        // When: sending multiple messages
        var messages = new String[]{"First", "Second", "Third"};
        for (var msg : messages) {
            client.sendTo(ByteBuffer.wrap(msg.getBytes()), serverAddress);
            Thread.sleep(30);

            var packet = serverChannel.receiveDatagram();
            assertThat(packet).isNotNull();
            echoService.onDatagram(serverChannel, packet);
            Thread.sleep(30);
        }

        // Then: all messages are echoed
        assertThat(echoService.getEchoCount()).isEqualTo(3);

        // And: responses are received
        for (var msg : messages) {
            var response = client.receiveDatagram();
            assertThat(response).isNotNull();
            assertThat(new String(response.toByteArray())).isEqualTo(msg);
        }

        client.close();
    }

    @Test
    void testEchoPreservesSenderAddress() throws Exception {
        // Given: a client channel
        var clientDc = DatagramChannel.open();
        var client = new UdpDataChannel(clientDc, manager.getSelector());
        client.bind(new InetSocketAddress("127.0.0.1", 0));

        var serverAddress = serverChannel.getLocalAddress();
        var clientAddress = client.getLocalAddress();

        // When: sending a message
        client.sendTo(ByteBuffer.wrap("address-test".getBytes()), serverAddress);
        Thread.sleep(50);

        var packet = serverChannel.receiveDatagram();
        assertThat(packet).isNotNull();

        // Then: the sender address matches the client
        assertThat(packet.sender()).isEqualTo(clientAddress);

        // And: echo sends back to the correct sender
        echoService.onDatagram(serverChannel, packet);
        Thread.sleep(50);

        var response = client.receiveDatagram();
        assertThat(response).isNotNull();
        assertThat(new String(response.toByteArray())).isEqualTo("address-test");

        client.close();
    }
}
