package ssg.legoflow.database.redis.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * RESP2 and RESP3 encoder/decoder.
 *
 * <p>Encodes {@link RespType} values into their wire format and decodes
 * byte buffers back into typed values. Supports all RESP2 types and all
 * RESP3 extension types.
 *
 * @since 1.0.0
 */
public final class RespCodec {

    private static final byte[] CRLF = {'\r', '\n'};

    private RespCodec() {}

    // ---- Encoding ----

    /**
     * Encodes a RESP type value into wire-format bytes.
     *
     * @param type the value to encode
     * @return wire-format bytes
     */
    public static byte[] encode(RespType type) {
        var out = new ByteArrayOutputStream(64);
        try {
            encode(type, out);
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream should not throw", e);
        }
        return out.toByteArray();
    }

    /**
     * Encodes a RESP type value into the given output stream.
     *
     * @param type the value to encode
     * @param out  the output stream
     * @throws IOException if writing fails
     */
    public static void encode(RespType type, ByteArrayOutputStream out) throws IOException {
        switch (type) {
            case RespType.SimpleString ss -> {
                out.write('+');
                out.write(ss.value().getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
            }
            case RespType.Error err -> {
                out.write('-');
                out.write(err.fullMessage().getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
            }
            case RespType.Integer i -> {
                out.write(':');
                out.write(Long.toString(i.value()).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
            }
            case RespType.BulkString bs -> {
                if (bs.value() == null) {
                    out.write("$-1\r\n".getBytes(StandardCharsets.UTF_8));
                } else {
                    out.write('$');
                    out.write(java.lang.Integer.toString(bs.value().length).getBytes(StandardCharsets.UTF_8));
                    out.write(CRLF);
                    out.write(bs.value());
                    out.write(CRLF);
                }
            }
            case RespType.Array arr -> {
                if (arr.elements() == null) {
                    out.write("*-1\r\n".getBytes(StandardCharsets.UTF_8));
                } else {
                    out.write('*');
                    out.write(java.lang.Integer.toString(arr.elements().size()).getBytes(StandardCharsets.UTF_8));
                    out.write(CRLF);
                    for (var element : arr.elements()) {
                        encode(element, out);
                    }
                }
            }
            case RespType.Null n -> out.write("_\r\n".getBytes(StandardCharsets.UTF_8));
            case RespType.RespDouble d -> {
                out.write(',');
                if (Double.isInfinite(d.value())) {
                    out.write((d.value() > 0 ? "inf" : "-inf").getBytes(StandardCharsets.UTF_8));
                } else {
                    out.write(Double.toString(d.value()).getBytes(StandardCharsets.UTF_8));
                }
                out.write(CRLF);
            }
            case RespType.RespBoolean b -> {
                out.write('#');
                out.write(b.value() ? 't' : 'f');
                out.write(CRLF);
            }
            case RespType.BigNumber bn -> {
                out.write('(');
                out.write(bn.value().toString().getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
            }
            case RespType.BlobError be -> {
                out.write('!');
                out.write(java.lang.Integer.toString(be.value().length).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
                out.write(be.value());
                out.write(CRLF);
            }
            case RespType.VerbatimString vs -> {
                byte[] content = (vs.encoding() + ":" + vs.value()).getBytes(StandardCharsets.UTF_8);
                out.write('=');
                out.write(java.lang.Integer.toString(content.length).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
                out.write(content);
                out.write(CRLF);
            }
            case RespType.RespMap map -> {
                out.write('%');
                out.write(java.lang.Integer.toString(map.entries().size()).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
                for (var entry : map.entries().entrySet()) {
                    encode(entry.getKey(), out);
                    encode(entry.getValue(), out);
                }
            }
            case RespType.RespSet set -> {
                out.write('~');
                out.write(java.lang.Integer.toString(set.elements().size()).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
                for (var element : set.elements()) {
                    encode(element, out);
                }
            }
            case RespType.Attribute attr -> {
                out.write('|');
                out.write(java.lang.Integer.toString(attr.attributes().size()).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
                for (var entry : attr.attributes().entrySet()) {
                    encode(entry.getKey(), out);
                    encode(entry.getValue(), out);
                }
            }
            case RespType.Push push -> {
                out.write('>');
                out.write(java.lang.Integer.toString(push.elements().size()).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
                for (var element : push.elements()) {
                    encode(element, out);
                }
            }
        }
    }

    // ---- Command encoding helpers ----

    /**
     * Encodes a Redis command as a RESP array of bulk strings.
     *
     * @param args command name followed by arguments
     * @return wire-format bytes
     */
    public static byte[] encodeCommand(String... args) {
        List<RespType> elements = new ArrayList<>(args.length);
        for (String arg : args) {
            elements.add(RespType.BulkString.of(arg));
        }
        return encode(new RespType.Array(elements));
    }

    /**
     * Encodes an inline command (space-separated, terminated by CRLF).
     *
     * @param command the inline command string
     * @return wire-format bytes
     */
    public static byte[] encodeInlineCommand(String command) {
        return (command + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
}
