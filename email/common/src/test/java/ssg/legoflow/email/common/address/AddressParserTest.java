package ssg.legoflow.email.common.address;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AddressParser}, {@link Mailbox}, and {@link AddressGroup}.
 */
class AddressParserTest {

    @Test
    void testParseSimpleMailbox() {
        Mailbox mbox = AddressParser.parseMailbox("user@example.com");
        assertThat(mbox.displayName()).isNull();
        assertThat(mbox.address().address()).isEqualTo("user@example.com");
    }

    @Test
    void testParseMailboxWithAngleBrackets() {
        Mailbox mbox = AddressParser.parseMailbox("<user@example.com>");
        assertThat(mbox.displayName()).isNull();
        assertThat(mbox.address().address()).isEqualTo("user@example.com");
    }

    @Test
    void testParseMailboxWithDisplayName() {
        Mailbox mbox = AddressParser.parseMailbox("\"John Doe\" <john@example.com>");
        assertThat(mbox.displayName()).isEqualTo("John Doe");
        assertThat(mbox.address().address()).isEqualTo("john@example.com");
    }

    @Test
    void testParseMailboxWithUnquotedDisplayName() {
        Mailbox mbox = AddressParser.parseMailbox("John Doe <john@example.com>");
        assertThat(mbox.displayName()).isEqualTo("John Doe");
        assertThat(mbox.address().address()).isEqualTo("john@example.com");
    }

    @Test
    void testParseMailboxWithEscapedQuotes() {
        Mailbox mbox = AddressParser.parseMailbox("\"John \\\"JD\\\" Doe\" <john@example.com>");
        assertThat(mbox.displayName()).isEqualTo("John \"JD\" Doe");
    }

    @Test
    void testParseMailboxList() {
        List<Mailbox> list = AddressParser.parseMailboxList(
                "alice@a.com, \"Bob\" <bob@b.com>, charlie@c.com");
        assertThat(list).hasSize(3);
        assertThat(list.get(0).address().address()).isEqualTo("alice@a.com");
        assertThat(list.get(1).displayName()).isEqualTo("Bob");
        assertThat(list.get(1).address().address()).isEqualTo("bob@b.com");
        assertThat(list.get(2).address().address()).isEqualTo("charlie@c.com");
    }

    @Test
    void testParseMailboxListEmpty() {
        assertThat(AddressParser.parseMailboxList("")).isEmpty();
        assertThat(AddressParser.parseMailboxList(null)).isEmpty();
    }

    @Test
    void testParseAddressGroup() {
        List<Object> addresses = AddressParser.parseAddressList(
                "friends: alice@a.com, bob@b.com;");
        assertThat(addresses).hasSize(1);
        assertThat(addresses.get(0)).isInstanceOf(AddressGroup.class);
        AddressGroup group = (AddressGroup) addresses.get(0);
        assertThat(group.name()).isEqualTo("friends");
        assertThat(group.mailboxes()).hasSize(2);
    }

    @Test
    void testMailboxToWireFormatSimple() {
        Mailbox mbox = new Mailbox(EmailAddress.parse("user@example.com"));
        assertThat(mbox.toWireFormat()).isEqualTo("user@example.com");
    }

    @Test
    void testMailboxToWireFormatWithDisplayName() {
        Mailbox mbox = new Mailbox("John Doe", EmailAddress.parse("john@example.com"));
        assertThat(mbox.toWireFormat()).isEqualTo("\"John Doe\" <john@example.com>");
    }

    @Test
    void testAddressGroupToWireFormat() {
        AddressGroup group = new AddressGroup("team", List.of(
                new Mailbox(EmailAddress.parse("a@b.com")),
                new Mailbox(EmailAddress.parse("c@d.com"))
        ));
        assertThat(group.toWireFormat()).isEqualTo("team: a@b.com, c@d.com;");
    }

    @Test
    void testParseMailboxWithSpecialCharsInLocalPart() {
        Mailbox mbox = AddressParser.parseMailbox("user+tag@example.com");
        assertThat(mbox.address().localPart()).isEqualTo("user+tag");
    }

    @Test
    void testParseMailboxNullThrows() {
        assertThatThrownBy(() -> AddressParser.parseMailbox(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMailboxEquality() {
        Mailbox a = new Mailbox("John", EmailAddress.parse("john@example.com"));
        Mailbox b = new Mailbox("John", EmailAddress.parse("john@example.com"));
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void testMailboxToWireFormatNonAscii() {
        Mailbox mbox = new Mailbox("José García", EmailAddress.parse("jose@example.com"));
        String wire = mbox.toWireFormat();
        // Non-ASCII display name should be encoded
        assertThat(wire).contains("=?");
        assertThat(wire).contains("<jose@example.com>");
    }

    @Test
    void testParseMailboxWithEncodedWord() {
        String input = "=?UTF-8?B?Sm9zw6k=?= <jose@example.com>";
        Mailbox mbox = AddressParser.parseMailbox(input);
        assertThat(mbox.displayName()).isEqualTo("José");
        assertThat(mbox.address().address()).isEqualTo("jose@example.com");
    }
}
