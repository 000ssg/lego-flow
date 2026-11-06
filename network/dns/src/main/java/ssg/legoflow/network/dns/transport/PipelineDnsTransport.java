package ssg.legoflow.network.dns.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.PipelineByteTransport;

/**
 * Pipeline transport for DNS over TCP/TLS.
 *
 * @since 0.3.0
 */
public final class PipelineDnsTransport extends PipelineByteTransport implements DnsTransport {

    public PipelineDnsTransport(DataChannel channel) {
        super(channel);
    }
}