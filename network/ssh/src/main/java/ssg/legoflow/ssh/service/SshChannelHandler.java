package ssg.legoflow.ssh.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Channel handler that bridges the service framework's {@link DataChannel} with
 * the SSH protocol layer ({@link SshService}).
 *
 * <p>This handler is added to a {@link ssg.legoflow.service.channel.ChannelPipeline}
 * and routes:
 * <ul>
 *   <li>Read events: Data from the channel is forwarded to the SshService for processing</li>
 *   <li>Write events: Outbound data is sent through the SSH transport</li>
 *   <li>Connect/Disconnect: Lifecycle management of the SSH connection</li>
 * </ul>
 *
 * <p>Usage within a pipeline:
 * <pre>{@code
 * SshService ssh = SshService.builder("server", 22)
 *     .username("admin")
 *     .password("secret")
 *     .build();
 *
 * ChannelHandler handler = ssh.createChannelHandler();
 * pipeline.addLast(handler);
 * }</pre>
 *
 * @since 0.1.0
 */
public final class SshChannelHandler implements ChannelHandler {

    private static final int DEFAULT_BUFFER_SIZE = 8192;
    
    private final SshService sshService;
    private volatile ByteBuffer inboundBuffer;
    private volatile boolean initialized;

    /**
     * Creates a new handler for the given SSH service.
     *
     * @param sshService the SSH service to route data through
     */
    public SshChannelHandler(SshService sshService) {
        this.sshService = sshService;
        this.inboundBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        
        try {
            // Feed inbound data to the SSH service
            sshService.consume(sshService.getServiceContext(), data);
        } catch (Exception e) {
            onError(channel, e);
        }
    }

    @Override
    public void onWrite(DataChannel channel) {
        if (!initialized) return;
        
        try {
            // The SSH service will handle sending through its transport
            // This callback signals that the channel is writable
        } catch (Exception e) {
            onError(channel, e);
        }
    }

    @Override
    public void onConnect(DataChannel channel) {
        if (!initialized) {
            try {
                // The SSH service connection is managed externally
                // This just marks that the underlying channel is connected
                initialized = true;
            } catch (Exception e) {
                onError(channel, e);
            }
        }
    }

    @Override
    public void onDisconnect(DataChannel channel) {
        try {
            if (sshService.isConnected()) {
                sshService.disconnect(sshService.getServiceContext());
            }
            initialized = false;
            
            // Clear buffers
            inboundBuffer.clear();
        } catch (Exception e) {
            onError(channel, e);
        }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        // Log the error through the SSH service context if available
        var ctx = sshService.getServiceContext();
        if (ctx != null) {
            ctx.setAttribute("ssh.error", cause);
        }
    }

    /**
     * Returns the associated SSH service.
     */
    public SshService getSshService() {
        return sshService;
    }

    /**
     * Sends data through the SSH connection via this handler.
     *
     * @param channel the data channel
     * @param data the data to send
     */
    public void sendData(DataChannel channel, ByteBuffer data) {
        if (sshService.isConnected()) {
            sshService.submit(sshService.getServiceContext(), data);
        }
    }
}
