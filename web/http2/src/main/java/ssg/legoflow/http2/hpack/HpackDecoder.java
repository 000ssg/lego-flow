package ssg.legoflow.http2.hpack;

import ssg.legoflow.http.core.HttpHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HpackDecoder {

    private final HpackDynamicTable dynamicTable;

    public HpackDecoder() {
        this(4096);
    }

    public HpackDecoder(int maxTableSize) {
        this.dynamicTable = new HpackDynamicTable(maxTableSize);
    }

    public record HeaderField(String name, String value) {}

    public List<HeaderField> decode(ByteBuffer data) {
        var headers = new ArrayList<HeaderField>();
        var buf = data.duplicate();

        while (buf.hasRemaining()) {
            int b = buf.get(buf.position()) & 0xFF;

            if ((b & 0x80) != 0) {
                int index = decodeInteger(buf, 7);
                var entry = lookupEntry(index);
                headers.add(new HeaderField(entry.name(), entry.value()));
            } else if ((b & 0x40) != 0) {
                int index = decodeInteger(buf, 6);
                String name;
                if (index > 0) {
                    name = lookupEntry(index).name();
                } else {
                    name = decodeString(buf);
                }
                String value = decodeString(buf);
                dynamicTable.add(name, value);
                headers.add(new HeaderField(name, value));
            } else if ((b & 0x20) != 0) {
                int maxSize = decodeInteger(buf, 5);
                dynamicTable.setMaxSize(maxSize);
            } else {
                boolean neverIndex = (b & 0x10) != 0;
                int prefixBits = neverIndex ? 4 : 4;
                int index = decodeInteger(buf, prefixBits);
                String name;
                if (index > 0) {
                    name = lookupEntry(index).name();
                } else {
                    name = decodeString(buf);
                }
                String value = decodeString(buf);
                headers.add(new HeaderField(name, value));
            }
        }

        return headers;
    }

    public HttpHeaders decodeToHttpHeaders(ByteBuffer data) {
        var fields = decode(data);
        var headers = new HttpHeaders();
        for (var field : fields) {
            headers.add(field.name(), field.value());
        }
        return headers;
    }

    private HpackStaticTable.Entry lookupEntry(int index) {
        if (index <= HpackStaticTable.SIZE) {
            return HpackStaticTable.get(index);
        }
        return dynamicTable.get(index);
    }

    static int decodeInteger(ByteBuffer buf, int prefixBits) {
        int maxPrefix = (1 << prefixBits) - 1;
        int b = buf.get() & 0xFF;
        int value = b & maxPrefix;

        if (value < maxPrefix) {
            return value;
        }

        int shift = 0;
        do {
            b = buf.get() & 0xFF;
            value += (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);

        return value;
    }

    static String decodeString(ByteBuffer buf) {
        int b = buf.get(buf.position()) & 0xFF;
        boolean huffmanEncoded = (b & 0x80) != 0;
        int length = decodeInteger(buf, 7);

        byte[] data = new byte[length];
        buf.get(data);

        if (huffmanEncoded) {
            data = HpackHuffman.decode(data);
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    public HpackDynamicTable getDynamicTable() {
        return dynamicTable;
    }

    public void setMaxTableSize(int maxSize) {
        dynamicTable.setMaxSize(maxSize);
    }
}
