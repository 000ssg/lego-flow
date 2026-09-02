package ssg.legoflow.messaging.amqp.common;

import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.messaging.amqp.client.BrokerMode;
import ssg.legoflow.messaging.amqp.container.ContainerMode;
import ssg.legoflow.messaging.amqp.session.AmqpSession;
import ssg.legoflow.messaging.amqp.transport.AmqpFrameCodec;
import ssg.legoflow.service.channel.DataChannel;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typed protocol state container for AMQP connections.
 *
 * <p>Extends {@link ServiceContext} with typed getters/setters for protocol
 * state, replacing generic {@code setAttribute}/getAttribute lookups with
 * strongly-typed volatile fields. Used by both client and server pipelines.
 *
 * <p>All fields are thread-safe (volatile or concurrent collections) because
 * the pipeline may touch them from different virtual threads (read pool, write
 * pool, connect pool).
 *
 * @since 0.2.0
 */
public interface AmqpContext extends ServiceContext {

    /* ── Lifecycle ─────────────────────────────────────────────────────── */

    /** Current processor state. */
    ProcessorState getState();

    /** Set processor state. */
    void setState(ProcessorState state);

    /** Associated data channel. */
    DataChannel getChannel();

    /** Set data channel. */
    void setChannel(DataChannel channel);

    /** Last error, if any. */
    Throwable getError();

    /** Set error. */
    void setError(Throwable error);

    /* ── Shared protocol state (client + server) ───────────────────────── */

    /** Current protocol state machine state. */
    ConnectionState getConnectionState();

    /** Transition to a new state. Returns true if the transition is valid. */
    boolean transitionTo(ConnectionState newState);

    /** Remote host. */
    String getRemoteHost();

    /** Set remote host. */
    void setRemoteHost(String host);

    /** Remote port. */
    int getRemotePort();

    /** Set remote port. */
    void setRemotePort(int port);

    /** Negotiated max frame size in bytes. */
    int getMaxFrameSize();

    /** Set negotiated max frame size. */
    void setMaxFrameSize(int maxFrameSize);

    /** Negotiated maximum channel number. */
    int getChannelMax();

    /** Set negotiated channel max. */
    void setChannelMax(int channelMax);

    /** Unique session identifier for this connection. */
    String getSessionId();

    /** Back-reference to the frame codec for this connection. */
    AmqpFrameCodec getCodec();

    /** Set the frame codec reference. */
    void setCodec(AmqpFrameCodec codec);

    /* ── Write buffer ──────────────────────────────────────────────────── */

    /** Queue data for outbound write. */
    void queueWrite(ByteBuffer data);

    /** Dequeue data for outbound write (called by handler). */
    ByteBuffer dequeueWrite();

    /* ── Client-side fields ────────────────────────────────────────────── */

    /** Broker simulation mode (client). */
    BrokerMode getBrokerMode();

    /** Set broker mode (client). */
    void setBrokerMode(BrokerMode brokerMode);

    /** Remote container ID (client, set after OPEN exchange). */
    String getContainerId();

    /** Set remote container ID (client). */
    void setContainerId(String containerId);

    /* ── Server-side fields ────────────────────────────────────────────── */

    /** Container simulation mode (server). */
    ContainerMode getContainerMode();

    /** Set container mode (server). */
    void setContainerMode(ContainerMode containerMode);

    /**
     * Per-channel session map (server). Channel number → session.
     */
    Map<Integer, AmqpSession> getSessions();
}

