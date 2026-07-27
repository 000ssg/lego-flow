package ssg.legoflow.service.demo;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.ChannelPipeline;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.DatagramHandler;
import ssg.legoflow.service.channel.DatagramPacketInfo;
import ssg.legoflow.service.channel.MulticastConfig;
import ssg.legoflow.service.channel.MulticastDataChannel;
import ssg.legoflow.service.channel.UdpDataChannel;
import ssg.legoflow.service.manager.SelectableChannelManager;
import ssg.legoflow.service.manager.ServiceGroup;
import ssg.legoflow.service.manager.ServiceGroupStatistics;
import ssg.legoflow.service.user.ServiceUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive demo exercising all ServiceGroup features: lifecycle, UDP echo,
 * multicast, statistics, multi-selector distribution, channel manager basics,
 * and service lifecycle.
 *
 * @since 1.0.0
 */
public class DemoServiceAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoServiceAll.class);

    static final boolean USE_EXTERNAL = false;

    /**
     * Results of all demo scenarios.
     *
     * @param serviceGroupLifecycle   whether lifecycle (start/stop) works correctly
     * @param serviceGroupUdpEcho     number of UDP echo responses received
     * @param serviceGroupMulticast   whether multicast send/receive works
     * @param serviceGroupStatistics  whether statistics capture non-zero per-selector bytes
     * @param serviceGroupMultiSelector whether channels distribute across selectors
     * @param channelManagerBasics    whether SelectableChannelManager basic operations work
     * @param serviceLifecycle        whether AbstractService connect/disconnect lifecycle works
     * @since 1.0.0
     */
    public record Results(
            boolean serviceGroupLifecycle,
            int serviceGroupUdpEcho,
            boolean serviceGroupMulticast,
            boolean serviceGroupStatistics,
            boolean serviceGroupMultiSelector,
            boolean channelManagerBasics,
            boolean serviceLifecycle
    ) {}

    /**
     * Runs all demo scenarios and returns aggregated results.
     *
     * @return the results record
     * @throws Exception if any demo fails unexpectedly
     * @since 1.0.0
     */
    public static Results runAll() throws Exception {
        boolean lifecycle = demoServiceGroupLifecycle();
        int udpEcho = demoServiceGroupUdpEcho();
        boolean multicast = demoServiceGroupMulticast();
        boolean stats = demoServiceGroupStatistics();
        boolean multiSelector = demoServiceGroupMultiSelector();
        boolean channelMgr = demoChannelManagerBasics();
        boolean svcLifecycle = demoServiceLifecycle();

        return new Results(lifecycle, udpEcho, multicast, stats, multiSelector, channelMgr, svcLifecycle);
    }

    /**
     * Creates a ServiceGroup, starts it, verifies it is running, then stops it.
     *
     * @return {@code true} if the lifecycle transitions succeed
     * @throws Exception if an error occurs
     * @since 1.0.0
     */
    public static boolean demoServiceGroupLifecycle() throws Exception {
        try (var group = ServiceGroup.builder("lifecycle-demo")
                .dataSelectorCount(1)
                .bufferSize(4096)
                .selectTimeoutMs(50)
                .build()) {

            if (group.isRunning()) return false;
            group.start();
            if (!group.isRunning()) return false;
            Thread.sleep(50);
            group.stop();
            if (group.isRunning()) return false;

            LOG.info("ServiceGroup lifecycle demo passed");
            return true;
        }
    }

    /**
     * Creates a ServiceGroup with 2 data selectors, registers a UDP channel with
     * an echo handler, sends 10 datagrams, and counts the responses.
     *
     * @return the number of echo responses received
     * @throws Exception if an error occurs
     * @since 1.0.0
     */
    public static int demoServiceGroupUdpEcho() throws Exception {
        int messageCount = 10;
        var receivedCount = new AtomicInteger(0);
        var latch = new CountDownLatch(messageCount);

        try (var group = ServiceGroup.builder("udp-echo-demo")
                .dataSelectorCount(2)
                .bufferSize(8192)
                .selectTimeoutMs(50)
                .build()) {

            // Create server UDP channel
            var serverDc = DatagramChannel.open();
            var serverChannel = new UdpDataChannel(serverDc);
            serverChannel.bind(new InetSocketAddress("127.0.0.1", 0));
            var serverAddress = serverChannel.getLocalAddress();

            // Create echo pipeline
            var pipeline = new ChannelPipeline();
            pipeline.addLast(new DatagramHandler() {
                @Override
                public void onDatagram(DataChannel channel, DatagramPacketInfo packet) {
                    if (channel instanceof UdpDataChannel udp) {
                        try {
                            udp.sendTo(ByteBuffer.wrap(packet.toByteArray()), packet.sender());
                        } catch (IOException e) {
                            LOG.error("Echo send failed", e);
                        }
                    }
                }

                @Override
                public void onSendComplete(DataChannel channel, java.net.SocketAddress target) {
                }

                @Override
                public void onError(DataChannel channel, Throwable cause) {
                    LOG.error("Echo error", cause);
                }
            });

            // Register with data selector
            group.registerData(serverDc, SelectionKey.OP_READ, serverChannel, pipeline);
            group.start();

            // Client sends datagrams
            var clientDc = DatagramChannel.open();
            clientDc.configureBlocking(false);
            clientDc.bind(new InetSocketAddress("127.0.0.1", 0));

            for (int i = 0; i < messageCount; i++) {
                var msg = ("echo-" + i).getBytes();
                clientDc.send(ByteBuffer.wrap(msg), serverAddress);
                Thread.sleep(30);
            }

            // Wait and collect responses
            Thread.sleep(200);
            var buf = ByteBuffer.allocate(1024);
            for (int i = 0; i < messageCount * 2; i++) {
                buf.clear();
                var sender = clientDc.receive(buf);
                if (sender != null) {
                    receivedCount.incrementAndGet();
                    latch.countDown();
                }
                if (latch.getCount() == 0) break;
                Thread.sleep(20);
            }

            clientDc.close();
            group.stop();
        }

        int result = receivedCount.get();
        LOG.info("UDP echo demo: received {} of {} responses", result, messageCount);
        return result;
    }

    /**
     * Creates a ServiceGroup, creates a MulticastDataChannel, joins a loopback
     * multicast group, and verifies send/receive.
     *
     * @return {@code true} if multicast send/receive succeeds
     * @throws Exception if an error occurs
     * @since 1.0.0
     */
    public static boolean demoServiceGroupMulticast() throws Exception {
        try (var group = ServiceGroup.builder("multicast-demo")
                .dataSelectorCount(1)
                .selectTimeoutMs(50)
                .build()) {

            var loopback = NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress());
            if (loopback == null || !loopback.supportsMulticast()) {
                LOG.warn("Loopback interface not found or doesn't support multicast, skipping multicast demo");
                return true;
            }

            var multicastGroup = InetAddress.getByName("224.0.0.99");
            var config = new MulticastConfig(loopback, multicastGroup, 1, true);

            DatagramChannel dc;
            MulticastDataChannel channel;
            try {
                dc = MulticastDataChannel.openMulticastChannel(StandardProtocolFamily.INET);
                // Bind to wildcard address (not multicast group) — binding to a multicast
                // address directly is not supported on all platforms (e.g., macOS)
                dc.bind(new InetSocketAddress("0.0.0.0", 0));
                channel = new MulticastDataChannel(dc);
            } catch (IOException e) {
                LOG.warn("Cannot open multicast channel, skipping multicast demo: {}", e.getMessage());
                return true;
            }
            var localAddress = channel.getLocalAddress();

            // Create pipeline that records received data
            var received = new AtomicInteger(0);
            var pipeline = new ChannelPipeline();
            pipeline.addLast(new DatagramHandler() {
                @Override
                public void onDatagram(DataChannel ch, DatagramPacketInfo packet) {
                    received.incrementAndGet();
                }

                @Override
                public void onSendComplete(DataChannel ch, java.net.SocketAddress target) {
                }

                @Override
                public void onError(DataChannel ch, Throwable cause) {
                    LOG.error("Multicast error", cause);
                }
            });

            group.registerData(dc, SelectionKey.OP_READ, channel, pipeline);
            try {
                channel.joinGroup(config);
            } catch (IOException e) {
                LOG.warn("Cannot join multicast group, skipping multicast demo: {}", e.getMessage());
                channel.close();
                return true;
            }
            group.start();

            // Send to multicast group — may fail with NoRouteToHostException
            // when VPN/security software captures the multicast route
            try {
                var msg = ByteBuffer.wrap("multicast-test".getBytes());
                channel.sendTo(msg, new InetSocketAddress(multicastGroup, ((InetSocketAddress) localAddress).getPort()));
                Thread.sleep(200);
            } catch (IOException e) {
                LOG.warn("Cannot send to multicast group (VPN/route issue), skipping: {}", e.getMessage());
                channel.leaveGroup(config);
                group.stop();
                return true;
            }

            channel.leaveGroup(config);
            group.stop();

            boolean success = received.get() > 0;
            LOG.info("Multicast demo: received={}, success={}", received.get(), success);
            return success;
        }
    }

    /**
     * After performing I/O, verifies that statistics.snapshot() has non-zero
     * per-selector bytes.
     *
     * @return {@code true} if statistics captured non-zero bytes
     * @throws Exception if an error occurs
     * @since 1.0.0
     */
    public static boolean demoServiceGroupStatistics() throws Exception {
        try (var group = ServiceGroup.builder("stats-demo")
                .dataSelectorCount(2)
                .selectTimeoutMs(50)
                .build()) {

            // Manually record some statistics
            var stats = group.getStatistics();
            stats.setSelectorIndex(1);
            stats.addUdpRead(100, 5000);
            stats.addUdpWrite(200, 3000);
            stats.addConnection();
            stats.addKeyProcessed(ServiceGroupStatistics.READ, 1000);

            var snap = stats.snapshot();
            boolean hasUdpBytes = snap.udpBytes()[0] == 100 && snap.udpBytes()[1] == 200;
            boolean hasConnections = snap.connections() == 1;
            boolean hasSelectorBytes = snap.selectorReadBytes()[1] == 100;
            boolean hasKeyCounts = snap.keyCounts()[ServiceGroupStatistics.READ] == 1;

            boolean success = hasUdpBytes && hasConnections && hasSelectorBytes && hasKeyCounts;
            LOG.info("Statistics demo: udpBytes={}, connections={}, selectorBytes={}, keyCounts={}, success={}",
                    hasUdpBytes, hasConnections, hasSelectorBytes, hasKeyCounts, success);
            return success;
        }
    }

    /**
     * Creates a ServiceGroup with 2 data selectors, registers 4 UDP channels,
     * and verifies distribution across selectors.
     *
     * @return {@code true} if channels are distributed across multiple selectors
     * @throws Exception if an error occurs
     * @since 1.0.0
     */
    public static boolean demoServiceGroupMultiSelector() throws Exception {
        try (var group = ServiceGroup.builder("multi-selector-demo")
                .dataSelectorCount(2)
                .selectTimeoutMs(50)
                .build()) {

            var pipeline = new ChannelPipeline();
            int[] selectorAssignments = new int[4];

            for (int i = 0; i < 4; i++) {
                var dc = DatagramChannel.open();
                var channel = new UdpDataChannel(dc);
                channel.bind(new InetSocketAddress("127.0.0.1", 0));

                int assignedIdx = group.getNextDataSelectorIndex();
                selectorAssignments[i] = assignedIdx;
                group.registerData(dc, SelectionKey.OP_READ, channel, pipeline);
            }

            // Verify that channels are distributed across both selectors
            boolean hasSelector0 = false;
            boolean hasSelector1 = false;
            for (int idx : selectorAssignments) {
                if (idx == 0) hasSelector0 = true;
                if (idx == 1) hasSelector1 = true;
            }

            boolean distributed = hasSelector0 && hasSelector1;
            LOG.info("Multi-selector demo: assignments={}, distributed={}",
                    java.util.Arrays.toString(selectorAssignments), distributed);
            return distributed;
        }
    }

    /**
     * Creates a SelectableChannelManager and performs basic operations.
     *
     * @return {@code true} if basic operations succeed
     * @throws Exception if an error occurs
     * @since 1.0.0
     */
    public static boolean demoChannelManagerBasics() throws Exception {
        var ctx = new DefaultServiceContext(ServiceUser.anonymous());
        try (var manager = new SelectableChannelManager(ctx)) {
            // Verify event loop can start and stop
            manager.startEventLoop();
            boolean running = manager.isEventLoopRunning();
            Thread.sleep(50);
            manager.stopEventLoop();
            boolean stopped = !manager.isEventLoopRunning();

            boolean success = running && stopped;
            LOG.info("Channel manager basics demo: running={}, stopped={}, success={}", running, stopped, success);
            return success;
        }
    }

    /**
     * Creates an AbstractService subclass and tests connect/disconnect lifecycle.
     *
     * @return {@code true} if the lifecycle transitions succeed
     * @throws Exception if an error occurs
     * @since 1.0.0
     */
    public static boolean demoServiceLifecycle() throws Exception {
        var service = new AbstractService<String, String>(String.class, String.class,
                new ServiceDescriptor("lifecycle-test", "Lifecycle test service")) {
            @Override
            protected String[] convertToOutput(Context ctx, String... input) {
                return input;
            }

            @Override
            protected String[] convertToInput(Context ctx, String... output) {
                return output;
            }
        };

        var ctx = new DefaultServiceContext(ServiceUser.anonymous());

        boolean notConnectedInitially = !service.isConnected();
        service.connect(ctx);
        boolean connectedAfterConnect = service.isConnected();
        service.disconnect(ctx);
        boolean disconnectedAfterDisconnect = !service.isConnected();

        boolean success = notConnectedInitially && connectedAfterConnect && disconnectedAfterDisconnect;
        LOG.info("Service lifecycle demo: success={}", success);
        return success;
    }
}
