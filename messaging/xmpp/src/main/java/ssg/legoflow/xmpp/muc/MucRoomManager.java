package ssg.legoflow.xmpp.muc;

import ssg.legoflow.xmpp.core.JID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
/**
 * Manages Multi-User Chat rooms (XEP-0045).
 *
 * <p>Provides operations for creating, joining, leaving MUC rooms, sending
 * groupchat messages, and managing occupant roles and affiliations.
 *
 * @since 0.1.0
 */
public class MucRoomManager {

    private static final Logger LOG = LoggerFactory.getLogger(MucRoomManager.class);

    /** The XEP-0045 MUC namespace. */
    public static final String NAMESPACE = "http://jabber.org/protocol/muc";

    /** The XEP-0045 MUC user namespace. */
    public static final String USER_NAMESPACE = "http://jabber.org/protocol/muc#user";

    /** The XEP-0045 MUC admin namespace. */
    public static final String ADMIN_NAMESPACE = "http://jabber.org/protocol/muc#admin";

    /** The XEP-0045 MUC owner namespace. */
    public static final String OWNER_NAMESPACE = "http://jabber.org/protocol/muc#owner";

    private final JID localJid;
    private final Map<String, MucRoom> rooms = new ConcurrentHashMap<>();
    private final List<Consumer<MucMessage>> messageListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<MucOccupant>> occupantListeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a new MUC room manager.
     *
     * @param localJid the local user's JID
     */
    public MucRoomManager(JID localJid) {
        this.localJid = Objects.requireNonNull(localJid, "localJid must not be null");
    }

    /**
     * Joins a MUC room with the specified nickname.
     *
     * <p>Sends a directed presence to room@service/nick to signal the MUC join.
     *
     * @param roomJid the bare room JID (e.g., room@conference.example.com)
     * @param nick    the desired nickname
     * @return the MUC room
     */
    public MucRoom join(JID roomJid, String nick) {
        Objects.requireNonNull(roomJid, "roomJid must not be null");
        Objects.requireNonNull(nick, "nick must not be null");

        var room = rooms.computeIfAbsent(roomJid.toBareJid(), k -> new MucRoom(roomJid));
        room.markJoined(nick);

        // Add self as occupant
        var selfRoomJid = roomJid.withResource(nick);
        var selfOccupant = new MucOccupant(selfRoomJid, localJid, nick,
                MucOccupant.Role.PARTICIPANT, MucOccupant.Affiliation.NONE);
        room.addOccupant(selfOccupant);

        LOG.info("Joined MUC room {} as {}", roomJid.toBareJid(), nick);
        return room;
    }

    /**
     * Leaves a MUC room.
     *
     * @param roomJid the bare room JID
     */
    public void leave(JID roomJid) {
        Objects.requireNonNull(roomJid, "roomJid must not be null");
        var room = rooms.get(roomJid.toBareJid());
        if (room != null && room.isJoined()) {
            room.removeOccupant(room.getLocalNick());
            room.markLeft();
            LOG.info("Left MUC room {}", roomJid.toBareJid());
        }
    }

    /**
     * Sends a groupchat message to a room.
     *
     * @param roomJid the bare room JID
     * @param body    the message body
     * @return the sent MUC message, or null if not joined
     */
    public MucMessage sendMessage(JID roomJid, String body) {
        Objects.requireNonNull(roomJid, "roomJid must not be null");
        Objects.requireNonNull(body, "body must not be null");

        var room = rooms.get(roomJid.toBareJid());
        if (room == null || !room.isJoined()) {
            LOG.warn("Cannot send message: not joined to room {}", roomJid.toBareJid());
            return null;
        }

        var fromJid = roomJid.withResource(room.getLocalNick());
        var message = new MucMessage(
                UUID.randomUUID().toString(), fromJid, roomJid, body, Instant.now());
        room.addMessage(message);

        for (var listener : messageListeners) {
            listener.accept(message);
        }

        LOG.debug("Sent groupchat message to {}", roomJid.toBareJid());
        return message;
    }

    /**
     * Handles an incoming groupchat message.
     *
     * @param message the received message
     */
    public void handleMessage(MucMessage message) {
        var room = rooms.get(message.roomJid().toBareJid());
        if (room != null) {
            room.addMessage(message);
        }
        for (var listener : messageListeners) {
            listener.accept(message);
        }
    }

