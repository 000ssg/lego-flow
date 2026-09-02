package ssg.legoflow.messaging.amqp.common;

import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.user.ServiceUser;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.messaging.amqp.client.BrokerMode;
import ssg.legoflow.messaging.amqp.container.ContainerMode;
import ssg.legoflow.messaging.amqp.session.AmqpSession;
import ssg.legoflow.messaging.amqp.transport.AmqpFrameCodec;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Concrete AMQP context with typed protocol state variables.
 *
 * <p>Extends {@link DefaultServiceContext} for lifecycle management.
 * All fields are thread-safe via volatile/atomic/concurrent structures.
 */
public class AmqpCtxImpl extends DefaultServiceContext implements AmqpContext {

    /* ── Lifecycle ─────────────────────────────────────────────────────── */

    private final AtomicReference<ProcessorState> processorState = new AtomicReference<>(ProcessorState.STOPPED);
    private volatile DataChannel channel;
    private volatile Throwable error;

    /* ── Shared protocol state ─────────────────────────────────────────── */

    private final AtomicReference<ConnectionState> connectionState = new AtomicReference<>(ConnectionState.START);
    private volatile int maxFrameSize = Integer.MAX_VALUE;
    private volatile int channelMax = 65535;
    private final AtomicReference<AmqpFrameCodec> codec = new AtomicReference<>();
    private final String sessionId = java.util.UUID.randomUUID().toString().substring(0, 8);
    private volatile String remoteHost;
    private volatile int remotePort;

    /* ── Write buffer ──────────────────────────────────────────────────── */

    private final LinkedBlockingQueue<ByteBuffer> writeQueue = new LinkedBlockingQueue<>();

    /* ── Client fields ─────────────────────────────────────────────────── */

    private volatile BrokerMode brokerMode = BrokerMode.STANDARD;
    private volatile String containerId;

    /* ── Server fields ─────────────────────────────────────────────────── */

    private volatile ContainerMode containerMode = ContainerMode.STANDARD;
    private final Map<Integer, AmqpSession> sessions = new ConcurrentHashMap<>();

    public AmqpCtxImpl() { super(ServiceUser.anonymous()); }

    public AmqpCtxImpl(ServiceUser user) { super(user); }

    /* ── Lifecycle ─────────────────────────────────────────────────────── */

    @Override public ProcessorState getState() { return processorState.get(); }
    @Override public void setState(ProcessorState state) { processorState.set(state); }
    @Override public DataChannel getChannel() { return channel; }
    @Override public void setChannel(DataChannel channel) { this.channel = channel; }
    @Override public Throwable getError() { return error; }
    @Override public void setError(Throwable e) { this.error = e; }

    /* ── Shared protocol state ─────────────────────────────────────────── */

    @Override public ConnectionState getConnectionState() { return connectionState.get(); }
    @Override public boolean transitionTo(ConnectionState newState) {
        ConnectionState current = connectionState.get();
        if (!current.isValidTransition(newState)) return false;
        return connectionState.compareAndSet(current, newState);
    }
    @Override public int getMaxFrameSize() { return maxFrameSize; }
    @Override public void setMaxFrameSize(int v) { this.maxFrameSize = v; }
    @Override public int getChannelMax() { return channelMax; }
    @Override public void setChannelMax(int v) { this.channelMax = v; }
    @Override public String getSessionId() { return sessionId; }
    @Override public AmqpFrameCodec getCodec() { return codec.get(); }
    @Override public void setCodec(AmqpFrameCodec c) { codec.set(c); }
    @Override public String getRemoteHost() { return remoteHost; }
    @Override public void setRemoteHost(String h) { this.remoteHost = h; }
    @Override public int getRemotePort() { return remotePort; }
    @Override public void setRemotePort(int p) { this.remotePort = p; }

    /* ── Write buffer ──────────────────────────────────────────────────── */

    @Override public void queueWrite(ByteBuffer data) { writeQueue.offer(data); }
    @Override public ByteBuffer dequeueWrite() { return writeQueue.poll(); }

    /* ── Client fields ─────────────────────────────────────────────────── */

    @Override public BrokerMode getBrokerMode() { return brokerMode; }
    @Override public void setBrokerMode(BrokerMode m) { this.brokerMode = m; }
    @Override public String getContainerId() { return containerId; }
    @Override public void setContainerId(String id) { this.containerId = id; }

    /* ── Server fields ─────────────────────────────────────────────────── */

    @Override public ContainerMode getContainerMode() { return containerMode; }
    @Override public void setContainerMode(ContainerMode m) { this.containerMode = m; }
    @Override public Map<Integer, AmqpSession> getSessions() { return sessions; }
}
