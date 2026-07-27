package ssg.legoflow.http2.hpack;

import java.util.HashMap;
import java.util.Map;

public final class HpackStaticTable {

    public record Entry(String name, String value) {}

    private static final Entry[] TABLE = {
        null, // index 0 is unused
        new Entry(":authority", ""),
        new Entry(":method", "GET"),
        new Entry(":method", "POST"),
        new Entry(":path", "/"),
        new Entry(":path", "/index.html"),
        new Entry(":scheme", "http"),
        new Entry(":scheme", "https"),
        new Entry(":status", "200"),
        new Entry(":status", "204"),
        new Entry(":status", "206"),
        new Entry(":status", "304"),
        new Entry(":status", "400"),
        new Entry(":status", "404"),
        new Entry(":status", "500"),
        new Entry("accept-charset", ""),
        new Entry("accept-encoding", "gzip, deflate"),
        new Entry("accept-language", ""),
        new Entry("accept-ranges", ""),
        new Entry("accept", ""),
        new Entry("access-control-allow-origin", ""),
        new Entry("age", ""),
        new Entry("allow", ""),
        new Entry("authorization", ""),
        new Entry("cache-control", ""),
        new Entry("content-disposition", ""),
        new Entry("content-encoding", ""),
        new Entry("content-language", ""),
        new Entry("content-length", ""),
        new Entry("content-location", ""),
        new Entry("content-range", ""),
        new Entry("content-type", ""),
        new Entry("cookie", ""),
        new Entry("date", ""),
        new Entry("etag", ""),
        new Entry("expect", ""),
        new Entry("expires", ""),
        new Entry("from", ""),
        new Entry("host", ""),
        new Entry("if-match", ""),
        new Entry("if-modified-since", ""),
        new Entry("if-none-match", ""),
        new Entry("if-range", ""),
        new Entry("if-unmodified-since", ""),
        new Entry("last-modified", ""),
        new Entry("link", ""),
        new Entry("location", ""),
        new Entry("max-forwards", ""),
        new Entry("proxy-authenticate", ""),
        new Entry("proxy-authorization", ""),
        new Entry("range", ""),
        new Entry("referer", ""),
        new Entry("refresh", ""),
        new Entry("retry-after", ""),
        new Entry("server", ""),
        new Entry("set-cookie", ""),
        new Entry("strict-transport-security", ""),
        new Entry("transfer-encoding", ""),
        new Entry("user-agent", ""),
        new Entry("vary", ""),
        new Entry("via", ""),
        new Entry("www-authenticate", "")
    };

    public static final int SIZE = TABLE.length - 1;

    private static final Map<String, Integer> NAME_INDEX = new HashMap<>();
    private static final Map<String, Integer> NAME_VALUE_INDEX = new HashMap<>();

    static {
        for (int i = 1; i < TABLE.length; i++) {
            var entry = TABLE[i];
            NAME_INDEX.putIfAbsent(entry.name(), i);
            if (!entry.value().isEmpty()) {
                NAME_VALUE_INDEX.putIfAbsent(entry.name() + "\0" + entry.value(), i);
            }
        }
    }

    private HpackStaticTable() {}

    public static Entry get(int index) {
        if (index < 1 || index >= TABLE.length) {
            throw new IllegalArgumentException("Invalid static table index: " + index);
        }
        return TABLE[index];
    }

    public static int findNameValueIndex(String name, String value) {
        var key = name + "\0" + value;
        return NAME_VALUE_INDEX.getOrDefault(key, 0);
    }

    public static int findNameIndex(String name) {
        return NAME_INDEX.getOrDefault(name, 0);
    }
}
