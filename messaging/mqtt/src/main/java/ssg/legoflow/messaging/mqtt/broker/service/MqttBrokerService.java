package ssg.legoflow.messaging.mqtt.broker.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.broker.MqttEventListener;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.ServerDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * MQTT broker service — listens through {@code SelectableChannelManager}.
 *
 * <p>Creates a {@link ServerSocketChannel}, wraps it in {@link ServerDataChannel},
 * registers it with the service manager via {@link ServiceContext#registerServerChannel}.
 * Accepted connections are bridged to {@link ssg.legoflow.messaging.mqtt.transport.MqttPipelineTransport}
 * via {@link MqttBrokerChannelHandler} and handed to {@link MqttBroker#handleConnection}.
 */
public final class MqttBrokerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private static final Logger LOG = LoggerFactory.getLogger(MqttBrokerService.class);

    private final int port;
    private final String host;
    private final MqttBrokerConfig config;

    private volatile MqttBroker broker;
    volatile ServerDataChannel serverChannel;

    MqttBrokerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "MQTT Broker Service",
                        builder.priority, builder.dependencies));
        this.port = builder.port;
        this.host = builder.host != null ? builder.host : "localhost";
        this.config = builder.config != null ? builder.config : MqttBrokerConfig.defaults();
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            // Create and bind server socket
            var serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(host, port));
            this.serverChannel = new ServerDataChannel(serverSocketChannel);

            // Register with service manager's selector
            ctx.registerServerChannel(this, serverChannel);

            // Add handler to the server pipeline so accepted connections are dispatched
            var pipeline = ctx.getChannelManager().getChannelPipeline(this);
            if (pipeline != null) {
                pipeline.addLast(createChannelHandler());
            }

            // Create and start broker
            this.broker = new MqttBroker(config);
            broker.start();
            LOG.info("MQTT broker listening on port {}",
                    serverChannel.getServerSocketChannel().socket().getLocalPort());
        } catch (Exception e) {
            throw new RuntimeException("MQTT broker failed to start on " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        try { if (broker != null) broker.close(); } catch (Exception ignored) {}
        try {
            var mgr = ctx.getChannelManager();
            if (mgr != null) mgr.unregisterServerChannel(this);
        } catch (Exception ignored) {}
        try { if (serverChannel != null) serverChannel.close(); } catch (Exception ignored) {}
    }

    /** Returns the underlying broker (after connect). */
    public MqttBroker getBroker() { return broker; }

    /** Returns the port the service is listening on. */
    public int port() {
        return serverChannel != null ? serverChannel.getServerSocketChannel().socket().getLocalPort() : -1;
    }

    /** Sets the protocol event listener. */
    public void setListener(MqttEventListener listener) {
        if (broker != null) broker.setListener(listener);
    }

    public ChannelHandler createChannelHandler() {
        return new MqttBrokerChannelHandler(this);
    }

    /** Returns this service for handler lookup. */
    public MqttBrokerService getService() { return this; }

    /** Returns the broker config (for handler TLS setup). */
    public MqttBrokerConfig getConfig() { return config; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) { return new ByteBuffer[0]; }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) { return new ByteBuffer[0]; }

    public static class Builder {
        private String name = "mqtt-broker";
        private final List<String> dependencies = new ArrayList<>();
        private int priority = 100;
        private int port = 1883;
        private String host;
        private MqttBrokerConfig config;

        public Builder port(int p) { this.port = p; return this; }
        public Builder host(String h) { this.host = h; return this; }
        public Builder config(MqttBrokerConfig c) { this.config = c; return this; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { for (String dep : d) dependencies.add(dep); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public MqttBrokerService build() { return new MqttBrokerService(this); }
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(String host, int port) { return new Builder().host(host).port(port); }
}
