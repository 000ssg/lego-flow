package ssg.legoflow.xmpp.muc;

import ssg.legoflow.xmpp.core.JID;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
/**
 * Represents a Multi-User Chat room (XEP-0045).
 *
 * <p>Tracks the room's occupants, messages, and configuration. A room is identified
 * by its bare JID (room@conference.example.com).
 *
 * @since 0.1.0
 */
public class MucRoom {

    private final JID roomJid;
    private final Map<String, MucOccupant> occupants = new ConcurrentHashMap<>();
    private final List<MucMessage> messages = new CopyOnWriteArrayList<>();
    private String subject;
    private boolean membersOnly;
    private boolean persistent;
    private boolean joined;
    private String localNick;

    /**
     * Creates a new MUC room.
     *
     * @param roomJid the bare room JID
     */
    public MucRoom(JID roomJid) {
        this.roomJid = Objects.requireNonNull(roomJid, "roomJid must not be null");
    }

    /**
     * Returns the room JID.
     *
     * @return the room JID
     */
    public JID getRoomJid() {
        return roomJid;
    }

    /**
     * Returns the current subject of the room.
     *
     * @return the subject, or null if not set
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Sets the room subject.
     *
     * @param subject the subject
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * Returns whether this room is members-only.
     *
     * @return true if members-only
     */
    public boolean isMembersOnly() {
        return membersOnly;
    }

    /**
     * Sets whether this room is members-only.
     *
     * @param membersOnly true for members-only
     */
    public void setMembersOnly(boolean membersOnly) {
        this.membersOnly = membersOnly;
    }

    /**
     * Returns whether this room is persistent.
     *
     * @return true if persistent
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Sets whether this room is persistent.
     *
     * @param persistent true for persistent
     */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    /**
     * Returns whether the local user has joined this room.
     *
     * @return true if joined
     */
    public boolean isJoined() {
        return joined;
    }

    /**
     * Returns the local user's nickname in this room.
     *
     * @return the local nickname, or null if not joined
     */
    public String getLocalNick() {
        return localNick;
    }

    /**
     * Marks the room as joined with the given nickname.
     *
     * @param nick the local nickname
     */
    public void markJoined(String nick) {
        this.joined = true;
        this.localNick = nick;
    }

    /**
     * Marks the room as left.
     */
    public void markLeft() {
        this.joined = false;
        this.localNick = null;
    }

    /**
     * Adds or updates an occupant in the room.
     *
     * @param occupant the occupant
     */
    public void addOccupant(MucOccupant occupant) {
        Objects.requireNonNull(occupant, "occupant must not be null");
        occupants.put(occupant.nick(), occupant);
    }

    /**
     * Removes an occupant by nickname.
     *
     * @param nick the occupant's nickname
     * @return the removed occupant, or null if not found
     */
    public MucOccupant removeOccupant(String nick) {
        return occupants.remove(nick);
    }

    /**
     * Returns the occupant with the given nickname.
     *
     * @param nick the nickname
     * @return the occupant, or null if not found
     */
    public MucOccupant getOccupant(String nick) {
        return occupants.get(nick);
    }

    /**
     * Returns all occupants in the room.
     *
     * @return the list of occupants
     */
    public List<MucOccupant> getOccupants() {
        return List.copyOf(occupants.values());
    }

    /**
     * Returns the number of occupants.
     *
     * @return the occupant count
     */
    public int getOccupantCount() {
        return occupants.size();
    }

    /**
     * Adds a message to the room history.
     *
     * @param message the message
     */
    public void addMessage(MucMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        messages.add(message);
    }

    /**
     * Returns all messages in the room.
     *
     * @return the list of messages
     */
    public List<MucMessage> getMessages() {
        return List.copyOf(messages);
    }

    /**
     * Returns the number of messages.
     *
     * @return the message count
     */
    public int getMessageCount() {
        return messages.size();
    }

    /**
     * Changes an occupant's nickname.
     *
     * @param oldNick the old nickname
     * @param newNick the new nickname
     * @return true if the nickname was changed, false if the old nick was not found
     */
    public boolean changeNick(String oldNick, String newNick) {
        var occupant = occupants.remove(oldNick);
        if (occupant == null) {
            return false;
        }
        var newRoomJid = roomJid.withResource(newNick);
        var updated = new MucOccupant(newRoomJid, occupant.realJid(), newNick,
                occupant.role(), occupant.affiliation());
        occupants.put(newNick, updated);
        if (oldNick.equals(localNick)) {
            localNick = newNick;
        }
        return true;
    }

    /**
     * Changes an occupant's role.
     *
     * @param nick the occupant's nickname
     * @param role the new role
     * @return true if the role was changed
     */
    public boolean changeRole(String nick, MucOccupant.Role role) {
        var occupant = occupants.get(nick);
        if (occupant == null) {
            return false;
        }
        var updated = new MucOccupant(occupant.roomJid(), occupant.realJid(), nick,
                role, occupant.affiliation());
        occupants.put(nick, updated);
        return true;
    }

    /**
     * Changes an occupant's affiliation.
     *
     * @param nick        the occupant's nickname
     * @param affiliation the new affiliation
     * @return true if the affiliation was changed
     */
    public boolean changeAffiliation(String nick, MucOccupant.Affiliation affiliation) {
        var occupant = occupants.get(nick);
        if (occupant == null) {
            return false;
        }
        var updated = new MucOccupant(occupant.roomJid(), occupant.realJid(), nick,
                occupant.role(), affiliation);
        occupants.put(nick, updated);
        return true;
    }
}
