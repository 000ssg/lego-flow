package ssg.legoflow.http3.qpack;

import java.util.HashMap;
import java.util.Map;

/**
 * QPACK static table as defined in RFC 9204 Appendix A.
 *
 * <p>Contains 99 pre-defined header field entries (indices 0-98) that are
 * always available for reference during header encoding and decoding.
 * Unlike HPACK, QPACK indices start at 0 and the table includes
 * commonly used header name-value pairs for HTTP/3.</p>
 *
 * @since 0.1.0
 */
public final class QpackStaticTable {

    /**
     * A single entry in the static table.
     *
     * @param name  the header field name
     * @param value the header field value
     * @since 0.1.0
     */
    public record Entry(String name, String value) {}

    private static final Entry[] TABLE = {
        new Entry(":authority", ""),                                     // 0
        new Entry(":path", "/"),                                        // 1
        new Entry("age", "0"),                                          // 2
        new Entry("content-disposition", ""),                            // 3
        new Entry("content-length", "0"),                                // 4
        new Entry("cookie", ""),                                        // 5
        new Entry("date", ""),                                          // 6
        new Entry("etag", ""),                                          // 7
        new Entry("if-modified-since", ""),                              // 8
        new Entry("if-none-match", ""),                                  // 9
        new Entry("last-modified", ""),                                  // 10
        new Entry("link", ""),                                          // 11
        new Entry("location", ""),                                      // 12
        new Entry("referer", ""),                                       // 13
        new Entry("set-cookie", ""),                                    // 14
        new Entry(":method", "CONNECT"),                                // 15
        new Entry(":method", "DELETE"),                                  // 16
        new Entry(":method", "GET"),                                    // 17
        new Entry(":method", "HEAD"),                                   // 18
        new Entry(":method", "OPTIONS"),                                // 19
        new Entry(":method", "POST"),                                   // 20
        new Entry(":method", "PUT"),                                    // 21
        new Entry(":scheme", "http"),                                   // 22
        new Entry(":scheme", "https"),                                  // 23
        new Entry(":status", "103"),                                    // 24
        new Entry(":status", "200"),                                    // 25
        new Entry(":status", "304"),                                    // 26
        new Entry(":status", "404"),                                    // 27
        new Entry(":status", "503"),                                    // 28
        new Entry("accept", "*/*"),                                     // 29
        new Entry("accept", "application/dns-message"),                 // 30
        new Entry("accept-encoding", "gzip, deflate, br"),              // 31
        new Entry("accept-ranges", "bytes"),                            // 32
        new Entry("access-control-allow-headers", "cache-control"),     // 33
        new Entry("access-control-allow-headers", "content-type"),      // 34
        new Entry("access-control-allow-origin", "*"),                  // 35
        new Entry("cache-control", "max-age=0"),                        // 36
        new Entry("cache-control", "max-age=2592000"),                  // 37
        new Entry("cache-control", "max-age=604800"),                   // 38
        new Entry("cache-control", "no-cache"),                         // 39
        new Entry("cache-control", "no-store"),                         // 40
        new Entry("cache-control", "public, max-age=31536000"),         // 41
        new Entry("content-encoding", "br"),                            // 42
        new Entry("content-encoding", "gzip"),                          // 43
        new Entry("content-type", "application/dns-message"),           // 44
        new Entry("content-type", "application/javascript"),            // 45
        new Entry("content-type", "application/json"),                  // 46
        new Entry("content-type", "application/x-www-form-urlencoded"), // 47
        new Entry("content-type", "image/gif"),                         // 48
        new Entry("content-type", "image/jpeg"),                        // 49
        new Entry("content-type", "image/png"),                         // 50
        new Entry("content-type", "text/css"),                          // 51
        new Entry("content-type", "text/html; charset=utf-8"),          // 52
        new Entry("content-type", "text/plain"),                        // 53
        new Entry("content-type", "text/plain;charset=utf-8"),          // 54
        new Entry("date", ""),                                          // 55
        new Entry("etag", ""),                                          // 56
        new Entry("if-modified-since", ""),                              // 57
        new Entry("if-none-match", ""),                                  // 58
        new Entry("if-range", ""),                                      // 59
        new Entry("last-modified", ""),                                  // 60
        new Entry("link", ""),                                          // 61
        new Entry("location", ""),                                      // 62
        new Entry("referer", ""),                                       // 63
        new Entry("set-cookie", ""),                                    // 64
        new Entry(":method", "PATCH"),                                  // 65
        new Entry("accept-encoding", "gzip"),                           // 66 (HPACK compat, mentioned in RFC)
        new Entry("accept-encoding", "gzip, deflate"),                  // 67 (HPACK compat)
        new Entry("accept-language", ""),                                // 68
        new Entry("access-control-allow-credentials", "FALSE"),         // 69
        new Entry("access-control-allow-credentials", "TRUE"),          // 70
        new Entry("access-control-allow-headers", "*"),                 // 71
        new Entry("access-control-allow-methods", "get"),               // 72
        new Entry("access-control-allow-methods", "get, post, options"),// 73
        new Entry("access-control-allow-methods", "options"),           // 74
        new Entry("access-control-expose-headers", "content-length"),   // 75
        new Entry("access-control-request-headers", "content-type"),    // 76
        new Entry("access-control-request-method", "get"),              // 77
        new Entry("access-control-request-method", "post"),             // 78
        new Entry("alt-svc", "clear"),                                  // 79
        new Entry("authorization", ""),                                 // 80
        new Entry("content-security-policy", "script-src 'none'; object-src 'none'; base-uri 'none'"), // 81
        new Entry("early-data", "1"),                                   // 82
        new Entry("expect-ct", ""),                                     // 83
        new Entry("forwarded", ""),                                     // 84
        new Entry("if-range", ""),                                      // 85
        new Entry("origin", ""),                                        // 86
        new Entry("purpose", "prefetch"),                               // 87
        new Entry("server", ""),                                        // 88
        new Entry("timing-allow-origin", "*"),                          // 89
        new Entry("upgrade-insecure-requests", "1"),                    // 90
        new Entry("user-agent", ""),                                    // 91
        new Entry("x-forwarded-for", ""),                               // 92
        new Entry("x-frame-options", "deny"),                           // 93
        new Entry("x-frame-options", "sameorigin"),                     // 94
        new Entry("strict-transport-security", "max-age=31536000"),     // 95
        new Entry("strict-transport-security", "max-age=31536000; includesubdomains"), // 96
        new Entry("strict-transport-security", "max-age=31536000; includesubdomains; preload"), // 97
        new Entry("vary", "accept-encoding"),                           // 98
    };

