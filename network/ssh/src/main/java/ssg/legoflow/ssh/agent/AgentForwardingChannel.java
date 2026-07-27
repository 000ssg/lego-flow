package ssg.legoflow.ssh.agent;

import ssg.legoflow.ssh.connection.SshChannel;
import ssg.legoflow.ssh.transport.SshTransport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Agent forwarding channel for handling SSH agent protocol over an SSH channel.
 *
 * <p>This channel type ({@code "auth-agent@openssh.com"}) is opened by the server when
 * agent forwarding has been requested. It receives agent protocol messages, dispatches
 * them to the local {@link SshAgent}, and sends responses back.
 *
 * @since 1.0.0
 */
public final class AgentForwardingChannel extends SshChannel {

    private static final Logger LOG = LoggerFactory.getLogger(AgentForwardingChannel.class);

    /** Channel type for agent forwarding. */
    public static final String CHANNEL_TYPE = "auth-agent@openssh.com";

    private final SshAgent agent;
    private ByteBuffer pendingData = ByteBuffer.allocate(0);

    /**
     * Creates a new agent forwarding channel.
     *
     * @param localId   the local channel ID
     * @param transport the transport layer
     * @param agent     the SSH agent to dispatch requests to
     */
    public AgentForwardingChannel(int localId, SshTransport transport, SshAgent agent) {
        super(localId, transport);
        this.agent = agent;
    }

    @Override
    public String channelType() { return CHANNEL_TYPE; }

    /**
     * Returns the SSH agent backing this channel.
     *
     * @return the SSH agent
     */
    public SshAgent agent() { return agent; }

    /**
     * Processes incoming data as agent protocol messages.
     *
     * <p>Agent messages have a 4-byte length prefix. This method accumulates data
     * until a complete message is available, processes it, and sends the response.
     *
     * @param data the incoming data
     */
    @Override
    public void onData(byte[] data) {
        super.onData(data);

        // Accumulate data for processing
        ByteBuffer combined = ByteBuffer.allocate(pendingData.remaining() + data.length);
        combined.put(pendingData);
        combined.put(data);
        combined.flip();

        while (combined.remaining() >= 4) {
            combined.mark();
            int msgLen = combined.getInt();
            if (combined.remaining() < msgLen) {
                combined.reset();
                break;
            }

            // Extract complete message (length prefix + payload)
            combined.reset();
            byte[] msgBytes = new byte[4 + msgLen];
            combined.get(msgBytes);

            try {
                SshAgentMessage request = SshAgentCodec.decode(msgBytes);
                SshAgentMessage response = agent.processMessage(request);
                byte[] responseBytes = SshAgentCodec.encode(response);
                sendData(responseBytes);
            } catch (Exception e) {
                LOG.warn("Failed to process agent message", e);
                try {
                    sendData(SshAgentCodec.encode(new SshAgentMessage.Failure()));
                } catch (IOException ex) {
                    LOG.warn("Failed to send agent failure response", ex);
                }
            }
        }

        // Save remaining incomplete data
        if (combined.hasRemaining()) {
            byte[] remaining = new byte[combined.remaining()];
            combined.get(remaining);
            pendingData = ByteBuffer.wrap(remaining);
        } else {
            pendingData = ByteBuffer.allocate(0);
        }
    }
}
