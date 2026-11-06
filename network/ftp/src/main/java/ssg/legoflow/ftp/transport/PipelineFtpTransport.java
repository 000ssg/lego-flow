package ssg.legoflow.network.ftp.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * Pipeline transport for the FTP protocol.
 *
 * @since 0.3.0
 */
public final class PipelineFtpTransport extends PipelineByteTransport implements FtpTransport {

    public PipelineFtpTransport(DataChannel channel) {
        super(channel);
    }
}