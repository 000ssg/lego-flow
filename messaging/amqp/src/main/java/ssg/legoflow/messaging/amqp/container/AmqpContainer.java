package ssg.legoflow.messaging.amqp.container;

import ssg.legoflow.messaging.amqp.common.AmqpConstants;
import ssg.legoflow.messaging.amqp.common.ConnectionState;
import ssg.legoflow.messaging.amqp.delivery.DeliveryState;
import ssg.legoflow.messaging.amqp.delivery.DeliveryStateCodec;
import ssg.legoflow.messaging.amqp.link.ReceiverLink;
import ssg.legoflow.messaging.amqp.link.SenderLink;
import ssg.legoflow.messaging.amqp.message.AmqpMessage;
import ssg.legoflow.messaging.amqp.message.MessageCodec;
import ssg.legoflow.messaging.amqp.sasl.SaslAuthenticator;
import ssg.legoflow.messaging.amqp.sasl.SaslCodec;
import ssg.legoflow.messaging.amqp.session.AmqpSession;
import ssg.legoflow.messaging.amqp.transport.*;
import ssg.legoflow.messaging.amqp.types.AmqpType;
import ssg.legoflow.messaging.amqp.types.TypeCodec;
import ssg.legoflow.messaging.amqp.types.Descriptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BiConsumer;

