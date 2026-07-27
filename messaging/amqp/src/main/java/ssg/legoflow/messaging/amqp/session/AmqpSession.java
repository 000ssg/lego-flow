package ssg.legoflow.messaging.amqp.session;

import ssg.legoflow.messaging.amqp.common.AmqpConstants;
import ssg.legoflow.messaging.amqp.link.ReceiverLink;
import ssg.legoflow.messaging.amqp.link.SenderLink;
import ssg.legoflow.messaging.amqp.transport.Performative;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents an AMQP 1.0 session multiplexed over a connection.
 *
 * <p>A session manages flow control via incoming/outgoing windows and tracks
 * transfer-ids. Multiple links can be attached to a session, each identified
 * by a handle number.
 *
 * <p>Session-level flow control uses four counters:
 * <ul>
 *   <li>next-incoming-id: expected next incoming transfer-id</li>
 *   <li>incoming-window: how many more transfers we can accept</li>
 *   <li>next-outgoing-id: next transfer-id we will use</li>
 *   <li>outgoing-window: how many more transfers we can send</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class AmqpSession {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpSession.class);

    /**
     * Session lifecycle states.
     */
    public enum State {
        /** Session not yet begun. */
        UNMAPPED,
        /** Begin sent, waiting for response. */
        BEGIN_SENT,
        /** Begin received, own begin not sent. */
        BEGIN_RCVD,
        /** Both peers have exchanged begin — session active. */
        MAPPED,
        /** End sent, waiting for response. */
        END_SENT,
        /** End received, own end not sent. */
        END_RCVD,
        /** Session fully ended. */
        DISCARDING
    }

    private final int localChannel;
    private volatile int remoteChannel = -1;
    private volatile State state = State.UNMAPPED;

    // Flow control
    private final AtomicLong nextIncomingId = new AtomicLong(0);
    private final AtomicLong incomingWindow = new AtomicLong(AmqpConstants.DEFAULT_INCOMING_WINDOW);
    private final AtomicLong nextOutgoingId = new AtomicLong(0);
    private final AtomicLong outgoingWindow = new AtomicLong(AmqpConstants.DEFAULT_OUTGOING_WINDOW);
    private volatile long remoteIncomingWindow;
    private volatile long remoteOutgoingWindow;
    private volatile long handleMax = 0xFFFFFFFFL;

    // Links by handle
    private final Map<Long, SenderLink> senderLinks = new ConcurrentHashMap<>();
    private final Map<Long, ReceiverLink> receiverLinks = new ConcurrentHashMap<>();
    private final Map<String, Long> linkNameToHandle = new ConcurrentHashMap<>();
    private final AtomicLong nextHandle = new AtomicLong(0);

    // Callback for sending frames
    private volatile SessionFrameSender frameSender;

    /**
     * Callback interface for sending session-level frames.
     */
    @FunctionalInterface
    public interface SessionFrameSender {
        /**
         * Sends a performative on the session's channel.
         *
         * @param performative the performative to send
         * @param payload      optional payload (for transfer), or null
         */
        void send(Performative performative, java.nio.ByteBuffer payload);
    }

    /**
     * Creates a new session on the given local channel.
     *
     * @param localChannel the local channel number
     */
    public AmqpSession(int localChannel) {
        this.localChannel = localChannel;
    }

    /** Returns the local channel number. */
    public int localChannel() { return localChannel; }

    /** Returns the remote channel number. */
    public int remoteChannel() { return remoteChannel; }

    /** Returns the session state. */
    public State state() { return state; }

    /** Sets the session state. */
    public void state(State state) { this.state = state; }

    /** Sets the remote channel. */
    public void remoteChannel(int remoteChannel) { this.remoteChannel = remoteChannel; }

    /** Sets the frame sender callback. */
    public void frameSender(SessionFrameSender frameSender) { this.frameSender = frameSender; }

    /** Returns the next outgoing transfer-id. */
    public long nextOutgoingId() { return nextOutgoingId.get(); }

    /** Returns the incoming window. */
    public long incomingWindow() { return incomingWindow.get(); }

    /** Returns the outgoing window. */
    public long outgoingWindow() { return outgoingWindow.get(); }

    /** Returns the next incoming id. */
    public long nextIncomingId() { return nextIncomingId.get(); }

    /** Returns the remote incoming window. */
    public long remoteIncomingWindow() { return remoteIncomingWindow; }

    /** Returns the handle max. */
    public long handleMax() { return handleMax; }

    /** Sets the handle max. */
    public void handleMax(long handleMax) { this.handleMax = handleMax; }

    /**
     * Creates the begin performative for initiating this session.
     *
     * @return the begin performative
     */
    public Performative.Begin createBegin() {
        return new Performative.Begin(
                remoteChannel >= 0 ? remoteChannel : null,
                nextOutgoingId.get(),
                incomingWindow.get(),
                outgoingWindow.get()
        );
    }

    /**
     * Processes a received begin performative.
     *
     * @param begin the received begin
     */
    public void handleBegin(Performative.Begin begin) {
        if (begin.remoteChannel() != null) {
            this.remoteChannel = begin.remoteChannel();
        }
        this.nextIncomingId.set(begin.nextOutgoingId());
        this.remoteIncomingWindow = begin.incomingWindow();
        this.remoteOutgoingWindow = begin.outgoingWindow();
        this.handleMax = Math.min(this.handleMax, begin.handleMax());
        LOG.debug("Session {} begun: remoteChannel={}, remoteIncomingWindow={}, remoteOutgoingWindow={}",
                localChannel, remoteChannel, remoteIncomingWindow, remoteOutgoingWindow);
    }

    /**
     * Processes a received flow performative (session-level).
     *
     * @param flow the received flow
     */
    public void handleFlow(Performative.Flow flow) {
        if (flow.nextIncomingId() != null) {
            this.remoteIncomingWindow = flow.incomingWindow() -
                    (nextOutgoingId.get() - flow.nextIncomingId());
        }
        this.remoteOutgoingWindow = flow.outgoingWindow();
    }

    /**
     * Allocates the next outgoing delivery-id, consuming one unit of outgoing window.
     *
     * @return the delivery-id, or -1 if the window is exhausted
     */
    public long allocateDeliveryId() {
        if (remoteIncomingWindow <= 0) {
            return -1;
        }
        remoteIncomingWindow--;
        return nextOutgoingId.getAndIncrement();
    }

    /**
     * Records reception of an incoming transfer, consuming one unit of incoming window.
     */
    public void recordIncomingTransfer() {
        nextIncomingId.incrementAndGet();
        long window = incomingWindow.decrementAndGet();
        if (window <= AmqpConstants.DEFAULT_INCOMING_WINDOW / 4) {
            // Replenish window
            incomingWindow.set(AmqpConstants.DEFAULT_INCOMING_WINDOW);
        }
    }

    // ---- Link management ----

    /**
     * Allocates the next available handle.
     *
     * @return the handle number
     */
    public long allocateHandle() {
        return nextHandle.getAndIncrement();
    }

    /**
     * Registers a sender link on this session.
     *
     * @param link the sender link
     */
    public void addSenderLink(SenderLink link) {
        senderLinks.put(link.handle(), link);
        linkNameToHandle.put(link.name(), link.handle());
    }

    /**
     * Registers a receiver link on this session.
     *
     * @param link the receiver link
     */
    public void addReceiverLink(ReceiverLink link) {
        receiverLinks.put(link.handle(), link);
        linkNameToHandle.put(link.name(), link.handle());
    }

    /**
     * Returns the sender link for the given handle, or null.
     *
     * @param handle the link handle
     * @return the sender link, or null
     */
    public SenderLink senderLink(long handle) {
        return senderLinks.get(handle);
    }

    /**
     * Returns the receiver link for the given handle, or null.
     *
     * @param handle the link handle
     * @return the receiver link, or null
     */
    public ReceiverLink receiverLink(long handle) {
        return receiverLinks.get(handle);
    }

    /**
     * Returns the handle for the given link name, or null.
     *
     * @param name the link name
     * @return the handle, or null
     */
    public Long handleForName(String name) {
        return linkNameToHandle.get(name);
    }

    /**
     * Removes a link by handle.
     *
     * @param handle the handle to remove
     */
    public void removeLink(long handle) {
        SenderLink sl = senderLinks.remove(handle);
        if (sl != null) linkNameToHandle.remove(sl.name());
        ReceiverLink rl = receiverLinks.remove(handle);
        if (rl != null) linkNameToHandle.remove(rl.name());
    }

    /** Returns all sender links. */
    public Map<Long, SenderLink> senderLinks() { return senderLinks; }

    /** Returns all receiver links. */
    public Map<Long, ReceiverLink> receiverLinks() { return receiverLinks; }

    /**
     * Sends a performative on this session's channel.
     *
     * @param performative the performative
     */
    public void send(Performative performative) {
        if (frameSender != null) {
            frameSender.send(performative, null);
        }
    }

    /**
     * Sends a performative with payload on this session's channel.
     *
     * @param performative the performative
     * @param payload      the payload
     */
    public void send(Performative performative, java.nio.ByteBuffer payload) {
        if (frameSender != null) {
            frameSender.send(performative, payload);
        }
    }
}
