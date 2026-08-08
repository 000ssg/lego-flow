package ssg.legoflow.messaging.nats.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * NATS text protocol parser and serializer.
 *
 * <p>Handles encoding and decoding of all NATS protocol operations:
 * INFO, CONNECT, PUB, HPUB, SUB, UNSUB, MSG, HMSG, +OK, -ERR, PING, PONG.
 *
 * <p>The NATS protocol is line-oriented with CRLF terminators. Operations
 * with payloads include a size field followed by the payload data and another CRLF.
 *
 * @since 0.1.0
 */
public final class NatsCodec {

    private NatsCodec() {
        // utility class
    }

    // --- Encoding methods ---

    /**
     * Encodes an INFO operation.
     *
     * @param info the server info
     * @return the encoded INFO line
     */
    public static String encodeInfo(ServerInfo info) {
        return NatsProtocol.OP_INFO + " " + info.toJson() + NatsProtocol.CRLF;
    }

    /**
     * Encodes a CONNECT operation.
     *
     * @param options the connect options
     * @return the encoded CONNECT line
     */
    public static String encodeConnect(ConnectOptions options) {
        return NatsProtocol.OP_CONNECT + " " + options.toJson() + NatsProtocol.CRLF;
    }

    /**
     * Encodes a PUB operation.
     *
     * @param subject the subject
     * @param replyTo the reply-to subject, or null
     * @param payload the message payload
     * @return the encoded PUB message
     */
    public static String encodePub(String subject, String replyTo, byte[] payload) {
        var sb = new StringBuilder();
        sb.append(NatsProtocol.OP_PUB).append(' ').append(subject);
        if (replyTo != null) {
            sb.append(' ').append(replyTo);
        }
        sb.append(' ').append(payload != null ? payload.length : 0);
        sb.append(NatsProtocol.CRLF);
        if (payload != null && payload.length > 0) {
            sb.append(new String(payload, StandardCharsets.UTF_8));
        }
        sb.append(NatsProtocol.CRLF);
        return sb.toString();
    }

    /**
     * Encodes an HPUB operation.
     *
     * @param subject the subject
     * @param replyTo the reply-to subject, or null
     * @param headers the message headers
     * @param payload the message payload
     * @return the encoded HPUB message
     */
    public static String encodeHpub(String subject, String replyTo, NatsHeaders headers, byte[] payload) {
        String hdrBlock = headers.serialize();
        byte[] hdrBytes = hdrBlock.getBytes(StandardCharsets.UTF_8);
        int hdrSize = hdrBytes.length;
        int totalSize = hdrSize + (payload != null ? payload.length : 0);

        var sb = new StringBuilder();
        sb.append(NatsProtocol.OP_HPUB).append(' ').append(subject);
        if (replyTo != null) {
            sb.append(' ').append(replyTo);
        }
        sb.append(' ').append(hdrSize).append(' ').append(totalSize);
        sb.append(NatsProtocol.CRLF);
        sb.append(hdrBlock);
        if (payload != null && payload.length > 0) {
            sb.append(new String(payload, StandardCharsets.UTF_8));
        }
        sb.append(NatsProtocol.CRLF);
        return sb.toString();
    }

    /**
     * Encodes a SUB operation.
     *
     * @param subject    the subject to subscribe to
     * @param queueGroup the queue group, or null
     * @param sid        the subscription ID
     * @return the encoded SUB line
     */
    public static String encodeSub(String subject, String queueGroup, String sid) {
        var sb = new StringBuilder();
        sb.append(NatsProtocol.OP_SUB).append(' ').append(subject);
        if (queueGroup != null) {
            sb.append(' ').append(queueGroup);
        }
        sb.append(' ').append(sid);
        sb.append(NatsProtocol.CRLF);
        return sb.toString();
    }

    /**
     * Encodes an UNSUB operation.
     *
     * @param sid     the subscription ID
     * @param maxMsgs the maximum messages before auto-unsub, or -1 for immediate
     * @return the encoded UNSUB line
     */
    public static String encodeUnsub(String sid, int maxMsgs) {
        var sb = new StringBuilder();
        sb.append(NatsProtocol.OP_UNSUB).append(' ').append(sid);
        if (maxMsgs > 0) {
            sb.append(' ').append(maxMsgs);
        }
        sb.append(NatsProtocol.CRLF);
        return sb.toString();
    }

