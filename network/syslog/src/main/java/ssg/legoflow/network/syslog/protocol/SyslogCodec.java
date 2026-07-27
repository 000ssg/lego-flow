package ssg.legoflow.network.syslog.protocol;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Codec for encoding and decoding RFC 5424 syslog messages.
 *
 * <p>Handles the full RFC 5424 message format including structured data
 * encoding and decoding with proper escaping of special characters.
 *
 * @since 1.0.0
 */
public final class SyslogCodec {

    private static final String NILVALUE = "-";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX");

    private SyslogCodec() {}

    /**
     * Encodes a syslog message to its RFC 5424 string representation.
     *
     * @param msg the message to encode
     * @return the encoded string
     */
    public static String encode(SyslogMessage msg) {
        var sb = new StringBuilder();
        sb.append('<').append(msg.pri()).append('>');
        sb.append(SyslogMessage.VERSION).append(' ');
        sb.append(formatTimestamp(msg.timestamp())).append(' ');
        sb.append(nilOr(msg.hostname())).append(' ');
        sb.append(nilOr(msg.appName())).append(' ');
        sb.append(nilOr(msg.procId())).append(' ');
        sb.append(nilOr(msg.msgId())).append(' ');

        if (msg.structuredData().isEmpty()) {
            sb.append(NILVALUE);
        } else {
            for (StructuredData sd : msg.structuredData()) {
                sb.append(sd.encode());
            }
        }

        if (msg.message() != null) {
            sb.append(' ').append(msg.message());
        }

        return sb.toString();
    }

