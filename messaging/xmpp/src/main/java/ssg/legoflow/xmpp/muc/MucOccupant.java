package ssg.legoflow.xmpp.muc;

import ssg.legoflow.xmpp.core.JID;

import java.util.Objects;

/**
 * Represents an occupant in a Multi-User Chat room (XEP-0045).
 *
 * <p>Each occupant has a room nickname, an optional real JID, a role (how they
 * can interact in the room), and an affiliation (their long-term association
 * with the room).
 *
 * @param roomJid     the occupant's room JID (room@service/nick)
 * @param realJid     the occupant's real JID (may be null if hidden)
 * @param nick        the occupant's nickname in the room
 * @param role        the occupant's role (visitor, participant, moderator)
 * @param affiliation the occupant's affiliation (none, member, admin, owner, outcast)
 * @since 1.0.0
 */
public record MucOccupant(JID roomJid, JID realJid, String nick, Role role, Affiliation affiliation) {

    /**
     * Roles define how an occupant can interact in the room.
     *
     * @since 1.0.0
     */
    public enum Role {
        /** Cannot send messages to the room. */
        NONE,
        /** Can receive messages but cannot send to all occupants. */
        VISITOR,
        /** Can send and receive messages. */
        PARTICIPANT,
        /** Can manage the room (kick visitors, grant voice). */
        MODERATOR
    }

    /**
     * Affiliations define a user's long-term association with the room.
     *
     * @since 1.0.0
     */
    public enum Affiliation {
        /** Banned from the room. */
        OUTCAST,
        /** No affiliation. */
        NONE,
        /** Member of the room (can join members-only rooms). */
        MEMBER,
        /** Administrator of the room. */
        ADMIN,
        /** Owner of the room. */
        OWNER
    }

    /**
     * Constructs a validated MUC occupant.
     */
    public MucOccupant {
        Objects.requireNonNull(roomJid, "roomJid must not be null");
        Objects.requireNonNull(nick, "nick must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(affiliation, "affiliation must not be null");
    }

    /**
     * Serializes this occupant to an XML {@code <item>} element.
     *
     * @return the XML representation
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<item");
        if (realJid != null) {
            sb.append(" jid=\"").append(realJid.toBareJid()).append("\"");
        }
        sb.append(" nick=\"").append(nick).append("\"");
        sb.append(" role=\"").append(role.name().toLowerCase()).append("\"");
        sb.append(" affiliation=\"").append(affiliation.name().toLowerCase()).append("\"");
        sb.append("/>");
        return sb.toString();
    }
}
