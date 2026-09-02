package ssg.legoflow.messaging.amqp.client;

import ssg.legoflow.messaging.amqp.common.AmqpConstants;
import ssg.legoflow.messaging.amqp.common.AmqpError;
import ssg.legoflow.messaging.amqp.common.AmqpException;
import ssg.legoflow.messaging.amqp.common.ConnectionState;
import ssg.legoflow.messaging.amqp.delivery.Delivery;
import ssg.legoflow.messaging.amqp.delivery.DeliveryState;
import ssg.legoflow.messaging.amqp.delivery.DeliveryStateCodec;
import ssg.legoflow.messaging.amqp.link.ReceiverLink;
import ssg.legoflow.messaging.amqp.link.SenderLink;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.message.MessageCodec;
import ssg.legoflow.messaging.amqp.sasl.SaslCodec;
import ssg.legoflow.messaging.amqp.sasl.AnonymousMechanism;
import ssg.legoflow.messaging.amqp.sasl.PlainMechanism;
import ssg.legoflow.messaging.amqp.sasl.SaslMechanism;
import ssg.legoflow.messaging.amqp.session.AmqpSession;
import ssg.legoflow.messaging.amqp.transport.*;
import ssg.legoflow.messaging.amqp.types.AmqpType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AMQP 1.0 client for connecting to containers and exchanging messages.
 *
 * <p>Provides methods to connect, create sessions, attach sender/receiver links,
 * send messages, and receive messages. Supports SASL authentication and
 * multiple delivery semantics.
 *
 * <p><b>Transport-agnostic:</b> this class uses only the {@link AmqpTransport}
 * interface. TCP infrastructure belongs in the service layer.
 *
 * <p><b>Single-reader architecture:</b> there is no background read loop.
 * All frame reading happens synchronously on the calling thread:
 * <ol>
 *   <li>{@link #connect(AmqpTransport)} reads handshake frames (SASL, OPEN)</li>
 *   <li>{@link #createSession()} reads BEGIN response</li>
 *   <li>{@link #createSender(AmqpSession, String, String)} and
 *       {@link #createReceiver(AmqpSession, String, String)} read ATTACH response</li>
 *   <li>{@link ReceiverLink#receive(long, TimeUnit)} reads frames until a
 *       TRANSFER for this link arrives</li>
 * </ol>
 * Data flows into the transport's ring buffer via
 * {@code fireRead} → handler → transport. Whoever calls {@code pollFrame()}
 * is the sole reader. No race, no lost frames.
 *
 * @since 0.1.0
 */
public final class AmqpClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpClient.class);

    private final ClientConfig config;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final Map<Integer, AmqpSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger nextChannel = new AtomicInteger(0);

    private volatile AmqpTransport transport;
    private volatile ConnectionState state = ConnectionState.START;
    private volatile int maxFrameSize = AmqpConstants.DEFAULT_MAX_FRAME_SIZE;

    /**
     * Creates a new AMQP client with the given configuration.
     *
     * @param config the client configuration
     */
    public AmqpClient(ClientConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Connects using a pre-configured transport.
     *
     * <p>Runs the SASL negotiation and AMQP OPEN handshake synchronously,
     * reading and writing frames on the calling thread. Once connected, the
     * caller is responsible for reading incoming frames via
     * {@link #pollFrame(long, TimeUnit)}.
     *
     * @param transport the transport to use
     * @throws IOException if the connection fails
     */
    public void connect(AmqpTransport transport) throws IOException {
        if (transport == null) {
            throw new IllegalArgumentException("Transport must not be null");
        }
        this.transport = transport;

        SaslMechanism mechanism;
        if (config.saslMechanism() != null) {
            mechanism = config.saslMechanism();
        } else if (config.username() != null && !config.username().isBlank()) {
            mechanism = new PlainMechanism(config.username(), config.password());
        } else {
            mechanism = new AnonymousMechanism();
        }

        if (config.proto0Accepted()) {
            // Proto-0 (Qpid Dispatch): AMQP_HEADER → OPEN (no SASL).
            LOG.debug("Proto-0 (Qpid): AMQP_HEADER, then OPEN (no SASL)");
            transport.send(ByteBuffer.wrap(AmqpConstants.AMQP_HEADER));
            ByteBuffer headerBuf = ByteBuffer.allocate(8);
            readFully(headerBuf);
            headerBuf.flip();
            byte[] echo = new byte[8];
            headerBuf.get(echo);
            if (!Arrays.equals(echo, AmqpConstants.AMQP_HEADER)) {
                throw new IOException("Invalid AMQP header echo in proto-0: " + Arrays.toString(echo));
            }
        } else {
            // Proto-3: SASL-first. Send SASL_HEADER, wait for server echo.
            transport.send(ByteBuffer.wrap(AmqpConstants.SASL_HEADER));
            ByteBuffer headerBuf = ByteBuffer.allocate(8);
            try {
                readFully(headerBuf);
            } catch (AmqpException e) {
                LOG.debug("Server closed connection on SASL_HEADER — proto-0 fallback needed");
                throw new IOException(
                        "Server does not support SASL-first protocol (proto-3). "
                        + "The service layer should create a new transport and retry with proto0Accepted=true.", e);
            }
            headerBuf.flip();
            byte[] serverHeader = new byte[8];
            headerBuf.get(serverHeader);

            if (Arrays.equals(serverHeader, AmqpConstants.SASL_HEADER)) {
                LOG.debug("Server supports SASL-first, proceeding with negotiation");
                doSaslNegotiation(mechanism);
                transport.send(ByteBuffer.wrap(AmqpConstants.AMQP_HEADER));
                headerBuf.clear();
                readFully(headerBuf);
                headerBuf.flip();
                byte[] amqpEcho = new byte[8];
                headerBuf.get(amqpEcho);
                if (!Arrays.equals(amqpEcho, AmqpConstants.AMQP_HEADER)) {
                    throw new IOException("Invalid AMQP header echo after SASL: " + Arrays.toString(amqpEcho));
                }
            } else if (Arrays.equals(serverHeader, AmqpConstants.AMQP_HEADER)) {
                LOG.debug("Server responded with AMQP_HEADER, doing SASL (proto-0 auto-detect)");
                doSaslNegotiation(mechanism);
            } else {
                throw new IOException("Invalid header from server: " + Arrays.toString(serverHeader));
            }
        }
        state = ConnectionState.HDR_EXCH;

        // Send open
        var open = new Performative.Open(
                config.containerId(), config.host(),
                config.maxFrameSize(), config.channelMax(),
                config.idleTimeout(), List.of(), List.of(), Map.of()
        );
        sendPerformative(0, open);

        // Receive open response
        AmqpFrame openFrame = readFrame();
        if (openFrame.performative() instanceof AmqpType.Described desc) {
            var remoteOpen = (Performative.Open) PerformativeCodec.decode(desc);
            maxFrameSize = (int) Math.min(config.maxFrameSize(), remoteOpen.maxFrameSize());
            LOG.debug("Connected to container '{}'", remoteOpen.containerId());
        }

        state = ConnectionState.OPENED;
        connected.set(true);
    }

    private void doSaslNegotiation(SaslMechanism mechanism) throws IOException {
        // Read sasl-mechanisms frame from server
        AmqpFrame mechFrame = readFrame();
        if (mechFrame == null || mechFrame.type() != AmqpConstants.FRAME_TYPE_SASL) {
            throw new AmqpException(AmqpError.FRAMING_ERROR,
                    "Expected SASL sasl-mechanisms frame, got type=" + (mechFrame != null ? mechFrame.type() : "null"));
        }
        if (!(mechFrame.performative() instanceof AmqpType.Described)) {
            throw new AmqpException(AmqpError.FRAMING_ERROR,
                    "sasl-mechanisms frame has non-described performative: " + mechFrame.performative().getClass().getSimpleName());
        }
        List<String> mechanisms = SaslCodec.decodeMechanisms((AmqpType.Described) mechFrame.performative());
        LOG.debug("Server SASL mechanisms: {}", mechanisms);

        // Send SASL init
        byte[] initialResponse = mechanism.initialResponse();
        var init = SaslCodec.encodeInit(mechanism.name(), initialResponse, config.host());
        var initFrame = new AmqpFrame(0, AmqpConstants.FRAME_TYPE_SASL, init);
        ByteBuffer initBuf = FrameCodec.encode(initFrame, maxFrameSize);
        transport.send(initBuf);

        // Read SASL outcome
        AmqpFrame outcomeFrame = readFrame();
        if (outcomeFrame == null || outcomeFrame.type() != AmqpConstants.FRAME_TYPE_SASL) {
            throw new AmqpException(AmqpError.FRAMING_ERROR,
                    "Expected SASL sasl-outcome frame, got type=" + (outcomeFrame != null ? outcomeFrame.type() : "null"));
        }
        if (outcomeFrame.performative() instanceof AmqpType.Described outcomeDesc) {
            int code = SaslCodec.decodeOutcomeCode(outcomeDesc);
            if (code != 0) {
                throw new AmqpException(AmqpError.UNAUTHORIZED_ACCESS,
                        "SASL authentication failed with code: " + code);
            }
            LOG.debug("SASL authentication successful");
        }
    }

    /**
     * Creates a new session.
     *
     * <p>Sends BEGIN and reads frames until the remote BEGIN response arrives,
     * processing them through the state machine.
     *
     * @return the session
     * @throws IOException if the operation fails
     */
    public AmqpSession createSession() throws IOException {
        int channel = nextChannel.getAndIncrement();
        var session = new AmqpSession(channel);
        session.frameSender((performative, payload) -> {
            var described = PerformativeCodec.encode(performative);
            var frame = new AmqpFrame(channel, AmqpConstants.FRAME_TYPE_AMQP, described, payload);
            transport.send(FrameCodec.encode(frame, maxFrameSize));
        });
        sessions.put(channel, session);

        // Send begin
        var begin = session.createBegin();
        sendPerformative(channel, begin);
        session.state(AmqpSession.State.BEGIN_SENT);

        // Wait for begin response — read frames until session is MAPPED
        waitForFrame(() -> session.state() == AmqpSession.State.MAPPED, config.connectTimeout());

        return session;
    }

    /**
     * Creates a sender link on the given session.
     *
     * <p>Sends ATTACH and reads frames until the remote ATTACH response arrives.
     *
     * @param session the session
     * @param name    the link name
     * @param address the target address
     * @return the sender link
     * @throws IOException if the operation fails
     */
    public SenderLink createSender(AmqpSession session, String name, String address) throws IOException {
        long handle = session.allocateHandle();
        var link = new SenderLink(name, handle, null, address);
        link.session(session);
        session.addSenderLink(link);

        // Send attach
        var attach = link.createAttach();
        sendPerformative(session.localChannel(), attach);
        link.state(SenderLink.State.ATTACH_SENT);

        // Wait for attach response
        waitForFrame(() -> link.state() == SenderLink.State.ATTACHED, config.connectTimeout());

        // Grant initial credit
        link.grantCredit(0, AmqpConstants.DEFAULT_LINK_CREDIT);

        return link;
    }

    /**
     * Creates a receiver link on the given session.
     *
     * <p>Sends ATTACH and reads frames until the remote ATTACH response arrives.
     *
     * @param session the session
     * @param name    the link name
     * @param address the source address
     * @return the receiver link
     * @throws IOException if the operation fails
     */
    public ReceiverLink createReceiver(AmqpSession session, String name, String address) throws IOException {
        long handle = session.allocateHandle();
        var link = new ReceiverLink(name, handle, address, null);
        link.session(session);
        link.client(this);
        session.addReceiverLink(link);

        // Send attach
        var attach = link.createAttach();
        sendPerformative(session.localChannel(), attach);
        link.state(ReceiverLink.State.ATTACH_SENT);

        // Wait for attach response
        waitForFrame(() -> link.state() == ReceiverLink.State.ATTACHED, config.connectTimeout());

        // Issue initial credit
        link.issueCredit(AmqpConstants.DEFAULT_LINK_CREDIT);

        return link;
    }

    /**
     * Reads one frame from the transport and processes it through the state machine.
     * Blocks until a frame is available or the timeout elapses.
     *
     * <p>This is the single entry point for frame consumption. All callers
     * (handshake, session setup, receiver) use this path. No background thread
     * reads frames — the caller is the reader.
     *
     * @param timeout how long to wait for a frame
     * @param unit    timeout unit
     * @return true if a frame was read and processed, false on timeout or closed transport
     */
    public boolean pollFrame(long timeout, TimeUnit unit) throws IOException {
        if (!connected.get() || transport == null || !transport.isOpen()) return false;

        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);

        // Read frame size (4 bytes)
        ByteBuffer sizeBuf = ByteBuffer.allocate(4);
        long remainingMs = deadline - System.currentTimeMillis();
        if (remainingMs <= 0) return false;
        int read = transport.receiveWithTimeout(sizeBuf, remainingMs, TimeUnit.MILLISECONDS);
        if (read < 4) {
            System.out.println("[pollFrame] size read only " + read + " bytes, timeout");
            return false;
        }
        sizeBuf.flip();
        int size = sizeBuf.getInt();
        System.out.println("[pollFrame] size=" + size);

        if (size < AmqpConstants.FRAME_HEADER_SIZE) return false;

        // Read frame body
        remainingMs = deadline - System.currentTimeMillis();
        if (remainingMs <= 0) return false;

        ByteBuffer bodyBuf = ByteBuffer.allocate(size - 4);
        read = transport.receiveWithTimeout(bodyBuf, remainingMs, TimeUnit.MILLISECONDS);
        if (read < (size - 4)) {
            System.out.println("[pollFrame] body read only " + read + "/" + (size - 4) + " bytes, timeout");
            return false;
        }
        bodyBuf.flip();

        ByteBuffer frameBuf = ByteBuffer.allocate(size);
        frameBuf.putInt(size);
        frameBuf.put(bodyBuf);
        frameBuf.flip();

        AmqpFrame frame = FrameCodec.decode(frameBuf);
        if (frame == null) {
            System.out.println("[pollFrame] decode returned null for size " + size);
            return false;
        }

        System.out.println("[pollFrame] ch=" + frame.channel() + " perf=" + frame.performative().getClass().getSimpleName());

        // Process through state machine
        if (frame.isHeartbeat()) return true;

        if (frame.performative() instanceof AmqpType.Described desc) {
            Performative perf = PerformativeCodec.decode(desc);
            System.out.println("[pollFrame] decoded " + perf.getClass().getSimpleName());
            handleIncomingPerformative(frame.channel(), perf, frame.payload());
        }
        return true;
    }

    /**
     * Sends a message through a sender link.
     *
     * @param sender  the sender link
     * @param message the message to send
     * @param settled whether to pre-settle (at-most-once)
     * @return the delivery tracker
     */
    public Delivery send(SenderLink sender, AmqpMessage message, boolean settled) {
        return sender.send(message, settled);
    }

    /**
     * Returns whether the client is connected.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Returns the negotiated max frame size.
     */
    public int getMaxFrameSize() {
        return maxFrameSize;
    }

    /**
     * Blocks reading frames until the condition becomes true or timeout elapses.
     * Processes every frame through the state machine — which may update session/link state.
     */
    private void waitForFrame(java.util.function.BooleanSupplier condition, Duration timeout) throws IOException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (!condition.getAsBoolean()) {
            long remainingMs = deadline - System.currentTimeMillis();
            if (remainingMs <= 0) {
                throw new AmqpException(AmqpError.ILLEGAL_STATE,
                        "Timeout waiting for response");
            }
            pollFrame(remainingMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Dispatches an incoming performative to the appropriate session/link handler.
     * The state machine updates session state, link state, and receiver queues.
     */
    private void handleIncomingPerformative(int channel, Performative perf, ByteBuffer payload) {
        AmqpSession session = sessions.get(channel);

        switch (perf) {
            case Performative.Begin begin -> {
                if (session != null) {
                    session.handleBegin(begin);
                    session.state(AmqpSession.State.MAPPED);
                }
            }
            case Performative.Attach attach -> {
                System.out.println("[handleAttach] handle=" + attach.handle() + " role=" + attach.role());
                if (session != null) {
                    SenderLink sl = session.senderLink(attach.handle());
                    if (sl != null) {
                        System.out.println("[handleAttach] -> sender: " + sl.name());
                        sl.state(SenderLink.State.ATTACHED);
                    }
                    ReceiverLink rl = session.receiverLink(attach.handle());
                    if (rl != null) {
                        System.out.println("[handleAttach] -> receiver: " + rl.name());
                        rl.state(ReceiverLink.State.ATTACHED);
                    }
                    if (sl == null && rl == null) {
                        System.out.println("[handleAttach] NO LINK FOUND for handle=" + attach.handle());
                    }
                }
            }
            case Performative.Flow flow -> {
                if (session != null) {
                    session.handleFlow(flow);
                    if (flow.handle() != null) {
                        SenderLink sender = session.senderLink(flow.handle());
                        if (sender != null && flow.linkCredit() != null) {
                            long brokerDeliveryCount = flow.deliveryCount() != null ? flow.deliveryCount() : sender.deliveryCount();
                            sender.grantCredit(brokerDeliveryCount, flow.linkCredit());
                        }
                    }
                }
            }
            case Performative.Transfer transfer -> {
                if (session != null) {
                    session.recordIncomingTransfer();
                    ReceiverLink receiver = session.receiverLink(transfer.handle());
                    if (receiver != null && payload != null && payload.hasRemaining()) {
                        AmqpMessage message = MessageCodec.decode(payload);
                        receiver.handleTransfer(transfer.deliveryId(), transfer.deliveryTag(),
                                message, transfer.settled());
                    }
                }
            }
            case Performative.Disposition disposition -> {
                if (session != null) {
                    DeliveryState ds = null;
                    if (disposition.state() instanceof AmqpType.Described desc) {
                        ds = DeliveryStateCodec.decode(desc);
                    }
                    for (var sender : session.senderLinks().values()) {
                        sender.handleDisposition(disposition.first(), disposition.last(),
                                disposition.settled(), ds != null ? ds : new DeliveryState.Accepted());
                    }
                }
            }
            case Performative.Detach detach -> {
                if (session != null) {
                    SenderLink sl = session.senderLink(detach.handle());
                    if (sl != null) sl.state(SenderLink.State.DETACHED);
                    ReceiverLink rl = session.receiverLink(detach.handle());
                    if (rl != null) rl.state(ReceiverLink.State.DETACHED);
                    session.removeLink(detach.handle());
                }
            }
            case Performative.End end -> {
                if (session != null) {
                    session.state(AmqpSession.State.DISCARDING);
                }
            }
            case Performative.Close close -> {
                state = ConnectionState.CLOSE_RCVD;
                connected.set(false);
                if (close.error() != null) {
                    System.out.println("[AmqpClient] Server sent Close with error: " + close.error());
                    throw new AmqpException(AmqpError.ILLEGAL_STATE, "Server closed connection: " + close.error());
                }
            }
            default -> {}
        }
    }

    /**
     * Sends a performative on the given channel.
     * Used by links to send FLOW, DISPOSITION, etc.
     */
    public void sendPerformative(int channel, Performative performative) {
        var described = PerformativeCodec.encode(performative);
        var frame = new AmqpFrame(channel, AmqpConstants.FRAME_TYPE_AMQP, described);
        transport.send(FrameCodec.encode(frame, maxFrameSize));
    }

    /**
     * Reads one frame from transport synchronously.
     * Used by handshake code during connect().
     */
    private AmqpFrame readFrame() {
        ByteBuffer sizeBuf = ByteBuffer.allocate(4);
        readFully(sizeBuf);
        sizeBuf.flip();
        int size = sizeBuf.getInt();

        if (size < AmqpConstants.FRAME_HEADER_SIZE) return null;

        ByteBuffer frameBuf = ByteBuffer.allocate(size);
        frameBuf.putInt(size);
        ByteBuffer remaining = ByteBuffer.allocate(size - 4);
        readFully(remaining);
        remaining.flip();
        frameBuf.put(remaining);
        frameBuf.flip();

        return FrameCodec.decode(frameBuf);
    }

    /**
     * Reads exactly buffer.remaining() bytes from transport.
     * Used by handshake (header exchange, SASL frames).
     */
    private void readFully(ByteBuffer buf) {
        while (buf.hasRemaining()) {
            int n = transport.receive(buf);
            if (n < 0) {
                throw new AmqpException(AmqpError.CONNECTION_FORCED, "Transport closed");
            }
        }
    }

    @Override
    public void close() {
        if (!connected.compareAndSet(true, false)) return;

        try {
            // Send close
            sendPerformative(0, new Performative.Close());
        } catch (Exception e) {
            LOG.debug("Error sending close", e);
        }

        if (transport != null) transport.close();
        state = ConnectionState.END;
        LOG.debug("AMQP client closed");
    }
}
