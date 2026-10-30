package ssg.legoflow.messaging.kafka.service;

import ssg.legoflow.messaging.kafka.transport.PipelineKafkaTransport;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Channel handler for the Kafka client service. Bridges pipeline events to the
 * {@link PipelineKafkaTransport} and signals the TCP connect event.
 *
 * <p><b>Responsibility separation:</b> TCP lifecycle (finishConnect, interestOps, close) is
 * handled entirely by the {@code SelectableChannelManager} in the selector thread — this
 * handler never touches sockets or selector keys. It only moves bytes to the transport and
 * signals the connect event.
 */
public final class KafkaClientChannelHandler implements ChannelHandler {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaClientChannelHandler.class);

    private final KafkaClientService service;
    private CountDownLatch connectLatch;
    private final AtomicBoolean connectHandled = new AtomicBoolean(false);

    public KafkaClientChannelHandler(KafkaClientService service) {
        this.service = service;
    }

    public void setConnectLatch(CountDownLatch latch) {
        this.connectLatch = latch;
    }

    /** Called when the TCP connect completes (fired by the manager after finishConnect). */
    @Override
    public void onConnect(DataChannel channel) {
        if (!connectHandled.compareAndSet(false, true)) {
            return; // already handled
        }
        if (connectLatch != null) {
            connectLatch.countDown();
        }
        LOG.debug("Kafka client channel connected");
    }

    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        var transport = service.getTransport();
        if (transport != null) {
            transport.onRead(channel, data);
        }
    }

    @Override
    public void onWrite(DataChannel channel) {
        var transport = service.getTransport();
        if (transport != null) {
            transport.onWrite(channel);
        }
    }

    @Override
    public void onDisconnect(DataChannel channel) {
        LOG.debug("Kafka client channel disconnected");
        var transport = service.getTransport();
        if (transport != null) {
            try { transport.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        LOG.warn("Kafka client channel error", cause);
    }

    public KafkaClientService getKafkaService() { return service; }
}
