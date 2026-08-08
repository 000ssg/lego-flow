package ssg.legoflow.service.channel;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Immutable record holding metadata for a received UDP datagram.
 *
 * <p>Contains the sender address, a read-only view of the payload data,
 * and a nanosecond-precision receive timestamp.
 *
 * @param sender    the socket address of the datagram sender
 * @param data      a read-only {@link ByteBuffer} containing the datagram payload
 * @param timestamp the receive timestamp in nanoseconds (from {@link System#nanoTime()})
 * @since 0.1.0
 */
public record DatagramPacketInfo(SocketAddress sender, ByteBuffer data, long timestamp) {

    /**
     * Creates a new {@code DatagramPacketInfo} with validation.
     *
     * @param sender    the socket address of the datagram sender; must not be {@code null}
     * @param data      the datagram payload; must not be {@code null}
     * @param timestamp the receive timestamp in nanoseconds
     * @throws NullPointerException if {@code sender} or {@code data} is {@code null}
     */
    public DatagramPacketInfo {
        Objects.requireNonNull(sender, "sender must not be null");
        Objects.requireNonNull(data, "data must not be null");
        data = data.asReadOnlyBuffer();
    }

    /**
     * Returns the size of the datagram payload in bytes.
     *
     * @return the number of remaining bytes in the data buffer
     */
    public int size() {
        return data.remaining();
    }

    /**
     * Returns a copy of the datagram payload as a byte array.
     *
     * @return a new byte array containing the payload data
     */
    public byte[] toByteArray() {
        var buf = data.duplicate();
        var bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return bytes;
    }
}