    /**
     * Handles an occupant joining or updating in a room.
     *
     * @param roomJid  the bare room JID
     * @param occupant the occupant
     */
    public void handleOccupantJoin(JID roomJid, MucOccupant occupant) {
        var room = rooms.get(roomJid.toBareJid());
        if (room != null) {
            room.addOccupant(occupant);
            for (var listener : occupantListeners) {
                listener.accept(occupant);
            }
        }
    }

    /**
     * Handles an occupant leaving a room.
     *
     * @param roomJid the bare room JID
     * @param nick    the occupant's nickname
     */
    public void handleOccupantLeave(JID roomJid, String nick) {
        var room = rooms.get(roomJid.toBareJid());
        if (room != null) {
            room.removeOccupant(nick);
        }
    }

    /**
     * Changes the local user's nickname in a room.
     *
     * @param roomJid the bare room JID
     * @param newNick the new nickname
     * @return true if the nick was changed
     */
    public boolean changeNick(JID roomJid, String newNick) {
        Objects.requireNonNull(roomJid, "roomJid must not be null");
        Objects.requireNonNull(newNick, "newNick must not be null");

        var room = rooms.get(roomJid.toBareJid());
        if (room == null || !room.isJoined()) {
            return false;
        }
        String oldNick = room.getLocalNick();
        boolean changed = room.changeNick(oldNick, newNick);
        if (changed) {
            LOG.info("Changed nick in {} from {} to {}", roomJid.toBareJid(), oldNick, newNick);
        }
        return changed;
    }

    /**
     * Changes the role of an occupant (moderator action).
     *
     * @param roomJid the bare room JID
     * @param nick    the target occupant's nickname
     * @param role    the new role
     * @return true if the role was changed
     */
    public boolean setRole(JID roomJid, String nick, MucOccupant.Role role) {
        Objects.requireNonNull(roomJid, "roomJid must not be null");
        var room = rooms.get(roomJid.toBareJid());
        if (room == null) {
            return false;
        }
        boolean changed = room.changeRole(nick, role);
        if (changed) {
            LOG.info("Changed role of {} in {} to {}", nick, roomJid.toBareJid(), role);
        }
        return changed;
    }

    /**
     * Changes the affiliation of an occupant (admin/owner action).
     *
     * @param roomJid     the bare room JID
     * @param nick        the target occupant's nickname
     * @param affiliation the new affiliation
     * @return true if the affiliation was changed
     */
    public boolean setAffiliation(JID roomJid, String nick, MucOccupant.Affiliation affiliation) {
        Objects.requireNonNull(roomJid, "roomJid must not be null");
        var room = rooms.get(roomJid.toBareJid());
        if (room == null) {
            return false;
        }
        boolean changed = room.changeAffiliation(nick, affiliation);
        if (changed) {
            LOG.info("Changed affiliation of {} in {} to {}", nick, roomJid.toBareJid(), affiliation);
        }
        return changed;
    }

    /**
     * Returns the room for the given JID.
     *
     * @param roomJid the bare room JID
     * @return the room, or null if not tracked
     */
    public MucRoom getRoom(JID roomJid) {
        return rooms.get(roomJid.toBareJid());
    }

    /**
     * Returns all tracked rooms.
     *
     * @return the list of rooms
     */
    public List<MucRoom> getRooms() {
        return List.copyOf(rooms.values());
    }

    /**
     * Adds a message listener.
     *
     * @param listener the listener
     */
    public void addMessageListener(Consumer<MucMessage> listener) {
        messageListeners.add(listener);
    }

    /**
     * Adds an occupant listener.
     *
     * @param listener the listener
     */
    public void addOccupantListener(Consumer<MucOccupant> listener) {
        occupantListeners.add(listener);
    }

    /**
     * Generates the presence XML for joining a MUC room.
     *
     * @param roomJid the bare room JID
     * @param nick    the desired nickname
     * @return the presence XML
     */
    public String generateJoinPresenceXml(JID roomJid, String nick) {
        return "<presence to=\"" + roomJid.toBareJid() + "/" + nick + "\">" +
                "<x xmlns=\"" + NAMESPACE + "\"/>" +
                "</presence>";
    }

    /**
     * Generates the presence XML for leaving a MUC room.
     *
     * @param roomJid the bare room JID
     * @param nick    the current nickname
     * @return the presence XML
     */
    public String generateLeavePresenceXml(JID roomJid, String nick) {
        return "<presence to=\"" + roomJid.toBareJid() + "/" + nick + "\" type=\"unavailable\"/>";
    }

    /**
     * Returns the local JID.
     *
     * @return the local JID
     */
    public JID getLocalJid() {
        return localJid;
    }
}
