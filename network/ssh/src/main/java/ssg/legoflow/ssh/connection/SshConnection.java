package ssg.legoflow.ssh.connection;

import ssg.legoflow.ssh.agent.AgentForwardingChannel;
import ssg.legoflow.ssh.agent.SshAgent;
import ssg.legoflow.ssh.transport.SshTransport;
import ssg.legoflow.ssh.transport.SshTransportCodec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * SSH connection layer managing multiplexed channels per RFC 4254.
 *
 * @since 1.0.0
 */
public final class SshConnection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SshConnection.class);

    private final SshTransport transport;
    private final Map<Integer, SshChannel> channels = new ConcurrentHashMap<>();
    private final AtomicInteger nextChannelId = new AtomicInteger(0);
    private SshAgent agent;
    private Consumer<X11ForwardingChannel> x11ChannelHandler;

    /**
     * Creates a new SSH connection layer.
     *
     * @param transport the transport layer
     */
    public SshConnection(SshTransport transport) {
        this.transport = transport;
    }

    /**
     * Sets the SSH agent for agent forwarding.
     *
     * @param agent the SSH agent
     */
    public void setAgent(SshAgent agent) {
        this.agent = agent;
    }

    /**
     * Returns the SSH agent.
     *
     * @return the SSH agent, or null if not set
     */
    public SshAgent agent() {
        return agent;
    }

    /**
     * Sets a handler for incoming X11 forwarding channels.
     *
     * @param handler the X11 channel handler
     */
    public void setX11ChannelHandler(Consumer<X11ForwardingChannel> handler) {
        this.x11ChannelHandler = handler;
    }

    /**
     * Opens a new session channel.
     *
     * @return the opened session channel
     * @throws IOException if an I/O error occurs
     */
    public SessionChannel openSession() throws IOException {
        int id = nextChannelId.getAndIncrement();
        SessionChannel channel = new SessionChannel(id, transport);
        channels.put(id, channel);
        sendChannelOpen(channel);
        // Wait for channel open confirmation from server
        try {
            if (!channel.waitForOpen(10000)) {
                throw new IOException("Timeout waiting for channel open confirmation");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for channel open", e);
        }
        return channel;
    }

    /**
     * Opens a direct-tcpip channel for local port forwarding.
     *
     * @param targetHost        the target host
     * @param targetPort        the target port
     * @param originatorAddress the originator address
     * @param originatorPort    the originator port
     * @return the opened channel
     * @throws IOException if an I/O error occurs
     */
    public DirectTcpIpChannel openDirectTcpIp(String targetHost, int targetPort,
                                                String originatorAddress, int originatorPort)
            throws IOException {
        int id = nextChannelId.getAndIncrement();
        DirectTcpIpChannel channel = new DirectTcpIpChannel(id, transport,
                targetHost, targetPort, originatorAddress, originatorPort);
        channels.put(id, channel);

        ByteBuffer data = ByteBuffer.allocate(256);
        SshTransportCodec.writeString(data, targetHost);
        data.putInt(targetPort);
        SshTransportCodec.writeString(data, originatorAddress);
        data.putInt(originatorPort);
        data.flip();
        byte[] extraData = new byte[data.remaining()];
        data.get(extraData);

        sendChannelOpen(channel, extraData);
        // Wait for channel open confirmation from server
        try {
            if (!channel.waitForOpen(10000)) {
                throw new IOException("Timeout waiting for direct-tcpip channel open confirmation");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for channel open", e);
        }
        return channel;
    }

    /**
     * Returns a channel by its local ID.
     *
     * @param localId the local channel ID
     * @return the channel, or null if not found
     */
    public SshChannel channel(int localId) {
        return channels.get(localId);
    }

    /**
     * Handles an incoming packet for the connection layer.
     *
     * @param payload the packet payload
     * @throws IOException if an I/O error occurs
     */
    public void handlePacket(byte[] payload) throws IOException {
        if (payload.length == 0) return;
        int msgType = payload[0] & 0xFF;
        ByteBuffer buf = ByteBuffer.wrap(payload);
        buf.get(); // skip message type

        switch (msgType) {
            case 90 -> handleChannelOpen(buf);
            case 91 -> handleChannelOpenConfirmation(buf);
            case 92 -> handleChannelOpenFailure(buf);
            case 93 -> handleWindowAdjust(buf);
            case 94 -> handleChannelData(buf);
            case 95 -> handleExtendedData(buf);
            case 96 -> handleChannelEof(buf);
            case 97 -> handleChannelClose(buf);
            case 98 -> handleChannelRequest(buf);
            case 99 -> handleChannelSuccess(buf);
            case 100 -> handleChannelFailure(buf);
            default -> LOG.warn("Unhandled connection message type: {}", msgType);
        }
    }

    /**
     * Returns the number of open channels.
     *
     * @return the channel count
     */
    public int channelCount() {
        return channels.size();
    }

    @Override
    public void close() throws IOException {
        for (SshChannel channel : channels.values()) {
            try { channel.close(); } catch (IOException ignored) {}
        }
        channels.clear();
    }

    // --- Private handlers ---

    private void handleChannelOpen(ByteBuffer buf) throws IOException {
        String channelType = SshTransportCodec.readString(buf);
        int senderChannel = buf.getInt();
        long initialWindow = SshTransportCodec.readUint32(buf);
        long maxPacket = SshTransportCodec.readUint32(buf);

        SshChannel channel = null;

        if (AgentForwardingChannel.CHANNEL_TYPE.equals(channelType) && agent != null) {
            int localId = nextChannelId.getAndIncrement();
            AgentForwardingChannel agentChannel = new AgentForwardingChannel(localId, transport, agent);
            agentChannel.setRemoteId(senderChannel);
            agentChannel.windowManager().setRemoteWindow(initialWindow);
            agentChannel.setOpen();
            channels.put(localId, agentChannel);
            channel = agentChannel;
            LOG.debug("Opened agent forwarding channel: local={}, remote={}", localId, senderChannel);
        } else if ("x11".equals(channelType)) {
            String originatorAddress = SshTransportCodec.readString(buf);
            int originatorPort = buf.getInt();
            int localId = nextChannelId.getAndIncrement();
            X11ForwardingChannel x11Channel = new X11ForwardingChannel(
                    localId, transport, originatorAddress, originatorPort);
            x11Channel.setRemoteId(senderChannel);
            x11Channel.windowManager().setRemoteWindow(initialWindow);
            x11Channel.setOpen();
            channels.put(localId, x11Channel);
            channel = x11Channel;
            if (x11ChannelHandler != null) {
                x11ChannelHandler.accept(x11Channel);
            }
            LOG.debug("Opened X11 forwarding channel: local={}, remote={}, originator={}:{}",
                    localId, senderChannel, originatorAddress, originatorPort);
        } else {
            LOG.warn("Rejecting unsupported channel open: {}", channelType);
            // Send channel open failure
            ByteBuffer reply = ByteBuffer.allocate(256);
            reply.put((byte) 92); // SSH_MSG_CHANNEL_OPEN_FAILURE
            reply.putInt(senderChannel);
            reply.putInt(1); // SSH_OPEN_ADMINISTRATIVELY_PROHIBITED
            SshTransportCodec.writeString(reply, "Channel type not supported");
            SshTransportCodec.writeString(reply, "en");
            reply.flip();
            byte[] payload = new byte[reply.remaining()];
            reply.get(payload);
            transport.sendPacket(payload);
            return;
        }

        // Send channel open confirmation
        ByteBuffer reply = ByteBuffer.allocate(256);
        reply.put((byte) 91); // SSH_MSG_CHANNEL_OPEN_CONFIRMATION
        reply.putInt(senderChannel);
        reply.putInt(channel.localId());
        reply.putInt((int) channel.windowManager().initialWindowSize());
        reply.putInt((int) channel.windowManager().maxPacketSize());
        reply.flip();
        byte[] payload = new byte[reply.remaining()];
        reply.get(payload);
        transport.sendPacket(payload);
    }

    private void sendChannelOpen(SshChannel channel) throws IOException {
        sendChannelOpen(channel, new byte[0]);
    }

    private void sendChannelOpen(SshChannel channel, byte[] extraData) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(256 + extraData.length);
        buf.put((byte) 90); // SSH_MSG_CHANNEL_OPEN
        SshTransportCodec.writeString(buf, channel.channelType());
        buf.putInt(channel.localId());
        buf.putInt((int) channel.windowManager().initialWindowSize());
        buf.putInt((int) channel.windowManager().maxPacketSize());
        buf.put(extraData);
        buf.flip();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        transport.sendPacket(payload);
        LOG.debug("Sent channel open: {} id={}", channel.channelType(), channel.localId());
    }

    private void handleChannelOpenConfirmation(ByteBuffer buf) {
        int recipientChannel = buf.getInt();
        int senderChannel = buf.getInt();
        long initialWindow = SshTransportCodec.readUint32(buf);
        long maxPacket = SshTransportCodec.readUint32(buf);

        SshChannel channel = channels.get(recipientChannel);
        if (channel != null) {
            channel.setRemoteId(senderChannel);
            channel.windowManager().setRemoteWindow(initialWindow);
            channel.setOpen();
            LOG.debug("Channel {} confirmed, remote id={}", recipientChannel, senderChannel);
        }
    }

    private void handleChannelOpenFailure(ByteBuffer buf) {
        int recipientChannel = buf.getInt();
        int reasonCode = buf.getInt();
        String description = SshTransportCodec.readString(buf);
        LOG.warn("Channel {} open failed: {} ({})", recipientChannel, description, reasonCode);
        channels.remove(recipientChannel);
    }

    private void handleWindowAdjust(ByteBuffer buf) {
        int recipientChannel = buf.getInt();
        long bytesToAdd = SshTransportCodec.readUint32(buf);
        SshChannel channel = channels.get(recipientChannel);
        if (channel != null) {
            channel.windowManager().adjustRemoteWindow(bytesToAdd);
        }
    }

    private void handleChannelData(ByteBuffer buf) {
        int recipientChannel = buf.getInt();
        byte[] data = SshTransportCodec.readBinary(buf);
        SshChannel channel = channels.get(recipientChannel);
        if (channel != null) {
            channel.onData(data);
        }
    }

    private void handleExtendedData(ByteBuffer buf) {
        int recipientChannel = buf.getInt();
        int dataTypeCode = buf.getInt();
        byte[] data = SshTransportCodec.readBinary(buf);
        SshChannel channel = channels.get(recipientChannel);
        if (channel != null) {
            channel.onExtendedData(dataTypeCode, data);
        }
    }

    private void handleChannelEof(ByteBuffer buf) {
        int recipientChannel = buf.getInt();
        SshChannel channel = channels.get(recipientChannel);
        if (channel != null) {
            channel.onEof();
        }
    }

    private void handleChannelClose(ByteBuffer buf) {
        int recipientChannel = buf.getInt();
        SshChannel channel = channels.remove(recipientChannel);
        if (channel != null) {
            try { channel.close(); } catch (IOException ignored) {}
        }
    }

    private void handleChannelRequest(ByteBuffer buf) {
        int recipientChannel = buf.getInt();
        String requestType = SshTransportCodec.readString(buf);
        boolean wantReply = SshTransportCodec.readBoolean(buf);
        SshChannel channel = channels.get(recipientChannel);

        if (channel != null && "exit-status".equals(requestType)) {
            int status = buf.getInt();
            channel.setExitStatus(status);
        }

        // Send success if reply wanted
        if (wantReply && channel != null) {
            try {
                ByteBuffer reply = ByteBuffer.allocate(5);
                reply.put((byte) 99); // SSH_MSG_CHANNEL_SUCCESS
                reply.putInt(channel.remoteId());
                transport.sendPacket(reply.array());
            } catch (IOException e) {
                LOG.warn("Failed to send channel success", e);
            }
        }
    }

    private void handleChannelSuccess(ByteBuffer buf) {
        int recipientChannel = buf.getInt();
        SshChannel channel = channels.get(recipientChannel);
        if (channel != null) {
            channel.onRequestSuccess();
        }
        LOG.debug("Channel {} request succeeded", recipientChannel);
    }

    private void handleChannelFailure(ByteBuffer buf) {
        int recipientChannel = buf.getInt();
        SshChannel channel = channels.get(recipientChannel);
        if (channel != null) {
            channel.onRequestFailure();
        }
        LOG.debug("Channel {} request failed", recipientChannel);
    }
}
