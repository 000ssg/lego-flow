package ssg.legoflow.service.manager;

import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.DatagramHandler;
import ssg.legoflow.service.channel.DatagramPacketInfo;
import ssg.legoflow.service.channel.UdpDataChannel;
import ssg.legoflow.service.demo.udp.UdpEchoService;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class UdpChannelManagerTest {

    private UdpChannelManager manager;
    private ServiceContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new DefaultServiceContext(ServiceUser.anonymous());
        manager = new UdpChannelManager(ctx);
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    @Test
    void testRegisterUdpChannel() throws IOException {
        // Given: a UDP echo service and channel
        var service = new UdpEchoService();
        manager.register(service);

        var dc = DatagramChannel.open();
        var udpChannel = new UdpDataChannel(dc, manager.getSelector());
        udpChannel.bind(new InetSocketAddress("127.0.0.1", 0));

        // When: registering the UDP channel
        manager.registerUdpChannel(service, udpChannel);

        // Then: the channel and pipeline are accessible
        assertThat(manager.getChannel(service)).isSameAs(udpChannel);
        assertThat(manager.getChannelPipeline(service)).isNotNull();

        udpChannel.close();
    }

    @Test
    void testBindAndRegister() throws IOException {
        // Given: a UDP echo service
        var service = new UdpEchoService();
        manager.register(service);

        // When: binding and registering
        var address = new InetSocketAddress("127.0.0.1", 0);
        var udpChannel = manager.bindAndRegister(service, address);

        // Then: the channel is bound and registered
        assertThat(udpChannel).isNotNull();
        assertThat(udpChannel.isBound()).isTrue();
        assertThat(udpChannel.isOpen()).isTrue();
        assertThat(manager.getChannel(service)).isSameAs(udpChannel);

        udpChannel.close();
    }

    @Test
    void testDatagramDispatch() throws Exception {
        // Given: a registered UDP channel with a datagram handler
        var service = new UdpEchoService();
        manager.register(service);

        var udpChannel = manager.bindAndRegister(service, new InetSocketAddress("127.0.0.1", 0));
        var receiverAddress = udpChannel.getLocalAddress();

        var receivedPackets = new CopyOnWriteArrayList<DatagramPacketInfo>();
        var latch = new CountDownLatch(1);

        DatagramHandler handler = new DatagramHandler() {
            @Override
            public void onDatagram(DataChannel channel, DatagramPacketInfo packet) {
                receivedPackets.add(packet);
                latch.countDown();
            }

            @Override
            public void onSendComplete(DataChannel channel, SocketAddress target) {}
        };

        var pipeline = manager.getChannelPipeline(service);
        pipeline.addLast(handler);

        // When: sending a datagram to the registered channel
        var senderDc = DatagramChannel.open();
        var sender = new UdpDataChannel(senderDc, manager.getSelector());
        sender.bind(new InetSocketAddress("127.0.0.1", 0));

        var message = "dispatch-test";
        sender.sendTo(ByteBuffer.wrap(message.getBytes()), receiverAddress);

        // Receive and dispatch manually (simulating event loop)
        Thread.sleep(50);
        var packet = udpChannel.receiveDatagram();
        assertThat(packet).isNotNull();
        manager.dispatchDatagram(udpChannel, pipeline, packet);

        // Then: the handler receives the datagram
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedPackets).hasSize(1);
        assertThat(new String(receivedPackets.getFirst().toByteArray())).isEqualTo(message);

        sender.close();
        udpChannel.close();
    }

    @Test
    void testCloseStopsManager() throws IOException {
        // Given: a manager with a registered channel
        var service = new UdpEchoService();
        manager.register(service);
        var udpChannel = manager.bindAndRegister(service, new InetSocketAddress("127.0.0.1", 0));
        manager.startEventLoop();

        // When: closing the manager
        manager.close();

        // Then: the event loop is stopped
        assertThat(manager.isEventLoopRunning()).isFalse();
    }

    @Test
    void testNullArgumentsRejected() {
        // Then: null service is rejected
        assertThatThrownBy(() -> manager.registerUdpChannel(null, null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> manager.bindAndRegister(null, new InetSocketAddress(0)))
                .isInstanceOf(NullPointerException.class);

        var service = new UdpEchoService();
        assertThatThrownBy(() -> manager.bindAndRegister(service, null))
                .isInstanceOf(NullPointerException.class);
    }
}
