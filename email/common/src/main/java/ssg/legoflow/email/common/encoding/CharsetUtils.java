package ssg.legoflow.email.common.encoding;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Map;

/**
 * Charset detection and conversion utilities for email processing.
 *
 * <p>Provides lenient charset lookup with common aliases, BOM detection,
 * and charset-safe conversions between UTF-8, ISO-8859-1, US-ASCII, and others.
 *
 * @since 0.1.0
 */
public final class CharsetUtils {

    /** UTF-8 BOM bytes. */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** UTF-16 Big Endian BOM. */
    private static final byte[] UTF16_BE_BOM = {(byte) 0xFE, (byte) 0xFF};

    /** UTF-16 Little Endian BOM. */
    private static final byte[] UTF16_LE_BOM = {(byte) 0xFF, (byte) 0xFE};

    /** Common charset aliases used in email. */
    private static final Map<String, Charset> ALIASES = Map.ofEntries(
            Map.entry("utf8", StandardCharsets.UTF_8),
            Map.entry("utf-8", StandardCharsets.UTF_8),
            Map.entry("ascii", StandardCharsets.US_ASCII),
            Map.entry("us-ascii", StandardCharsets.US_ASCII),
            Map.entry("iso-8859-1", StandardCharsets.ISO_8859_1),
            Map.entry("iso8859-1", StandardCharsets.ISO_8859_1),
            Map.entry("iso_8859-1", StandardCharsets.ISO_8859_1),
            Map.entry("latin1", StandardCharsets.ISO_8859_1),
            Map.entry("latin-1", StandardCharsets.ISO_8859_1),
            Map.entry("iso-8859-15", Charset.forName("ISO-8859-15")),
            Map.entry("windows-1252", Charset.forName("windows-1252")),
            Map.entry("cp1252", Charset.forName("windows-1252")),
            Map.entry("utf-16", StandardCharsets.UTF_16),
            Map.entry("utf-16be", StandardCharsets.UTF_16BE),
            Map.entry("utf-16le", StandardCharsets.UTF_16LE)
    );

    private CharsetUtils() {
    }

    /**
     * Resolves a charset by name, supporting common aliases.
     *
     * @param name the charset name (case-insensitive)
     * @return the resolved charset
     * @throws UnsupportedCharsetException if the charset is not recognized
     */
    public static Charset forName(String name) {
        if (name == null || name.isBlank()) {
            return StandardCharsets.US_ASCII;
        }
        String normalized = name.trim().toLowerCase();
        Charset aliased = ALIASES.get(normalized);
        if (aliased != null) {
            return aliased;
        }
        return Charset.forName(name);
    }

    /**
     * Detects the charset from a byte order mark (BOM).
     *
     * @param data the bytes to inspect
     * @return the detected charset, or null if no BOM is found
     */
    public static Charset detectBom(byte[] data) {
        if (data == null || data.length < 2) {
            return null;
        }
        if (data.length >= 3 && data[0] == UTF8_BOM[0] && data[1] == UTF8_BOM[1]
                && data[2] == UTF8_BOM[2]) {
            return StandardCharsets.UTF_8;
        }
        if (data[0] == UTF16_BE_BOM[0] && data[1] == UTF16_BE_BOM[1]) {
            return StandardCharsets.UTF_16BE;
        }
        if (data[0] == UTF16_LE_BOM[0] && data[1] == UTF16_LE_BOM[1]) {
            return StandardCharsets.UTF_16LE;
        }
        return null;
    }

    /**
     * Checks whether the given bytes are valid UTF-8.
     *
     * @param data the bytes to check
     * @return true if the bytes form valid UTF-8
     */
    public static boolean isValidUtf8(byte[] data) {
        int i = 0;
        while (i < data.length) {
            int b = data[i] & 0xFF;
            int expectedContinuation;
            if (b <= 0x7F) {
                expectedContinuation = 0;
            } else if (b >= 0xC2 && b <= 0xDF) {
                expectedContinuation = 1;
            } else if (b >= 0xE0 && b <= 0xEF) {
                expectedContinuation = 2;
            } else if (b >= 0xF0 && b <= 0xF4) {
                expectedContinuation = 3;
            } else {
                return false;
            }
            for (int j = 1; j <= expectedContinuation; j++) {
                if (i + j >= data.length) {
                    return false;
                }
                int cb = data[i + j] & 0xFF;
                if (cb < 0x80 || cb > 0xBF) {
                    return false;
                }
            }
            i += expectedContinuation + 1;
        }
        return true;
    }

    /**
     * Checks whether the given bytes are pure ASCII (all bytes 0-127).
     *
     * @param data the bytes to check
     * @return true if all bytes are in the ASCII range
     */
    public static boolean isAscii(byte[] data) {
        for (byte b : data) {
            if ((b & 0xFF) > 127) {
                return false;
            }
        }
        return true;
    }

    /**
     * Detects the most likely charset for the given bytes.
     *
     * <p>Detection priority: BOM, ASCII, UTF-8, fallback to ISO-8859-1.
     *
     * @param data the bytes to analyze
     * @return the detected charset (never null)
     */
    public static Charset detect(byte[] data) {
        Charset bomCharset = detectBom(data);
        if (bomCharset != null) {
            return bomCharset;
        }
        if (isAscii(data)) {
            return StandardCharsets.US_ASCII;
        }
        if (isValidUtf8(data)) {
            return StandardCharsets.UTF_8;
        }
        return StandardCharsets.ISO_8859_1;
    }

    /**
     * Converts bytes from the source charset to UTF-8.
     *
     * @param data          the bytes to convert
     * @param sourceCharset the source charset
     * @return the bytes in UTF-8 encoding
     */
    public static byte[] toUtf8(byte[] data, Charset sourceCharset) {
        if (sourceCharset.equals(StandardCharsets.UTF_8)) {
            return data;
        }
        String text = new String(data, sourceCharset);
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
