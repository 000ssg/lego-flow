package ssg.legoflow.email.smtp.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * Pipeline transport for the SMTP protocol.
 *
 * @since 0.3.0
 */
public final class PipelineSmtpTransport extends PipelineByteTransport implements SmtpTransport {

    public PipelineSmtpTransport(DataChannel channel) {
        super(channel);
    }
}