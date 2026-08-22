package ssg.legoflow.http2.hpack;

import ssg.legoflow.http.core.HttpHeaders;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
public class HpackEncoder {

    private static final Set<String> DEFAULT_SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie", "proxy-authorization",
            "www-authenticate", "proxy-authenticate"
    );

    private final HpackDynamicTable dynamicTable;
    private boolean useHuffman = true;
    private Set<String> sensitiveHeaders = DEFAULT_SENSITIVE_HEADERS;

    public HpackEncoder() {
        this(4096);
    }

    public HpackEncoder(int maxTableSize) {
        this.dynamicTable = new HpackDynamicTable(maxTableSize);
    }

    public void setUseHuffman(boolean useHuffman) {
        this.useHuffman = useHuffman;
    }

    /**
     * Sets the set of header names that should be marked as never-indexed (sensitive).
     *
     * <p>Per RFC 7541 Section 7.1.3, sensitive headers like Authorization and Cookie
     * should use the "never indexed" literal representation to prevent them from
     * being stored in the dynamic table and to signal intermediaries not to compress them.
     *
     * @param names the set of lowercase header names to treat as sensitive
     * @since 0.1.0
     */
    public void setSensitiveHeaders(Set<String> names) {
        this.sensitiveHeaders = Set.copyOf(names);
    }

    /**
     * Returns the current set of sensitive header names.
     *
     * @return the sensitive header names
     * @since 0.1.0
     */
    public Set<String> sensitiveHeaders() {
        return sensitiveHeaders;
    }

    /**
     * Returns whether the given header name is considered sensitive (never-indexed).
     *
     * @param name the header name (lowercase)
     * @return true if the header should be never-indexed
     * @since 0.1.0
     */
    public boolean isSensitive(String name) {
        return sensitiveHeaders.contains(name.toLowerCase());
    }

    public ByteBuffer encode(HttpHeaders headers) {
        var out = new ByteArrayOutputStream();

        for (String name : headers.names()) {
            for (String value : headers.getAll(name)) {
                encodeHeader(out, name, value);
            }
        }

        return ByteBuffer.wrap(out.toByteArray());
    }

    public ByteBuffer encodeHeaderList(String... nameValuePairs) {
        if (nameValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Name-value pairs must be even");
        }
        var out = new ByteArrayOutputStream();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            encodeHeader(out, nameValuePairs[i], nameValuePairs[i + 1]);
        }
        return ByteBuffer.wrap(out.toByteArray());
    }

    private void encodeHeader(ByteArrayOutputStream out, String name, String value) {
        // Sensitive headers use "never indexed" representation (RFC 7541 Section 6.2.3)
        if (isSensitive(name)) {
            encodeNeverIndexed(out, name, value);
            return;
        }

        int nameValueIdx = HpackStaticTable.findNameValueIndex(name, value);
        if (nameValueIdx > 0) {
            encodeInteger(out, nameValueIdx, 7, 0x80);
            return;
        }

        int dynamicNvIdx = dynamicTable.findNameValueIndex(name, value);
        if (dynamicNvIdx > 0) {
            encodeInteger(out, dynamicNvIdx, 7, 0x80);
            return;
        }

        int nameIdx = HpackStaticTable.findNameIndex(name);
        if (nameIdx == 0) {
            nameIdx = dynamicTable.findNameIndex(name);
        }

        dynamicTable.add(name, value);

        if (nameIdx > 0) {
            encodeInteger(out, nameIdx, 6, 0x40);
        } else {
            out.write(0x40);
            encodeString(out, name);
        }
        encodeString(out, value);
    }

    /**
     * Encodes a header using the "never indexed" literal representation.
     *
     * <p>Per RFC 7541 Section 6.2.3, the header is encoded with a 0001 prefix,
     * signaling that the header should never be indexed by any recipient.
     *
     * @param out   the output stream
     * @param name  the header name
     * @param value the header value
     */
    private void encodeNeverIndexed(ByteArrayOutputStream out, String name, String value) {
        int nameIdx = HpackStaticTable.findNameIndex(name);
        if (nameIdx == 0) {
            nameIdx = dynamicTable.findNameIndex(name);
        }

        if (nameIdx > 0) {
            // Literal header field never indexed — indexed name (prefix 0001xxxx, 4-bit prefix)
            encodeInteger(out, nameIdx, 4, 0x10);
        } else {
            // Literal header field never indexed — new name (0001 0000)
            out.write(0x10);
            encodeString(out, name);
        }
        encodeString(out, value);
    }

    static void encodeInteger(ByteArrayOutputStream out, int value, int prefixBits, int prefix) {
        int maxPrefix = (1 << prefixBits) - 1;
        if (value < maxPrefix) {
            out.write(prefix | value);
        } else {
            out.write(prefix | maxPrefix);
            value -= maxPrefix;
            while (value >= 128) {
                out.write((value & 0x7F) | 0x80);
                value >>= 7;
            }
            out.write(value);
        }
    }

    private void encodeString(ByteArrayOutputStream out, String s) {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        if (useHuffman) {
            byte[] huffmanEncoded = HpackHuffman.encode(raw);
            if (huffmanEncoded.length < raw.length) {
                encodeInteger(out, huffmanEncoded.length, 7, 0x80);
                out.writeBytes(huffmanEncoded);
                return;
            }
        }
        encodeInteger(out, raw.length, 7, 0x00);
        out.writeBytes(raw);
    }

    public HpackDynamicTable getDynamicTable() {
        return dynamicTable;
    }

    public void setMaxTableSize(int maxSize) {
        dynamicTable.setMaxSize(maxSize);
    }
}