    /** The number of entries in the static table (99 entries, indices 0-98). */
    public static final int SIZE = TABLE.length;

    private static final Map<String, Integer> NAME_INDEX = new HashMap<>();
    private static final Map<String, Integer> NAME_VALUE_INDEX = new HashMap<>();

    static {
        for (int i = 0; i < TABLE.length; i++) {
            var entry = TABLE[i];
            NAME_INDEX.putIfAbsent(entry.name(), i);
            var key = entry.name() + "\0" + entry.value();
            NAME_VALUE_INDEX.putIfAbsent(key, i);
        }
    }

    private QpackStaticTable() {}

    /**
     * Returns the entry at the given index.
     *
     * @param index the zero-based index (0-98)
     * @return the static table entry
     * @throws IllegalArgumentException if the index is out of range
     * @since 0.1.0
     */
    public static Entry getEntry(int index) {
        if (index < 0 || index >= TABLE.length) {
            throw new IllegalArgumentException("Invalid QPACK static table index: " + index);
        }
        return TABLE[index];
    }

    /**
     * Returns the number of entries in the static table.
     *
     * @return 99
     * @since 0.1.0
     */
    public static int getSize() {
        return SIZE;
    }

    /**
     * Finds the index of an entry matching both name and value.
     *
     * @param name  the header field name
     * @param value the header field value
     * @return the index (0-98), or {@code -1} if not found
     * @since 0.1.0
     */
    public static int findEntry(String name, String value) {
        var key = name + "\0" + value;
        return NAME_VALUE_INDEX.getOrDefault(key, -1);
    }

    /**
     * Finds the index of the first entry matching the given name.
     *
     * @param name the header field name
     * @return the index (0-98), or {@code -1} if not found
     * @since 0.1.0
     */
    public static int findNameIndex(String name) {
        return NAME_INDEX.getOrDefault(name, -1);
    }
}
