package ssg.legoflow.xmpp.sm;

import ssg.legoflow.xmpp.core.Stanza;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
/**
 * Implements XEP-0198 Stream Management for XMPP.
 *
 * <p>Provides stanza counting, acknowledgement requests ({@code <r/>}) and
 * responses ({@code <a/>}), an unacknowledged stanza queue, and session
 * resumption after disconnect.
 *
 * <p>The outbound counter tracks stanzas sent by this entity. The inbound counter
 * tracks stanzas received from the peer. When the peer sends {@code <r/>}, we
 * respond with {@code <a h='N'/>} where N is our inbound count. When we receive
 * {@code <a h='N'/>}, we remove stanzas up to N from the unacknowledged queue.
 *
 * @since 0.1.0
 */
public class StreamManagement {

    private static final Logger LOG = LoggerFactory.getLogger(StreamManagement.class);

    /** The XEP-0198 namespace. */
    public static final String NAMESPACE = "urn:xmpp:sm:3";

    private final AtomicLong outboundCount = new AtomicLong(0);
    private final AtomicLong inboundCount = new AtomicLong(0);
    private final AtomicLong lastAckedOutbound = new AtomicLong(0);
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicBoolean resumable = new AtomicBoolean(false);

    private final Deque<Stanza> unackedQueue = new ArrayDeque<>();
    private String sessionId;
    private String prevSessionId;

    /**
     * Creates a new Stream Management instance.
     */
    public StreamManagement() {
    }

    /**
     * Enables stream management.
     *
     * <p>Generates the {@code <enable>} element and resets counters.
     *
     * @param resume whether to request session resumption capability
     * @return the enable XML element
     */
    public String enable(boolean resume) {
        outboundCount.set(0);
        inboundCount.set(0);
        lastAckedOutbound.set(0);
        synchronized (unackedQueue) {
            unackedQueue.clear();
        }
        enabled.set(true);
        resumable.set(resume);
        LOG.info("Stream management enabled (resume={})", resume);
        return "<enable xmlns=\"" + NAMESPACE + "\"" +
                (resume ? " resume=\"true\"" : "") + "/>";
    }

    /**
     * Handles the server's {@code <enabled>} response.
     *
     * @param smSessionId the session id assigned by the server (may be null)
     * @param resume      whether resumption was granted
     */
    public void handleEnabled(String smSessionId, boolean resume) {
        this.sessionId = smSessionId;
        this.resumable.set(resume);
        LOG.info("Stream management enabled by server: id={}, resume={}", smSessionId, resume);
    }

    /**
     * Records an outbound stanza (sent by us).
     *
     * <p>Increments the outbound counter and adds the stanza to the
     * unacknowledged queue.
     *
     * @param stanza the sent stanza
     */
    public void trackOutbound(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza must not be null");
        if (!enabled.get()) {
            return;
        }
        outboundCount.incrementAndGet();
        synchronized (unackedQueue) {
            unackedQueue.addLast(stanza);
        }
        LOG.debug("Tracked outbound stanza: id={}, outboundCount={}", stanza.id(), outboundCount.get());
    }

    /**
     * Records an inbound stanza (received from the peer).
     *
     * <p>Increments the inbound counter.
     *
     * @param stanza the received stanza
     */
    public void trackInbound(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza must not be null");
        if (!enabled.get()) {
            return;
        }
        inboundCount.incrementAndGet();
        LOG.debug("Tracked inbound stanza: id={}, inboundCount={}", stanza.id(), inboundCount.get());
    }

    /**
     * Generates an ack request ({@code <r/>}) element.
     *
     * <p>Sent to the peer to request acknowledgement of received stanzas.
     *
     * @return the {@code <r/>} XML element
     */
    public String requestAck() {
        return "<r xmlns=\"" + NAMESPACE + "\"/>";
    }

