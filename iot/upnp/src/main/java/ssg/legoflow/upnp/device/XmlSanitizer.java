package ssg.legoflow.upnp.device;

import java.util.Set;

/**
 * Character-level XML sanitizer for real-world UPnP/DLNA device responses.
 *
 * <p>Many consumer devices (NAS units, Smart TVs, media servers) embed HTML
 * fragments in their XML responses — unclosed void elements like
 * {@code <img src="...">}, {@code <br>}, {@code <IMG SRC="x">} that are
 * valid in HTML5 but break strict XML parsers.
 *
 * <p>This sanitizer scans character-by-character, correctly handling:
 * <ul>
 *   <li>Quoted attribute values containing {@code >} characters
 *       (e.g. {@code alt="a > b"})</li>
 *   <li>Case-insensitive tag names (e.g. {@code <IMG>}, {@code <Br>})</li>
 *   <li>Already self-closed tags ({@code <br />}) — left unchanged</li>
 *   <li>Single-quoted and double-quoted attribute values</li>
 *   <li>Attributes spanning multiple lines</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class XmlSanitizer {

    /** HTML void elements that never have a closing tag. */
    private static final Set<String> VOID_ELEMENTS = Set.of(
            "img", "br", "hr", "input", "meta", "link", "col",
            "area", "base", "embed", "param", "source", "track", "wbr"
    );

    private XmlSanitizer() {
    }

    /**
     * Sanitizes XML by converting unclosed HTML void elements to self-closing
     * XML form.
     *
     * <p>For example, {@code <img src="photo.jpg">} becomes
     * {@code <img src="photo.jpg" />}, and {@code <BR>} becomes
     * {@code <BR />}. Already self-closed elements like {@code <br />}
     * are left unchanged.
     *
     * @param xml the raw XML string, possibly containing HTML void elements
     * @return sanitized XML safe for DOM parsing, or the original if
     *         {@code null} or empty
     * @since 1.0.0
     */
    public static String sanitize(String xml) {
        if (xml == null || xml.isEmpty()) {
            return xml;
        }

        var sb = new StringBuilder(xml.length() + 64);
        int len = xml.length();
        int i = 0;

        while (i < len) {
            char ch = xml.charAt(i);

            // Look for '<' that might start a void element
            if (ch == '<' && i + 1 < len && xml.charAt(i + 1) != '/'
                    && xml.charAt(i + 1) != '!' && xml.charAt(i + 1) != '?') {
                // Extract the tag name
                int tagStart = i + 1;
                int tagNameEnd = tagStart;
                while (tagNameEnd < len) {
                    char c = xml.charAt(tagNameEnd);
                    if (c == ' ' || c == '\t' || c == '\n' || c == '\r'
                            || c == '>' || c == '/') {
                        break;
                    }
                    tagNameEnd++;
                }

                String tagName = xml.substring(tagStart, tagNameEnd);
                if (VOID_ELEMENTS.contains(tagName.toLowerCase())) {
                    // Found a void element — scan to end of tag, respecting quotes
                    int tagEnd = findTagEnd(xml, tagNameEnd, len);
                    if (tagEnd >= 0) {
                        // Check if already self-closed
                        boolean selfClosed = tagEnd > 0 && xml.charAt(tagEnd - 1) == '/';
                        if (selfClosed) {
                            // Already <tag ... /> — copy as-is including '>'
                            sb.append(xml, i, tagEnd + 1);
                        } else {
                            // Unclosed <tag ...> — make self-closing
                            sb.append(xml, i, tagEnd);
                            sb.append(" />");
                        }
                        i = tagEnd + 1;
                        continue;
                    }
                }
            }

            sb.append(ch);
            i++;
        }

        return sb.toString();
    }

    /**
     * Finds the position of the closing {@code >} of a tag, correctly
     * skipping over quoted attribute values that may contain {@code >}.
     *
     * @param xml  the XML string
     * @param from the position to start scanning (after the tag name)
     * @param len  the string length
     * @return the index of the closing {@code >}, or {@code -1} if not found
     */
    private static int findTagEnd(String xml, int from, int len) {
        int pos = from;
        while (pos < len) {
            char ch = xml.charAt(pos);
            if (ch == '"' || ch == '\'') {
                // Skip quoted attribute value
                pos = skipQuoted(xml, pos, len, ch);
                if (pos < 0) return -1; // unterminated quote
            } else if (ch == '>') {
                return pos;
            } else {
                pos++;
            }
        }
        return -1; // tag never closed
    }

    /**
     * Skips past a quoted string starting at the given position.
     *
     * @param xml   the XML string
     * @param from  the position of the opening quote character
     * @param len   the string length
     * @param quote the quote character ({@code '"'} or {@code '\''})
     * @return the position after the closing quote, or {@code -1} if unterminated
     */
    private static int skipQuoted(String xml, int from, int len, char quote) {
        int pos = from + 1;
        while (pos < len) {
            if (xml.charAt(pos) == quote) {
                return pos + 1;
            }
            pos++;
        }
        return -1;
    }
}
