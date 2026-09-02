package ssg.legoflow.xmpp.client;

import ssg.legoflow.xmpp.auth.SaslAuthenticator;
import ssg.legoflow.xmpp.core.*;
import ssg.legoflow.xmpp.iot.ControlManager;
import ssg.legoflow.xmpp.iot.DiscoveryManager;
import ssg.legoflow.xmpp.iot.SensorManager;
import ssg.legoflow.xmpp.presence.PresenceManager;
import ssg.legoflow.xmpp.roster.Roster;
import ssg.legoflow.xmpp.stream.XmppCodec;
import ssg.legoflow.xmpp.stream.XmppStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
/**
 * Full XMPP client with support for messaging, presence, roster, and IoT extensions.
 *
 * <p>Provides high-level methods for connecting, authenticating, sending messages,
 * managing presence and roster, and interacting with IoT sensor/control/discovery.
 *
 * @since 0.1.0
 */
public class XmppClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(XmppClient.class);

    private final XmppCodec codec;
    private final XmppStream stream;
    private final SaslAuthenticator authenticator;
    private final Roster roster;
    private final List<MessageListener> messageListeners = new CopyOnWriteArrayList<>();

    private PresenceManager presenceManager;
    private SensorManager sensorManager;
    private ControlManager controlManager;
    private DiscoveryManager discoveryManager;

    private XmppClientConfig config;
    private JID localJid;
    private volatile boolean connected;
    private volatile boolean authenticated;

    /**
     * Creates a new XMPP client.
     */
    public XmppClient() {
        this.codec = new XmppCodec();
        this.stream = new XmppStream(codec);
        this.authenticator = new SaslAuthenticator();
        this.roster = new Roster();
        this.sensorManager = new SensorManager();
        this.controlManager = new ControlManager();
        this.discoveryManager = new DiscoveryManager();
    }

    /**
     * Connects to the XMPP server.
     *
     * @param config the client configuration
     * @return a future that completes when connected
     */
    public CompletableFuture<Void> connect(XmppClientConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        this.config = config;
        LOG.info("Connecting to {}:{} (domain: {})", config.host(), config.port(), config.domain());

        return stream.open(config.domain()).thenRun(() -> {
            this.connected = true;
            LOG.info("Connected to {}", config.domain());
        });
    }

    /**
     * Disconnects from the XMPP server.
     */
    public void disconnect() {
        if (connected) {
            if (presenceManager != null) {
                presenceManager.sendUnavailable();
            }
            stream.closeStream();
            this.connected = false;
            this.authenticated = false;
            LOG.info("Disconnected");
        }
    }

    /**
     * Authenticates with the server.
     *
     * @param username the username
     * @param password the password
     * @return a future indicating authentication success
     */
    public CompletableFuture<Boolean> login(String username, String password) {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");

        return authenticator.authenticate(config.saslMechanism(), username, password)
                .thenApply(success -> {
                    if (success) {
                        String domain = config != null ? config.domain() : "localhost";
                        this.localJid = new JID(username, domain, UUID.randomUUID().toString().substring(0, 8));
                        this.authenticated = true;
                        this.presenceManager = new PresenceManager(localJid);
                        this.discoveryManager.setLocalJid(localJid);
                        stream.markAuthenticated();
                        stream.markBound(localJid);
                        stream.markActive();
                        LOG.info("Authenticated as {}", localJid.toFullJid());
                    }
                    return success;
                });
    }

    /**
     * Sends a chat message to a recipient.
     *
     * @param to   the recipient JID
     * @param body the message body
     */
    public void sendMessage(JID to, String body) {
        Objects.requireNonNull(to, "to must not be null");
        var message = MessageStanza.chat(UUID.randomUUID().toString(), localJid, to, body);
        stream.sendStanza(message);
        LOG.debug("Sent message to {}", to.toBareJid());
    }

    /**
     * Sends a presence update.
     *
     * @param show   the presence show value
     * @param status the status text
     */
    public void sendPresence(PresenceStanza.PresenceShow show, String status) {
        if (presenceManager != null) {
            presenceManager.sendPresence(show, status);
        }
    }

    /**
     * Handles a received stanza.
     *
     * @param stanza the received stanza
     */
    public void handleStanza(Stanza stanza) {
        switch (stanza) {
            case MessageStanza msg -> {
                for (var listener : messageListeners) {
                    listener.onMessage(msg);
                }
            }
            case PresenceStanza pres -> {
                if (presenceManager != null) {
                    presenceManager.handlePresence(pres);
                }
            }
            case IqStanza iq -> LOG.debug("Received IQ: id={}, type={}", iq.id(), iq.iqType());
        }
    }

    /**
     * Returns the roster.
     *
     * @return the roster
     */
    public Roster getRoster() {
        return roster;
    }

    /**
     * Returns the presence manager.
     *
     * @return the presence manager
     */
    public PresenceManager getPresenceManager() {
        return presenceManager;
    }

    /**
     * Returns the sensor manager.
     *
     * @return the sensor manager
     */
    public SensorManager getSensorManager() {
        return sensorManager;
    }

    /**
     * Returns the control manager.
     *
     * @return the control manager
     */
    public ControlManager getControlManager() {
        return controlManager;
    }

    /**
     * Returns the discovery manager.
     *
     * @return the discovery manager
     */
    public DiscoveryManager getDiscoveryManager() {
        return discoveryManager;
    }

    /**
     * Adds a message listener.
     *
     * @param listener the listener
     */
    public void addMessageListener(MessageListener listener) {
        messageListeners.add(listener);
    }

    /**
     * Removes a message listener.
     *
     * @param listener the listener
     */
    public void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }

    /**
     * Returns whether the client is connected.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Returns whether the client is authenticated.
     *
     * @return true if authenticated
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Returns the local JID (set after login).
     *
     * @return the local JID
     */
    public JID getLocalJid() {
        return localJid;
    }

    /**
     * Returns the underlying stream.
     *
     * @return the XMPP stream
     */
    public XmppStream getStream() {
        return stream;
    }

    /**
     * Returns the configuration.
     *
     * @return the client config
     */
    public XmppClientConfig getConfig() {
        return config;
    }

    @Override
    public void close() {
        disconnect();
    }
}
