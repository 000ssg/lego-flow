package ssg.legoflow.service.demo.udp;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.DatagramHandler;
import ssg.legoflow.service.channel.DatagramPacketInfo;
import ssg.legoflow.service.channel.UdpDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple UDP echo service that receives datagrams and echoes them back to the sender.
 *
 * <p>Implements {@link DatagramHandler} to receive datagrams and immediately send
 * the payload back to the original sender address. Tracks the total number of
 * echoed datagrams via an atomic counter.
 *
 * @since 1.0.0
 */
public class UdpEchoService extends AbstractService<byte[], byte[]> implements DatagramHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UdpEchoService.class);

    private final AtomicLong echoCount = new AtomicLong(0);

    /**
     * Creates a new {@code UdpEchoService} with the default descriptor.
     *
     * @since 1.0.0
     */
    public UdpEchoService() {
        super(byte[].class, byte[].class, new ServiceDescriptor("udp-echo", "UDP echo service"));
    }

    /**
     * Handles a received datagram by echoing it back to the sender.
     *
     * @param channel the data channel that received the datagram
     * @param packet  the received datagram packet with sender address and payload
     * @since 1.0.0
     */
    @Override
    public void onDatagram(DataChannel channel, DatagramPacketInfo packet) {
        if (!(channel instanceof UdpDataChannel udpChannel)) {
            LOG.warn("onDatagram called with non-UDP channel: {}", channel.getClass().getSimpleName());
            return;
        }
        try {
            var responseData = ByteBuffer.wrap(packet.toByteArray());
            udpChannel.sendTo(responseData, packet.sender());
            echoCount.incrementAndGet();
            LOG.debug("Echoed {} bytes to {}", packet.size(), packet.sender());
        } catch (IOException e) {
            LOG.error("Failed to echo datagram to {}", packet.sender(), e);
            onError(channel, e);
        }
    }

    /**
     * Called when a datagram send completes successfully.
     *
     * @param channel the data channel that sent the datagram
     * @param target  the destination address
     * @since 1.0.0
     */
    @Override
    public void onSendComplete(DataChannel channel, java.net.SocketAddress target) {
        LOG.debug("Echo send complete to {}", target);
    }

    /**
     * Returns the total number of datagrams echoed.
     *
     * @return the echo count
     * @since 1.0.0
     */
    public long getEchoCount() {
        return echoCount.get();
    }

    /**
     * Converts input to output (identity transform for echo).
     *
     * @param ctx   the processing context
     * @param input the input data
     * @return the same data as output
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    @Override
    protected byte[][] convertToOutput(Context ctx, byte[]... input) {
        return input;
    }

    /**
     * Converts output to input (identity transform for echo).
     *
     * @param ctx    the processing context
     * @param output the output data
     * @return the same data as input
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    @Override
    protected byte[][] convertToInput(Context ctx, byte[]... output) {
        return output;
    }
}
