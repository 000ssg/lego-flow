package ssg.legoflow.service.channel;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * A specialized {@link ChannelHandler} for datagram-oriented communication.
 *
 * <p>Provides callbacks for datagram receipt and send completion in addition
 * to the standard channel handler events. Default implementations of
 * {@link #onRead(DataChannel, ByteBuffer)} and {@link #onWrite(DataChannel)}
 * delegate to the datagram-specific methods when the channel is a {@link UdpDataChannel}.
 *
 * @since 0.1.0
 */
public interface DatagramHandler extends ChannelHandler {

    /**
     * Called when a datagram is received on the channel.
     *
     * @param channel the data channel that received the datagram
     * @param packet  the datagram packet information including sender address and payload
     * @since 0.1.0
     */
    void onDatagram(DataChannel channel, DatagramPacketInfo packet);

    /**
     * Called when a datagram has been successfully sent to the target address.
     *
     * @param channel the data channel that sent the datagram
     * @param target  the destination address the datagram was sent to
     * @since 0.1.0
     */
    void onSendComplete(DataChannel channel, SocketAddress target);

    /**
     * Default read handler that delegates to {@link #onDatagram(DataChannel, DatagramPacketInfo)}
     * when the channel is a {@link UdpDataChannel}.
     *
     * <p>If the channel is a {@code UdpDataChannel}, this method calls
     * {@link UdpDataChannel#receiveDatagram()} and forwards the result to
     * {@link #onDatagram(DataChannel, DatagramPacketInfo)}. Otherwise, this is a no-op.
     *
     * @param channel the data channel that is readable
     * @param data    the read data buffer
     * @since 0.1.0
     */
    @Override
    default void onRead(DataChannel channel, ByteBuffer data) {
        if (channel instanceof UdpDataChannel udpChannel) {
            try {
                var packet = udpChannel.receiveDatagram();
                if (packet != null) {
                    onDatagram(channel, packet);
                }
            } catch (Exception e) {
                onError(channel, e);
            }
        }
    }

    /**
     * Default write handler that is a no-op. Override if write-complete
     * notification is needed beyond {@link #onSendComplete(DataChannel, SocketAddress)}.
     *
     * @param channel the data channel that is writable
     * @since 0.1.0
     */
    @Override
    default void onWrite(DataChannel channel) {
        // No-op by default; send completion is handled via onSendComplete
    }

    /**
     * Default connect handler. No-op for datagram channels.
     *
     * @param channel the data channel
     * @since 0.1.0
     */
    @Override
    default void onConnect(DataChannel channel) {
        // No-op by default for UDP
    }

    /**
     * Default disconnect handler. No-op for datagram channels.
     *
     * @param channel the data channel
     * @since 0.1.0
     */
    @Override
    default void onDisconnect(DataChannel channel) {
        // No-op by default for UDP
    }

    /**
     * Default error handler. Subclasses should override for custom error handling.
     *
     * @param channel the data channel where the error occurred
     * @param cause   the error cause
     * @since 0.1.0
     */
    @Override
    default void onError(DataChannel channel, Throwable cause) {
        // No-op by default; override for error handling
    }
}
