package ssg.legoflow.messaging.amqp.container;

import ssg.legoflow.messaging.amqp.common.AmqpConstants;
import ssg.legoflow.messaging.amqp.common.AmqpError;
import ssg.legoflow.messaging.amqp.common.ConnectionState;
import ssg.legoflow.messaging.amqp.delivery.Delivery;
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
import ssg.legoflow.messaging.amqp.types.Descriptors;
import ssg.legoflow.messaging.amqp.types.TypeCodec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AMQP 1.0 container (server) that accepts connections and routes messages.
 *
 * <p>The container manages connections, sessions, and links. When a message
 * is received on a receiver link, it is routed to all sender links attached
 * to the same address, implementing basic message routing.
 *
 * <p>Uses virtual threads for connection handling, enabling high concurrency
 * without thread pool tuning.
 *
 * @since 0.1.0
 */
public final class AmqpContainer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpContainer.class);

    private final ContainerConfig config;
    private final SaslAuthenticator authenticator;
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
     * Starts the container and begins accepting connections.
     *
     * @throws IOException if the container cannot bind
     */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;

        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(config.host(), config.port()));
        boundPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();

        LOG.info("AMQP container '{}' listening on {}:{}", config.containerId(), config.host(), boundPort);

        executor.submit(this::acceptLoop);
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
            // Protocol header negotiation
            if (config.requireSasl()) {
                handleSaslNegotiation(ctx);
            }
            handleProtocolHeader(ctx);
            handleConnectionLifecycle(ctx);
        } catch (Exception e) {
            LOG.debug("Connection {} error: {}", connId, e.getMessage());
        } finally {
            cleanupConnection(ctx);
            connections.remove(connId);
            transport.close();
        }
    }

    private void handleSaslNegotiation(ConnectionContext ctx) {
        // Receive SASL header
        ByteBuffer headerBuf = ByteBuffer.allocate(8);
        readFully(ctx.transport, headerBuf);
        headerBuf.flip();

        // Verify SASL header
        byte[] header = new byte[8];
        headerBuf.get(header);
        if (!Arrays.equals(header, AmqpConstants.SASL_HEADER)) {
            throw new IllegalStateException("Invalid SASL header");
        }

        // Send SASL header back
        ctx.transport.send(ByteBuffer.wrap(AmqpConstants.SASL_HEADER));

        // Send mechanisms
        var mechanisms = SaslCodec.encodeMechanisms(authenticator.mechanisms());
        var mechFrame = new AmqpFrame(0, AmqpConstants.FRAME_TYPE_SASL, mechanisms);
        ctx.transport.send(FrameCodec.encode(mechFrame, config.maxFrameSize()));

        // Receive sasl-init
        AmqpFrame initFrame = readFrame(ctx);
        if (initFrame.performative() instanceof AmqpType.Described desc) {
            long descriptor = TypeCodec.toLong(desc.descriptor());
            if (descriptor == Descriptors.SASL_INIT) {
                String mechanism = SaslCodec.decodeInitMechanism(desc);
                byte[] response = SaslCodec.decodeInitResponse(desc);

                SaslAuthenticator.Result result = authenticator.authenticate(mechanism, response);
                int code = switch (result) {
                    case OK -> 0;
                    case AUTH -> 1;
                    case SYS -> 2;
                    case SYS_PERM -> 3;
                    case SYS_TEMP -> 4;
                };

                var outcome = SaslCodec.encodeOutcome(code, null);
                var outcomeFrame = new AmqpFrame(0, AmqpConstants.FRAME_TYPE_SASL, outcome);
                ctx.transport.send(FrameCodec.encode(outcomeFrame, config.maxFrameSize()));

                if (result != SaslAuthenticator.Result.OK) {
                    throw new IllegalStateException("SASL authentication failed: " + result);
                }
            }
        }
    }

    private void handleProtocolHeader(ConnectionContext ctx) {
        // Receive AMQP header
        ByteBuffer headerBuf = ByteBuffer.allocate(8);
        readFully(ctx.transport, headerBuf);
        headerBuf.flip();

        byte[] header = new byte[8];
        headerBuf.get(header);
        if (!Arrays.equals(header, AmqpConstants.AMQP_HEADER)) {
            throw new IllegalStateException("Invalid AMQP header");
        }

        // Send AMQP header back
        ctx.transport.send(ByteBuffer.wrap(AmqpConstants.AMQP_HEADER));
        ctx.state = ConnectionState.HDR_EXCH;
    }

    private void handleConnectionLifecycle(ConnectionContext ctx) {
        // Receive open
        AmqpFrame openFrame = readFrame(ctx);
        if (openFrame.performative() instanceof AmqpType.Described desc) {
            var open = (Performative.Open) PerformativeCodec.decode(desc);
            ctx.remoteContainerId = open.containerId();
            ctx.maxFrameSize = (int) Math.min(open.maxFrameSize(), config.maxFrameSize());
            ctx.channelMax = Math.min(open.channelMax(), config.channelMax());
            LOG.debug("Connection {} open from '{}'", ctx.id, open.containerId());
        }

        // Send open
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

        // Send begin response
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

        String sourceAddr = PerformativeCodec.extractAddress(attach.source());
        String targetAddr = PerformativeCodec.extractAddress(attach.target());

        if (attach.role()) {
            // Remote is receiver, we create a sender to it
            var senderLink = new SenderLink(attach.name(), attach.handle(), sourceAddr, targetAddr);
            senderLink.session(session);
            senderLink.state(SenderLink.State.ATTACHED);
            session.addSenderLink(senderLink);

            // Register in address routing
            addressToSenders.computeIfAbsent(sourceAddr != null ? sourceAddr : targetAddr,
                    k -> new CopyOnWriteArrayList<>()).add(senderLink);

            // Send attach response
            var response = new Performative.Attach(
                    attach.name(), attach.handle(), false, // our role is sender
                    PerformativeCodec.encodeSource(sourceAddr),
                    PerformativeCodec.encodeTarget(targetAddr)
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

            // Send attach response
            var response = new Performative.Attach(
                    attach.name(), attach.handle(), true, // our role is receiver
                    PerformativeCodec.encodeSource(sourceAddr),
                    PerformativeCodec.encodeTarget(targetAddr)
            );
            sendPerformative(ctx, localChannel, response);

            // Issue initial credit
            issueCredit(ctx, localChannel, receiverLink);
            LOG.debug("Receiver link attached: '{}' on address '{}'", attach.name(), address);
        }
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

        // If pre-settled, auto-accept
        if (transfer.settled()) {
            receiver.handleTransfer(transfer.deliveryId(), transfer.deliveryTag(), message, true);
        } else {
            receiver.handleTransfer(transfer.deliveryId(), transfer.deliveryTag(), message, false);
            // Auto-accept and settle for the receiver side
            if (transfer.deliveryId() != null) {
                var disposition = new Performative.Disposition(
                        true, transfer.deliveryId(), null, true,
                        new DeliveryState.Accepted().encode(), false
                );
                sendPerformative(ctx, localChannel, disposition);
            }
        }

        // Route message to sender links on the same address
        String address = receiver.targetAddress() != null ? receiver.targetAddress() : receiver.sourceAddress();
        routeMessage(ctx, address, message);
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
    private static final class ConnectionContext {
        final String id;
        final AmqpTransport transport;
        volatile ConnectionState state = ConnectionState.START;
        volatile String remoteContainerId;
        volatile int maxFrameSize = AmqpConstants.DEFAULT_MAX_FRAME_SIZE;
        volatile int channelMax = AmqpConstants.DEFAULT_CHANNEL_MAX;
        int nextChannel = 0;
        final Map<Integer, AmqpSession> sessions = new ConcurrentHashMap<>();
        final Map<Integer, Integer> remoteToLocalChannel = new ConcurrentHashMap<>();

        ConnectionContext(String id, AmqpTransport transport) {
            this.id = id;
            this.transport = transport;
        }
    }
}
