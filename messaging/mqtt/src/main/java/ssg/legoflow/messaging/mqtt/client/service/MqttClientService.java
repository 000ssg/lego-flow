package ssg.legoflow.messaging.mqtt.client.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.channel.ChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
/** Service-based MQTT client adapter for DP/DF composition. */
public final class MqttClientService extends AbstractService<ByteBuffer, ByteBuffer> {

    private static final Logger LOG = LoggerFactory.getLogger(MqttClientService.class);

    private final String host;
    private final int port;
    private final String name;
    private volatile ssg.legoflow.messaging.mqtt.client.MqttClient client;
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
        this.name = builder.name;
    }

    @Override
    protected void doConnect(ServiceContext ctx) {
        try {
            transitionTo(ProcessorState.CONNECTING);
            // 1. Open non-blocking socket
            var socketChannel = java.nio.channels.SocketChannel.open();
            socketChannel.configureBlocking(false);
            var dataChannel = new ssg.legoflow.service.channel.TcpDataChannel(socketChannel);

            // 2. Create transport and handler
            var transport = new ssg.legoflow.messaging.mqtt.transport.MqttPipelineTransport(dataChannel);
            var handler = new MqttClientChannelHandler(this, transport);
            var latch = new java.util.concurrent.CountDownLatch(1);
            handler.setConnectLatch(latch);

            // 3. Register channel with manager + handler
            ctx.registerChannel(this, dataChannel, handler);

            // 4. Start async connect
            socketChannel.connect(new java.net.InetSocketAddress(host, port));

            // 5. Wait for TCP connect
            if (!latch.await(5000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("MQTT connection timeout: " + host + ":" + port);
            }

            // 6. Create client with transport and trigger MQTT connect
            var config = ssg.legoflow.messaging.mqtt.client.MqttClientConfig.defaults()
                    .clientId("mqtt-" + name).build();
            this.client = new ssg.legoflow.messaging.mqtt.client.MqttClient(config, transport);
            client.connect().join();

            LOG.info("MQTT client connected to {}:{}", host, port);
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

    public ssg.legoflow.messaging.mqtt.client.MqttClient getClient() { return client; }
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
