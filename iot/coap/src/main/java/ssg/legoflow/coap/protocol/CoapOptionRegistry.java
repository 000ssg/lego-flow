package ssg.legoflow.coap.protocol;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of CoAP option metadata as defined in RFC 7252, Section 5.4.
 *
 * <p>Provides information about each option number including whether it is
 * critical or elective, unsafe or safe-to-forward, whether it acts as a
 * no-cache-key, its format, length constraints, and whether it is repeatable.
 *
 * @since 1.0.0
 */
public final class CoapOptionRegistry {

    /**
     * Option value format types.
     *
     * @since 1.0.0
     */
    public enum Format {
        /** Empty value (zero-length). */
        EMPTY,
        /** Opaque byte sequence. */
        OPAQUE,
        /** Unsigned integer (0-4 bytes, network byte order). */
        UINT,
        /** UTF-8 string. */
        STRING
    }

    /**
     * Metadata for a single CoAP option.
     *
     * @param number      the option number
     * @param name        the human-readable option name
     * @param critical    whether the option is critical (must be understood)
     * @param unsafe      whether the option is unsafe (not safe to forward by proxies)
     * @param noCacheKey  whether the option is a no-cache-key
     * @param format      the value format
     * @param minLength   the minimum value length in bytes
     * @param maxLength   the maximum value length in bytes
     * @param repeatable  whether the option may appear multiple times
     * @since 1.0.0
     */
    public record OptionInfo(
            int number,
            String name,
            boolean critical,
            boolean unsafe,
            boolean noCacheKey,
            Format format,
            int minLength,
            int maxLength,
            boolean repeatable
    ) {
    }

    private static final Map<Integer, OptionInfo> REGISTRY = new ConcurrentHashMap<>();

    static {
        register(new OptionInfo(CoapOption.IF_MATCH, "If-Match", true, false, false, Format.OPAQUE, 0, 8, true));
        register(new OptionInfo(CoapOption.URI_HOST, "Uri-Host", true, true, false, Format.STRING, 1, 255, false));
        register(new OptionInfo(CoapOption.ETAG, "ETag", false, false, false, Format.OPAQUE, 1, 8, true));
        register(new OptionInfo(CoapOption.IF_NONE_MATCH, "If-None-Match", true, false, false, Format.EMPTY, 0, 0, false));
        register(new OptionInfo(CoapOption.OBSERVE, "Observe", false, true, false, Format.UINT, 0, 3, false));
        register(new OptionInfo(CoapOption.URI_PORT, "Uri-Port", true, true, false, Format.UINT, 0, 2, false));
        register(new OptionInfo(CoapOption.LOCATION_PATH, "Location-Path", false, false, false, Format.STRING, 0, 255, true));
        register(new OptionInfo(CoapOption.URI_PATH, "Uri-Path", true, true, false, Format.STRING, 0, 255, true));
        register(new OptionInfo(CoapOption.CONTENT_FORMAT, "Content-Format", false, false, false, Format.UINT, 0, 2, false));
        register(new OptionInfo(CoapOption.MAX_AGE, "Max-Age", false, true, false, Format.UINT, 0, 4, false));
        register(new OptionInfo(CoapOption.URI_QUERY, "Uri-Query", true, true, false, Format.STRING, 0, 255, true));
        register(new OptionInfo(CoapOption.ACCEPT, "Accept", true, false, false, Format.UINT, 0, 2, false));
        register(new OptionInfo(CoapOption.LOCATION_QUERY, "Location-Query", false, false, false, Format.STRING, 0, 255, true));
        register(new OptionInfo(CoapOption.BLOCK2, "Block2", true, true, false, Format.UINT, 0, 3, false));
        register(new OptionInfo(CoapOption.BLOCK1, "Block1", true, true, false, Format.UINT, 0, 3, false));
        register(new OptionInfo(CoapOption.SIZE2, "Size2", false, false, true, Format.UINT, 0, 4, false));
        register(new OptionInfo(CoapOption.PROXY_URI, "Proxy-Uri", true, true, false, Format.STRING, 1, 1034, false));
        register(new OptionInfo(CoapOption.PROXY_SCHEME, "Proxy-Scheme", true, true, false, Format.STRING, 1, 255, false));
        register(new OptionInfo(CoapOption.SIZE1, "Size1", false, false, true, Format.UINT, 0, 4, false));
    }

    private CoapOptionRegistry() {
        // Utility class
    }

    /**
     * Registers option metadata.
     *
     * @param info the option info to register
     * @since 1.0.0
     */
    public static void register(OptionInfo info) {
        REGISTRY.put(info.number(), info);
    }

    /**
     * Returns the metadata for the given option number, if registered.
     *
     * @param number the option number
     * @return an {@link Optional} containing the info, or empty if unregistered
     * @since 1.0.0
     */
    public static Optional<OptionInfo> lookup(int number) {
        return Optional.ofNullable(REGISTRY.get(number));
    }

    /**
     * Returns whether an option number is critical (odd numbers per RFC 7252).
     *
     * @param number the option number
     * @return {@code true} if the option is critical
     * @since 1.0.0
     */
    public static boolean isCritical(int number) {
        return (number & 1) == 1;
    }

    /**
     * Returns whether an option number is elective (even numbers per RFC 7252).
     *
     * @param number the option number
     * @return {@code true} if the option is elective
     * @since 1.0.0
     */
    public static boolean isElective(int number) {
        return (number & 1) == 0;
    }

    /**
     * Returns whether an option is unsafe to forward (second least significant bit).
     *
     * @param number the option number
     * @return {@code true} if the option is unsafe
     * @since 1.0.0
     */
    public static boolean isUnsafe(int number) {
        return (number & 2) == 2;
    }

    /**
     * Returns whether an option is safe to forward.
     *
     * @param number the option number
     * @return {@code true} if the option is safe to forward
     * @since 1.0.0
     */
    public static boolean isSafeToForward(int number) {
        return !isUnsafe(number);
    }

    /**
     * Returns whether an option is a no-cache-key (bits 1-2 of option number are 0x1c).
     *
     * @param number the option number
     * @return {@code true} if the option is a no-cache-key
     * @since 1.0.0
     */
    public static boolean isNoCacheKey(int number) {
        return (number & 0x1E) == 0x1C;
    }
}
