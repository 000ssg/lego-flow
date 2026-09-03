package ssg.legoflow.messaging.mqtt.broker.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
/**
 * Service-based MQTT broker adapter for composition within the service framework.
 */
public final class MqttBrokerService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final InetSocketAddress bindAddress;
    private volatile ssg.legoflow.messaging.mqtt.broker.MqttBroker broker;
    private volatile java.util.function.Consumer<ByteBuffer> messageCallback;

    MqttBrokerService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
            new ServiceDescriptor(builder.name, "MQTT Broker Service", builder.priority, builder.dependencies));
        this.bindAddress = builder.bindAddress;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            var config = ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig.defaults();
            if (bindAddress != null && !bindAddress.getHostName().equals(config.host())) {
                // Use the configured host/port via builder pattern on defaults
            }
            this.broker = new ssg.legoflow.messaging.mqtt.broker.MqttBroker(config);
            broker.start();
        } catch (Exception e) {
            throw new RuntimeException("MQTT broker service failed to connect: " + bindAddress, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (broker != null) broker.close();
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.messaging.mqtt.broker.MqttBroker getBroker() { return broker; }
    public void setMessageCallback(java.util.function.Consumer<ByteBuffer> cb) { this.messageCallback = cb; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try { if (buf != null && buf.hasRemaining()) processInbound(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    @Override
    protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) {
        for (ByteBuffer buf : output) {
            try { if (buf != null && buf.hasRemaining()) processOutbound(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    private void processInbound(ByteBuffer data) {
        if (messageCallback != null) messageCallback.accept(data.asReadOnlyBuffer());
    }
    private void processOutbound(ByteBuffer data) {}

    public ChannelHandler createChannelHandler() { return new MqttBrokerChannelHandler(this); }

    public static class Builder {
        private InetSocketAddress bindAddress;
        private String name = "mqtt-broker";
        private java.util.List<String> dependencies = new java.util.ArrayList<>();
        private int priority = 100;

        public Builder(String host, int port) { this.bindAddress = new InetSocketAddress(host, port); }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) {
            java.util.List<String> list = new java.util.ArrayList<>(this.dependencies);
            java.util.Collections.addAll(list, d); this.dependencies = list; return this;
        }
        public Builder priority(int p) { this.priority = p; return this; }
        public MqttBrokerService build() { return new MqttBrokerService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
