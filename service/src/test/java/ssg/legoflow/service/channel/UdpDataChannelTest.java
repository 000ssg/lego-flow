package ssg.legoflow.service.channel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;

import static org.assertj.core.api.Assertions.*;

class UdpDataChannelTest {

    private Selector selector;

    @BeforeEach
    void setUp() throws IOException {
        selector = Selector.open();
    }

    @AfterEach
    void tearDown() throws IOException {
        selector.close();
    }

    @Test
    void testBind() throws IOException {
        // Given: a new UDP channel
        var datagramChannel = DatagramChannel.open();
        var udpChannel = new UdpDataChannel(datagramChannel, selector);

        // When: binding to a local address
        udpChannel.bind(new InetSocketAddress("127.0.0.1", 0));

        // Then: the channel is bound and local address is available
        assertThat(udpChannel.isBound()).isTrue();
        assertThat(udpChannel.getLocalAddress()).isNotNull();

        udpChannel.close();
    }

    @Test
    void testSendReceive() throws Exception {
        // Given: two UDP channels bound to ephemeral ports
        var senderDc = DatagramChannel.open();
        var senderChannel = new UdpDataChannel(senderDc, selector);
        senderChannel.bind(new InetSocketAddress("127.0.0.1", 0));

        var receiverDc = DatagramChannel.open();
        var receiverChannel = new UdpDataChannel(receiverDc, selector);
        receiverChannel.bind(new InetSocketAddress("127.0.0.1", 0));

        var receiverAddress = receiverChannel.getLocalAddress();
        var message = "Hello UDP";

        // When: sending a datagram to the receiver
        var sendBuf = ByteBuffer.wrap(message.getBytes());
        senderChannel.sendTo(sendBuf, receiverAddress);

        // Allow NIO to propagate
        Thread.sleep(50);

        // Then: the receiver can read the datagram
        var packet = receiverChannel.receiveDatagram();
        assertThat(packet).isNotNull();
        assertThat(new String(packet.toByteArray())).isEqualTo(message);
        assertThat(packet.sender()).isNotNull();
        assertThat(packet.timestamp()).isGreaterThan(0);

        senderChannel.close();
        receiverChannel.close();
    }

    @Test
    void testConnectedUdp() throws Exception {
        // Given: two UDP channels
        var channelA_dc = DatagramChannel.open();
        var channelA = new UdpDataChannel(channelA_dc, selector);
        channelA.bind(new InetSocketAddress("127.0.0.1", 0));

        var channelB_dc = DatagramChannel.open();
        var channelB = new UdpDataChannel(channelB_dc, selector);
        channelB.bind(new InetSocketAddress("127.0.0.1", 0));

        // When: connecting A to B
        channelA.connect(channelB.getLocalAddress());

        // Then: A is in connected UDP mode
        assertThat(channelA.isConnectedUdp()).isTrue();
        assertThat(channelA.getRemoteAddress()).isEqualTo(channelB.getLocalAddress());

        // And: write/read work in connected mode
        var writeBuf = ByteBuffer.wrap("connected".getBytes());
        int written = channelA.write(writeBuf);
        assertThat(written).isEqualTo(9);

        Thread.sleep(50);

        var readBuf = ByteBuffer.allocate(64);
        int bytesRead = channelB.read(readBuf);
        readBuf.flip();
        var bytes = new byte[bytesRead];
        readBuf.get(bytes);
        assertThat(new String(bytes)).isEqualTo("connected");

        channelA.close();
        channelB.close();
    }

    @Test
    void testMaxPacketSize() throws IOException {
        // Given: a UDP channel
        var dc = DatagramChannel.open();
        var channel = new UdpDataChannel(dc, selector);

        // Then: default max packet size is 65535
        assertThat(channel.getMaxPacketSize()).isEqualTo(65535);

        // When: setting a custom max packet size
        channel.setMaxPacketSize(1500);
        assertThat(channel.getMaxPacketSize()).isEqualTo(1500);

        // Then: invalid sizes are rejected
        assertThatThrownBy(() -> channel.setMaxPacketSize(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> channel.setMaxPacketSize(-1))
                .isInstanceOf(IllegalArgumentException.class);

        channel.close();
    }

    @Test
    void testClose() throws IOException {
        // Given: an open UDP channel
        var dc = DatagramChannel.open();
        var channel = new UdpDataChannel(dc, selector);
        channel.bind(new InetSocketAddress("127.0.0.1", 0));
        assertThat(channel.isOpen()).isTrue();

        // When: closing the channel
        channel.close();

        // Then: the channel is no longer open and flags are reset
        assertThat(channel.isOpen()).isFalse();
        assertThat(channel.isBound()).isFalse();
        assertThat(channel.isConnectedUdp()).isFalse();
    }

    @Test
    void testDatagramInfo() throws Exception {
        // Given: a sender and receiver
        var senderDc = DatagramChannel.open();
        var sender = new UdpDataChannel(senderDc, selector);
        sender.bind(new InetSocketAddress("127.0.0.1", 0));

        var receiverDc = DatagramChannel.open();
        var receiver = new UdpDataChannel(receiverDc, selector);
        receiver.bind(new InetSocketAddress("127.0.0.1", 0));

        // When: sending a datagram
        var payload = "datagram-info-test";
        sender.sendTo(ByteBuffer.wrap(payload.getBytes()), receiver.getLocalAddress());
        Thread.sleep(50);

        // Then: DatagramPacketInfo contains correct metadata
        var packet = receiver.receiveDatagram();
        assertThat(packet).isNotNull();
        assertThat(packet.size()).isEqualTo(payload.length());
        assertThat(packet.toByteArray()).isEqualTo(payload.getBytes());
        assertThat(packet.sender()).isEqualTo(sender.getLocalAddress());
        assertThat(packet.data().isReadOnly()).isTrue();

        sender.close();
        receiver.close();
    }

    @Test
    void testWriteWithoutConnectThrows() throws IOException {
        // Given: an unconnected UDP channel
        var dc = DatagramChannel.open();
        var channel = new UdpDataChannel(dc, selector);
        channel.bind(new InetSocketAddress("127.0.0.1", 0));

        // When/Then: writing on unconnected channel throws
        assertThatThrownBy(() -> channel.write(ByteBuffer.wrap("test".getBytes())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");

        channel.close();
    }

    @Test
    void testSelectionKey() throws IOException {
        // Given: a UDP channel registered with a selector
        var dc = DatagramChannel.open();
        var channel = new UdpDataChannel(dc, selector);

        // Then: the selection key is available and valid
        assertThat(channel.getSelectionKey()).isNotNull();
        assertThat(channel.getSelectionKey().isValid()).isTrue();
        assertThat(channel.getSelectionKey().interestOps()).isEqualTo(java.nio.channels.SelectionKey.OP_READ);

        channel.close();
    }

    @Test
    void testNullArgumentsRejected() {
        // Then: null arguments are rejected
        assertThatThrownBy(() -> new UdpDataChannel(null, selector))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UdpDataChannel(DatagramChannel.open(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
