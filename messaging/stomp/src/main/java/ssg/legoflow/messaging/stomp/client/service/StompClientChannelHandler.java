package ssg.legoflow.messaging.stomp.client.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Channel handler for STOMP client service. Bridges pipeline events
 * to the transport and handles the connection lifecycle.
 *
 * <p><b>Responsibility separation:</b> TCP lifecycle (finishConnect, interestOps,
 * close) is handled entirely by {@code SelectableChannelManager} in the
 * selector thread — protocol handlers never touch sockets or selector keys.
 * This handler only signals protocol readiness.
 */
public final class StompClientChannelHandler implements ChannelHandler {

    private static final Logger LOG = LoggerFactory.getLogger(StompClientChannelHandler.class);

    private final StompClientService service;
    private CountDownLatch connectLatch;
    private final AtomicBoolean connectHandled = new AtomicBoolean(false);

    public StompClientChannelHandler(StompClientService service, ssg.legoflow.service.ServiceContext ctx) {
        this.service = service;
    }

    public void setConnectLatch(CountDownLatch latch) {
        this.connectLatch = latch;
    }

    /**
     * Called when TCP connect completes (fired by the manager after
     * finishConnect). Only signals readiness for protocol processing.
     */
    @Override
    public void onConnect(DataChannel channel) {
        if (!connectHandled.compareAndSet(false, true)) {
            return; // Already handled
        }
        if (connectLatch != null) {
            connectLatch.countDown();
        }
        LOG.debug("STOMP client channel connected");
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
        LOG.debug("STOMP client channel disconnected");
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        LOG.warn("STOMP client channel error", cause);
    }
}
