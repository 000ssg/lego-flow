package ssg.legoflow.media.rtp.transport;

import ssg.legoflow.service.channel.ByteTransport;

/**
 * Byte-level transport SPI for the RTP protocol.
 *
 * <p>Extends {@link ByteTransport} with RTP-specific semantics.
 * Note: RTP is UDP-based, so this SPI is primarily for testing.
 *
 * @since 0.3.0
 */
public interface RtpByteTransport extends ByteTransport {
}