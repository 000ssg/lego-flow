package ssg.legoflow.email.imap.server;

import ssg.legoflow.email.imap.protocol.FetchDataItem;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Handles FETCH command processing, extracting requested data items from messages.
 *
 * <p>Supports all standard FETCH data items: FLAGS, INTERNALDATE, RFC822.SIZE,
 * ENVELOPE, BODY, BODYSTRUCTURE, BODY[section], and partial fetches.
 *
 * @since 1.0.0
 */
public final class FetchHandler {

    private static final DateTimeFormatter IMAP_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss Z", Locale.US);

    private FetchHandler() {
    }

    /**
     * Fetches the requested data items from a message.
     *
     * @param msg      the stored message
     * @param seqNum   the sequence number
     * @param items    the data items to fetch
     * @param markSeen whether to set the \Seen flag on non-peek body fetches
     * @return the formatted FETCH response data
     */
    public static String fetch(StoredMessage msg, int seqNum, List<FetchDataItem> items,
                               boolean markSeen) {
        StringBuilder sb = new StringBuilder();
        sb.append("* ").append(seqNum).append(" FETCH (");

        boolean first = true;
        for (FetchDataItem item : items) {
            if (!first) sb.append(' ');
            first = false;

            String data = fetchItem(msg, item, markSeen);
            sb.append(data);
        }

        sb.append(')');
        return sb.toString();
    }

    /**
     * Fetches a single data item from a message.
     *
     * @param msg      the message
     * @param item     the data item
     * @param markSeen whether to set \Seen on body fetch
     * @return the formatted data
     */
    public static String fetchItem(StoredMessage msg, FetchDataItem item, boolean markSeen) {
        return switch (item.name()) {
            case "FLAGS" -> "FLAGS " + formatFlags(msg.flags());
            case "INTERNALDATE" -> "INTERNALDATE \"" + formatInternalDate(msg) + "\"";
            case "RFC822.SIZE" -> "RFC822.SIZE " + msg.size();
            case "ENVELOPE" -> "ENVELOPE " + formatEnvelope(msg);
            case "BODYSTRUCTURE" -> "BODYSTRUCTURE " + formatBodyStructure(msg, true);
            case "UID" -> "UID " + msg.uid();
            case "RFC822" -> {
                if (markSeen) msg.addFlag("\\Seen");
                yield "RFC822 {" + msg.size() + "}\r\n" + msg.contentAsString();
            }
            case "RFC822.HEADER" -> {
                String headers = msg.getHeaders();
                yield "RFC822.HEADER {" + headers.length() + "}\r\n" + headers;
            }
            case "RFC822.TEXT" -> {
                if (markSeen) msg.addFlag("\\Seen");
                String body = msg.getBody();
                yield "RFC822.TEXT {" + body.length() + "}\r\n" + body;
            }
            case "BODY" -> {
                if (item.hasSection()) {
                    String data = fetchBodySection(msg, item);
                    if (!item.isPeek() && markSeen) {
                        msg.addFlag("\\Seen");
                    }
                    yield data;
                } else {
                    yield "BODY " + formatBodyStructure(msg, false);
                }
            }
            default -> item.name() + " NIL";
        };
    }

    private static String fetchBodySection(StoredMessage msg, FetchDataItem item) {
        String section = item.section();
        String prefix = item.isPeek() ? "BODY[" : "BODY[";
        String data;

        if (section == null || section.isEmpty()) {
            // Full message
            data = msg.contentAsString();
        } else if (section.equalsIgnoreCase("HEADER")) {
            data = msg.getHeaders();
        } else if (section.equalsIgnoreCase("TEXT")) {
            data = msg.getBody();
        } else if (section.toUpperCase().startsWith("HEADER.FIELDS")) {
            data = fetchHeaderFields(msg, section);
        } else if (section.toUpperCase().startsWith("HEADER.FIELDS.NOT")) {
            data = fetchHeaderFieldsNot(msg, section);
        } else {
            // MIME part number (e.g., "1", "1.2")
            data = fetchMimePart(msg, section);
        }

        // Apply partial if requested
        if (item.isPartial()) {
            int offset = (int) item.partialOffset();
            int length = (int) item.partialLength();
            if (offset < data.length()) {
                int end = Math.min(offset + length, data.length());
                data = data.substring(offset, end);
                prefix = "BODY[" + (section != null ? section : "") + "]<" + offset + ">";
            } else {
                data = "";
            }
        } else {
            prefix = "BODY[" + (section != null ? section : "") + "]";
        }

        return prefix + " {" + data.length() + "}\r\n" + data;
    }

    private static String fetchHeaderFields(StoredMessage msg, String section) {
        // Extract field names from HEADER.FIELDS (field1 field2 ...)
        int parenStart = section.indexOf('(');
        int parenEnd = section.indexOf(')');
        if (parenStart < 0 || parenEnd < 0) return "";

        String[] fields = section.substring(parenStart + 1, parenEnd).split("\\s+");
        Set<String> fieldSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Collections.addAll(fieldSet, fields);

        return filterHeaders(msg, fieldSet, true);
    }

