package ssg.legoflow.ssh.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.ssh.transport.SshTransport;
import java.io.IOException;
import java.nio.ByteBuffer;
/**
 * Service-based SSH adapter that wraps the existing SSH protocol implementation
 * as a {@link ssg.legoflow.service.Service} for composition within the service framework.
 *
 * <p>This service manages the full SSH lifecycle: connection, key exchange,
 * authentication, and channel management. Data flows through the service
 * pipeline where inbound data from the {@link DataChannel} is processed by the
 * SSH transport layer and dispatched to appropriate channels.
 *
 * <p>Usage example with ServicesManager:
 * <pre>{@code
 * var ssh = new SshService.Builder("10.0.0.1", 22)
 *     .username("admin")
 *     .password("secret")
 *     .build();
 * services.register(ssh);
 * ssh.connect(ctx);
 * // Service now processes SSH packets through the pipeline
 * }</pre>
 *
 * @since 0.1.0
 */
public final class SshService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private final String username;
    private final byte[] password;
    
    private volatile SshTransport transport;
    
    /** Handler for data received from SSH channels. */
    private volatile java.util.function.Consumer<ByteBuffer> channelDataHandler;
    
    /** Callback for when an SSH session becomes ready. */
    private volatile java.util.function.Consumer<SshService> sessionReadyCallback;

    SshService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class, 
            new ServiceDescriptor(builder.name, "SSH Protocol Service", builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
        this.username = builder.username;
        this.password = builder.password;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            // Create the SSH transport layer using a socket
            var socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(host, port));
            
            this.transport = new SshTransport(socket, false);
            
            // Perform key exchange and authentication
            doHandshake();
            authenticate();
            
        } catch (Exception e) {
            throw new RuntimeException("SSH connection failed: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try {
            if (transport != null) {
                transport.close();
            }
        } catch (IOException e) {
            // Log but don't fail on disconnect cleanup
        } finally {
            transitionTo(ProcessorState.STOPPED);
        }
    }

    /**
     * Sets a handler for data received from SSH channels.
     * When channel data arrives through the service pipeline, this handler is invoked.
     *
     * @param handler the data handler
     */
    public void setChannelDataHandler(java.util.function.Consumer<ByteBuffer> handler) {
        this.channelDataHandler = handler;
    }

    /**
     * Sets a callback that is invoked when an SSH session becomes ready.
     *
     * @param callback the callback
     */
    public void onSessionReady(java.util.function.Consumer<SshService> callback) {
        this.sessionReadyCallback = callback;
    }

    /**
     * Returns the underlying transport (for advanced use).
     */
    public SshTransport getTransport() {
        return transport;
    }

    /**
     * Sends data through the SSH connection to a specific channel.
     *
     * @param channelNumber the SSH channel number
     * @param data the data to send
     * @throws IOException if an I/O error occurs
     */
    public void sendChannelData(int channelNumber, ByteBuffer data) throws IOException {
        ensureConnected();
        byte[] payload = new byte[data.remaining()];
        data.get(payload);
        transport.sendPacket(payload);
    }

    // ── DP/DF Pipeline Methods ──────────────────────────────

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        // Process inbound data through SSH transport
        for (ByteBuffer buf : input) {
            try {
                if (buf != null && buf.hasRemaining()) {
                    processInboundData(buf);
                }
            } catch (Exception e) {
                ctx.setAttribute("ssh.error", e);
            }
        }
        return new ByteBuffer[0];
    }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) {
        // Process outbound data through SSH transport  
        for (ByteBuffer buf : output) {
            try {
                if (buf != null && buf.hasRemaining()) {
                    processOutboundData(buf);
                }
            } catch (Exception e) {
                ctx.setAttribute("ssh.error", e);
            }
        }
        return new ByteBuffer[0];
    }

    private void processInboundData(ByteBuffer data) throws IOException {
        if (transport == null || !isConnected()) return;
        
        // Read SSH packets from transport and dispatch to channel handlers
        byte[] packet = transport.readPacket();
        if (packet != null && packet.length > 0) {
            handleSshPacket(packet);
        }
    }

    private void processOutboundData(ByteBuffer data) throws IOException {
        if (transport == null || !isConnected()) return;
        
        // Send data through SSH transport to remote
        byte[] payload = new byte[data.remaining()];
        data.get(payload);
        transport.sendPacket(payload);
    }

    private void handleSshPacket(byte[] packet) {
        if (channelDataHandler != null && packet != null && packet.length > 0) {
            channelDataHandler.accept(ByteBuffer.wrap(packet));
        }
    }

    // ── Internal SSH handshake/auth methods ────────────────

    private void doHandshake() throws IOException {
        if (transport == null) return;
        
        var remoteVersion = transport.exchangeVersions();
        
        var localKexInit = ssg.legoflow.ssh.kex.KexInit.defaultKexInit();
        transport.sendKexInit(localKexInit);
        
        byte[] remoteKexPayload = transport.readPacket();
        var remoteKexInit = ssg.legoflow.ssh.kex.KexInit.decode(remoteKexPayload);
        
        transport.negotiateAlgorithms(localKexInit, remoteKexInit);
        transport.sendNewKeys();
        transport.readPacket(); // NEWKEYS acknowledgment
    }

    private void authenticate() throws IOException {
        if (transport == null || username == null) return;
        
        transport.sendServiceRequest("ssh-userauth");
        transport.readPacket(); // service accept
        
        var authMethod = new ssg.legoflow.ssh.auth.PasswordAuth(
            password != null ? new String(password) : "");
        
        byte[] request = authMethod.encodeRequest(username, "ssh-connection");
        transport.sendPacket(request);
        
        byte[] response = transport.readPacket();
        if (response.length == 0) {
            throw new IOException("Empty authentication response from " + host);
        }
        
        int msgType = response[0] & 0xFF;
        if (msgType == 52) { // SSH_MSG_USERAUTH_SUCCESS
            transport.sendServiceRequest("ssh-connection");
            transport.readPacket(); // service accept
            
            if (sessionReadyCallback != null) {
                sessionReadyCallback.accept(this);
            }
        } else {
            throw new IOException("Authentication failed: type=" + msgType);
        }
    }

    private void ensureConnected() throws IOException {
        if (!isConnected()) {
            throw new IOException("SSH service not connected. Connect first.");
        }
    }

    // ── ChannelHandler Integration ────────────────────────

    /**
     * Creates a {@link ChannelHandler} that can be added to a service pipeline.
     * This handler routes data between the DataChannel and the SSH transport layer.
     *
     * @return the channel handler
     */
    public ChannelHandler createChannelHandler() {
        return new SshChannelHandler(this);
    }

    // ── Builder ───────────────────────────────────────────

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "ssh";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;
        private String username;
        private byte[] password;

        public Builder(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return this;
        }

        public Builder dependencies(String... deps) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, deps);
            this.dependencies = list;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public SshService build() {
            return new SshService(this);
        }
    }

    /**
     * Creates a builder for {@link SshService}.
     *
     * @param host the SSH server hostname
     * @param port the SSH server port (typically 22)
     * @return the builder
     */
    public static Builder builder(String host, int port) {
        return new Builder(host, port);
    }
}
