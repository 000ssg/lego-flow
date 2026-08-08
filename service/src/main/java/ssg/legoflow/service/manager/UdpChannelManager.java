package ssg.legoflow.service.manager;

import ssg.legoflow.service.Service;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.ChannelPipeline;
import ssg.legoflow.service.channel.DatagramHandler;
import ssg.legoflow.service.channel.DatagramPacketInfo;
import ssg.legoflow.service.channel.UdpDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A UDP-aware channel manager that extends {@link SelectableChannelManager} with
 * datagram-specific registration, binding, and event dispatching.
 *
 * <p>When the NIO selector signals an {@code OP_READ} event on a registered
 * {@link UdpDataChannel}, this manager receives the datagram (including sender
 * address) and dispatches it to all {@link DatagramHandler} instances in the
 * associated pipeline via {@link DatagramHandler#onDatagram}. Non-datagram handlers
 * receive the standard {@code onRead} callback instead.
 *
 * <p>Datagram processing is dispatched to virtual threads for non-blocking operation.
 *
 * @since 0.1.0
 */
public class UdpChannelManager extends SelectableChannelManager {

    private static final Logger LOG = LoggerFactory.getLogger(UdpChannelManager.class);

    private final ExecutorService datagramPool;

    /**
     * Creates a new {@code UdpChannelManager} with the given service context.
     *
     * @param context the service context for the manager
     * @throws NullPointerException if {@code context} is {@code null}
     * @since 0.1.0
     */
    public UdpChannelManager(ServiceContext context) {
        super(context);
        this.datagramPool = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Creates a new {@code UdpChannelManager} with custom buffer size and select timeout.
     *
     * @param context        the service context for the manager
     * @param bufferSize     the buffer size for read operations
     * @param selectTimeoutMs the selector timeout in milliseconds
     * @throws NullPointerException if {@code context} is {@code null}
     * @since 0.1.0
     */
    public UdpChannelManager(ServiceContext context, int bufferSize, long selectTimeoutMs) {
        super(context, bufferSize, selectTimeoutMs);
        this.datagramPool = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Registers a UDP data channel for the given service.
     *
     * <p>Registers the channel with the parent manager and configures the
     * selection key attachment for UDP-aware dispatching.
     *
     * @param service the service to associate the channel with
     * @param channel the UDP data channel to register
     * @throws NullPointerException if {@code service} or {@code channel} is {@code null}
     * @since 0.1.0
     */
    public void registerUdpChannel(Service<?, ?> service, UdpDataChannel channel) {
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(channel, "channel must not be null");
        registerChannel(service, channel);

        var pipeline = getChannelPipeline(service);
        var key = channel.getSelectionKey();
        if (key != null && key.isValid()) {
            key.attach(new ChannelRegistration(channel, pipeline));
        }
        LOG.debug("Registered UDP channel for service: {}", service.getDescriptor().name());
    }

    /**
     * Creates, binds, and registers a new UDP data channel for the given service.
     *
     * <p>Opens a new {@link DatagramChannel}, wraps it in a {@link UdpDataChannel},
     * binds it to the specified address, and registers it with this manager.
     *
     * @param service the service to associate the channel with
     * @param address the local address to bind to
     * @return the created and registered {@link UdpDataChannel}
     * @throws IOException          if channel creation, binding, or registration fails
     * @throws NullPointerException if {@code service} or {@code address} is {@code null}
     * @since 0.1.0
     */
    public UdpDataChannel bindAndRegister(Service<?, ?> service, SocketAddress address) throws IOException {
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(address, "address must not be null");

        var datagramChannel = DatagramChannel.open();
        var udpChannel = new UdpDataChannel(datagramChannel, getSelector());
        udpChannel.bind(address);
        registerUdpChannel(service, udpChannel);

        LOG.info("Bound and registered UDP channel for service {} on {}", service.getDescriptor().name(), address);
        return udpChannel;
    }

    /**
     * Dispatches a datagram received on the given UDP channel to the pipeline handlers.
     *
     * <p>{@link DatagramHandler} instances receive the
     * {@link DatagramHandler#onDatagram(ssg.legoflow.service.channel.DataChannel, DatagramPacketInfo)}
     * callback. Other {@link ChannelHandler} instances receive
     * {@link ChannelHandler#onRead} with the datagram data.
     *
     * <p>Dispatching is performed on a virtual thread.
     *
     * @param channel  the UDP channel that received the datagram
     * @param pipeline the pipeline to dispatch through
     * @param packet   the received datagram packet info
     * @since 0.1.0
     */
    public void dispatchDatagram(UdpDataChannel channel, ChannelPipeline pipeline, DatagramPacketInfo packet) {
        datagramPool.submit(() -> {
            for (var handler : pipeline.getHandlers()) {
                try {
                    if (handler instanceof DatagramHandler dh) {
                        dh.onDatagram(channel, packet);
                    } else {
                        handler.onRead(channel, packet.data());
                    }
                } catch (Exception e) {
                    LOG.error("Handler {} threw on datagram", handler.getClass().getSimpleName(), e);
                    pipeline.fireError(channel, e);
                    return;
                }
            }
        });
    }

    /**
     * Processes a readable UDP channel by receiving a datagram and dispatching it.
     *
     * <p>This method is called from the selector event loop when an {@code OP_READ}
     * event is detected on a UDP channel.
     *
     * @param channel  the UDP channel to process
     * @param pipeline the associated pipeline
     * @since 0.1.0
     */
    public void processUdpReadable(UdpDataChannel channel, ChannelPipeline pipeline) {
        datagramPool.submit(() -> {
            try {
                var packet = channel.receiveDatagram();
                if (packet != null) {
                    dispatchDatagram(channel, pipeline, packet);
                }
            } catch (Exception e) {
                LOG.error("Error receiving datagram", e);
                pipeline.fireError(channel, e);
            }
        });
    }

    /**
     * Closes this manager, shutting down the datagram processing pool.
     *
     * @since 0.1.0
     */
    @Override
    public void close() {
        datagramPool.close();
        super.close();
    }
}
