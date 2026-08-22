package ssg.legoflow.email.common.address;

import java.util.List;
import java.util.Objects;
/**
 * An RFC 5322 named group of addresses.
 *
 * <p>Format: {@code group-name: mailbox1, mailbox2, ... ;}
 *
 * @since 0.1.0
 */
public final class AddressGroup {

    private final String name;
    private final List<Mailbox> mailboxes;

    /**
     * Creates an address group with the given name and mailboxes.
     *
     * @param name      the group name
     * @param mailboxes the list of mailboxes in this group
     */
    public AddressGroup(String name, List<Mailbox> mailboxes) {
        this.name = Objects.requireNonNull(name, "Group name must not be null");
        this.mailboxes = List.copyOf(mailboxes);
    }

    /**
     * Returns the group name.
     *
     * @return the group name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the mailboxes in this group.
     *
     * @return an unmodifiable list of mailboxes
     */
    public List<Mailbox> mailboxes() {
        return mailboxes;
    }

    /**
     * Returns the wire format representation.
     *
     * @return the formatted group string (e.g., "friends: a@b.com, c@d.com;")
     */
    public String toWireFormat() {
        var sb = new StringBuilder();
        sb.append(name).append(": ");
        for (int i = 0; i < mailboxes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(mailboxes.get(i).toWireFormat());
        }
        sb.append(";");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AddressGroup other)) return false;
        return name.equals(other.name) && mailboxes.equals(other.mailboxes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, mailboxes);
    }

    @Override
    public String toString() {
        return toWireFormat();
    }
}
