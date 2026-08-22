package ssg.legoflow.service.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
public class ChannelPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelPipeline.class);

    private final CopyOnWriteArrayList<ChannelHandler> handlers = new CopyOnWriteArrayList<>();

    public ChannelPipeline addFirst(ChannelHandler handler) {
        handlers.add(0, handler);
        return this;
    }

    public ChannelPipeline addLast(ChannelHandler handler) {
        handlers.add(handler);
        return this;
    }

    public ChannelPipeline remove(ChannelHandler handler) {
        handlers.remove(handler);
        return this;
    }

    public List<ChannelHandler> getHandlers() {
        return List.copyOf(handlers);
    }

    public void fireRead(DataChannel channel, ByteBuffer data) {
        for (var handler : handlers) {
            try {
                handler.onRead(channel, data.asReadOnlyBuffer());
            } catch (Exception e) {
                LOG.error("Handler {} threw on read", handler.getClass().getSimpleName(), e);
                fireError(channel, e);
                return;
            }
        }
    }

    public void fireWrite(DataChannel channel) {
        for (var handler : handlers) {
            try {
                handler.onWrite(channel);
            } catch (Exception e) {
                LOG.error("Handler {} threw on write", handler.getClass().getSimpleName(), e);
                fireError(channel, e);
                return;
            }
        }
    }

    public void fireConnect(DataChannel channel) {
        for (var handler : handlers) {
            try {
                handler.onConnect(channel);
            } catch (Exception e) {
                LOG.error("Handler {} threw on connect", handler.getClass().getSimpleName(), e);
                fireError(channel, e);
                return;
            }
        }
    }

    public void fireDisconnect(DataChannel channel) {
        for (var handler : handlers) {
            try {
                handler.onDisconnect(channel);
            } catch (Exception e) {
                LOG.error("Handler {} threw on disconnect", handler.getClass().getSimpleName(), e);
                fireError(channel, e);
                return;
            }
        }
    }

    /**
     * Fires a datagram event to all {@link DatagramHandler} instances in the pipeline.
     *
     * <p>Handlers that are not {@code DatagramHandler} are skipped. This method
     * is used by {@link ssg.legoflow.service.manager.ServiceGroup} to dispatch
     * received datagrams directly, avoiding the double-read issue that occurs when
     * {@link #fireRead} triggers {@code DatagramHandler.onRead()} which calls
     * {@code receiveDatagram()} again on an already-consumed channel.
     *
     * @param channel the data channel the datagram was received on
     * @param packet  the datagram packet information including sender address and payload
     * @since 0.1.0
     */
    public void fireDatagram(DataChannel channel, DatagramPacketInfo packet) {
        for (var handler : handlers) {
            if (handler instanceof DatagramHandler dh) {
                try {
                    dh.onDatagram(channel, packet);
                } catch (Exception e) {
                    LOG.error("Handler {} threw on datagram", handler.getClass().getSimpleName(), e);
                    fireError(channel, e);
                    return;
                }
            }
        }
    }

    public void fireError(DataChannel channel, Throwable cause) {
        for (var handler : handlers) {
            try {
                handler.onError(channel, cause);
            } catch (Exception e) {
                LOG.error("Handler {} threw on error handling", handler.getClass().getSimpleName(), e);
            }
        }
    }
}
