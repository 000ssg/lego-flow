package ssg.legoflow.email.common.address;

import ssg.legoflow.email.common.encoding.EncodedWordCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses RFC 5322 address, mailbox, and group lists.
 *
 * <p>Handles various address formats including:
 * <ul>
 *   <li>Simple: {@code user@example.com}</li>
 *   <li>Angle-bracket: {@code <user@example.com>}</li>
 *   <li>Display name: {@code "John Doe" <user@example.com>}</li>
 *   <li>Unquoted display name: {@code John Doe <user@example.com>}</li>
 *   <li>Groups: {@code friends: a@b.com, c@d.com;}</li>
 *   <li>Comma-separated lists</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class AddressParser {

    private AddressParser() {
    }

    /**
     * Parses a single mailbox from a header value.
     *
     * @param input the mailbox string
     * @return the parsed Mailbox
     * @throws IllegalArgumentException if the input is not a valid mailbox
     */
    public static Mailbox parseMailbox(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Mailbox string must not be empty");
        }
        String decoded = EncodedWordCodec.decode(input.trim());
        return parseMailboxInternal(decoded);
    }

    /**
     * Parses a comma-separated list of mailboxes.
     *
     * @param input the comma-separated mailbox list
     * @return the list of parsed Mailboxes
     */
    public static List<Mailbox> parseMailboxList(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        String decoded = EncodedWordCodec.decode(input.trim());
        var result = new ArrayList<Mailbox>();
        List<String> segments = splitAddresses(decoded);
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                result.add(parseMailboxInternal(trimmed));
            }
        }
        return result;
    }

    /**
     * Parses an address list that may contain mailboxes and groups.
     *
     * <p>Returns a list of objects, each being either a {@link Mailbox} or
     * an {@link AddressGroup}.
     *
     * @param input the address list string
     * @return the list of parsed addresses (Mailbox or AddressGroup instances)
     */
    public static List<Object> parseAddressList(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        String decoded = EncodedWordCodec.decode(input.trim());
        var result = new ArrayList<>();

        // Check for groups: "name: addr1, addr2;"
        int colonPos = findGroupColon(decoded);
        if (colonPos > 0) {
            // Could be a group
            String beforeColon = decoded.substring(0, colonPos).trim();
            String afterColon = decoded.substring(colonPos + 1).trim();

            // Check if this is really a group (not a quoted string or angle-bracket addr)
            if (!beforeColon.contains("@") && !beforeColon.contains("<")) {
                int semiPos = afterColon.indexOf(';');
                if (semiPos >= 0) {
                    String groupAddresses = afterColon.substring(0, semiPos);
                    List<Mailbox> members = parseMailboxList(groupAddresses);
                    result.add(new AddressGroup(beforeColon, members));

                    // Parse remaining after the group
                    String remaining = afterColon.substring(semiPos + 1).trim();
                    if (remaining.startsWith(",")) {
                        remaining = remaining.substring(1).trim();
                    }
                    if (!remaining.isEmpty()) {
                        result.addAll(parseAddressList(remaining));
                    }
                    return result;
                }
            }
        }

        // Not a group — parse as mailbox list
        List<String> segments = splitAddresses(decoded);
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                result.add(parseMailboxInternal(trimmed));
            }
        }
        return result;
    }

    private static Mailbox parseMailboxInternal(String input) {
        String trimmed = input.trim();

        // Check for angle-bracket form: ... <addr>
        int ltPos = trimmed.lastIndexOf('<');
        int gtPos = trimmed.lastIndexOf('>');

        if (ltPos >= 0 && gtPos > ltPos) {
            String addrPart = trimmed.substring(ltPos + 1, gtPos).trim();
            String displayPart = trimmed.substring(0, ltPos).trim();
            // Remove surrounding quotes from display name
            if (displayPart.length() >= 2 && displayPart.startsWith("\"") && displayPart.endsWith("\"")) {
                displayPart = displayPart.substring(1, displayPart.length() - 1);
                // Unescape
                displayPart = displayPart.replace("\\\"", "\"").replace("\\\\", "\\");
            }
            EmailAddress addr = EmailAddress.parse(addrPart);
            if (displayPart.isEmpty()) {
                return new Mailbox(addr);
            }
            return new Mailbox(displayPart, addr);
        }

        // Simple address form: user@example.com
        return new Mailbox(EmailAddress.parse(trimmed));
    }

    private static List<String> splitAddresses(String input) {
        var result = new ArrayList<String>();
        var current = new StringBuilder();
        int depth = 0; // angle bracket depth
        boolean inQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && inQuotes && i + 1 < input.length()) {
                current.append(c);
                current.append(input.charAt(i + 1));
                i++;
            } else if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == '<' && !inQuotes) {
                depth++;
                current.append(c);
            } else if (c == '>' && !inQuotes) {
                depth = Math.max(0, depth - 1);
                current.append(c);
            } else if (c == ',' && !inQuotes && depth == 0) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private static int findGroupColon(String input) {
        boolean inQuotes = false;
        int angleBracketDepth = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == '<' && !inQuotes) {
                angleBracketDepth++;
            } else if (c == '>' && !inQuotes) {
                angleBracketDepth = Math.max(0, angleBracketDepth - 1);
            } else if (c == ':' && !inQuotes && angleBracketDepth == 0) {
                return i;
            }
        }
        return -1;
    }
}
