package ssg.legoflow.messaging.mqtt.protocol;

/**
 * MQTT protocol version identifiers.
 *
 * <p>Supports MQTT v3.1.1 (protocol level 4) and MQTT v5.0 (protocol level 5).
 *
 * @since 0.1.0
 */
public enum MqttVersion {

    /** MQTT version 3.1.1, protocol level 4. */
    V3_1_1("3.1.1", 4),

    /** MQTT version 5.0, protocol level 5. */
    V5_0("5.0", 5);

    private final String versionString;
    private final int protocolLevel;

    MqttVersion(String versionString, int protocolLevel) {
        this.versionString = versionString;
        this.protocolLevel = protocolLevel;
    }

    /**
     * Returns the human-readable version string.
     *
     * @return the version string (e.g. "3.1.1" or "5.0")
     */
    public String versionString() {
        return versionString;
    }

    /**
     * Returns the protocol level byte used in CONNECT packets.
     *
     * @return the protocol level (4 for v3.1.1, 5 for v5.0)
     */
    public int protocolLevel() {
        return protocolLevel;
    }

    /**
     * Returns the protocol name used in CONNECT packets.
     *
     * @return "MQTT" for both versions
     */
    public String protocolName() {
        return "MQTT";
    }

    /**
     * Resolves a {@code MqttVersion} from the given protocol level.
     *
     * @param level the protocol level byte
     * @return the matching version
     * @throws IllegalArgumentException if the level is not recognized
     */
    public static MqttVersion fromProtocolLevel(int level) {
        return switch (level) {
            case 4 -> V3_1_1;
            case 5 -> V5_0;
            default -> throw new IllegalArgumentException("Unknown MQTT protocol level: " + level);
        };
    }
}