    private static String fetchHeaderFieldsNot(StoredMessage msg, String section) {
        int parenStart = section.indexOf('(');
        int parenEnd = section.indexOf(')');
        if (parenStart < 0 || parenEnd < 0) return msg.getHeaders();

        String[] fields = section.substring(parenStart + 1, parenEnd).split("\\s+");
        Set<String> fieldSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Collections.addAll(fieldSet, fields);

        return filterHeaders(msg, fieldSet, false);
    }

    private static String filterHeaders(StoredMessage msg, Set<String> fields, boolean include) {
        StringBuilder result = new StringBuilder();
        String headers = msg.getHeaders();
        String[] lines = headers.split("\r?\n");
        String currentField = null;
        StringBuilder currentValue = new StringBuilder();

        for (String line : lines) {
            if (line.isEmpty()) break;
            if (line.startsWith(" ") || line.startsWith("\t")) {
                // Continuation line
                if (currentField != null) {
                    currentValue.append("\r\n").append(line);
                }
            } else {
                // New header field
                if (currentField != null) {
                    boolean inSet = fields.contains(currentField);
                    if (include == inSet) {
                        result.append(currentField).append(":").append(currentValue).append("\r\n");
                    }
                }
                int colon = line.indexOf(':');
                if (colon > 0) {
                    currentField = line.substring(0, colon);
                    currentValue = new StringBuilder(line.substring(colon + 1));
                }
            }
        }
        // Last header
        if (currentField != null) {
            boolean inSet = fields.contains(currentField);
            if (include == inSet) {
                result.append(currentField).append(":").append(currentValue).append("\r\n");
            }
        }
        result.append("\r\n");
        return result.toString();
    }

    private static String fetchMimePart(StoredMessage msg, String partNumber) {
        // Simplified MIME part extraction -- for single-part messages, "1" returns the body
        if ("1".equals(partNumber)) {
            return msg.getBody();
        }
        // For multipart, a full MIME parser would be needed (from email-common)
        // Return empty for parts we cannot resolve
        return "";
    }

    static String formatFlags(Set<String> flags) {
        if (flags.isEmpty()) return "()";
        return "(" + String.join(" ", new TreeSet<>(flags)) + ")";
    }

    static String formatInternalDate(StoredMessage msg) {
        return IMAP_DATE_FORMAT.format(msg.internalDate().atOffset(ZoneOffset.UTC));
    }

    /**
     * Formats the ENVELOPE data for a message.
     *
     * @param msg the message
     * @return the formatted envelope
     */
    public static String formatEnvelope(StoredMessage msg) {
        StringBuilder sb = new StringBuilder("(");
        sb.append(quote(msg.getHeader("Date"))).append(' ');
        sb.append(quote(msg.getHeader("Subject"))).append(' ');
        sb.append(formatAddressList(msg.getHeader("From"))).append(' ');
        sb.append(formatAddressList(msg.getHeader("Sender"))).append(' ');
        sb.append(formatAddressList(msg.getHeader("Reply-To"))).append(' ');
        sb.append(formatAddressList(msg.getHeader("To"))).append(' ');
        sb.append(formatAddressList(msg.getHeader("Cc"))).append(' ');
        sb.append(formatAddressList(msg.getHeader("Bcc"))).append(' ');
        sb.append(quote(msg.getHeader("In-Reply-To"))).append(' ');
        sb.append(quote(msg.getHeader("Message-ID")));
        sb.append(')');
        return sb.toString();
    }

    /**
     * Formats a simple BODYSTRUCTURE for a message.
     *
     * @param msg      the message
     * @param extended true for extended (BODYSTRUCTURE), false for basic (BODY)
     * @return the formatted body structure
     */
    public static String formatBodyStructure(StoredMessage msg, boolean extended) {
        String contentType = msg.getHeader("Content-Type");
        if (contentType == null) contentType = "text/plain";

        String type, subtype;
        int slashIdx = contentType.indexOf('/');
        if (slashIdx > 0) {
            type = contentType.substring(0, slashIdx).trim();
            int semiIdx = contentType.indexOf(';', slashIdx);
            subtype = semiIdx > 0
                    ? contentType.substring(slashIdx + 1, semiIdx).trim()
                    : contentType.substring(slashIdx + 1).trim();
        } else {
            type = "TEXT";
            subtype = "PLAIN";
        }

        StringBuilder sb = new StringBuilder("(");
        sb.append(quote(type.toUpperCase())).append(' ');
        sb.append(quote(subtype.toUpperCase())).append(' ');
        sb.append("(\"CHARSET\" \"UTF-8\") "); // body params
        sb.append("NIL "); // body id
        sb.append("NIL "); // body description
        sb.append("\"7BIT\" "); // encoding
        sb.append(msg.size()); // size
        if (type.equalsIgnoreCase("TEXT")) {
            // Text types include line count
            long lineCount = msg.getBody().chars().filter(c -> c == '\n').count();
            sb.append(' ').append(lineCount);
        }
        if (extended) {
            sb.append(" NIL NIL NIL NIL"); // md5, disposition, language, location
        }
        sb.append(')');
        return sb.toString();
    }

    private static String quote(String value) {
        if (value == null) return "NIL";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String formatAddressList(String header) {
        if (header == null || header.isBlank()) return "NIL";
        // Simplified: just wrap in address structure
        // Full implementation would parse RFC 5322 addresses
        return "((NIL NIL " + quote(header) + " NIL))";
    }
}