    /**
     * Encodes a syslog message to bytes using UTF-8.
     *
     * @param msg the message to encode
     * @return the encoded bytes
     */
    public static byte[] encodeToBytes(SyslogMessage msg) {
        return encode(msg).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Decodes an RFC 5424 syslog message from its string representation.
     *
     * @param text the message string
     * @return the decoded message
     * @throws SyslogParseException if the message is malformed
     */
    public static SyslogMessage decode(String text) {
        if (text == null || text.isEmpty()) {
            throw new SyslogParseException("Message is null or empty");
        }

        var parser = new MessageParser(text);
        int pri = parser.parsePri();
        int facility = pri / 8;
        int severity = pri % 8;

        int version = parser.parseVersion();
        if (version != SyslogMessage.VERSION) {
            throw new SyslogParseException("Unsupported version: " + version);
        }

        parser.expectSpace();
        String timestampStr = parser.parseField();
        Instant timestamp = parseTimestamp(timestampStr);

        parser.expectSpace();
        String hostname = parseNilable(parser.parseField());

        parser.expectSpace();
        String appName = parseNilable(parser.parseField());

        parser.expectSpace();
        String procId = parseNilable(parser.parseField());

        parser.expectSpace();
        String msgId = parseNilable(parser.parseField());

        parser.expectSpace();
        List<StructuredData> structuredData = parser.parseStructuredData();

        String message = null;
        if (parser.hasMore()) {
            parser.expectSpace();
            message = parser.remaining();
        }

        return new SyslogMessage(
                Facility.of(facility),
                Severity.of(severity),
                timestamp, hostname, appName, procId, msgId,
                structuredData, message
        );
    }

    /**
     * Decodes an RFC 5424 syslog message from bytes.
     *
     * @param data the message bytes
     * @return the decoded message
     * @throws SyslogParseException if the message is malformed
     */
    public static SyslogMessage decode(byte[] data) {
        return decode(new String(data, StandardCharsets.UTF_8));
    }

    private static String formatTimestamp(Instant timestamp) {
        if (timestamp == null) {
            return NILVALUE;
        }
        return TIMESTAMP_FORMAT.format(timestamp.atOffset(ZoneOffset.UTC));
    }

    private static String nilOr(String value) {
        return value != null ? value : NILVALUE;
    }

    private static String parseNilable(String value) {
        return NILVALUE.equals(value) ? null : value;
    }

    private static Instant parseTimestamp(String value) {
        if (NILVALUE.equals(value)) {
            return null;
        }
        try {
            return Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value));
        } catch (DateTimeParseException e) {
            throw new SyslogParseException("Invalid timestamp: " + value, e);
        }
    }

    /**
     * Internal parser for RFC 5424 messages.
     */
    private static final class MessageParser {
        private final String text;
        private int pos;

        MessageParser(String text) {
            this.text = text;
            this.pos = 0;
        }

        int parsePri() {
            if (pos >= text.length() || text.charAt(pos) != '<') {
                throw new SyslogParseException("Expected '<' at position " + pos);
            }
            pos++;
            int end = text.indexOf('>', pos);
            if (end < 0) {
                throw new SyslogParseException("Missing '>' for PRI");
            }
            String priStr = text.substring(pos, end);
            pos = end + 1;
            try {
                int pri = Integer.parseInt(priStr);
                if (pri < 0 || pri > 191) {
                    throw new SyslogParseException("PRI out of range: " + pri);
                }
                return pri;
            } catch (NumberFormatException e) {
                throw new SyslogParseException("Invalid PRI: " + priStr, e);
            }
        }

        int parseVersion() {
            if (pos >= text.length()) {
                throw new SyslogParseException("Expected version at position " + pos);
            }
            char c = text.charAt(pos);
            if (c < '1' || c > '9') {
                throw new SyslogParseException("Invalid version character: " + c);
            }
            pos++;
            return c - '0';
        }

        void expectSpace() {
            if (pos >= text.length() || text.charAt(pos) != ' ') {
                throw new SyslogParseException("Expected space at position " + pos);
            }
            pos++;
        }

        String parseField() {
            int start = pos;
            while (pos < text.length() && text.charAt(pos) != ' ') {
                pos++;
            }
            if (pos == start) {
                throw new SyslogParseException("Empty field at position " + start);
            }
            return text.substring(start, pos);
        }

        List<StructuredData> parseStructuredData() {
            if (pos >= text.length()) {
                return List.of();
            }
            if (text.charAt(pos) == '-') {
                pos++;
                return List.of();
            }
            List<StructuredData> result = new ArrayList<>();
            while (pos < text.length() && text.charAt(pos) == '[') {
                result.add(parseStructuredDataElement());
            }
            return result;
        }

        private StructuredData parseStructuredDataElement() {
            pos++; // skip '['
            int idStart = pos;
            while (pos < text.length() && text.charAt(pos) != ' ' && text.charAt(pos) != ']') {
                pos++;
            }
            String id = text.substring(idStart, pos);
            Map<String, String> params = new LinkedHashMap<>();
            while (pos < text.length() && text.charAt(pos) == ' ') {
                pos++; // skip space
                int nameStart = pos;
                while (pos < text.length() && text.charAt(pos) != '=') {
                    pos++;
                }
                String name = text.substring(nameStart, pos);
                pos++; // skip '='
                if (pos >= text.length() || text.charAt(pos) != '"') {
                    throw new SyslogParseException("Expected '\"' at position " + pos);
                }
                pos++; // skip opening quote
                var value = new StringBuilder();
                while (pos < text.length() && text.charAt(pos) != '"') {
                    if (text.charAt(pos) == '\\' && pos + 1 < text.length()) {
                        pos++;
                        value.append(text.charAt(pos));
                    } else {
                        value.append(text.charAt(pos));
                    }
                    pos++;
                }
                pos++; // skip closing quote
                params.put(name, value.toString());
            }
            if (pos < text.length() && text.charAt(pos) == ']') {
                pos++;
            }
            return StructuredData.of(id, params);
        }

        boolean hasMore() {
            return pos < text.length();
        }

        String remaining() {
            String r = text.substring(pos);
            pos = text.length();
            return r;
        }
    }
}
