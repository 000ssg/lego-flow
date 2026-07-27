package ssg.legoflow.email.imap.client;

import java.util.*;

/**
 * Parsed result of an IMAP FETCH response for a single message.
 *
 * <p>Contains the data items returned by the server, such as FLAGS,
 * ENVELOPE, BODY sections, UID, INTERNALDATE, and RFC822.SIZE.
 *
 * @since 1.0.0
 */
public final class FetchResult {

    private final int sequenceNumber;
    private final Map<String, String> items = new LinkedHashMap<>();

    /**
     * Creates a fetch result for the given sequence number.
     *
     * @param sequenceNumber the message sequence number
     */
    public FetchResult(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    /** Returns the message sequence number. */
    public int sequenceNumber() { return sequenceNumber; }

    /**
     * Adds a data item.
     *
     * @param name  the item name (e.g., "FLAGS", "UID")
     * @param value the item value
     */
    public void put(String name, String value) {
        items.put(name.toUpperCase(), value);
    }

    /**
     * Returns a data item value.
     *
     * @param name the item name
     * @return the value, or null if not present
     */
    public String get(String name) {
        return items.get(name.toUpperCase());
    }

    /** Returns the FLAGS value, or null. */
    public String flags() { return items.get("FLAGS"); }

    /** Returns the UID value, or -1 if not fetched. */
    public long uid() {
        String uid = items.get("UID");
        return uid != null ? Long.parseLong(uid.trim()) : -1;
    }

    /** Returns the INTERNALDATE value, or null. */
    public String internalDate() { return items.get("INTERNALDATE"); }

    /** Returns the RFC822.SIZE value, or -1. */
    public long size() {
        String size = items.get("RFC822.SIZE");
        return size != null ? Long.parseLong(size.trim()) : -1;
    }

    /** Returns the ENVELOPE value, or null. */
    public String envelope() { return items.get("ENVELOPE"); }

    /** Returns the BODYSTRUCTURE value, or null. */
    public String bodyStructure() { return items.get("BODYSTRUCTURE"); }

    /** Returns the raw message body content, or null. */
    public String bodyContent() {
        // Look for any BODY[] or RFC822 item
        for (Map.Entry<String, String> entry : items.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("BODY[") || key.equals("RFC822")) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Returns all data item names. */
    public Set<String> itemNames() {
        return Collections.unmodifiableSet(items.keySet());
    }

    /**
     * Parses a FETCH response line into a FetchResult.
     *
     * @param line the response line (e.g., "* 1 FETCH (FLAGS (\\Seen) UID 42)")
     * @return the parsed result, or null if not a FETCH response
     */
    public static FetchResult parse(String line) {
        if (!line.startsWith("* ") || !line.contains(" FETCH ")) {
            return null;
        }

        // Extract sequence number
        int firstSpace = line.indexOf(' ', 2);
        if (firstSpace < 0) return null;
        int seqNum;
        try {
            seqNum = Integer.parseInt(line.substring(2, firstSpace));
        } catch (NumberFormatException e) {
            return null;
        }

        FetchResult result = new FetchResult(seqNum);

        // Find the parenthesized data
        int parenStart = line.indexOf('(', firstSpace);
        if (parenStart < 0) return result;

        String data = line.substring(parenStart + 1);
        if (data.endsWith(")")) {
            data = data.substring(0, data.length() - 1);
        }

        // Simple key-value parsing
        parseFetchData(data, result);
        return result;
    }

    private static void parseFetchData(String data, FetchResult result) {
        int pos = 0;
        while (pos < data.length()) {
            // Skip whitespace
            while (pos < data.length() && data.charAt(pos) == ' ') pos++;
            if (pos >= data.length()) break;

            // Read key
            int keyEnd = pos;
            while (keyEnd < data.length() && data.charAt(keyEnd) != ' '
                    && data.charAt(keyEnd) != '(' && data.charAt(keyEnd) != '{') {
                // Handle BODY[section]
                if (data.charAt(keyEnd) == '[') {
                    while (keyEnd < data.length() && data.charAt(keyEnd) != ']') keyEnd++;
                    if (keyEnd < data.length()) keyEnd++;
                    // Check for partial <offset>
                    if (keyEnd < data.length() && data.charAt(keyEnd) == '<') {
                        while (keyEnd < data.length() && data.charAt(keyEnd) != '>') keyEnd++;
                        if (keyEnd < data.length()) keyEnd++;
                    }
                } else {
                    keyEnd++;
                }
            }

            String key = data.substring(pos, keyEnd).trim();
            pos = keyEnd;

            // Skip whitespace
            while (pos < data.length() && data.charAt(pos) == ' ') pos++;
            if (pos >= data.length()) {
                result.put(key, "");
                break;
            }

            // Read value
            char c = data.charAt(pos);
            if (c == '(') {
                // Parenthesized value
                int depth = 1;
                int start = pos;
                pos++;
                while (pos < data.length() && depth > 0) {
                    if (data.charAt(pos) == '(') depth++;
                    else if (data.charAt(pos) == ')') depth--;
                    pos++;
                }
                result.put(key, data.substring(start, pos));
            } else if (c == '"') {
                // Quoted value
                int start = pos;
                pos++;
                while (pos < data.length()) {
                    if (data.charAt(pos) == '\\') pos += 2;
                    else if (data.charAt(pos) == '"') { pos++; break; }
                    else pos++;
                }
                result.put(key, data.substring(start, pos));
            } else if (c == '{') {
                // Literal value - extract size
                int braceEnd = data.indexOf('}', pos);
                if (braceEnd > pos) {
                    int size = Integer.parseInt(data.substring(pos + 1, braceEnd));
                    int dataStart = data.indexOf('\n', braceEnd) + 1;
                    if (dataStart <= 0) dataStart = braceEnd + 1;
                    // Skip \r\n after }
                    while (dataStart < data.length() &&
                            (data.charAt(dataStart) == '\r' || data.charAt(dataStart) == '\n')) {
                        dataStart++;
                    }
                    int dataEnd = Math.min(dataStart + size, data.length());
                    result.put(key, data.substring(dataStart, dataEnd));
                    pos = dataEnd;
                } else {
                    pos++;
                }
            } else if (c == 'N' && data.substring(pos).startsWith("NIL")) {
                result.put(key, "NIL");
                pos += 3;
            } else {
                // Atom value
                int start = pos;
                while (pos < data.length() && data.charAt(pos) != ' '
                        && data.charAt(pos) != ')') {
                    pos++;
                }
                result.put(key, data.substring(start, pos));
            }
        }
    }

    @Override
    public String toString() {
        return "FetchResult{seq=" + sequenceNumber + ", items=" + items + "}";
    }
}
