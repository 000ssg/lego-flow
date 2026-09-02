package ssg.legoflow.database.redis.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
/**
 * Streaming RESP parser that reads from an {@link InputStream}.
 *
 * <p>Handles partial reads by blocking until a complete RESP message
 * is available. Supports both RESP2 and RESP3 type prefixes.
 *
 * @since 0.1.0
 */
public final class RespParser {

    private final InputStream in;

    /**
     * Creates a parser reading from the given input stream.
     *
     * @param in the input stream
     */
    public RespParser(InputStream in) {
        this.in = Objects.requireNonNull(in);
    }

    /**
     * Reads and parses the next complete RESP message.
     *
     * @return the parsed RESP type, or null on end-of-stream
     * @throws IOException if reading fails
     * @throws RespParseException if the data is malformed
     */
    public RespType parse() throws IOException {
        int prefix = in.read();
        if (prefix == -1) {
            return null;
        }
        return switch (prefix) {
            case '+' -> parseSimpleString();
            case '-' -> parseError();
            case ':' -> parseInteger();
            case '$' -> parseBulkString();
            case '*' -> parseArray();
            case '_' -> parseNull();
            case ',' -> parseDouble();
            case '#' -> parseBoolean();
            case '(' -> parseBigNumber();
            case '!' -> parseBlobError();
            case '=' -> parseVerbatimString();
            case '%' -> parseMap();
            case '~' -> parseSet();
            case '|' -> parseAttribute();
            case '>' -> parsePush();
            default -> parseInlineCommand(prefix);
        };
    }

    private RespType.SimpleString parseSimpleString() throws IOException {
        return new RespType.SimpleString(readLine());
    }

    private RespType.Error parseError() throws IOException {
        String line = readLine();
        int space = line.indexOf(' ');
        if (space < 0) {
            return new RespType.Error(line, "");
        }
        return new RespType.Error(line.substring(0, space), line.substring(space + 1));
    }

    private RespType.Integer parseInteger() throws IOException {
        return new RespType.Integer(Long.parseLong(readLine()));
    }

    private RespType.BulkString parseBulkString() throws IOException {
        int len = java.lang.Integer.parseInt(readLine());
        if (len < 0) {
            return RespType.BulkString.NULL;
        }
        byte[] data = readExact(len);
        readLine(); // consume trailing CRLF
        return new RespType.BulkString(data);
    }

    private RespType.Array parseArray() throws IOException {
        int count = java.lang.Integer.parseInt(readLine());
        if (count < 0) {
            return RespType.Array.NULL;
        }
        List<RespType> elements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            elements.add(parse());
        }
        return new RespType.Array(Collections.unmodifiableList(elements));
    }

    private RespType.Null parseNull() throws IOException {
        readLine(); // consume CRLF
        return RespType.Null.INSTANCE;
    }

    private RespType.RespDouble parseDouble() throws IOException {
        String line = readLine();
        double value = switch (line) {
            case "inf" -> Double.POSITIVE_INFINITY;
            case "-inf" -> Double.NEGATIVE_INFINITY;
            default -> Double.parseDouble(line);
        };
        return new RespType.RespDouble(value);
    }

    private RespType.RespBoolean parseBoolean() throws IOException {
        String line = readLine();
        return new RespType.RespBoolean("t".equals(line));
    }

    private RespType.BigNumber parseBigNumber() throws IOException {
        return new RespType.BigNumber(new BigInteger(readLine()));
    }

    private RespType.BlobError parseBlobError() throws IOException {
        int len = java.lang.Integer.parseInt(readLine());
        byte[] data = readExact(len);
        readLine(); // consume trailing CRLF
        return new RespType.BlobError(data);
    }

    private RespType.VerbatimString parseVerbatimString() throws IOException {
        int len = java.lang.Integer.parseInt(readLine());
        byte[] data = readExact(len);
        readLine(); // consume trailing CRLF
        String content = new String(data, StandardCharsets.UTF_8);
        if (content.length() < 4 || content.charAt(3) != ':') {
            throw new RespParseException("Invalid verbatim string format: missing encoding prefix");
        }
        return new RespType.VerbatimString(content.substring(0, 3), content.substring(4));
    }

    private RespType.RespMap parseMap() throws IOException {
        int count = java.lang.Integer.parseInt(readLine());
        Map<RespType, RespType> entries = new LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            RespType key = parse();
            RespType value = parse();
            entries.put(key, value);
        }
        return new RespType.RespMap(Collections.unmodifiableMap(entries));
    }

    private RespType.RespSet parseSet() throws IOException {
        int count = java.lang.Integer.parseInt(readLine());
        Set<RespType> elements = new LinkedHashSet<>(count);
        for (int i = 0; i < count; i++) {
            elements.add(parse());
        }
        return new RespType.RespSet(Collections.unmodifiableSet(elements));
    }

    private RespType.Attribute parseAttribute() throws IOException {
        int count = java.lang.Integer.parseInt(readLine());
        Map<RespType, RespType> attributes = new LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            RespType key = parse();
            RespType value = parse();
            attributes.put(key, value);
        }
        return new RespType.Attribute(Collections.unmodifiableMap(attributes));
    }

    private RespType.Push parsePush() throws IOException {
        int count = java.lang.Integer.parseInt(readLine());
        List<RespType> elements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            elements.add(parse());
        }
        return new RespType.Push(Collections.unmodifiableList(elements));
    }

    /**
     * Parses an inline command (non-RESP protocol, space-separated).
     * The first byte has already been read as {@code firstByte}.
     */
    private RespType parseInlineCommand(int firstByte) throws IOException {
        var sb = new StringBuilder();
        sb.append((char) firstByte);
        String rest = readLine();
        sb.append(rest);
        String line = sb.toString().trim();
        if (line.isEmpty()) {
            return parse(); // skip empty lines
        }
        String[] parts = line.split("\\s+");
        List<RespType> elements = new ArrayList<>(parts.length);
        for (String part : parts) {
            elements.add(RespType.BulkString.of(part));
        }
        return new RespType.Array(Collections.unmodifiableList(elements));
    }

    // ---- I/O helpers ----

    private String readLine() throws IOException {
        var sb = new ByteArrayOutputStream(64);
        int prev = -1;
        while (true) {
            int b = in.read();
            if (b == -1) {
                throw new RespParseException("Unexpected end of stream while reading line");
            }
            if (b == '\n' && prev == '\r') {
                // Remove the trailing \r
                byte[] bytes = sb.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
            }
            sb.write(b);
            prev = b;
        }
    }

    private byte[] readExact(int len) throws IOException {
        byte[] data = new byte[len];
        int offset = 0;
        while (offset < len) {
            int read = in.read(data, offset, len - offset);
            if (read == -1) {
                throw new RespParseException("Unexpected end of stream, expected " + len + " bytes");
            }
            offset += read;
        }
        return data;
    }
}
