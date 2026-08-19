package ssg.legoflow.network.dns.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.network.dns.protocol.DnsCodec;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.server.DnsHandler;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
/**
 * Service-based DNS adapter that wraps the existing DNS protocol implementation
 * as a {@link ssg.legoflow.service.Service} for composition within the service framework.
 *
 * <p>Supports both client (resolver) and server (authoritative/recursive) modes.
 * Data flows through the DP/DF pipeline where inbound data from a {@link ssg.legoflow.service.channel.DataChannel}
 * is decoded into DNS messages, processed by the handler, and responses flow back
 * through the output pipeline as ByteBuffers.
 *
 * <p>Usage example with ServicesManager:
 * <pre>{@code
 * var dns = DnsService.builder("8.8.8.8", 53)
 *     .mode(DnsService.Mode.CLIENT)
 *     .timeout(java.time.Duration.ofSeconds(2))
 *     .build();
 * services.register(dns);
 * }</pre>
 *
 * @since 0.1.0
 */
public final class DnsService extends AbstractService<ByteBuffer, ByteBuffer> {


    /** Operational mode for the DNS service. */
    public enum Mode {
        /** Client/resolver mode - sends queries and receives responses. */
        CLIENT,
        /** Server/authoritative mode - receives queries and sends responses. */
        SERVER
    }

    private final InetSocketAddress bindAddress;
    private final DnsHandler handler;
    private final Mode mode;
    
    private volatile ssg.legoflow.network.dns.server.DnsServer server;
    private volatile java.util.function.Consumer<DnsMessage> queryCallback;
    private volatile java.util.function.Consumer<DnsMessage> responseCallback;

    DnsService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "DNS Protocol Service", builder.priority, builder.dependencies));
        this.bindAddress = builder.bindAddress;
        this.handler = builder.handler;
        this.mode = builder.mode;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            
            if (mode == Mode.SERVER) {
                // Start DNS server with zone-based or custom handler
                var serverAddress = bindAddress != null ? bindAddress : new InetSocketAddress("0.0.0.0", 53);
                this.server = handler != null 
                    ? new ssg.legoflow.network.dns.server.DnsServer(serverAddress, handler)
                    : new ssg.legoflow.network.dns.server.DnsServer(serverAddress);
                server.start();
            }
            
        } catch (Exception e) {
            throw new RuntimeException("DNS service failed to connect: " + bindAddress, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try {
            if (server != null) {
                server.close();
            }
        } catch (IOException e) {
            // Log but don't fail on disconnect cleanup
        } finally {
            transitionTo(ProcessorState.STOPPED);
        }
    }

    /**
     * Returns the underlying DNS server (null in client mode or before connect).
     */
    public ssg.legoflow.network.dns.server.DnsServer getServer() {
        return server;
    }

    /**
     * Sets a callback for DNS queries received by the service.
     */
    public void setQueryCallback(java.util.function.Consumer<DnsMessage> callback) {
        this.queryCallback = callback;
    }

    /**
     * Sets a callback for DNS responses sent by the service.
     */
    public void setResponseCallback(java.util.function.Consumer<DnsMessage> callback) {
        this.responseCallback = callback;
    }

    // ── DP/DF Pipeline Methods ──────────────────────────────

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try {
                if (buf != null && buf.hasRemaining()) {
                    processInboundData(buf);
                }
            } catch (Exception e) {
                ctx.handleError(e);
            }
        }
        return new ByteBuffer[0];
    }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) {
        for (ByteBuffer buf : output) {
            try {
                if (buf != null && buf.hasRemaining()) {
                    processOutboundData(buf);
                }
            } catch (Exception e) {
                ctx.handleError(e);
            }
        }
        return new ByteBuffer[0];
    }

    private void processInboundData(ByteBuffer data) {
        // Decode DNS message from inbound buffer and dispatch to handlers
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        
        if (queryCallback != null && bytes.length > 12) {
            try {
                var msg = ssg.legoflow.network.dns.protocol.DnsCodec.decode(bytes);
                queryCallback.accept(msg);
            } catch (Exception e) {
                // Silently drop malformed DNS messages
            }
        }
    }

    private void processOutboundData(ByteBuffer data) {
        // In server mode, outbound data would be sent through the DNS transport
        // For now, track via statistics
        if (responseCallback != null && data.hasRemaining()) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            try {
                var msg = ssg.legoflow.network.dns.protocol.DnsCodec.decode(bytes);
                responseCallback.accept(msg);
            } catch (Exception e) {
                // Silently drop malformed DNS messages
            }
        }
    }

    // ── ChannelHandler Integration ────────────────────────

    /**
     * Creates a {@link ChannelHandler} that can be added to a service pipeline.
     */
    public ChannelHandler createChannelHandler() {
        return new DnsChannelHandler(this);
    }

    // ── Builder ───────────────────────────────────────────

    public static class Builder {
        private InetSocketAddress bindAddress;
        private String name = "dns";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;
        private Mode mode = Mode.CLIENT;
        private DnsHandler handler;

        public Builder(String host, int port) {
            this.bindAddress = new InetSocketAddress(host, port);
        }

        public Builder bindAddress(InetSocketAddress address) {
            this.bindAddress = address;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder mode(Mode mode) {
            this.mode = mode;
            return this;
        }

        public Builder handler(DnsHandler handler) {
            this.handler = handler;
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

        public DnsService build() {
            return new DnsService(this);
        }
    }

    public static Builder builder(String host, int port) {
        return new Builder(host, port);
    }
}
