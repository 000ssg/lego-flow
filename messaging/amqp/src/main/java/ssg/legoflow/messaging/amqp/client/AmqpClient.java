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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * AMQP 1.0 client for connecting to containers and exchanging messages.
 *
 * <p>Provides methods to connect, create sessions, attach sender/receiver links,
 * send messages, and receive messages. Supports SASL authentication and
 * multiple delivery semantics.
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
    private volatile ExecutorService executor;
    private volatile Future<?> readerFuture;
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
     * Connects to the AMQP container.
     *
     * @throws IOException if the connection fails
     */
    public void connect() throws IOException {
        connect(null);
    }

    /**
     * Connects to the AMQP container using the given transport (for testing).
     *
     * @param testTransport the transport to use, or null for TCP
     * @throws IOException if the connection fails
     */
    public void connect(AmqpTransport testTransport) throws IOException {
        if (testTransport != null) {
            this.transport = testTransport;
        } else {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            channel.connect(new InetSocketAddress(config.host(), config.port()));
            this.transport = new TcpTransport(channel);
            // Finish the connection handshake before sending data
            ((TcpTransport) this.transport).ensureConnected();
        }

        executor = Executors.newVirtualThreadPerTaskExecutor();

        // Always try SASL-first (proto-3) header regardless of mechanism.
        // Most modern brokers (RabbitMQ, Artemis) require SASL.
        // If the server responds with AMQP_HEADER (proto-0) or closes the socket,
        // fall back to proto-0 (Qpid Dispatch path).
        SaslMechanism mechanism;
        if (config.saslMechanism() != null) {
            mechanism = config.saslMechanism();
        } else if (config.username() != null && !config.username().isBlank()) {
            mechanism = new PlainMechanism(config.username(), config.password());
        } else {
            mechanism = new AnonymousMechanism();
        }

        // Send SASL_HEADER first — readFully accumulates bytes until a full 8-byte header
        // is available. If the server closes the connection, readFully throws and we
        // fall back to proto-0.
        transport.send(ByteBuffer.wrap(AmqpConstants.SASL_HEADER));
        ByteBuffer headerBuf = ByteBuffer.allocate(8);
        try {
            readFully(headerBuf);
        } catch (AmqpException e) {
            // Server closed connection on SASL_HEADER — Qpid Dispatch doesn't support
            // SASL-first. Fall back to proto-0: reopen socket, send AMQP_HEADER.
            LOG.debug("Server closed connection on SASL_HEADER, falling back to proto-0");
            transport.close();
            SocketChannel newChannel = SocketChannel.open();
            newChannel.configureBlocking(true);
            newChannel.connect(new InetSocketAddress(config.host(), config.port()));
            this.transport = new TcpTransport(newChannel);
            ((TcpTransport) this.transport).ensureConnected();
            headerBuf = ByteBuffer.allocate(8);
            transport.send(ByteBuffer.wrap(AmqpConstants.AMQP_HEADER));
            readFully(headerBuf);
            headerBuf.flip();
            byte[] fbHeader = new byte[8];
            headerBuf.get(fbHeader);
            if (!Arrays.equals(fbHeader, AmqpConstants.AMQP_HEADER)) {
                throw new IOException("Invalid AMQP header on proto-0 fallback: " + Arrays.toString(fbHeader));
            }
            LOG.debug("Fell back to proto-0, proceeding without SASL");
            // Proceed to OPEN on proto-0 connection
            state = ConnectionState.HDR_EXCH;
            var open = new Performative.Open(
                    config.containerId(), config.host(),
                    config.maxFrameSize(), config.channelMax(),
                    config.idleTimeout(), List.of(), List.of(), Map.of()
            );
            sendPerformative(0, open);
            AmqpFrame openFrame = readFrame();
            if (openFrame != null && openFrame.performative() instanceof AmqpType.Described desc) {
                var remoteOpen = (Performative.Open) PerformativeCodec.decode(desc);
                maxFrameSize = (int) Math.min(config.maxFrameSize(), remoteOpen.maxFrameSize());
                LOG.debug("Connected (proto-0) to container '{}'", remoteOpen.containerId());
            }
            state = ConnectionState.OPENED;
            connected.set(true);
            readerFuture = executor.submit(this::readLoop);
            return;
        }
        headerBuf.flip();
        byte[] serverHeader = new byte[8];
        headerBuf.get(serverHeader);

        if (Arrays.equals(serverHeader, AmqpConstants.SASL_HEADER)) {
            LOG.debug("Server supports SASL, proceeding with negotiation");
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
            LOG.debug("Server responded with AMQP_HEADER, skipping SASL");
        } else {
            throw new IOException("Invalid protocol header response: " + Arrays.toString(serverHeader));
        }
        state = ConnectionState.HDR_EXCH;

        // Send open
        var open = new Performative.Open(
                config.containerId(), config.host(),
                config.maxFrameSize(), config.channelMax(),
                config.idleTimeout(), List.of(), List.of(), Map.of()
        );
        sendPerformative(0, open);

        // Receive open
        AmqpFrame openFrame = readFrame();
        if (openFrame.performative() instanceof AmqpType.Described desc) {
            var remoteOpen = (Performative.Open) PerformativeCodec.decode(desc);
            maxFrameSize = (int) Math.min(config.maxFrameSize(), remoteOpen.maxFrameSize());
            LOG.debug("Connected to container '{}'", remoteOpen.containerId());
        }

        state = ConnectionState.OPENED;
        connected.set(true);

        // Start background frame reader
        readerFuture = executor.submit(this::readLoop);
    }

    private void doSaslNegotiation(SaslMechanism mechanism) throws IOException {
        // Read sasl-mechanisms frame from server (frame type 0x01 = SASL)
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
        LOG.debug("SASL init: mechanism={}, responseLen={}", mechanism.name(), initialResponse != null ? initialResponse.length : 0);
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

    private void performSasl(SaslMechanism mechanism) {
        // Deprecated: use doSaslNegotiation instead (called after header exchange)
        throw new UnsupportedOperationException("Use connect() which handles SASL automatically");
    }

    /**
     * Creates a new session.
     *
     * @return the session
     */
    public AmqpSession createSession() {
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

        // Wait for begin response
        waitForSessionBegin(session);

        return session;
    }

    /**
     * Creates a sender link on the given session.
     *
     * @param session the session
     * @param name    the link name
     * @param address the target address
     * @return the sender link
     */
    public SenderLink createSender(AmqpSession session, String name, String address) {
        long handle = session.allocateHandle();
        var link = new SenderLink(name, handle, null, address);
        link.session(session);
        session.addSenderLink(link);

        // Send attach
        var attach = link.createAttach();
        sendPerformative(session.localChannel(), attach);
        link.state(SenderLink.State.ATTACH_SENT);

        // Wait for attach response
        waitForAttach(link);

        // Grant initial credit so the sender can send messages immediately.
        // RabbitMQ AMQP 1.0 may not grant credit until the queue exists (created
        // by the receiver link). The broker will replenish credit via FLOW frames.
        link.grantCredit(0, AmqpConstants.DEFAULT_LINK_CREDIT);

        return link;
    }

    /**
     * Creates a receiver link on the given session.
     *
     * @param session the session
     * @param name    the link name
     * @param address the source address
     * @return the receiver link
     */
    public ReceiverLink createReceiver(AmqpSession session, String name, String address) {
        long handle = session.allocateHandle();
        var link = new ReceiverLink(name, handle, address, null);
        link.session(session);
        session.addReceiverLink(link);

        // Send attach
        var attach = link.createAttach();
        sendPerformative(session.localChannel(), attach);
        link.state(ReceiverLink.State.ATTACH_SENT);

        // Wait for attach response
        waitForAttach(link);

        // Issue initial credit
        link.issueCredit(AmqpConstants.DEFAULT_LINK_CREDIT);

        return link;
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

    private void readLoop() {
        try {
            while (connected.get() && transport.isOpen()) {
                AmqpFrame frame = readFrame();
                if (frame == null) break;
                if (frame.isHeartbeat()) continue;

                if (frame.performative() instanceof AmqpType.Described desc) {
                    Performative perf = PerformativeCodec.decode(desc);
                    handleIncomingPerformative(frame.channel(), perf, frame.payload());
                }
            }
        } catch (Exception e) {
            if (connected.get()) {
                LOG.debug("Read loop error: {}", e.getMessage());
            }
        } finally {
            connected.set(false);
        }
    }

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
                if (session != null) {
                    // Update link state
                    SenderLink sl = session.senderLink(attach.handle());
                    if (sl != null) sl.state(SenderLink.State.ATTACHED);
                    ReceiverLink rl = session.receiverLink(attach.handle());
                    if (rl != null) rl.state(ReceiverLink.State.ATTACHED);
                }
            }
            case Performative.Flow flow -> {
                if (session != null) {
                    session.handleFlow(flow);
                    if (flow.handle() != null) {
                        // Link-level flow: update credit on the matching link
                        SenderLink sender = session.senderLink(flow.handle());
                        if (sender != null && flow.linkCredit() != null) {
                            // Broker is granting us credit to send.
                            // delivery-count from broker = how many the broker thinks we've sent.
                            long brokerDeliveryCount = flow.deliveryCount() != null ? flow.deliveryCount() : sender.deliveryCount();
                            sender.grantCredit(brokerDeliveryCount, flow.linkCredit());
                        }
                        ReceiverLink receiver = session.receiverLink(flow.handle());
                        if (receiver != null) {
                            // Broker (acting as sender) may also flow to our receiver links
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
            }
            default -> {}
        }
    }

    private void waitForSessionBegin(AmqpSession session) {
        long deadline = System.currentTimeMillis() + config.connectTimeout().toMillis();
        while (session.state() != AmqpSession.State.MAPPED && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
    }

    private void waitForAttach(SenderLink link) {
        long deadline = System.currentTimeMillis() + config.connectTimeout().toMillis();
        while (link.state() != SenderLink.State.ATTACHED && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
    }

    private void waitForAttach(ReceiverLink link) {
        long deadline = System.currentTimeMillis() + config.connectTimeout().toMillis();
        while (link.state() != ReceiverLink.State.ATTACHED && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
    }

    private void sendPerformative(int channel, Performative performative) {
        var described = PerformativeCodec.encode(performative);
        var frame = new AmqpFrame(channel, AmqpConstants.FRAME_TYPE_AMQP, described);
        transport.send(FrameCodec.encode(frame, maxFrameSize));
    }

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
        if (executor != null) executor.close();
        state = ConnectionState.END;
        LOG.debug("AMQP client closed");
    }
}
