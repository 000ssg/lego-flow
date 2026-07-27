package ssg.legoflow.xmpp.stream;

import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.core.Stanza;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * XMPP XML stream management (RFC 6120).
 *
 * <p>Manages the lifecycle of an XMPP XML stream including opening/closing the stream,
 * feature negotiation, stanza sending/receiving, and error handling.
 *
 * @since 1.0.0
 */
public class XmppStream implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(XmppStream.class);

    private static final String STREAM_OPEN_TEMPLATE =
            "<?xml version='1.0'?><stream:stream xmlns='jabber:client' " +
            "xmlns:stream='http://etherx.jabber.org/streams' to='%s' version='1.0'>";
    private static final String STREAM_CLOSE = "</stream:stream>";

    private final AtomicReference<XmppStreamState> state = new AtomicReference<>(XmppStreamState.INITIAL);
    private final List<StreamFeature> features = new CopyOnWriteArrayList<>();
    private final List<Consumer<Stanza>> stanzaListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<XmppStreamState>> stateListeners = new CopyOnWriteArrayList<>();
    private final XmppCodec codec;

    private String streamId;
    private JID localJid;
    private JID remoteJid;
    private final List<ByteBuffer> outboundQueue = new ArrayList<>();

    /**
     * Creates a new XMPP stream.
     *
     * @param codec the codec for encoding/decoding stanzas
     */
    public XmppStream(XmppCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
    }

    /**
     * Opens the XML stream to the specified domain.
     *
     * @param domain the target domain
     * @return a future that completes when the stream is opened
     */
    public CompletableFuture<Void> open(String domain) {
        Objects.requireNonNull(domain, "domain must not be null");
        transitionTo(XmppStreamState.CONNECTING);
        LOG.info("Opening XMPP stream to {}", domain);

        this.remoteJid = new JID(null, domain, null);
        String openElement = String.format(STREAM_OPEN_TEMPLATE, domain);
        outboundQueue.add(ByteBuffer.wrap(openElement.getBytes(StandardCharsets.UTF_8)));

        transitionTo(XmppStreamState.NEGOTIATING);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Closes the XML stream.
     *
     * @return a future that completes when the stream is closed
     */
    public CompletableFuture<Void> closeStream() {
        var currentState = state.get();
        if (currentState == XmppStreamState.CLOSED) {
            return CompletableFuture.completedFuture(null);
        }
        LOG.info("Closing XMPP stream");
        transitionTo(XmppStreamState.CLOSING);
        outboundQueue.add(ByteBuffer.wrap(STREAM_CLOSE.getBytes(StandardCharsets.UTF_8)));
        transitionTo(XmppStreamState.CLOSED);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Sends a stanza over this stream.
     *
     * @param stanza the stanza to send
     */
    public void sendStanza(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza must not be null");
        var currentState = state.get();
        if (currentState != XmppStreamState.ACTIVE && currentState != XmppStreamState.BOUND) {
            throw new IllegalStateException("Cannot send stanza in state: " + currentState);
        }
        ByteBuffer encoded = codec.encodeStanza(stanza);
        outboundQueue.add(encoded);
        LOG.debug("Sent stanza: id={}, type={}", stanza.id(), stanza.type());
    }

    /**
     * Processes incoming data from the stream.
     *
     * @param data the incoming data
     */
    public void receiveData(ByteBuffer data) {
        Objects.requireNonNull(data, "data must not be null");
        List<Stanza> stanzas = codec.decodeStanzas(data);
        for (var stanza : stanzas) {
            LOG.debug("Received stanza: id={}, type={}", stanza.id(), stanza.type());
            for (var listener : stanzaListeners) {
                listener.accept(stanza);
            }
        }
    }

    /**
     * Negotiates stream features.
     *
     * @param featureList the features to negotiate
     */
    public void negotiateFeatures(List<StreamFeature> featureList) {
        this.features.addAll(featureList);
        LOG.info("Negotiated {} stream features", featureList.size());
    }

    /**
     * Marks the stream as authenticated.
     */
    public void markAuthenticated() {
        transitionTo(XmppStreamState.AUTHENTICATED);
    }

    /**
     * Marks the stream as bound (resource binding complete).
     *
     * @param jid the bound JID with resource
     */
    public void markBound(JID jid) {
        this.localJid = jid;
        transitionTo(XmppStreamState.BOUND);
    }

    /**
     * Marks the stream as active (ready for stanza exchange).
     */
    public void markActive() {
        transitionTo(XmppStreamState.ACTIVE);
    }

    /**
     * Adds a listener for incoming stanzas.
     *
     * @param listener the stanza listener
     */
    public void addStanzaListener(Consumer<Stanza> listener) {
        stanzaListeners.add(listener);
    }

    /**
     * Adds a listener for state changes.
     *
     * @param listener the state listener
     */
    public void addStateListener(Consumer<XmppStreamState> listener) {
        stateListeners.add(listener);
    }

    /**
     * Returns the current stream state.
     *
     * @return the current state
     */
    public XmppStreamState getState() {
        return state.get();
    }

    /**
     * Returns the negotiated features.
     *
     * @return the list of features
     */
    public List<StreamFeature> getFeatures() {
        return List.copyOf(features);
    }

    /**
     * Returns the stream identifier.
     *
     * @return the stream id
     */
    public String getStreamId() {
        return streamId;
    }

    /**
     * Sets the stream identifier.
     *
     * @param streamId the stream id
     */
    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    /**
     * Returns the local JID (set after binding).
     *
     * @return the local JID
     */
    public JID getLocalJid() {
        return localJid;
    }

    /**
     * Returns the remote JID (the domain).
     *
     * @return the remote JID
     */
    public JID getRemoteJid() {
        return remoteJid;
    }

    /**
     * Drains and returns queued outbound data.
     *
     * @return the list of outbound buffers
     */
    public List<ByteBuffer> drainOutbound() {
        var result = new ArrayList<>(outboundQueue);
        outboundQueue.clear();
        return result;
    }

    private void transitionTo(XmppStreamState newState) {
        var current = state.get();
        if (current == newState) {
            return;
        }
        if (!current.canTransitionTo(newState)) {
            throw new IllegalStateException(
                    "Invalid state transition: " + current + " -> " + newState);
        }
        state.set(newState);
        LOG.debug("Stream state: {} -> {}", current, newState);
        for (var listener : stateListeners) {
            listener.accept(newState);
        }
    }

    @Override
    public void close() {
        if (state.get() != XmppStreamState.CLOSED) {
            closeStream();
        }
    }
}