    /**
     * Encodes a MSG operation.
     *
     * @param subject the message subject
     * @param sid     the subscription ID
     * @param replyTo the reply-to subject, or null
     * @param payload the message payload
     * @return the encoded MSG message
     */
    public static String encodeMsg(String subject, String sid, String replyTo, byte[] payload) {
        var sb = new StringBuilder();
        sb.append(NatsProtocol.OP_MSG).append(' ').append(subject);
        sb.append(' ').append(sid);
        if (replyTo != null) {
            sb.append(' ').append(replyTo);
        }
        sb.append(' ').append(payload != null ? payload.length : 0);
        sb.append(NatsProtocol.CRLF);
        if (payload != null && payload.length > 0) {
            sb.append(new String(payload, StandardCharsets.UTF_8));
        }
        sb.append(NatsProtocol.CRLF);
        return sb.toString();
    }

    /**
     * Encodes an HMSG operation.
     *
     * @param subject the message subject
     * @param sid     the subscription ID
     * @param replyTo the reply-to subject, or null
     * @param headers the message headers
     * @param payload the message payload
     * @return the encoded HMSG message
     */
    public static String encodeHmsg(String subject, String sid, String replyTo,
                                     NatsHeaders headers, byte[] payload) {
        String hdrBlock = headers.serialize();
        byte[] hdrBytes = hdrBlock.getBytes(StandardCharsets.UTF_8);
        int hdrSize = hdrBytes.length;
        int totalSize = hdrSize + (payload != null ? payload.length : 0);

        var sb = new StringBuilder();
        sb.append(NatsProtocol.OP_HMSG).append(' ').append(subject);
        sb.append(' ').append(sid);
        if (replyTo != null) {
            sb.append(' ').append(replyTo);
        }
        sb.append(' ').append(hdrSize).append(' ').append(totalSize);
        sb.append(NatsProtocol.CRLF);
        sb.append(hdrBlock);
        if (payload != null && payload.length > 0) {
            sb.append(new String(payload, StandardCharsets.UTF_8));
        }
        sb.append(NatsProtocol.CRLF);
        return sb.toString();
    }

    /**
     * Encodes a PING operation.
     *
     * @return the PING line
     */
    public static String encodePing() {
        return NatsProtocol.OP_PING + NatsProtocol.CRLF;
    }

    /**
     * Encodes a PONG operation.
     *
     * @return the PONG line
     */
    public static String encodePong() {
        return NatsProtocol.OP_PONG + NatsProtocol.CRLF;
    }

    /**
     * Encodes an +OK operation.
     *
     * @return the +OK line
     */
    public static String encodeOk() {
        return NatsProtocol.OP_OK + NatsProtocol.CRLF;
    }

    /**
     * Encodes an -ERR operation.
     *
     * @param message the error message
     * @return the -ERR line
     */
    public static String encodeErr(String message) {
        return NatsProtocol.OP_ERR + " '" + message + "'" + NatsProtocol.CRLF;
    }

    // --- Decoding methods ---

    /**
     * Parsed protocol operation, which is a sealed hierarchy of message types.
     *
     * @since 0.1.0
     */
    public sealed interface ParsedOp {
        /** INFO operation with server info. */
        record Info(ServerInfo serverInfo) implements ParsedOp {}
        /** CONNECT operation with client options. */
        record Connect(ConnectOptions options) implements ParsedOp {}
        /** PUB operation. */
        record Pub(String subject, String replyTo, byte[] payload) implements ParsedOp {}
        /** HPUB operation with headers. */
        record Hpub(String subject, String replyTo, NatsHeaders headers, byte[] payload) implements ParsedOp {}
        /** SUB operation. */
        record Sub(String subject, String queueGroup, String sid) implements ParsedOp {}
        /** UNSUB operation. */
        record Unsub(String sid, int maxMsgs) implements ParsedOp {}
        /** MSG operation. */
        record Msg(String subject, String sid, String replyTo, byte[] payload) implements ParsedOp {}
        /** HMSG operation with headers. */
        record Hmsg(String subject, String sid, String replyTo, NatsHeaders headers, byte[] payload) implements ParsedOp {}
        /** PING operation. */
        record Ping() implements ParsedOp {}
        /** PONG operation. */
        record Pong() implements ParsedOp {}
        /** +OK operation. */
        record Ok() implements ParsedOp {}
        /** -ERR operation. */
        record Err(String message) implements ParsedOp {}
    }

