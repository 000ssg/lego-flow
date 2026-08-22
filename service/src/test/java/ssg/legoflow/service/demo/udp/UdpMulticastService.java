package ssg.legoflow.service.demo.udp;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.DatagramHandler;
import ssg.legoflow.service.channel.DatagramPacketInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
/**
 * A multicast demo service that joins a multicast group and processes group messages.
 *
 * <p>Implements {@link DatagramHandler} to receive multicast datagrams. All received
 * messages are stored in a thread-safe list for inspection. Tracks the total count
 * of received multicast messages.
 *
 * @since 0.1.0
 */
public class UdpMulticastService extends AbstractService<byte[], byte[]> implements DatagramHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UdpMulticastService.class);

    private final CopyOnWriteArrayList<DatagramPacketInfo> receivedMessages = new CopyOnWriteArrayList<>();
    private final AtomicLong messageCount = new AtomicLong(0);

    /**
     * Creates a new {@code UdpMulticastService} with the default descriptor.
     *
     * @since 0.1.0
     */
    public UdpMulticastService() {
        super(byte[].class, byte[].class, new ServiceDescriptor("udp-multicast", "UDP multicast service"));
    }

    /**
     * Handles a received multicast datagram by storing it and incrementing the message counter.
     *
     * @param channel the data channel that received the datagram
     * @param packet  the received datagram packet
     * @since 0.1.0
     */
    @Override
    public void onDatagram(DataChannel channel, DatagramPacketInfo packet) {
        receivedMessages.add(packet);
        messageCount.incrementAndGet();
        LOG.debug("Received multicast message ({} bytes) from {}", packet.size(), packet.sender());
    }

    /**
     * Called when a datagram send completes.
     *
     * @param channel the data channel
     * @param target  the destination address
     * @since 0.1.0
     */
    @Override
    public void onSendComplete(DataChannel channel, SocketAddress target) {
        LOG.debug("Multicast send complete to {}", target);
    }

    /**
     * Returns the list of all received multicast messages.
     *
     * @return an unmodifiable view of received messages
     * @since 0.1.0
     */
    public List<DatagramPacketInfo> getReceivedMessages() {
        return List.copyOf(receivedMessages);
    }

    /**
     * Returns the total number of multicast messages received.
     *
     * @return the message count
     * @since 0.1.0
     */
    public long getMessageCount() {
        return messageCount.get();
    }

    /**
     * Clears the stored messages and resets the counter.
     *
     * @since 0.1.0
     */
    public void clearMessages() {
        receivedMessages.clear();
        messageCount.set(0);
    }

    /**
     * Converts input to output (identity transform).
     *
     * @param ctx   the processing context
     * @param input the input data
     * @return the same data as output
     * @since 0.1.0
     */
    @SuppressWarnings("unchecked")
    @Override
    protected byte[][] convertToOutput(Context ctx, byte[]... input) {
        return input;
    }

    /**
     * Converts output to input (identity transform).
     *
     * @param ctx    the processing context
     * @param output the output data
     * @return the same data as input
     * @since 0.1.0
     */
    @SuppressWarnings("unchecked")
    @Override
    protected byte[][] convertToInput(Context ctx, byte[]... output) {
        return output;
    }
}
