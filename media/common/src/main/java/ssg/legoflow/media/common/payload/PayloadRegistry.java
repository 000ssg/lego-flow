package ssg.legoflow.media.common.payload;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Registry of known RTP payload types.
 *
 * <p>Pre-populated with static payload types from RFC 3551. Dynamic payload
 * types can be registered at runtime based on SDP negotiation.
 *
 * @since 1.0.0
 */
public final class PayloadRegistry {

    private static final Map<Integer, PayloadType> STATIC_TYPES;

    static {
        var map = new LinkedHashMap<Integer, PayloadType>();
        register(map, PayloadType.PCMU);
        register(map, PayloadType.GSM);
        register(map, PayloadType.G723);
        register(map, PayloadType.DVI4_8000);
        register(map, PayloadType.DVI4_16000);
        register(map, PayloadType.LPC);
        register(map, PayloadType.PCMA);
        register(map, PayloadType.G722);
        register(map, PayloadType.L16_STEREO);
        register(map, PayloadType.L16_MONO);
        register(map, PayloadType.QCELP);
        register(map, PayloadType.CN);
        register(map, PayloadType.G729);
        register(map, PayloadType.JPEG);
        register(map, PayloadType.H261);
        register(map, PayloadType.MPV);
        register(map, PayloadType.MP2T);
        register(map, PayloadType.H263);
        STATIC_TYPES = Collections.unmodifiableMap(map);
    }

    private static void register(Map<Integer, PayloadType> map, PayloadType pt) {
        map.put(pt.number(), pt);
    }

    private final Map<Integer, PayloadType> dynamicTypes = new LinkedHashMap<>();

    /**
     * Creates a new payload registry with only static types.
     */
    public PayloadRegistry() {
    }

    /**
     * Looks up a payload type by number, checking dynamic types first, then static.
     *
     * @param number the payload type number
     * @return the payload type, or empty if unknown
     */
    public Optional<PayloadType> lookup(int number) {
        PayloadType pt = dynamicTypes.get(number);
        if (pt != null) {
            return Optional.of(pt);
        }
        return Optional.ofNullable(STATIC_TYPES.get(number));
    }

    /**
     * Registers a dynamic payload type.
     *
     * @param number    the payload type number (96-127)
     * @param codec     the encoding name
     * @param clockRate the clock rate in Hz
     * @param channels  the number of channels, or empty for video
     * @param mediaType the media type
     * @return the registered payload type
     * @throws IllegalArgumentException if the number is not in the dynamic range
     */
    public PayloadType registerDynamic(int number, String codec, int clockRate,
                                        OptionalInt channels, String mediaType) {
        if (number < PayloadType.DYNAMIC_MIN || number > PayloadType.MAX) {
            throw new IllegalArgumentException(
                    "Dynamic payload type must be " + PayloadType.DYNAMIC_MIN + "-" + PayloadType.MAX + ": " + number);
        }
        var pt = new PayloadType(number, codec, clockRate, channels, mediaType);
        dynamicTypes.put(number, pt);
        return pt;
    }

    /**
     * Returns all known static payload types.
     *
     * @return unmodifiable map of static payload types
     */
    public static Map<Integer, PayloadType> staticTypes() {
        return STATIC_TYPES;
    }

    /**
     * Returns all registered dynamic payload types.
     *
     * @return unmodifiable map of dynamic payload types
     */
    public Map<Integer, PayloadType> dynamicTypes() {
        return Collections.unmodifiableMap(dynamicTypes);
    }

    /**
     * Clears all dynamic payload type registrations.
     */
    public void clearDynamic() {
        dynamicTypes.clear();
    }
}
