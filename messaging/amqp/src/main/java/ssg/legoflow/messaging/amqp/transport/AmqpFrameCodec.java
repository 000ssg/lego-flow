package ssg.legoflow.messaging.amqp.transport;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;

/**
 * Stateful codec that accumulates raw bytes and emits complete AMQP frames.
 *
 * <p>Stub interface for Phase 1 context wiring. Phase 2 implements the
 * full ChannelHandler accumulation logic.
 */
public interface AmqpFrameCodec extends ChannelHandler {

    /** Encode a frame to a ByteBuffer for outbound write. */
    ByteBuffer encode(Object frame);
}
