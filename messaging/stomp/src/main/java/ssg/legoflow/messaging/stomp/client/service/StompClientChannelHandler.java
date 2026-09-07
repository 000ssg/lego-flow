package ssg.legoflow.messaging.stomp.client.service;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.TcpDataChannel;
import ssg.legoflow.messaging.stomp.transport.PipelineTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CountDownLatch;

/**
 * Channel handler for STOMP client service. Bridges pipeline events
 * to the transport and handles the connection lifecycle.
 */
public final class StompClientChannelHandler implements ChannelHandler {

    private static final Logger LOG = LoggerFactory.getLogger(StompClientChannelHandler.class);

    private final StompClientService service;
    private CountDownLatch connectLatch;

    public StompClientChannelHandler(StompClientService service, ssg.legoflow.service.ServiceContext ctx) {
        this.service = service;
    }

    public void setConnectLatch(CountDownLatch latch) {
        this.connectLatch = latch;
    }

    @Override
    public void onConnect(DataChannel channel) {
        if (channel instanceof TcpDataChannel) {
            try {
                var socketChannel = ((TcpDataChannel) channel).getSocketChannel();
                socketChannel.finishConnect();

                // Enable OP_READ|OP_WRITE
                var key = channel.getSelectionKey();
                if (key != null) {
                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                }

                // Signal connect complete
                if (connectLatch != null) {
                    connectLatch.countDown();
                }
                LOG.debug("STOMP client channel connected");
            } catch (Exception e) {
                LOG.error("STOMP client connection failed: {}", e.getMessage(), e);
                if (connectLatch != null) {
                    connectLatch.countDown();
                }
            }
        }
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
