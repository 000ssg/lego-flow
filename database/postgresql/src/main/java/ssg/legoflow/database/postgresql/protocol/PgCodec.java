package ssg.legoflow.database.postgresql.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encoder/decoder for PostgreSQL v3 wire protocol messages.
 *
 * <p>All messages (except startup-phase untyped messages) consist of:
 * <ol>
 *   <li>1-byte type identifier</li>
 *   <li>4-byte length (including self, excluding type byte)</li>
 *   <li>Payload bytes</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class PgCodec {

    private PgCodec() {}

    // ======== ENCODING ========

    /**
     * Encodes a frontend message to bytes.
     *
     * @param msg the message to encode
     * @return the encoded bytes
     */
    public static byte[] encodeFrontend(FrontendMessage msg) {
        return switch (msg) {
            case FrontendMessage.StartupMessage m -> encodeStartup(m);
            case FrontendMessage.SSLRequest m -> encodeSSLRequest();
            case FrontendMessage.CancelRequest m -> encodeCancelRequest(m);
            case FrontendMessage.PasswordMessage m -> encodeTyped((byte) 'p', encodeString(m.password()));
            case FrontendMessage.SASLInitialResponse m -> encodeSASLInitial(m);
            case FrontendMessage.SASLResponse m -> encodeTyped((byte) 'p', m.data());
            case FrontendMessage.Query m -> encodeTyped((byte) 'Q', encodeString(m.sql()));
            case FrontendMessage.Parse m -> encodeParse(m);
            case FrontendMessage.Bind m -> encodeBind(m);
            case FrontendMessage.Describe m -> encodeDescribe(m);
            case FrontendMessage.Execute m -> encodeExecute(m);
            case FrontendMessage.Sync m -> encodeTyped((byte) 'S', new byte[0]);
            case FrontendMessage.Flush m -> encodeTyped((byte) 'H', new byte[0]);
            case FrontendMessage.Close m -> encodeClose(m);
            case FrontendMessage.CopyData m -> encodeTyped((byte) 'd', m.data());
            case FrontendMessage.CopyDone m -> encodeTyped((byte) 'c', new byte[0]);
            case FrontendMessage.CopyFail m -> encodeTyped((byte) 'f', encodeString(m.errorMessage()));
            case FrontendMessage.Terminate m -> encodeTyped((byte) 'X', new byte[0]);
        };
    }

    /**
     * Encodes a backend message to bytes.
     *
     * @param msg the message to encode
     * @return the encoded bytes
     */
    public static byte[] encodeBackend(BackendMessage msg) {
        return switch (msg) {
            case BackendMessage.AuthenticationOk m -> encodeAuth(0, new byte[0]);
            case BackendMessage.AuthenticationCleartextPassword m -> encodeAuth(3, new byte[0]);
            case BackendMessage.AuthenticationMD5Password m -> encodeAuth(5, m.salt());
            case BackendMessage.AuthenticationSASL m -> encodeAuthSASL(m);
            case BackendMessage.AuthenticationSASLContinue m -> encodeAuth(11, m.data());
            case BackendMessage.AuthenticationSASLFinal m -> encodeAuth(12, m.data());
            case BackendMessage.ParameterStatus m -> encodeParameterStatus(m);
            case BackendMessage.BackendKeyData m -> encodeBackendKeyData(m);
            case BackendMessage.ReadyForQuery m -> encodeTyped((byte) 'Z', new byte[]{m.status().indicator()});
            case BackendMessage.RowDescription m -> encodeRowDescription(m);
            case BackendMessage.DataRow m -> encodeDataRow(m);
            case BackendMessage.CommandComplete m -> encodeTyped((byte) 'C', encodeString(m.tag()));
            case BackendMessage.EmptyQueryResponse m -> encodeTyped((byte) 'I', new byte[0]);
            case BackendMessage.ParseComplete m -> encodeTyped((byte) '1', new byte[0]);
            case BackendMessage.BindComplete m -> encodeTyped((byte) '2', new byte[0]);
            case BackendMessage.CloseComplete m -> encodeTyped((byte) '3', new byte[0]);
            case BackendMessage.NoData m -> encodeTyped((byte) 'n', new byte[0]);
            case BackendMessage.ParameterDescription m -> encodeParameterDescription(m);
            case BackendMessage.PortalSuspended m -> encodeTyped((byte) 's', new byte[0]);
            case BackendMessage.CopyInResponse m -> encodeCopyResponse((byte) 'G', m.overallFormat(), m.columnFormats());
            case BackendMessage.CopyOutResponse m -> encodeCopyResponse((byte) 'H', m.overallFormat(), m.columnFormats());
            case BackendMessage.CopyBothResponse m -> encodeCopyResponse((byte) 'W', m.overallFormat(), m.columnFormats());
            case BackendMessage.CopyData m -> encodeTyped((byte) 'd', m.data());
            case BackendMessage.CopyDone m -> encodeTyped((byte) 'c', new byte[0]);
            case BackendMessage.NotificationResponse m -> encodeNotification(m);
            case BackendMessage.ErrorResponse m -> encodeErrorNotice((byte) 'E', m.fields());
            case BackendMessage.NoticeResponse m -> encodeErrorNotice((byte) 'N', m.fields());
        };
    }

    // ======== DECODING ========

    /**
     * Reads and decodes a backend message from an input stream.
     *
     * @param in the input stream
     * @return the decoded backend message
     * @throws IOException if an I/O error occurs
     */
    public static BackendMessage decodeBackend(InputStream in) throws IOException {
        int typeByte = in.read();
        if (typeByte == -1) {
            throw new IOException("End of stream");
        }
        int length = readInt32(in);
        byte[] payload = readExact(in, length - 4);
        ByteBuffer buf = ByteBuffer.wrap(payload);

        return switch ((byte) typeByte) {
            case (byte) 'R' -> decodeAuthentication(buf);
            case (byte) 'S' -> decodeParameterStatus(buf);
            case (byte) 'K' -> new BackendMessage.BackendKeyData(buf.getInt(), buf.getInt());
            case (byte) 'Z' -> new BackendMessage.ReadyForQuery(TransactionStatus.fromByte(buf.get()));
            case (byte) 'T' -> decodeRowDescription(buf);
            case (byte) 'D' -> decodeDataRow(buf);
            case (byte) 'C' -> new BackendMessage.CommandComplete(readCString(buf));
            case (byte) 'I' -> new BackendMessage.EmptyQueryResponse();
            case (byte) '1' -> new BackendMessage.ParseComplete();
            case (byte) '2' -> new BackendMessage.BindComplete();
            case (byte) '3' -> new BackendMessage.CloseComplete();
            case (byte) 'n' -> new BackendMessage.NoData();
            case (byte) 't' -> decodeParameterDescription(buf);
            case (byte) 's' -> new BackendMessage.PortalSuspended();
            case (byte) 'G' -> decodeCopyIn(buf);
            case (byte) 'H' -> decodeCopyOut(buf);
            case (byte) 'W' -> decodeCopyBoth(buf);
            case (byte) 'd' -> new BackendMessage.CopyData(getRemaining(buf));
            case (byte) 'c' -> new BackendMessage.CopyDone();
            case (byte) 'A' -> decodeNotification(buf);
            case (byte) 'E' -> new BackendMessage.ErrorResponse(decodeErrorFields(buf));
            case (byte) 'N' -> new BackendMessage.NoticeResponse(decodeErrorFields(buf));
            default -> throw new IOException("Unknown backend message type: " + (char) typeByte);
        };
    }

    /**
     * Reads and decodes a frontend message from an input stream.
     * Handles both typed messages and startup-phase untyped messages.
     *
     * @param in      the input stream
     * @param startup true if in startup phase (expecting untyped messages)
     * @return the decoded frontend message
     * @throws IOException if an I/O error occurs
     */
    public static FrontendMessage decodeFrontend(InputStream in, boolean startup) throws IOException {
        if (startup) {
            return decodeStartupPhase(in);
        }
        int typeByte = in.read();
        if (typeByte == -1) {
            throw new IOException("End of stream");
        }
        int length = readInt32(in);
        byte[] payload = readExact(in, length - 4);
        ByteBuffer buf = ByteBuffer.wrap(payload);

        return switch ((byte) typeByte) {
            case (byte) 'p' -> decodePasswordOrSASL(buf, payload);
            case (byte) 'Q' -> new FrontendMessage.Query(readCString(buf));
            case (byte) 'P' -> decodeParse(buf);
            case (byte) 'B' -> decodeBind(buf);
            case (byte) 'D' -> new FrontendMessage.Describe(buf.get(), readCString(buf));
            case (byte) 'E' -> new FrontendMessage.Execute(readCString(buf), buf.getInt());
            case (byte) 'S' -> new FrontendMessage.Sync();
            case (byte) 'H' -> new FrontendMessage.Flush();
            case (byte) 'C' -> new FrontendMessage.Close(buf.get(), readCString(buf));
            case (byte) 'd' -> new FrontendMessage.CopyData(getRemaining(buf));
            case (byte) 'c' -> new FrontendMessage.CopyDone();
            case (byte) 'f' -> new FrontendMessage.CopyFail(readCString(buf));
            case (byte) 'X' -> new FrontendMessage.Terminate();
            default -> throw new IOException("Unknown frontend message type: " + (char) typeByte);
        };
    }

    /**
     * Decodes a startup-phase message (no type byte, just length + payload).
     *
     * @param in the input stream
     * @return the decoded frontend message
     * @throws IOException if an I/O error occurs
     */
    private static FrontendMessage decodeStartupPhase(InputStream in) throws IOException {
        int length = readInt32(in);
        byte[] payload = readExact(in, length - 4);
        ByteBuffer buf = ByteBuffer.wrap(payload);
        int code = buf.getInt();

        if (code == FrontendMessage.SSLRequest.SSL_REQUEST_CODE) {
            return new FrontendMessage.SSLRequest();
        }
        if (code == FrontendMessage.CancelRequest.CANCEL_REQUEST_CODE) {
            return new FrontendMessage.CancelRequest(buf.getInt(), buf.getInt());
        }
        // StartupMessage
        Map<String, String> params = new LinkedHashMap<>();
        while (buf.hasRemaining()) {
            String key = readCString(buf);
            if (key.isEmpty()) break;
            String value = readCString(buf);
            params.put(key, value);
        }
        return new FrontendMessage.StartupMessage(code, params);
    }

    // ======== Encoding helpers ========

    private static byte[] encodeTyped(byte type, byte[] payload) {
        byte[] result = new byte[1 + 4 + payload.length];
        result[0] = type;
        putInt32(result, 1, 4 + payload.length);
        System.arraycopy(payload, 0, result, 5, payload.length);
        return result;
    }

    private static byte[] encodeString(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bytes.length + 1]; // null terminator
        System.arraycopy(bytes, 0, result, 0, bytes.length);
        return result;
    }

    private static byte[] encodeStartup(FrontendMessage.StartupMessage msg) {
        var bos = new ByteArrayOutputStream();
        writeInt32(bos, 0); // placeholder for length
        writeInt32(bos, msg.protocolVersion());
        for (var entry : msg.parameters().entrySet()) {
            writeCString(bos, entry.getKey());
            writeCString(bos, entry.getValue());
        }
        bos.write(0); // terminator
        byte[] data = bos.toByteArray();
        putInt32(data, 0, data.length);
        return data;
    }

    private static byte[] encodeSSLRequest() {
        byte[] data = new byte[8];
        putInt32(data, 0, 8);
        putInt32(data, 4, FrontendMessage.SSLRequest.SSL_REQUEST_CODE);
        return data;
    }

    private static byte[] encodeCancelRequest(FrontendMessage.CancelRequest msg) {
        byte[] data = new byte[16];
        putInt32(data, 0, 16);
        putInt32(data, 4, FrontendMessage.CancelRequest.CANCEL_REQUEST_CODE);
        putInt32(data, 8, msg.processId());
        putInt32(data, 12, msg.secretKey());
        return data;
    }

    private static byte[] encodeSASLInitial(FrontendMessage.SASLInitialResponse msg) {
        var bos = new ByteArrayOutputStream();
        writeCString(bos, msg.mechanism());
        writeInt32(bos, msg.initialResponse().length);
        bos.writeBytes(msg.initialResponse());
        return encodeTyped((byte) 'p', bos.toByteArray());
    }

    private static byte[] encodeParse(FrontendMessage.Parse msg) {
        var bos = new ByteArrayOutputStream();
        writeCString(bos, msg.statementName());
        writeCString(bos, msg.sql());
        writeInt16(bos, (short) msg.parameterTypes().length);
        for (int oid : msg.parameterTypes()) {
            writeInt32(bos, oid);
        }
        return encodeTyped((byte) 'P', bos.toByteArray());
    }

    private static byte[] encodeBind(FrontendMessage.Bind msg) {
        var bos = new ByteArrayOutputStream();
        writeCString(bos, msg.portalName());
        writeCString(bos, msg.statementName());
        // Parameter format codes
        writeInt16(bos, (short) msg.parameterFormats().length);
        for (short f : msg.parameterFormats()) {
            writeInt16(bos, f);
        }
        // Parameter values
        writeInt16(bos, (short) msg.parameterValues().length);
        for (byte[] val : msg.parameterValues()) {
            if (val == null) {
                writeInt32(bos, -1);
            } else {
                writeInt32(bos, val.length);
                bos.writeBytes(val);
            }
        }
        // Result format codes
        writeInt16(bos, (short) msg.resultFormats().length);
        for (short f : msg.resultFormats()) {
            writeInt16(bos, f);
        }
        return encodeTyped((byte) 'B', bos.toByteArray());
    }

    private static byte[] encodeDescribe(FrontendMessage.Describe msg) {
        var bos = new ByteArrayOutputStream();
        bos.write(msg.target());
        writeCString(bos, msg.name());
        return encodeTyped((byte) 'D', bos.toByteArray());
    }

    private static byte[] encodeExecute(FrontendMessage.Execute msg) {
        var bos = new ByteArrayOutputStream();
        writeCString(bos, msg.portalName());
        writeInt32(bos, msg.maxRows());
        return encodeTyped((byte) 'E', bos.toByteArray());
    }

    private static byte[] encodeClose(FrontendMessage.Close msg) {
        var bos = new ByteArrayOutputStream();
        bos.write(msg.target());
        writeCString(bos, msg.name());
        return encodeTyped((byte) 'C', bos.toByteArray());
    }

    private static byte[] encodeAuth(int authType, byte[] extra) {
        byte[] payload = new byte[4 + extra.length];
        putInt32(payload, 0, authType);
        System.arraycopy(extra, 0, payload, 4, extra.length);
        return encodeTyped((byte) 'R', payload);
    }

    private static byte[] encodeAuthSASL(BackendMessage.AuthenticationSASL msg) {
        var bos = new ByteArrayOutputStream();
        writeInt32(bos, 10); // auth type
        for (String mechanism : msg.mechanisms()) {
            writeCString(bos, mechanism);
        }
        bos.write(0); // terminator
        return encodeTyped((byte) 'R', bos.toByteArray());
    }

    private static byte[] encodeParameterStatus(BackendMessage.ParameterStatus msg) {
        var bos = new ByteArrayOutputStream();
        writeCString(bos, msg.name());
        writeCString(bos, msg.value());
        return encodeTyped((byte) 'S', bos.toByteArray());
    }

    private static byte[] encodeBackendKeyData(BackendMessage.BackendKeyData msg) {
        byte[] payload = new byte[8];
        putInt32(payload, 0, msg.processId());
        putInt32(payload, 4, msg.secretKey());
        return encodeTyped((byte) 'K', payload);
    }

    private static byte[] encodeRowDescription(BackendMessage.RowDescription msg) {
        var bos = new ByteArrayOutputStream();
        writeInt16(bos, (short) msg.columns().size());
        for (var col : msg.columns()) {
            writeCString(bos, col.name());
            writeInt32(bos, col.tableOid());
            writeInt16(bos, col.columnIndex());
            writeInt32(bos, col.typeOid());
            writeInt16(bos, col.typeSize());
            writeInt32(bos, col.typeModifier());
            writeInt16(bos, col.formatCode());
        }
        return encodeTyped((byte) 'T', bos.toByteArray());
    }

    private static byte[] encodeDataRow(BackendMessage.DataRow msg) {
        var bos = new ByteArrayOutputStream();
        writeInt16(bos, (short) msg.values().length);
        for (byte[] val : msg.values()) {
            if (val == null) {
                writeInt32(bos, -1);
            } else {
                writeInt32(bos, val.length);
                bos.writeBytes(val);
            }
        }
        return encodeTyped((byte) 'D', bos.toByteArray());
    }

    private static byte[] encodeParameterDescription(BackendMessage.ParameterDescription msg) {
        var bos = new ByteArrayOutputStream();
        writeInt16(bos, (short) msg.parameterOids().length);
        for (int oid : msg.parameterOids()) {
            writeInt32(bos, oid);
        }
        return encodeTyped((byte) 't', bos.toByteArray());
    }

    private static byte[] encodeCopyResponse(byte type, byte overallFormat, short[] columnFormats) {
        var bos = new ByteArrayOutputStream();
        bos.write(overallFormat);
        writeInt16(bos, (short) columnFormats.length);
        for (short f : columnFormats) {
            writeInt16(bos, f);
        }
        return encodeTyped(type, bos.toByteArray());
    }

    private static byte[] encodeNotification(BackendMessage.NotificationResponse msg) {
        var bos = new ByteArrayOutputStream();
        writeInt32(bos, msg.processId());
        writeCString(bos, msg.channel());
        writeCString(bos, msg.payload());
        return encodeTyped((byte) 'A', bos.toByteArray());
    }

    private static byte[] encodeErrorNotice(byte type, Map<Byte, String> fields) {
        var bos = new ByteArrayOutputStream();
        for (var entry : fields.entrySet()) {
            bos.write(entry.getKey());
            writeCString(bos, entry.getValue());
        }
        bos.write(0); // terminator
        return encodeTyped(type, bos.toByteArray());
    }

    // ======== Decoding helpers ========

    private static BackendMessage decodeAuthentication(ByteBuffer buf) {
        int authType = buf.getInt();
        return switch (authType) {
            case 0 -> new BackendMessage.AuthenticationOk();
            case 3 -> new BackendMessage.AuthenticationCleartextPassword();
            case 5 -> {
                byte[] salt = new byte[4];
                buf.get(salt);
                yield new BackendMessage.AuthenticationMD5Password(salt);
            }
            case 10 -> {
                List<String> mechanisms = new ArrayList<>();
                while (buf.hasRemaining()) {
                    String m = readCString(buf);
                    if (m.isEmpty()) break;
                    mechanisms.add(m);
                }
                yield new BackendMessage.AuthenticationSASL(mechanisms);
            }
            case 11 -> new BackendMessage.AuthenticationSASLContinue(getRemaining(buf));
            case 12 -> new BackendMessage.AuthenticationSASLFinal(getRemaining(buf));
            default -> throw new IllegalArgumentException("Unknown auth type: " + authType);
        };
    }

    private static BackendMessage.ParameterStatus decodeParameterStatus(ByteBuffer buf) {
        return new BackendMessage.ParameterStatus(readCString(buf), readCString(buf));
    }

    private static BackendMessage.RowDescription decodeRowDescription(ByteBuffer buf) {
        int count = buf.getShort() & 0xFFFF;
        List<BackendMessage.ColumnDescription> columns = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            columns.add(new BackendMessage.ColumnDescription(
                    readCString(buf),
                    buf.getInt(),
                    buf.getShort(),
                    buf.getInt(),
                    buf.getShort(),
                    buf.getInt(),
                    buf.getShort()
            ));
        }
        return new BackendMessage.RowDescription(columns);
    }

    private static BackendMessage.DataRow decodeDataRow(ByteBuffer buf) {
        int count = buf.getShort() & 0xFFFF;
        byte[][] values = new byte[count][];
        for (int i = 0; i < count; i++) {
            int len = buf.getInt();
            if (len == -1) {
                values[i] = null;
            } else {
                values[i] = new byte[len];
                buf.get(values[i]);
            }
        }
        return new BackendMessage.DataRow(values);
    }

    private static BackendMessage.ParameterDescription decodeParameterDescription(ByteBuffer buf) {
        int count = buf.getShort() & 0xFFFF;
        int[] oids = new int[count];
        for (int i = 0; i < count; i++) {
            oids[i] = buf.getInt();
        }
        return new BackendMessage.ParameterDescription(oids);
    }

    private static BackendMessage.CopyInResponse decodeCopyIn(ByteBuffer buf) {
        byte format = buf.get();
        short[] colFormats = readColumnFormats(buf);
        return new BackendMessage.CopyInResponse(format, colFormats);
    }

    private static BackendMessage.CopyOutResponse decodeCopyOut(ByteBuffer buf) {
        byte format = buf.get();
        short[] colFormats = readColumnFormats(buf);
        return new BackendMessage.CopyOutResponse(format, colFormats);
    }

    private static BackendMessage.CopyBothResponse decodeCopyBoth(ByteBuffer buf) {
        byte format = buf.get();
        short[] colFormats = readColumnFormats(buf);
        return new BackendMessage.CopyBothResponse(format, colFormats);
    }

    private static short[] readColumnFormats(ByteBuffer buf) {
        int count = buf.getShort() & 0xFFFF;
        short[] formats = new short[count];
        for (int i = 0; i < count; i++) {
            formats[i] = buf.getShort();
        }
        return formats;
    }

    private static BackendMessage.NotificationResponse decodeNotification(ByteBuffer buf) {
        int pid = buf.getInt();
        String channel = readCString(buf);
        String payload = readCString(buf);
        return new BackendMessage.NotificationResponse(pid, channel, payload);
    }

    private static Map<Byte, String> decodeErrorFields(ByteBuffer buf) {
        Map<Byte, String> fields = new LinkedHashMap<>();
        while (buf.hasRemaining()) {
            byte fieldType = buf.get();
            if (fieldType == 0) break;
            fields.put(fieldType, readCString(buf));
        }
        return fields;
    }

    private static FrontendMessage decodePasswordOrSASL(ByteBuffer buf, byte[] payload) {
        // Heuristic: SASL initial response contains a null-terminated mechanism name
        // followed by int32 length + data. Password is just a null-terminated string.
        // We need context to distinguish, so for decoding we try the simple password case.
        // The server-side codec uses context from the auth state to decide.
        String text = readCString(ByteBuffer.wrap(payload));
        return new FrontendMessage.PasswordMessage(text);
    }

    /**
     * Decodes a frontend 'p' message as a SASL initial response (used by server when expecting SASL).
     *
     * @param payload the raw payload bytes
     * @return the decoded SASLInitialResponse
     */
    public static FrontendMessage.SASLInitialResponse decodeSASLInitialResponse(byte[] payload) {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        String mechanism = readCString(buf);
        int length = buf.getInt();
        byte[] data = new byte[length];
        buf.get(data);
        return new FrontendMessage.SASLInitialResponse(mechanism, data);
    }

    /**
     * Decodes a frontend 'p' message as a SASL response (used by server when expecting SASL continue).
     *
     * @param payload the raw payload bytes
     * @return the decoded SASLResponse
     */
    public static FrontendMessage.SASLResponse decodeSASLResponse(byte[] payload) {
        return new FrontendMessage.SASLResponse(payload);
    }

    /**
     * Reads a raw frontend 'p' message payload from a stream (type byte already consumed).
     *
     * @param in the input stream (type byte already consumed)
     * @return the raw payload bytes (without type or length)
     * @throws IOException if an I/O error occurs
     */
    public static byte[] readRawPayload(InputStream in) throws IOException {
        int length = readInt32(in);
        return readExact(in, length - 4);
    }

    private static FrontendMessage.Parse decodeParse(ByteBuffer buf) {
        String name = readCString(buf);
        String sql = readCString(buf);
        int count = buf.getShort() & 0xFFFF;
        int[] types = new int[count];
        for (int i = 0; i < count; i++) {
            types[i] = buf.getInt();
        }
        return new FrontendMessage.Parse(name, sql, types);
    }

    private static FrontendMessage.Bind decodeBind(ByteBuffer buf) {
        String portalName = readCString(buf);
        String stmtName = readCString(buf);
        int pfCount = buf.getShort() & 0xFFFF;
        short[] paramFormats = new short[pfCount];
        for (int i = 0; i < pfCount; i++) {
            paramFormats[i] = buf.getShort();
        }
        int pvCount = buf.getShort() & 0xFFFF;
        byte[][] paramValues = new byte[pvCount][];
        for (int i = 0; i < pvCount; i++) {
            int len = buf.getInt();
            if (len == -1) {
                paramValues[i] = null;
            } else {
                paramValues[i] = new byte[len];
                buf.get(paramValues[i]);
            }
        }
        int rfCount = buf.getShort() & 0xFFFF;
        short[] resultFormats = new short[rfCount];
        for (int i = 0; i < rfCount; i++) {
            resultFormats[i] = buf.getShort();
        }
        return new FrontendMessage.Bind(portalName, stmtName, paramFormats, paramValues, resultFormats);
    }

    // ======== I/O primitives ========

    /**
     * Writes a message to an output stream.
     *
     * @param out the output stream
     * @param msg the message bytes
     * @throws IOException if an I/O error occurs
     */
    public static void write(OutputStream out, byte[] msg) throws IOException {
        out.write(msg);
        out.flush();
    }

    private static String readCString(ByteBuffer buf) {
        var sb = new StringBuilder();
        while (buf.hasRemaining()) {
            byte b = buf.get();
            if (b == 0) break;
            sb.append((char) (b & 0xFF));
        }
        return sb.toString();
    }

    private static byte[] getRemaining(ByteBuffer buf) {
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        return data;
    }

    private static int readInt32(InputStream in) throws IOException {
        byte[] b = readExact(in, 4);
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    private static byte[] readExact(InputStream in, int count) throws IOException {
        byte[] data = new byte[count];
        int offset = 0;
        while (offset < count) {
            int read = in.read(data, offset, count - offset);
            if (read == -1) {
                throw new IOException("Premature end of stream (expected " + count + " bytes, got " + offset + ")");
            }
            offset += read;
        }
        return data;
    }

    private static void putInt32(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >> 24);
        data[offset + 1] = (byte) (value >> 16);
        data[offset + 2] = (byte) (value >> 8);
        data[offset + 3] = (byte) value;
    }

    private static void writeInt32(ByteArrayOutputStream bos, int value) {
        bos.write((value >> 24) & 0xFF);
        bos.write((value >> 16) & 0xFF);
        bos.write((value >> 8) & 0xFF);
        bos.write(value & 0xFF);
    }

    private static void writeInt16(ByteArrayOutputStream bos, short value) {
        bos.write((value >> 8) & 0xFF);
        bos.write(value & 0xFF);
    }

    private static void writeCString(ByteArrayOutputStream bos, String s) {
        bos.writeBytes(s.getBytes(StandardCharsets.UTF_8));
        bos.write(0);
    }
}