    /**
     * Generates an ack response ({@code <a/>}) element with the current
     * inbound count.
     *
     * <p>Sent in response to the peer's {@code <r/>} request.
     *
     * @return the {@code <a/>} XML element
     */
    public String generateAck() {
        long h = inboundCount.get();
        LOG.debug("Generating ack: h={}", h);
        return "<a xmlns=\"" + NAMESPACE + "\" h=\"" + h + "\"/>";
    }

    /**
     * Processes an ack ({@code <a h='N'/>}) from the peer.
     *
     * <p>Removes acknowledged stanzas from the unacknowledged queue.
     *
     * @param h the peer's acknowledged count
     * @return the number of stanzas removed from the queue
     */
    public int processAck(long h) {
        long previouslyAcked = lastAckedOutbound.get();
        if (h < previouslyAcked) {
            LOG.warn("Received ack h={} less than previous h={}", h, previouslyAcked);
            return 0;
        }
        long newlyAcked = h - previouslyAcked;
        lastAckedOutbound.set(h);

        int removed = 0;
        synchronized (unackedQueue) {
            for (long i = 0; i < newlyAcked && !unackedQueue.isEmpty(); i++) {
                unackedQueue.removeFirst();
                removed++;
            }
        }
        LOG.debug("Processed ack h={}: removed {} stanzas, {} unacked remaining",
                h, removed, getUnackedCount());
        return removed;
    }

    /**
     * Initiates session resumption.
     *
     * @return the {@code <resume>} XML element, or null if resumption is not available
     */
    public String resume() {
        if (sessionId == null || !resumable.get()) {
            LOG.warn("Cannot resume: no session id or resumption not enabled");
            return null;
        }
        prevSessionId = sessionId;
        long h = inboundCount.get();
        LOG.info("Attempting session resumption: prevId={}, h={}", prevSessionId, h);
        return "<resume xmlns=\"" + NAMESPACE + "\" previd=\"" + prevSessionId + "\" h=\"" + h + "\"/>";
    }

    /**
     * Handles a successful session resumption response from the server.
     *
     * @param h the server's ack count at time of resumption
     * @return the number of stanzas removed from the queue
     */
    public int handleResumed(long h) {
        enabled.set(true);
        int removed = processAck(h);
        LOG.info("Session resumed: {} stanzas acked, {} unacked to resend",
                removed, getUnackedCount());
        return removed;
    }

    /**
     * Returns the list of unacknowledged stanzas for resending after resumption.
     *
     * @return the unacknowledged stanzas (oldest first)
     */
    public List<Stanza> getUnackedStanzas() {
        synchronized (unackedQueue) {
            return List.copyOf(unackedQueue);
        }
    }

    /**
     * Returns the number of unacknowledged stanzas.
     *
     * @return the unacked count
     */
    public int getUnackedCount() {
        synchronized (unackedQueue) {
            return unackedQueue.size();
        }
    }

    /**
     * Returns the outbound stanza count.
     *
     * @return the outbound count
     */
    public long getOutboundCount() {
        return outboundCount.get();
    }

    /**
     * Returns the inbound stanza count.
     *
     * @return the inbound count
     */
    public long getInboundCount() {
        return inboundCount.get();
    }

    /**
     * Returns the last acknowledged outbound count.
     *
     * @return the last acked count
     */
    public long getLastAckedOutbound() {
        return lastAckedOutbound.get();
    }

    /**
     * Returns whether stream management is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Returns whether session resumption is available.
     *
     * @return true if resumable
     */
    public boolean isResumable() {
        return resumable.get();
    }

    /**
     * Returns the stream management session id.
     *
     * @return the session id
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Disables stream management.
     */
    public void disable() {
        enabled.set(false);
        resumable.set(false);
        sessionId = null;
        synchronized (unackedQueue) {
            unackedQueue.clear();
        }
        outboundCount.set(0);
        inboundCount.set(0);
        lastAckedOutbound.set(0);
        LOG.info("Stream management disabled");
    }
}
