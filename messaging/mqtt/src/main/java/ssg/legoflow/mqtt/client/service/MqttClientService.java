package ssg.legoflow.mqtt.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
/** Service-based MQTT client adapter for DP/DF composition. */
public final class MqttClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private final String host;
    private final int port;
    private volatile ssg.legoflow.mqtt.client.MqttClient client;
    private volatile Consumer<MqttResult> messageCallback;

    public record MqttResult(boolean success, String topic, ByteBuffer payload) {
        public static MqttResult ok(String topic, ByteBuffer data) { return new MqttResult(true, topic, data); }
        public static MqttResult error(String msg) { return new MqttResult(false, null, null); }
    }

    MqttClientService(Builder builder) {
        super(ByteBuffer.class, ByteBuffer.class,
                new ServiceDescriptor(builder.name, "MQTT Client Service",
                        builder.priority, builder.dependencies));
        this.host = builder.host;
        this.port = builder.port;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            var config = ssg.legoflow.mqtt.client.MqttClientConfig.defaults()
                    .host(host).port(port).build();
            this.client = new ssg.legoflow.mqtt.client.MqttClient(config);
            client.connect().join();
        } catch (Exception e) {
            throw new RuntimeException("MQTT client failed to connect: " + host + ":" + port, e);
        }
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        if (client != null) {
            try { client.disconnect(); } catch (Exception ignored) {}
            try { client.close(); } catch (Exception ignored) {}
        }
        transitionTo(ProcessorState.STOPPED);
    }

    public ssg.legoflow.mqtt.client.MqttClient getClient() { return client; }
    public void setMessageCallback(Consumer<MqttResult> cb) { this.messageCallback = cb; }

    @Override
    protected ByteBuffer[] convertToOutput(Context ctx, ByteBuffer... input) {
        for (ByteBuffer buf : input) {
            try { if (buf != null && buf.hasRemaining()) processInbound(buf); }
            catch (Exception e) { ctx.handleError(e); }
        }
        return new ByteBuffer[0];
    }

    @Override protected ByteBuffer[] convertToInput(Context ctx, ByteBuffer... output) { return new ByteBuffer[0]; }

    private void processInbound(ByteBuffer data) {
        if (messageCallback != null) messageCallback.accept(MqttResult.ok("mqtt", data.asReadOnlyBuffer()));
    }

    public ChannelHandler createChannelHandler() { return new MqttClientChannelHandler(this); }

    public static class Builder {
        private final String host;
        private final int port;
        private String name = "mqtt-client";
        private List<String> dependencies = List.of();
        private int priority = 100;

        public Builder(String host, int port) { this.host = host; this.port = port; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder dependencies(String... d) { this.dependencies = List.of(d); return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public MqttClientService build() { return new MqttClientService(this); }
    }

    public static Builder builder(String host, int port) { return new Builder(host, port); }
}
