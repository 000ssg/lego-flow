package ssg.legoflow.ssh.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * Pipeline transport for the SSH protocol.
 *
 * @since 0.3.0
 */
public final class PipelineSshTransport extends PipelineByteTransport implements ssg.legoflow.ssh.transport.spi.SshTransport {

    public PipelineSshTransport(DataChannel channel) {
        super(channel);
    }
}