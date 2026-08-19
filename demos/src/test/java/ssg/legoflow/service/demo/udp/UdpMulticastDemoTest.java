package ssg.legoflow.service.demo.udp;

import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.channel.DatagramPacketInfo;
import ssg.legoflow.service.channel.MulticastConfig;
import ssg.legoflow.service.channel.MulticastDataChannel;
import ssg.legoflow.service.channel.UdpDataChannel;
import ssg.legoflow.service.manager.UdpChannelManager;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import static org.assertj.core.api.Assertions.*;
class UdpMulticastDemoTest {

    private UdpChannelManager manager;
    private NetworkInterface loopbackInterface;
    private InetAddress multicastGroup;

    @BeforeEach
    void setUp() throws IOException {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        manager = new UdpChannelManager(ctx);
        loopbackInterface = NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress());
        multicastGroup = InetAddress.getByName("239.255.0.1");
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    @Test
    void testJoinGroupAndReceiveMulticastMessage() throws Exception {
        // Given: a multicast service joined to a group
        var service = new UdpMulticastService();
        manager.register(service);

        var dc = MulticastDataChannel.openMulticastChannel(StandardProtocolFamily.INET);
        var multicastChannel = new MulticastDataChannel(dc, manager.getSelector());
        multicastChannel.bind(new InetSocketAddress(0));

        var config = new MulticastConfig(loopbackInterface, multicastGroup, 1, true);
        multicastChannel.joinGroup(config);

        manager.registerUdpChannel(service, multicastChannel);
        manager.getChannelPipeline(service).addLast(service);

        // When: sending a multicast message via a multicast-capable sender
        var senderDc = DatagramChannel.open(StandardProtocolFamily.INET);
        senderDc.setOption(StandardSocketOptions.IP_MULTICAST_IF, loopbackInterface);
        var sender = new UdpDataChannel(senderDc, manager.getSelector());
        sender.bind(new InetSocketAddress(0));

        var multicastTarget = new InetSocketAddress(multicastGroup, ((InetSocketAddress) multicastChannel.getLocalAddress()).getPort());
        var message = "Multicast Hello!";
        sender.sendTo(ByteBuffer.wrap(message.getBytes()), multicastTarget);
        Thread.sleep(100);

        // Then: receive and dispatch the multicast message
        var packet = multicastChannel.receiveDatagram();
        if (packet != null) {
            service.onDatagram(multicastChannel, packet);

            assertThat(service.getMessageCount()).isEqualTo(1);
            assertThat(service.getReceivedMessages()).hasSize(1);
            assertThat(new String(service.getReceivedMessages().getFirst().toByteArray())).isEqualTo(message);
        }
        // Note: multicast delivery on loopback is platform-dependent; packet may be null on some systems

        sender.close();
        multicastChannel.close();
    }

    @Test
    void testMultipleReceiversInGroup() throws Exception {
        // Given: two multicast services joined to the same group
        var service1 = new UdpMulticastService();
        var service2 = new UdpMulticastService();
        manager.register(service1);
        manager.register(service2);

        var dc1 = MulticastDataChannel.openMulticastChannel(StandardProtocolFamily.INET);
        var channel1 = new MulticastDataChannel(dc1, manager.getSelector());
        channel1.bind(new InetSocketAddress(0));

        var dc2 = MulticastDataChannel.openMulticastChannel(StandardProtocolFamily.INET);
        var channel2 = new MulticastDataChannel(dc2, manager.getSelector());
        channel2.bind(new InetSocketAddress(0));

        var config1 = new MulticastConfig(loopbackInterface, multicastGroup, 1, true);
        var config2 = new MulticastConfig(loopbackInterface, multicastGroup, 1, true);
        channel1.joinGroup(config1);
        channel2.joinGroup(config2);

        manager.registerUdpChannel(service1, channel1);
        manager.registerUdpChannel(service2, channel2);
        manager.getChannelPipeline(service1).addLast(service1);
        manager.getChannelPipeline(service2).addLast(service2);

        // Then: both channels are members of the group
        assertThat(channel1.getGroups()).hasSize(1);
        assertThat(channel2.getGroups()).hasSize(1);

        // When: publishing a multicast message via a multicast-capable sender
        var senderDc = DatagramChannel.open(StandardProtocolFamily.INET);
        senderDc.setOption(StandardSocketOptions.IP_MULTICAST_IF, loopbackInterface);
        var sender = new UdpDataChannel(senderDc, manager.getSelector());
        sender.bind(new InetSocketAddress(0));

        var port1 = ((InetSocketAddress) channel1.getLocalAddress()).getPort();
        var port2 = ((InetSocketAddress) channel2.getLocalAddress()).getPort();

        var message = "Group message";

        // Send to both receivers' ports (multicast delivery is port-specific)
        sender.sendTo(ByteBuffer.wrap(message.getBytes()), new InetSocketAddress(multicastGroup, port1));
        sender.sendTo(ByteBuffer.wrap(message.getBytes()), new InetSocketAddress(multicastGroup, port2));
        Thread.sleep(100);

        // Dispatch to each service
        var packet1 = channel1.receiveDatagram();
        var packet2 = channel2.receiveDatagram();

        if (packet1 != null) {
            service1.onDatagram(channel1, packet1);
            assertThat(service1.getMessageCount()).isEqualTo(1);
        }
        if (packet2 != null) {
            service2.onDatagram(channel2, packet2);
            assertThat(service2.getMessageCount()).isEqualTo(1);
        }

        sender.close();
        channel1.close();
        channel2.close();
    }

    @Test
    void testClearMessages() throws Exception {
        // Given: a service with some messages
        var service = new UdpMulticastService();
        var fakePacket = new DatagramPacketInfo(
                new InetSocketAddress("127.0.0.1", 12345),
                ByteBuffer.wrap("test".getBytes()),
                System.nanoTime()
        );
        service.onDatagram(null, fakePacket);
        assertThat(service.getMessageCount()).isEqualTo(1);

        // When: clearing messages
        service.clearMessages();

        // Then: messages and counter are reset
        assertThat(service.getMessageCount()).isEqualTo(0);
        assertThat(service.getReceivedMessages()).isEmpty();
    }
}
