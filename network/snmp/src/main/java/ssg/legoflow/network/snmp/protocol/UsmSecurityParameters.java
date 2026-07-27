package ssg.legoflow.network.snmp.protocol;

import java.util.Arrays;

/**
 * USM (User-based Security Model) security parameters as defined in RFC 3414.
 *
 * <p>These parameters are carried in the msgSecurityParameters field of an
 * SNMPv3 message. They contain the engine ID, timing information for replay
 * protection, user name, and authentication/privacy parameters.
 *
 * @param engineId    the authoritative engine ID
 * @param engineBoots the engine boot counter
 * @param engineTime  the engine time in seconds
 * @param userName    the user name
 * @param authParams  the authentication parameters (HMAC digest)
 * @param privParams  the privacy parameters (encryption salt/IV)
 * @since 1.0.0
 */
public record UsmSecurityParameters(
        byte[] engineId,
        int engineBoots,
        int engineTime,
        String userName,
        byte[] authParams,
        byte[] privParams
) {

    /**
     * Creates USM security parameters with validation and defensive copies.
     *
     * @param engineId    the engine ID (must not be null)
     * @param engineBoots the boot counter
     * @param engineTime  the engine time
     * @param userName    the user name (must not be null)
     * @param authParams  the auth parameters (must not be null)
     * @param privParams  the privacy parameters (must not be null)
     */
    public UsmSecurityParameters {
        if (engineId == null) throw new IllegalArgumentException("Engine ID must not be null");
        if (userName == null) throw new IllegalArgumentException("User name must not be null");
        if (authParams == null) throw new IllegalArgumentException("Auth params must not be null");
        if (privParams == null) throw new IllegalArgumentException("Priv params must not be null");
        engineId = engineId.clone();
        authParams = authParams.clone();
        privParams = privParams.clone();
    }

    /**
     * Returns a copy of the engine ID.
     *
     * @return copy of the engine ID
     */
    @Override
    public byte[] engineId() {
        return engineId.clone();
    }

    /**
     * Returns a copy of the authentication parameters.
     *
     * @return copy of auth params
     */
    @Override
    public byte[] authParams() {
        return authParams.clone();
    }

    /**
     * Returns a copy of the privacy parameters.
     *
     * @return copy of privacy params
     */
    @Override
    public byte[] privParams() {
        return privParams.clone();
    }

    /**
     * Creates empty USM security parameters (for engine discovery).
     *
     * @return empty parameters
     */
    public static UsmSecurityParameters empty() {
        return new UsmSecurityParameters(new byte[0], 0, 0, "", new byte[0], new byte[0]);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UsmSecurityParameters other
                && Arrays.equals(engineId, other.engineId)
                && engineBoots == other.engineBoots
                && engineTime == other.engineTime
                && userName.equals(other.userName)
                && Arrays.equals(authParams, other.authParams)
                && Arrays.equals(privParams, other.privParams);
    }

    @Override
    public int hashCode() {
        int h = Arrays.hashCode(engineId);
        h = 31 * h + engineBoots;
        h = 31 * h + engineTime;
        h = 31 * h + userName.hashCode();
        h = 31 * h + Arrays.hashCode(authParams);
        h = 31 * h + Arrays.hashCode(privParams);
        return h;
    }
}
