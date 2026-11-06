package ssg.legoflow.email.imap.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * Pipeline transport for the IMAP protocol.
 *
 * @since 0.3.0
 */
public final class PipelineImapTransport extends PipelineByteTransport implements ImapTransport {

    public PipelineImapTransport(DataChannel channel) {
        super(channel);
    }
}