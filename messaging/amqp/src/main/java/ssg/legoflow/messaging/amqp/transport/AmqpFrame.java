package ssg.legoflow.messaging.amqp.transport;

import ssg.legoflow.messaging.amqp.types.AmqpType;

import java.nio.ByteBuffer;

/**
 * Represents an AMQP 1.0 frame.
 *
 * <p>An AMQP frame consists of an 8-byte header followed by an optional body:
 * <ul>
 *   <li>SIZE (4 bytes) — total frame size including header</li>
 *   <li>DOFF (1 byte) — data offset in 4-byte words (minimum 2)</li>
 *   <li>TYPE (1 byte) — frame type: 0x00 (AMQP) or 0x01 (SASL)</li>
 *   <li>CHANNEL (2 bytes) — channel number</li>
 *   <li>BODY — optional performative + payload</li>
 * </ul>
 *
 * @param channel     the channel number
 * @param type        the frame type (0x00 for AMQP, 0x01 for SASL)
 * @param performative the decoded performative body, or null for empty frames (heartbeats)
 * @param payload     optional payload (for transfer frames), or null
 * @since 1.0.0
 */
public record AmqpFrame(
        int channel,
        byte type,
        AmqpType performative,
        ByteBuffer payload
) {

    /**
     * Creates an AMQP frame with no payload.
     *
     * @param channel     the channel number
     * @param type        the frame type
     * @param performative the performative body
     */
    public AmqpFrame(int channel, byte type, AmqpType performative) {
        this(channel, type, performative, null);
    }

    /**
     * Creates an empty heartbeat frame.
     *
     * @return an empty AMQP frame on channel 0
     */
    public static AmqpFrame heartbeat() {
        return new AmqpFrame(0, (byte) 0x00, null, null);
    }

    /**
     * Returns whether this frame is a heartbeat (empty body).
     *
     * @return true if the frame has no performative and no payload
     */
    public boolean isHeartbeat() {
        return performative == null && payload == null;
    }
}
