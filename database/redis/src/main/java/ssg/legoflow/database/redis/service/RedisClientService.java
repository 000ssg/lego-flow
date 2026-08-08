package ssg.legoflow.database.redis.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Redis client service implementation.
 *
 * Provides a service interface for Redis database operations over TCP connections.
 *
 * @since 0.1.0
 */
public final class RedisClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private final String password;
    private final Duration timeout;

    private volatile RedisClient client;
    private volatile Consumer<Object> commandCallback;

    RedisClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "Redis Client Service", builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
        this.password = builder.password;
        this.timeout = builder.timeout != null ? builder.timeout : Duration.ofSeconds(5);
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            client = new RedisClient(host, port);
            client.connect();
            if (password != null && !password.isEmpty()) {
                client.execute("AUTH", password);
            }
        } catch (IOException e) {
            throw new RuntimeException("Redis client connection failed: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (client != null) client.close(); } catch (Exception ignored) {}
        finally { transitionTo(ProcessorState.STOPPED); }
    }

    /**
     * Returns the underlying Redis client.
     *
     * @return the client, or null if not connected
     */
    public RedisClient getClient() { return client; }

    /**
     * Sets a callback for command results.
     *
     * @param cb the callback
     */
    public void setCommandCallback(Consumer<Object> cb) { this.commandCallback = cb; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try { if (buf != null && buf.hasRemaining()) processInboundData(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) {
        for (ByteBuffer buf : output) {
            try { if (buf != null && buf.hasRemaining()) processOutboundData(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    private void processInboundData(ByteBuffer data) {
        if (commandCallback != null && client != null && client.isConnected()) {
            try {
                Object response = client.receive();
                commandCallback.accept(response);
            } catch (Exception e) {
                commandCallback.accept(null);
            }
        }
    }

    private void processOutboundData(ByteBuffer data) {
        if (client != null && client.isConnected()) {
            try { client.sendRaw(new byte[data.remaining()]); }
            catch (IOException ignored) {}
        }
    }

    /**
     * Creates a channel handler for this service.
     *
     * @return a new RedisClientChannelHandler
     */
    public ChannelHandler createChannelHandler() { return new RedisClientChannelHandler(this); }

    // ---- Builder ----

    public static class Builder {
        private final String host;
        private final int port;
        private String password;
        private String name = "redis-client";
        private final List<String> dependencies = new ArrayList<>();
        private int priority = 100;
        private Duration timeout;

        public Builder(String h, int p) { this.host = h; this.port = p; }

        public Builder password(String pw) { this.password = pw; return this; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder timeout(Duration t) { this.timeout = t; return this; }
        public Builder dependencies(String... d) {
            for (String dep : d) { this.dependencies.add(dep); }
            return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public RedisClientService build() { return new RedisClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
