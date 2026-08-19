package ssg.legoflow.service.channel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;
import java.nio.channels.Selector;
import static org.assertj.core.api.Assertions.*;
class MulticastDataChannelTest {

    private Selector selector;
    private NetworkInterface loopbackInterface;
    private InetAddress multicastGroup;

    @BeforeEach
    void setUp() throws IOException {
        selector = Selector.open();
        loopbackInterface = NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress());
        multicastGroup = InetAddress.getByName("239.255.0.1");
    }

    @AfterEach
    void tearDown() throws IOException {
        selector.close();
    }

    @Test
    void testJoinGroup() throws IOException {
        // Given: a multicast-capable channel
        var dc = MulticastDataChannel.openMulticastChannel(StandardProtocolFamily.INET);
        var channel = new MulticastDataChannel(dc, selector);
        channel.bind(new InetSocketAddress(0));

        var config = new MulticastConfig(loopbackInterface, multicastGroup, 1, true);

        // When: joining a multicast group
        var key = channel.joinGroup(config);

        // Then: the channel is a member of the group
        assertThat(key).isNotNull();
        assertThat(key.isValid()).isTrue();
        assertThat(channel.getGroups()).hasSize(1).contains(config);

        channel.close();
    }

    @Test
    void testLeaveGroup() throws IOException {
        // Given: a channel that has joined a multicast group
        var dc = MulticastDataChannel.openMulticastChannel(StandardProtocolFamily.INET);
        var channel = new MulticastDataChannel(dc, selector);
        channel.bind(new InetSocketAddress(0));

        var config = new MulticastConfig(loopbackInterface, multicastGroup, 1, true);
        channel.joinGroup(config);
        assertThat(channel.getGroups()).hasSize(1);

        // When: leaving the group
        channel.leaveGroup(config);

        // Then: the group is no longer in the membership list
        assertThat(channel.getGroups()).isEmpty();

        channel.close();
    }

    @Test
    void testGetGroups() throws IOException {
        // Given: a multicast channel
        var dc = MulticastDataChannel.openMulticastChannel(StandardProtocolFamily.INET);
        var channel = new MulticastDataChannel(dc, selector);
        channel.bind(new InetSocketAddress(0));

        // Then: initially no groups
        assertThat(channel.getGroups()).isEmpty();

        // When: joining two groups
        var group1 = InetAddress.getByName("239.255.0.1");
        var group2 = InetAddress.getByName("239.255.0.2");
        var config1 = new MulticastConfig(loopbackInterface, group1, 1, true);
        var config2 = new MulticastConfig(loopbackInterface, group2, 1, true);

        channel.joinGroup(config1);
        channel.joinGroup(config2);

        // Then: both groups are listed
        assertThat(channel.getGroups()).hasSize(2).contains(config1, config2);

        channel.close();
    }

    @Test
    void testJoinGroupTwiceThrows() throws IOException {
        // Given: a channel already in a group
        var dc = MulticastDataChannel.openMulticastChannel(StandardProtocolFamily.INET);
        var channel = new MulticastDataChannel(dc, selector);
        channel.bind(new InetSocketAddress(0));

        var config = new MulticastConfig(loopbackInterface, multicastGroup, 1, true);
        channel.joinGroup(config);

        // When/Then: joining the same group again throws
        assertThatThrownBy(() -> channel.joinGroup(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Already a member");

        channel.close();
    }

    @Test
    void testLeaveGroupNotJoinedThrows() throws IOException {
        // Given: a channel not in any group
        var dc = MulticastDataChannel.openMulticastChannel(StandardProtocolFamily.INET);
        var channel = new MulticastDataChannel(dc, selector);
        channel.bind(new InetSocketAddress(0));

        var config = new MulticastConfig(loopbackInterface, multicastGroup, 1, true);

        // When/Then: leaving a group not joined throws
        assertThatThrownBy(() -> channel.leaveGroup(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not a member");

        channel.close();
    }

    @Test
    void testCloseDropsAllMemberships() throws IOException {
        // Given: a channel in multiple groups
        var dc = MulticastDataChannel.openMulticastChannel(StandardProtocolFamily.INET);
        var channel = new MulticastDataChannel(dc, selector);
        channel.bind(new InetSocketAddress(0));

        var config1 = new MulticastConfig(loopbackInterface, InetAddress.getByName("239.255.0.1"), 1, true);
        var config2 = new MulticastConfig(loopbackInterface, InetAddress.getByName("239.255.0.2"), 1, true);
        channel.joinGroup(config1);
        channel.joinGroup(config2);

        // When: closing the channel
        channel.close();

        // Then: the channel is closed
        assertThat(channel.isOpen()).isFalse();
        assertThat(channel.getGroups()).isEmpty();
    }

    @Test
    void testMulticastConfigValidation() {
        // Then: non-multicast address is rejected
        assertThatThrownBy(() -> new MulticastConfig(
                loopbackInterface, InetAddress.getByName("192.168.1.1"), 1, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a multicast address");

        // And: invalid TTL is rejected
        assertThatThrownBy(() -> new MulticastConfig(loopbackInterface, multicastGroup, 0, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL");

        assertThatThrownBy(() -> new MulticastConfig(loopbackInterface, multicastGroup, 256, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL");
    }

    @Test
    void testMulticastConfigFactoryMethods() throws Exception {
        // When: using the factory method with interface
        var config = MulticastConfig.of(multicastGroup, loopbackInterface);

        // Then: defaults are applied
        assertThat(config.group()).isEqualTo(multicastGroup);
        assertThat(config.networkInterface()).isEqualTo(loopbackInterface);
        assertThat(config.ttl()).isEqualTo(1);
        assertThat(config.loopback()).isTrue();
    }
}