    /**
     * Reads and parses the next operation from the input stream.
     *
     * @param reader the buffered reader
     * @return the parsed operation, or null on EOF
     * @throws IOException if an I/O error occurs
     */
    public static ParsedOp readOp(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) return null;

        // Strip trailing \r if present (readLine strips \n)
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }

        if (line.startsWith(NatsProtocol.OP_INFO + " ")) {
            String json = line.substring(NatsProtocol.OP_INFO.length() + 1);
            return new ParsedOp.Info(ServerInfo.fromJson(json));
        }

        if (line.startsWith(NatsProtocol.OP_CONNECT + " ")) {
            String json = line.substring(NatsProtocol.OP_CONNECT.length() + 1);
            return new ParsedOp.Connect(ConnectOptions.fromJson(json));
        }

        if (line.startsWith(NatsProtocol.OP_HPUB + " ")) {
            return parseHpub(line, reader);
        }

        if (line.startsWith(NatsProtocol.OP_PUB + " ")) {
            return parsePub(line, reader);
        }

        if (line.startsWith(NatsProtocol.OP_SUB + " ")) {
            return parseSub(line);
        }

        if (line.startsWith(NatsProtocol.OP_UNSUB + " ")) {
            return parseUnsub(line);
        }

        if (line.startsWith(NatsProtocol.OP_HMSG + " ")) {
            return parseHmsg(line, reader);
        }

        if (line.startsWith(NatsProtocol.OP_MSG + " ")) {
            return parseMsg(line, reader);
        }

        if (line.equals(NatsProtocol.OP_PING)) {
            return new ParsedOp.Ping();
        }

        if (line.equals(NatsProtocol.OP_PONG)) {
            return new ParsedOp.Pong();
        }

        if (line.equals(NatsProtocol.OP_OK)) {
            return new ParsedOp.Ok();
        }

        if (line.startsWith(NatsProtocol.OP_ERR)) {
            String msg = line.substring(NatsProtocol.OP_ERR.length()).trim();
            // Remove surrounding quotes
            if (msg.startsWith("'") && msg.endsWith("'")) {
                msg = msg.substring(1, msg.length() - 1);
            }
            return new ParsedOp.Err(msg);
        }

        throw new IOException("Unknown NATS operation: " + line);
    }

    private static ParsedOp.Pub parsePub(String line, BufferedReader reader) throws IOException {
        // PUB subject [reply-to] size
        String[] parts = line.substring(NatsProtocol.OP_PUB.length() + 1).split(" ");
        String subject;
        String replyTo = null;
        int size;

        if (parts.length == 2) {
            subject = parts[0];
            size = Integer.parseInt(parts[1]);
        } else if (parts.length == 3) {
            subject = parts[0];
            replyTo = parts[1];
            size = Integer.parseInt(parts[2]);
        } else {
            throw new IOException("Invalid PUB format: " + line);
        }

        byte[] payload = readPayload(reader, size);
        return new ParsedOp.Pub(subject, replyTo, payload);
    }

    private static ParsedOp.Hpub parseHpub(String line, BufferedReader reader) throws IOException {
        // HPUB subject [reply-to] hdr_size total_size
        String[] parts = line.substring(NatsProtocol.OP_HPUB.length() + 1).split(" ");
        String subject;
        String replyTo = null;
        int hdrSize;
        int totalSize;

        if (parts.length == 3) {
            subject = parts[0];
            hdrSize = Integer.parseInt(parts[1]);
            totalSize = Integer.parseInt(parts[2]);
        } else if (parts.length == 4) {
            subject = parts[0];
            replyTo = parts[1];
            hdrSize = Integer.parseInt(parts[2]);
            totalSize = Integer.parseInt(parts[3]);
        } else {
            throw new IOException("Invalid HPUB format: " + line);
        }

        // Read combined header + payload
        byte[] combined = readPayload(reader, totalSize);
        String hdrBlock = new String(combined, 0, hdrSize, StandardCharsets.UTF_8);
        NatsHeaders headers = NatsHeaders.parse(hdrBlock);
        byte[] payload = new byte[totalSize - hdrSize];
        System.arraycopy(combined, hdrSize, payload, 0, payload.length);

        return new ParsedOp.Hpub(subject, replyTo, headers, payload);
    }

    private static ParsedOp.Sub parseSub(String line) {
        // SUB subject [queue-group] sid
        String[] parts = line.substring(NatsProtocol.OP_SUB.length() + 1).split(" ");
        if (parts.length == 2) {
            return new ParsedOp.Sub(parts[0], null, parts[1]);
        } else if (parts.length == 3) {
            return new ParsedOp.Sub(parts[0], parts[1], parts[2]);
        }
        throw new IllegalArgumentException("Invalid SUB format: " + line);
    }

    private static ParsedOp.Unsub parseUnsub(String line) {
        // UNSUB sid [max_msgs]
        String[] parts = line.substring(NatsProtocol.OP_UNSUB.length() + 1).split(" ");
        if (parts.length == 1) {
            return new ParsedOp.Unsub(parts[0], -1);
        } else if (parts.length == 2) {
            return new ParsedOp.Unsub(parts[0], Integer.parseInt(parts[1]));
        }
        throw new IllegalArgumentException("Invalid UNSUB format: " + line);
    }

    private static ParsedOp.Msg parseMsg(String line, BufferedReader reader) throws IOException {
        // MSG subject sid [reply-to] size
        String[] parts = line.substring(NatsProtocol.OP_MSG.length() + 1).split(" ");
        String subject;
        String sid;
        String replyTo = null;
        int size;

        if (parts.length == 3) {
            subject = parts[0];
            sid = parts[1];
            size = Integer.parseInt(parts[2]);
        } else if (parts.length == 4) {
            subject = parts[0];
            sid = parts[1];
            replyTo = parts[2];
            size = Integer.parseInt(parts[3]);
        } else {
            throw new IOException("Invalid MSG format: " + line);
        }

        byte[] payload = readPayload(reader, size);
        return new ParsedOp.Msg(subject, sid, replyTo, payload);
    }

    private static ParsedOp.Hmsg parseHmsg(String line, BufferedReader reader) throws IOException {
        // HMSG subject sid [reply-to] hdr_size total_size
        String[] parts = line.substring(NatsProtocol.OP_HMSG.length() + 1).split(" ");
        String subject;
        String sid;
        String replyTo = null;
        int hdrSize;
        int totalSize;

        if (parts.length == 4) {
            subject = parts[0];
            sid = parts[1];
            hdrSize = Integer.parseInt(parts[2]);
            totalSize = Integer.parseInt(parts[3]);
        } else if (parts.length == 5) {
            subject = parts[0];
            sid = parts[1];
            replyTo = parts[2];
            hdrSize = Integer.parseInt(parts[3]);
            totalSize = Integer.parseInt(parts[4]);
        } else {
            throw new IOException("Invalid HMSG format: " + line);
        }

        byte[] combined = readPayload(reader, totalSize);
        String hdrBlock = new String(combined, 0, hdrSize, StandardCharsets.UTF_8);
        NatsHeaders headers = NatsHeaders.parse(hdrBlock);
        byte[] payload = new byte[totalSize - hdrSize];
        System.arraycopy(combined, hdrSize, payload, 0, payload.length);

        return new ParsedOp.Hmsg(subject, sid, replyTo, headers, payload);
    }

    private static byte[] readPayload(BufferedReader reader, int size) throws IOException {
        if (size == 0) {
            reader.readLine(); // consume trailing CRLF
            return new byte[0];
        }
        char[] buf = new char[size];
        int read = 0;
        while (read < size) {
            int n = reader.read(buf, read, size - read);
            if (n < 0) throw new IOException("Unexpected EOF reading payload");
            read += n;
        }
        reader.readLine(); // consume trailing CRLF
        return new String(buf).getBytes(StandardCharsets.UTF_8);
    }
}
