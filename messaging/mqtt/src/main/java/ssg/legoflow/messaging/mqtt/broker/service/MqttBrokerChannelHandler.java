package ssg.legoflow.messaging.mqtt.broker.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.TcpDataChannel;
import ssg.legoflow.messaging.mqtt.transport.MqttPipelineTransport;
import ssg.legoflow.messaging.mqtt.transport.MqttTransport;
import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttTlsConfig;
import ssg.legoflow.messaging.mqtt.transport.MqttTlsTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Channel handler for MQTT broker service. Bridges pipeline events
 * to per-connection transports and hands them to the broker.
 *
 * <p>Pattern mirrors {@code AmqpContainerChannelHandler}: one transport
 * per DataChannel, mapped via {@link ConcurrentHashMap}. If TLS is configured,
 * wraps the pipeline transport with {@link MqttTlsTransport} before handing to broker.</p>
 */
public final class MqttBrokerChannelHandler implements ChannelHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MqttBrokerChannelHandler.class);

    private final MqttBrokerService service;
    /** Maps DataChannel → outer transport (pipeline, or TLS wrapping pipeline). */
    private final ConcurrentHashMap<DataChannel, MqttTransport> transportByChannel = new ConcurrentHashMap<>();
    /** Maps DataChannel → inner pipeline transport (for routing pipeline write events). */
    private final ConcurrentHashMap<DataChannel, MqttPipelineTransport> pipelineByChannel = new ConcurrentHashMap<>();

    public MqttBrokerChannelHandler(MqttBrokerService service) {
        this.service = service;
    }

    /** Returns the service this handler belongs to. */
    public MqttBrokerService getService() { return service; }

    @Override
    public void onConnect(DataChannel channel) {
        LOG.debug("Client channel accepted by broker");
        if (channel instanceof TcpDataChannel) {
            var pipeline = new MqttPipelineTransport(channel);
            MqttTransport transport = pipeline;

            // Wrap with TLS if configured
            MqttBroker broker = service.getBroker();
            if (broker != null && service.getConfig() != null && service.getConfig().tlsConfig() != null) {
                try {
                    var sslContext = service.getConfig().tlsConfig().createSslContext();
                    var engine = service.getConfig().tlsConfig().createServerEngine(sslContext);
                    transport = new MqttTlsTransport(pipeline, engine);
                } catch (Exception e) {
                    LOG.error("Failed to initialize TLS for connection", e);
                    transport.close();
                    try { channel.close(); } catch (Exception ignored) {}
                    return;
                }
            }

            // Store both: outer transport for reads (TLS unwraps), pipeline for writes
            transportByChannel.put(channel, transport);
            pipelineByChannel.put(channel, pipeline);
            try {
                if (broker != null) {
                    broker.handleConnection(transport);
                } else {
                    LOG.error("Broker not initialized for service: {}", service.getDescriptor().name());
                    transportByChannel.remove(channel);
                    pipelineByChannel.remove(channel);
                    transport.close();
                    try { channel.close(); } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                LOG.error("Failed to handle connection: {}", e.getMessage(), e);
                transportByChannel.remove(channel);
                pipelineByChannel.remove(channel);
                transport.close();
                try { channel.close(); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        var transport = transportByChannel.get(channel);
        if (transport instanceof MqttTlsTransport tls) {
            // TLS transport handles handshake + unwrap
            tls.onRead(channel, data);
        } else if (transport instanceof MqttPipelineTransport pt) {
            // Plain pipeline transport
            pt.onRead(channel, data);
        } else {
            LOG.debug("No transport mapped for channel in onRead");
        }
    }

    @Override
    public void onWrite(DataChannel channel) {
        var pipeline = pipelineByChannel.get(channel);
        if (pipeline != null) {
            pipeline.onWrite(channel);
        } else {
            LOG.debug("No pipeline mapped for channel in onWrite");
        }
    }

    @Override
    public void onDisconnect(DataChannel channel) {
        var transport = transportByChannel.remove(channel);
        if (transport != null) {
            transport.close();
        }
        pipelineByChannel.remove(channel);
        LOG.debug("Client channel disconnected from broker");
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        LOG.warn("Client channel error in broker", cause);
        onDisconnect(channel);
    }
}
