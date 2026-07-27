package ssg.legoflow.service.manager;

import ssg.legoflow.service.channel.ChannelPipeline;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.DatagramHandler;
import ssg.legoflow.service.channel.DatagramPacketInfo;
import ssg.legoflow.service.channel.UdpDataChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ServiceGroup}.
 *
 * @since 1.0.0
 */
class ServiceGroupTest {

    private ServiceGroup group;

    @BeforeEach
    void setUp() {
        group = ServiceGroup.builder("test-group")
                .dataSelectorCount(2)
                .bufferSize(8192)
                .selectTimeoutMs(50)
                .build();
    }

    @AfterEach
    void tearDown() {
        group.close();
    }

    @Test
    void testBuilderCreatesGroup() {
        assertThat(group.getName()).isEqualTo("test-group");
        assertThat(group.getDataSelectorCount()).isEqualTo(2);
        assertThat(group.getBufferSize()).isEqualTo(8192);
    }

    @Test
    void testBuilderDefaults() {
        try (var defaultGroup = ServiceGroup.builder("defaults").build()) {
            assertThat(defaultGroup.getDataSelectorCount()).isEqualTo(2);
            assertThat(defaultGroup.getBufferSize()).isEqualTo(8192);
        }
    }

    @Test
    void testBuilderValidation() {
        assertThatThrownBy(() -> ServiceGroup.builder("bad").dataSelectorCount(0).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServiceGroup.builder("bad").bufferSize(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServiceGroup.builder("bad").selectTimeoutMs(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testStartStop() {
        assertThat(group.isRunning()).isFalse();
        group.start();
        assertThat(group.isRunning()).isTrue();
        group.stop();
        assertThat(group.isRunning()).isFalse();
    }

    @Test
    void testDoubleStartIsIdempotent() {
        group.start();
        group.start(); // Should not throw
        assertThat(group.isRunning()).isTrue();
    }

    @Test
    void testDoubleStopIsIdempotent() {
        group.start();
        group.stop();
        group.stop(); // Should not throw
        assertThat(group.isRunning()).isFalse();
    }

    @Test
    void testRegisterDataChannel() throws IOException {
        var dc = DatagramChannel.open();
        var channel = new UdpDataChannel(dc);
        channel.bind(new InetSocketAddress("127.0.0.1", 0));
        var pipeline = new ChannelPipeline();

        var key = group.registerData(dc, SelectionKey.OP_READ, channel, pipeline);

        assertThat(key).isNotNull();
        assertThat(key.isValid()).isTrue();
        assertThat(group.getRegisteredChannels()).contains(channel);
    }

    @Test
    void testRoundRobinDistribution() throws IOException {
        var pipeline = new ChannelPipeline();

        // Register 4 channels, expect 2 on each data selector
        for (int i = 0; i < 4; i++) {
            var dc = DatagramChannel.open();
            var channel = new UdpDataChannel(dc);
            channel.bind(new InetSocketAddress("127.0.0.1", 0));
            group.registerData(dc, SelectionKey.OP_READ, channel, pipeline);
        }

        // With 2 data selectors and 4 channels, both selectors should have 2 keys each
        var sel0Keys = group.getDataSelector(0).keys().size();
        var sel1Keys = group.getDataSelector(1).keys().size();
        assertThat(sel0Keys).isEqualTo(2);
        assertThat(sel1Keys).isEqualTo(2);
    }

    @Test
    void testConnectorSelectorAccessible() {
        assertThat(group.getConnectorSelector()).isNotNull();
        assertThat(group.getConnectorSelector().isOpen()).isTrue();
    }

    @Test
    void testDataSelectorAccessible() {
        assertThat(group.getDataSelector(0)).isNotNull();
        assertThat(group.getDataSelector(1)).isNotNull();
        assertThatThrownBy(() -> group.getDataSelector(2))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
    }

    @Test
    void testStatisticsAvailable() {
        assertThat(group.getStatistics()).isNotNull();
        assertThat(group.getStatistics().getSelectorCount()).isEqualTo(3); // 1 connector + 2 data
    }

    @Test
    void testUdpEchoThroughEventLoop() throws Exception {
        var received = new AtomicInteger(0);

        // Create server channel
        var serverDc = DatagramChannel.open();
        var serverChannel = new UdpDataChannel(serverDc);
        serverChannel.bind(new InetSocketAddress("127.0.0.1", 0));
        var serverAddress = serverChannel.getLocalAddress();

        // Echo pipeline
        var pipeline = new ChannelPipeline();
        pipeline.addLast(new DatagramHandler() {
            @Override
            public void onDatagram(DataChannel channel, DatagramPacketInfo packet) {
                if (channel instanceof UdpDataChannel udp) {
                    try {
                        udp.sendTo(ByteBuffer.wrap(packet.toByteArray()), packet.sender());
                        received.incrementAndGet();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }

            @Override
            public void onSendComplete(DataChannel channel, java.net.SocketAddress target) {
            }

            @Override
            public void onError(DataChannel channel, Throwable cause) {
            }
        });

        group.registerData(serverDc, SelectionKey.OP_READ, serverChannel, pipeline);
        group.start();

        // Client
        var clientDc = DatagramChannel.open();
        clientDc.configureBlocking(false);
        clientDc.bind(new InetSocketAddress("127.0.0.1", 0));

        clientDc.send(ByteBuffer.wrap("hello".getBytes()), serverAddress);
        Thread.sleep(300);

        var buf = ByteBuffer.allocate(1024);
        var sender = clientDc.receive(buf);

        clientDc.close();
        group.stop();

        // At least verify the server processed the datagram
        assertThat(received.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testCloseReleasesResources() throws IOException {
        var dc = DatagramChannel.open();
        var channel = new UdpDataChannel(dc);
        channel.bind(new InetSocketAddress("127.0.0.1", 0));
        group.registerData(dc, SelectionKey.OP_READ, channel, new ChannelPipeline());

        group.close();

        assertThat(group.isRunning()).isFalse();
        assertThat(group.getRegisteredChannels()).isEmpty();
    }

    @Test
    void testCloseAfterStartReleasesResources() throws Exception {
        group.start();
        Thread.sleep(50);
        group.close();
        assertThat(group.isRunning()).isFalse();
    }

    @Test
    void testNullNameThrows() {
        assertThatThrownBy(() -> ServiceGroup.builder(null).build())
                .isInstanceOf(NullPointerException.class);
    }
}
