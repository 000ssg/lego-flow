package ssg.legoflow.email.common.address;

import ssg.legoflow.email.common.encoding.EncodedWordCodec;
import java.util.Objects;
/**
 * An RFC 5322 mailbox: optional display name plus email address.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code "John Doe" <john@example.com>}</li>
 *   <li>{@code john@example.com}</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class Mailbox {

    private final String displayName;
    private final EmailAddress address;

    /**
     * Creates a mailbox with a display name and address.
     *
     * @param displayName the display name (may be null)
     * @param address     the email address
     */
    public Mailbox(String displayName, EmailAddress address) {
        this.displayName = displayName;
        this.address = Objects.requireNonNull(address, "Address must not be null");
    }

    /**
     * Creates a mailbox from an email address without a display name.
     *
     * @param address the email address
     */
    public Mailbox(EmailAddress address) {
        this(null, address);
    }

    /**
     * Returns the display name, or null if not present.
     *
     * @return the display name
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the email address.
     *
     * @return the email address
     */
    public EmailAddress address() {
        return address;
    }

    /**
     * Returns the wire format representation.
     *
     * <p>If a display name is present: {@code "Display Name" <addr>}.
     * Otherwise just: {@code addr}.
     *
     * @return the formatted mailbox string
     */
    public String toWireFormat() {
        if (displayName == null || displayName.isEmpty()) {
            return address.address();
        }
        String encodedName;
        if (EncodedWordCodec.needsEncoding(displayName)) {
            encodedName = EncodedWordCodec.encode(displayName);
        } else {
            encodedName = "\"" + displayName.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return encodedName + " <" + address.address() + ">";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Mailbox other)) return false;
        return Objects.equals(displayName, other.displayName) && address.equals(other.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, address);
    }

    @Override
    public String toString() {
        return toWireFormat();
    }
}
