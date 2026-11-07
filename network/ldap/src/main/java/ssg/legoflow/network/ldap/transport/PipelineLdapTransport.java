package ssg.legoflow.network.ldap.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * Pipeline transport for the LDAP protocol.
 *
 * @since 0.3.0
 */
public final class PipelineLdapTransport extends PipelineByteTransport implements LdapTransport {

    public PipelineLdapTransport(DataChannel channel) {
        super(channel);
    }
}