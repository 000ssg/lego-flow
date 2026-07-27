package ssg.legoflow.media.rtsp.interleaved;

import java.util.Objects;

/**
 * An interleaved binary data frame embedded in the RTSP TCP stream.
 *
 * <p>Format: {@code $ <channel:1> <length:2> <data:length>}
 *
 * <p>Used for RTP-over-RTSP-TCP transport, where RTP and RTCP packets are
 * multiplexed on the same TCP connection as RTSP messages.
 *
 * @param channel the channel number (0-255)
 * @param data    the frame payload (RTP or RTCP packet)
 * @since 1.0.0
 */
public record InterleavedFrame(int channel, byte[] data) {

    /** The magic byte that marks the start of an interleaved frame. */
    public static final byte MAGIC = '$';

    /** Minimum frame size: magic (1) + channel (1) + length (2). */
    public static final int HEADER_SIZE = 4;

    /**
     * Creates an interleaved frame with validation.
     */
    public InterleavedFrame {
        if (channel < 0 || channel > 255) {
            throw new IllegalArgumentException("Channel must be 0-255: " + channel);
        }
        Objects.requireNonNull(data, "data");
        if (data.length > 65535) {
            throw new IllegalArgumentException("Data length must be <= 65535: " + data.length);
        }
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    /**
     * Returns the total frame size including the 4-byte header.
     *
     * @return the total frame size
     */
    public int frameSize() {
        return HEADER_SIZE + data.length;
    }

    @Override
    public String toString() {
        return "InterleavedFrame[channel=" + channel + ", length=" + data.length + "]";
    }
}
