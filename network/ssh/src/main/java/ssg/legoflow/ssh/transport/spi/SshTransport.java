package ssg.legoflow.ssh.transport.spi;

import ssg.legoflow.service.channel.ByteTransport;

/**
 * Byte-level transport SPI for the SSH protocol.
 *
 * <p>Extends {@link ByteTransport} with SSH-specific methods for binary packet
 * encoding/decoding support.
 *
 * @since 0.3.0
 */
public interface SshTransport extends ByteTransport {
}