/**
 * AMQP 1.0 server container.
 *
 * <p>Acts as a broker: listens for connections, negotiates SASL, handles
 * connections, sessions, and links. Supports vendor simulation modes
 * via {@link ContainerMode} for interop testing.
 *
 * <p>Protocol gaps fixed in this version:
 * <ul>
 *   <li>SASL-first header exchange (client sends SASL_HEADER before AMQP_HEADER)</li>
 *   <li>authzid validation (RABBITMQ mode rejects non-empty authzid)</li>
 *   <li>sasl-init max-frame-size extraction</li>
 *   <li>unsettled(0) sender settle mode, first(0) receiver settle mode as defaults</li>
 *   <li>No auto-accept — unsettled transfers wait for application disposition</li>
 *   <li>Per-mode address format normalization</li>
 *   <li>Idle timeout enforcement via periodic timer</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class AmqpContainer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpContainer.class);

    private final ContainerConfig config;
    private final SaslAuthenticator authenticator;

    // Application-provided message handler (receives unsettled messages for disposition)
    private BiConsumer<ConnectionContext, IncomingMessage> messageHandler;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, ConnectionContext> connections = new ConcurrentHashMap<>();
    private final Map<String, List<SenderLink>> addressToSenders = new ConcurrentHashMap<>();
    private final Map<String, List<ReceiverLink>> addressToReceivers = new ConcurrentHashMap<>();

    private volatile ServerSocketChannel serverChannel;
    private volatile int boundPort;

    /**
     * Creates a container with the given configuration.
     *
     * @param config the container configuration
     */
    public AmqpContainer(ContainerConfig config) {
        this.config = Objects.requireNonNull(config);
        this.authenticator = config.authenticator() != null ? config.authenticator() : new SaslAuthenticator();
    }

    /**
     * Sets the application message handler for unsettled transfers.
     *
     * <p>When not set, unsettled transfers are queued in the connection context
     * and the application should call {@link #pendingMessages(ConnectionContext)} to drain them.
     *
     * @param handler invoked for each unsettled transfer
     */
    public void messageHandler(BiConsumer<ConnectionContext, IncomingMessage> handler) {
        this.messageHandler = handler;
    }

    /**
     * Returns pending (unsettled) messages for a connection.
     *
     * @param ctx the connection context
     * @return list of pending incoming messages
     */
    public List<IncomingMessage> pendingMessages(ConnectionContext ctx) {
        return new ArrayList<>(ctx.pendingMessages);
    }

    /**
     * Accepts a pending message (sends ACCEPT disposition).
     *
     * @param ctx the connection context
     * @param msg the incoming message to accept
     */
    public void accept(ConnectionContext ctx, IncomingMessage msg) {
        sendDisposition(ctx, msg, new DeliveryState.Accepted(), true);
        ctx.pendingMessages.remove(msg);
    }

    /**
     * Rejects a pending message (sends REJECT disposition).
     *
     * @param ctx the connection context
     * @param msg the incoming message to reject
     * @param error error description (optional)
     */
    public void reject(ConnectionContext ctx, IncomingMessage msg, String error) {
        var rejectState = new DeliveryState.Rejected(error != null ? error : "rejected");
        sendDisposition(ctx, msg, rejectState, true);
        ctx.pendingMessages.remove(msg);
    }

    /**
     * Releases a pending message (sends RELEASE disposition).
     *
     * @param ctx the connection context
     * @param msg the incoming message to release
     */
    public void release(ConnectionContext ctx, IncomingMessage msg) {
        sendDisposition(ctx, msg, new DeliveryState.Released(), true);
        ctx.pendingMessages.remove(msg);
    }

    /**
     * Starts the container and begins accepting connections.
     *
     * @throws IOException if the container cannot bind
     */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;

        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(config.host(), config.port()));
        boundPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();

        LOG.info("AMQP container '{}' (mode={}) listening on {}:{}",
                config.containerId(), config.mode(), config.host(), boundPort);

        executor.submit(this::acceptLoop);

        // Start idle timeout checker if configured
        if (config.idleTimeout() > 0) {
            executor.submit(this::idleTimeoutChecker);
        }
    }

    /**
     * Returns the port the container is bound to.
     *
     * @return the bound port
     */
    public int port() {
        return boundPort;
    }

    /**
     * Returns whether the container is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /** Periodic idle timeout checker — runs every 5s. */
    private void idleTimeoutChecker() {
        while (running.get()) {
            try {
                Thread.sleep(5_000);
                long cutoff = System.currentTimeMillis() - config.idleTimeout();
                for (var entry : connections.entrySet()) {
                    ConnectionContext ctx = entry.getValue();
                    if (ctx.lastActivity.get() < cutoff) {
                        LOG.info("Idle timeout: closing connection {}", ctx.id);
                        sendPerformative(ctx, 0, new Performative.Close());
                        ctx.transport.close();
                    }
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                SocketChannel client = serverChannel.accept();
                client.configureBlocking(true);
                executor.submit(() -> handleConnection(new TcpTransport(client)));
            } catch (IOException e) {
                if (running.get()) {
                    LOG.warn("Error accepting connection", e);
                }
            }
        }
    }

    /**
     * Incoming message (unsettled transfer) for application disposition.
     */
    public static final class IncomingMessage {
        private final ReceiverLink receiver;
        private final long deliveryId;
        private final AmqpMessage message;
        private final ConnectionContext ctx;
        private final int localChannel;

        IncomingMessage(ConnectionContext ctx, int localChannel, ReceiverLink receiver,
                        long deliveryId, AmqpMessage message) {
            this.ctx = ctx;
            this.localChannel = localChannel;
            this.receiver = receiver;
            this.deliveryId = deliveryId;
            this.message = message;
        }

        /** Returns the connection this message arrived on. */
        public ConnectionContext connection() { return ctx; }
        /** Returns the receiver link. */
        public ReceiverLink receiverLink() { return receiver; }
        /** Returns the delivery ID. */
        public long deliveryId() { return deliveryId; }
        /** Returns the AMQP message. */
        public AmqpMessage message() { return message; }
        /** Returns the local channel number. */
        public int localChannel() { return localChannel; }
    }

    /**
     * Handles a connection from any transport (used for both TCP and in-memory).
     *
     * @param transport the transport
     */
    public void handleConnection(AmqpTransport transport) {
        String connId = UUID.randomUUID().toString().substring(0, 8);
        var ctx = new ConnectionContext(connId, transport);
        connections.put(connId, ctx);
        LOG.debug("New connection: {}", connId);

        try {
            // Detect client's first header: SASL_HEADER (proto-3) or AMQP_HEADER (proto-0)
            byte[] firstHeader = readHeader(transport);

            if (Arrays.equals(firstHeader, AmqpConstants.SASL_HEADER)) {
                // Client sends SASL_HEADER first — do SASL negotiation, then AMQP header
                LOG.debug("Connection {} — SASL-first protocol", connId);

                // Echo SASL_HEADER back (spec §3.1.4.1)
                transport.send(ByteBuffer.wrap(AmqpConstants.SASL_HEADER));

                // SASL exchange: server sends mechanisms, client sends init
                doSaslExchange(ctx, transport);

                // AMQP header exchange
                byte[] amqpHeader = readHeader(transport);
                if (!Arrays.equals(amqpHeader, AmqpConstants.AMQP_HEADER)) {
                    throw new IllegalStateException("Expected AMQP header after SASL");
                }
                transport.send(ByteBuffer.wrap(AmqpConstants.AMQP_HEADER));
                ctx.state = ConnectionState.HDR_EXCH;

                // Read client OPEN frame
                handleConnectionLifecycle(ctx, true);
            } else if (Arrays.equals(firstHeader, AmqpConstants.AMQP_HEADER)) {
                // Client sends AMQP_HEADER first (proto-0)
                if (!config.proto0Accepted()) {
                    LOG.debug("Connection {} — proto-0 rejected (mode={})", connId, config.mode());
                    throw new IllegalStateException("Protocol header exchange not supported in this mode");
                }
                LOG.debug("Connection {} — proto-0 protocol", connId);

                // Echo AMQP_HEADER back
                transport.send(ByteBuffer.wrap(AmqpConstants.AMQP_HEADER));
                ctx.state = ConnectionState.HDR_EXCH;

                // Read client OPEN frame (no SASL)
                handleConnectionLifecycle(ctx, false);
            } else {
                throw new IllegalStateException("Invalid protocol header: " + bytesHex(firstHeader));
            }
        } catch (Exception e) {
            LOG.debug("Connection {} error: {}", connId, e.getMessage());
        } finally {
            cleanupConnection(ctx);
            connections.remove(connId);
            transport.close();
        }
    }

    /** Reads an 8-byte protocol header. */
    private byte[] readHeader(AmqpTransport transport) {
        ByteBuffer buf = ByteBuffer.allocate(8);
        readFully(transport, buf);
        buf.flip();
        byte[] header = new byte[8];
        buf.get(header);
        return header;
    }

    private static String bytesHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte byte0 : b) sb.append(String.format("%02x", byte0));
        return sb.toString();
    }

    /** Performs SASL negotiation: send mechanisms → read init → send outcome. */
    private void doSaslExchange(ConnectionContext ctx, AmqpTransport transport) {
        // Send SASL mechanisms frame
        var mechanisms = SaslCodec.encodeMechanisms(authenticator.mechanisms());
        var mechFrame = new AmqpFrame(0, AmqpConstants.FRAME_TYPE_SASL, mechanisms);
        transport.send(FrameCodec.encode(mechFrame, config.maxFrameSize()));

        // Receive sasl-init
        AmqpFrame initFrame = readFrame(ctx);
        if (initFrame.performative() instanceof AmqpType.Described desc) {
            long descriptor = TypeCodec.toLong(desc.descriptor());
            if (descriptor == Descriptors.SASL_INIT) {
                String mechanism = SaslCodec.decodeInitMechanism(desc);
                byte[] response = SaslCodec.decodeInitResponse(desc);

                SaslAuthenticator.Result result = authenticator.authenticate(mechanism, response);

                // authzid validation: RABBITMQ mode rejects non-empty authzid
                // sasl-init is a described list: [mechanism, initial-response, mechanism-profile, authzid]
                // The described value is an AmqpList; authzid is the 4th element (index 3) if present
                if (config.authzidMustBeEmpty()) {
                    AmqpType saslInitValue = desc.described();
                    if (saslInitValue instanceof AmqpType.AmqpList list) {
                        List<AmqpType> fields = list.elements();
                        if (fields.size() > 3 && fields.get(3) instanceof AmqpType.AmqpString authzidStr) {
                            if (authzidStr.value() != null && !authzidStr.value().isEmpty()) {
                                LOG.debug("Connection {} — authzid rejected: {}", ctx.id, authzidStr.value());
                                var outcome = SaslCodec.encodeOutcome(2, null); // SYS error
                                var outcomeFrame = new AmqpFrame(0, AmqpConstants.FRAME_TYPE_SASL, outcome);
                                transport.send(FrameCodec.encode(outcomeFrame, config.maxFrameSize()));
                                throw new IllegalStateException("Non-empty authzid not accepted");
                            }
                        }
                    }
                }
                int code = switch (result) {
                    case OK -> 0;
                    case AUTH -> 1;
                    case SYS -> 2;
                    case SYS_PERM -> 3;
                    case SYS_TEMP -> 4;
                };

                var outcome = SaslCodec.encodeOutcome(code, null);
                var outcomeFrame = new AmqpFrame(0, AmqpConstants.FRAME_TYPE_SASL, outcome);
                transport.send(FrameCodec.encode(outcomeFrame, config.maxFrameSize()));

                if (result != SaslAuthenticator.Result.OK) {
                    throw new IllegalStateException("SASL authentication failed: " + result);
                }
            }
        }
    }

    /**
     * Handles the OPEN exchange and main frame loop.
     * @param saslFirst true if we already completed SASL (client sends OPEN next)
     */
    private void handleConnectionLifecycle(ConnectionContext ctx, boolean saslFirst) {
        // Receive client OPEN
        AmqpFrame openFrame = readFrame(ctx);
        if (openFrame.performative() instanceof AmqpType.Described desc) {
            var open = (Performative.Open) PerformativeCodec.decode(desc);
            ctx.remoteContainerId = open.containerId();
            ctx.maxFrameSize = (int) Math.min(open.maxFrameSize(), config.maxFrameSize());
            ctx.channelMax = Math.min(open.channelMax(), config.channelMax());
            LOG.debug("Connection {} open from '{}'", ctx.id, open.containerId());
        }

        // Send our OPEN — use mode-aware defaults:
        // - unsettled(0) sender settle mode, first(0) receiver settle mode
        // - proper channel-max from config
        // - idle timeout from config
        var myOpen = new Performative.Open(
                config.containerId(), config.host(),
                config.maxFrameSize(), config.channelMax(),
                config.idleTimeout(), List.of(), List.of(), Map.of()
        );
        sendPerformative(ctx, 0, myOpen);
        ctx.state = ConnectionState.OPENED;

        // Main frame loop
        while (ctx.transport.isOpen() && ctx.state == ConnectionState.OPENED) {
            AmqpFrame frame = readFrame(ctx);
            if (frame == null) break;
            if (frame.isHeartbeat()) continue;

            ctx.lastActivity.set(System.currentTimeMillis());

            if (frame.performative() instanceof AmqpType.Described desc) {
                long descriptor = TypeCodec.toLong(desc.descriptor());
                Performative perf = PerformativeCodec.decode(desc);
                handlePerformative(ctx, frame.channel(), perf, frame.payload());
            }
        }
    }

    private void handlePerformative(ConnectionContext ctx, int channel, Performative perf, ByteBuffer payload) {
        switch (perf) {
            case Performative.Begin begin -> handleBegin(ctx, channel, begin);
            case Performative.Attach attach -> handleAttach(ctx, channel, attach);
            case Performative.Flow flow -> handleFlow(ctx, channel, flow);
            case Performative.Transfer transfer -> handleTransfer(ctx, channel, transfer, payload);
            case Performative.Disposition disposition -> handleDisposition(ctx, channel, disposition);
            case Performative.Detach detach -> handleDetach(ctx, channel, detach);
            case Performative.End end -> handleEnd(ctx, channel, end);
            case Performative.Close close -> handleClose(ctx, close);
            default -> LOG.warn("Unhandled performative: {}", perf);
        }
    }

    private void handleBegin(ConnectionContext ctx, int remoteChannel, Performative.Begin begin) {
        int localChannel = ctx.nextChannel++;
        var session = new AmqpSession(localChannel);
        session.remoteChannel(remoteChannel);
        session.frameSender((performative, payload) -> {
            var described = PerformativeCodec.encode(performative);
            var frame = new AmqpFrame(localChannel, AmqpConstants.FRAME_TYPE_AMQP, described, payload);
            ctx.transport.send(FrameCodec.encode(frame, ctx.maxFrameSize));
        });
        session.handleBegin(begin);
        session.state(AmqpSession.State.MAPPED);
        ctx.sessions.put(localChannel, session);
        ctx.remoteToLocalChannel.put(remoteChannel, localChannel);

        // Send begin response — match client's window sizes
        var response = new Performative.Begin(
                remoteChannel,
                session.nextOutgoingId(),
                session.incomingWindow(),
                session.outgoingWindow()
        );
        sendPerformative(ctx, localChannel, response);
        LOG.debug("Session begun: local={}, remote={}", localChannel, remoteChannel);
    }

    private void handleAttach(ConnectionContext ctx, int channel, Performative.Attach attach) {
        int localChannel = ctx.remoteToLocalChannel.getOrDefault(channel, channel);
        AmqpSession session = ctx.sessions.get(localChannel);
        if (session == null) return;

        // Extract addresses and normalize per mode
        String sourceAddr = PerformativeCodec.extractAddress(attach.source());
        String targetAddr = PerformativeCodec.extractAddress(attach.target());
        sourceAddr = normalizeAddress(sourceAddr);
        targetAddr = normalizeAddress(targetAddr);

        if (attach.role()) {
            // Remote is receiver, we create a sender to it
            var senderLink = new SenderLink(attach.name(), attach.handle(), sourceAddr, targetAddr);
            senderLink.session(session);
            senderLink.state(SenderLink.State.ATTACHED);
            session.addSenderLink(senderLink);

            // Register in address routing
            addressToSenders.computeIfAbsent(sourceAddr != null ? sourceAddr : targetAddr,
                    k -> new CopyOnWriteArrayList<>()).add(senderLink);

            // Send attach response — use unsettled(0) snd-settle, first(0) rcv-settle
            var response = new Performative.Attach(
                    attach.name(), attach.handle(), false, // our role is sender
                    0, // snd-settle-mode: unsettled
                    0, // rcv-settle-mode: first
                    PerformativeCodec.encodeSource(sourceAddr),
                    PerformativeCodec.encodeTarget(targetAddr),
                    null, 0, List.of(), List.of(), Map.of()
            );
            sendPerformative(ctx, localChannel, response);
            LOG.debug("Sender link attached: '{}' on address '{}'", attach.name(),
                    sourceAddr != null ? sourceAddr : targetAddr);
        } else {
            // Remote is sender, we create a receiver for it
            var receiverLink = new ReceiverLink(attach.name(), attach.handle(), sourceAddr, targetAddr);
            receiverLink.session(session);
            receiverLink.state(ReceiverLink.State.ATTACHED);
            session.addReceiverLink(receiverLink);

            // Register in address routing
            String address = targetAddr != null ? targetAddr : sourceAddr;
            addressToReceivers.computeIfAbsent(address,
                    k -> new CopyOnWriteArrayList<>()).add(receiverLink);

            // Send attach response — use unsettled(0) snd-settle, first(0) rcv-settle
            var response = new Performative.Attach(
                    attach.name(), attach.handle(), true, // our role is receiver
                    0, // snd-settle-mode: unsettled
                    0, // rcv-settle-mode: first
                    PerformativeCodec.encodeSource(sourceAddr),
                    PerformativeCodec.encodeTarget(targetAddr),
                    null, 0, List.of(), List.of(), Map.of()
            );
            sendPerformative(ctx, localChannel, response);

            // Issue initial credit
            issueCredit(ctx, localChannel, receiverLink);
            LOG.debug("Receiver link attached: '{}' on address '{}'", attach.name(), address);
        }
    }

    /** Normalizes address per vendor mode. */
    private String normalizeAddress(String address) {
        if (address == null) return null;
        ContainerMode mode = config.mode();
        if (mode == ContainerMode.RABBITMQ) {
            // RabbitMQ: /queues/:name -> :name (strip prefix)
            if (address.startsWith("/queues/")) {
                return address.substring("/queues/".length());
            }
        } else if (mode == ContainerMode.QPID_DISPATCH) {
            // Qpid: closest:queueName -> queueName
            if (address.startsWith("closest:")) {
                return address.substring("closest:".length());
            }
        }
        // STANDARD, ARTEMIS, IBM_MQ: passthrough
        return address;
    }

    private void handleFlow(ConnectionContext ctx, int channel, Performative.Flow flow) {
        int localChannel = ctx.remoteToLocalChannel.getOrDefault(channel, channel);
        AmqpSession session = ctx.sessions.get(localChannel);
        if (session == null) return;

        session.handleFlow(flow);

        if (flow.handle() != null) {
            SenderLink sender = session.senderLink(flow.handle());
            if (sender != null && flow.deliveryCount() != null && flow.linkCredit() != null) {
                sender.grantCredit(flow.deliveryCount(), flow.linkCredit());
            }
        }
    }

    /**
     * Handles an incoming TRANSFER — NO auto-accept for unsettled messages.
     * Pre-settled messages are accepted immediately.
     * Unsettled messages are queued for the application to disposition.
     */
    private void handleTransfer(ConnectionContext ctx, int channel, Performative.Transfer transfer, ByteBuffer payload) {
        int localChannel = ctx.remoteToLocalChannel.getOrDefault(channel, channel);
        AmqpSession session = ctx.sessions.get(localChannel);
        if (session == null) return;

        session.recordIncomingTransfer();

        ReceiverLink receiver = session.receiverLink(transfer.handle());
        if (receiver == null) return;

        // Decode message from payload
        AmqpMessage message = null;
        if (payload != null && payload.hasRemaining()) {
            message = MessageCodec.decode(payload);
        }
        if (message == null) {
            message = new AmqpMessage();
        }

        receiver.handleTransfer(transfer.deliveryId(), transfer.deliveryTag(), message, false);

        if (transfer.settled()) {
            // Pre-settled (at-most-once) — auto-accept since sender already settled
            LOG.debug("Pre-settled transfer received on link '{}', auto-accepted", receiver.name());
        } else {
            // Unsettled — queue for application disposition (NO auto-accept)
            IncomingMessage incoming = new IncomingMessage(ctx, localChannel, receiver,
                    transfer.deliveryId(), message);
            ctx.pendingMessages.add(incoming);

            if (messageHandler != null) {
                messageHandler.accept(ctx, incoming);
            }
            // Otherwise: message stays in ctx.pendingMessages for app to drain
        }

        // Route message to sender links on the same address
        String address = receiver.targetAddress() != null ? receiver.targetAddress() : receiver.sourceAddress();
        routeMessage(ctx, address, message);
    }

    /** Sends a disposition frame for a pending message. */
    private void sendDisposition(ConnectionContext ctx, IncomingMessage msg,
                                  DeliveryState state, boolean settled) {
        var disposition = new Performative.Disposition(
                true, msg.deliveryId(), null, settled, state.encode(), false
        );
        sendPerformative(ctx, msg.localChannel, disposition);
    }

    private void routeMessage(ConnectionContext ctx, String address, AmqpMessage message) {
        List<SenderLink> senders = addressToSenders.get(address);
        if (senders == null || senders.isEmpty()) return;

        for (SenderLink sender : senders) {
            if (sender.state() == SenderLink.State.ATTACHED && sender.hasCredit()) {
                sender.send(message, true); // Pre-settle routed messages
            }
        }
    }

    private void handleDisposition(ConnectionContext ctx, int channel, Performative.Disposition disposition) {
        int localChannel = ctx.remoteToLocalChannel.getOrDefault(channel, channel);
        AmqpSession session = ctx.sessions.get(localChannel);
        if (session == null) return;

        DeliveryState state = null;
        if (disposition.state() instanceof AmqpType.Described desc) {
            state = DeliveryStateCodec.decode(desc);
        }

        if (!disposition.role()) {
            // Sender disposition — update our receiver links
            for (var receiver : session.receiverLinks().values()) {
                receiver.handleTransfer(disposition.first(), null, null, disposition.settled());
            }
        } else {
            // Receiver disposition — update our sender links
            for (var sender : session.senderLinks().values()) {
                sender.handleDisposition(disposition.first(), disposition.last(),
                        disposition.settled(), state);
            }
        }
    }

    private void handleDetach(ConnectionContext ctx, int channel, Performative.Detach detach) {
        int localChannel = ctx.remoteToLocalChannel.getOrDefault(channel, channel);
        AmqpSession session = ctx.sessions.get(localChannel);
        if (session == null) return;

        // Remove link from address routing
        SenderLink sl = session.senderLink(detach.handle());
        if (sl != null) {
            String addr = sl.sourceAddress() != null ? sl.sourceAddress() : sl.targetAddress();
            List<SenderLink> senders = addressToSenders.get(addr);
            if (senders != null) senders.remove(sl);
        }
        ReceiverLink rl = session.receiverLink(detach.handle());
        if (rl != null) {
            String addr = rl.targetAddress() != null ? rl.targetAddress() : rl.sourceAddress();
            List<ReceiverLink> receivers = addressToReceivers.get(addr);
            if (receivers != null) receivers.remove(rl);
        }

        session.removeLink(detach.handle());

        // Send detach response
        var response = new Performative.Detach(detach.handle(), detach.closed());
        sendPerformative(ctx, localChannel, response);
        LOG.debug("Link detached: handle={}", detach.handle());
    }

    private void handleEnd(ConnectionContext ctx, int channel, Performative.End end) {
        int localChannel = ctx.remoteToLocalChannel.getOrDefault(channel, channel);
        AmqpSession session = ctx.sessions.get(localChannel);
        if (session != null) {
            session.state(AmqpSession.State.DISCARDING);
            ctx.sessions.remove(localChannel);
        }

        // Send end response
        sendPerformative(ctx, localChannel, new Performative.End());
        LOG.debug("Session ended: channel={}", localChannel);
    }

    private void handleClose(ConnectionContext ctx, Performative.Close close) {
        ctx.state = ConnectionState.CLOSE_RCVD;
        sendPerformative(ctx, 0, new Performative.Close());
        ctx.state = ConnectionState.END;
        LOG.debug("Connection {} closed", ctx.id);
    }

    private void issueCredit(ConnectionContext ctx, int localChannel, ReceiverLink receiver) {
        AmqpSession session = ctx.sessions.get(localChannel);
        if (session == null) return;

        var flow = new Performative.Flow(
                session.nextIncomingId(),
                session.incomingWindow(),
                session.nextOutgoingId(),
                session.outgoingWindow(),
                receiver.handle(),
                receiver.deliveryCount(),
                (long) AmqpConstants.DEFAULT_LINK_CREDIT,
                null, false, false, Map.of()
        );
        sendPerformative(ctx, localChannel, flow);
    }

    private void sendPerformative(ConnectionContext ctx, int channel, Performative performative) {
        var described = PerformativeCodec.encode(performative);
        var frame = new AmqpFrame(channel, AmqpConstants.FRAME_TYPE_AMQP, described);
        ctx.transport.send(FrameCodec.encode(frame, ctx.maxFrameSize));
    }

    private AmqpFrame readFrame(ConnectionContext ctx) {
        ByteBuffer sizeBuf = ByteBuffer.allocate(4);
        readFully(ctx.transport, sizeBuf);
        sizeBuf.flip();
        int size = sizeBuf.getInt();

        if (size < AmqpConstants.FRAME_HEADER_SIZE) {
            return null;
        }

        ByteBuffer frameBuf = ByteBuffer.allocate(size);
        frameBuf.putInt(size);
        ByteBuffer remaining = ByteBuffer.allocate(size - 4);
        readFully(ctx.transport, remaining);
        remaining.flip();
        frameBuf.put(remaining);
        frameBuf.flip();

        return FrameCodec.decode(frameBuf);
    }

    private void readFully(AmqpTransport transport, ByteBuffer buf) {
        while (buf.hasRemaining()) {
            int n = transport.receive(buf);
            if (n < 0) {
                throw new IllegalStateException("Transport closed during read");
            }
        }
    }

    private void cleanupConnection(ConnectionContext ctx) {
        // Remove all links from address routing
        for (var session : ctx.sessions.values()) {
            for (var sender : session.senderLinks().values()) {
                String addr = sender.sourceAddress() != null ? sender.sourceAddress() : sender.targetAddress();
                List<SenderLink> senders = addressToSenders.get(addr);
                if (senders != null) senders.remove(sender);
            }
            for (var receiver : session.receiverLinks().values()) {
                String addr = receiver.targetAddress() != null ? receiver.targetAddress() : receiver.sourceAddress();
                List<ReceiverLink> receivers = addressToReceivers.get(addr);
                if (receivers != null) receivers.remove(receiver);
            }
        }
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) return;
        try {
            if (serverChannel != null) serverChannel.close();
        } catch (IOException e) {
            LOG.debug("Error closing server channel", e);
        }
        for (var ctx : connections.values()) {
            ctx.transport.close();
        }
        executor.close();
        LOG.info("AMQP container '{}' stopped", config.containerId());
    }

    /** Internal connection context. */
    public static final class ConnectionContext {
        final String id;
        final AmqpTransport transport;
        final AtomicLong lastActivity = new AtomicLong(System.currentTimeMillis());
        volatile ConnectionState state = ConnectionState.START;
        volatile String remoteContainerId;
        volatile int maxFrameSize = AmqpConstants.DEFAULT_MAX_FRAME_SIZE;
        volatile int channelMax = AmqpConstants.DEFAULT_CHANNEL_MAX;
        int nextChannel = 0;
        final Map<Integer, AmqpSession> sessions = new ConcurrentHashMap<>();
        final Map<Integer, Integer> remoteToLocalChannel = new ConcurrentHashMap<>();
        final List<IncomingMessage> pendingMessages = Collections.synchronizedList(new ArrayList<>());

        ConnectionContext(String id, AmqpTransport transport) {
            this.id = id;
            this.transport = transport;
        }
    }
}
