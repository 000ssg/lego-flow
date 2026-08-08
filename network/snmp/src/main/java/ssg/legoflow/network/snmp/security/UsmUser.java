package ssg.legoflow.network.snmp.security;

import java.util.Arrays;

/**
 * A USM (User-based Security Model) user entry.
 *
 * <p>Contains the user's authentication and privacy credentials.
 * Passwords are stored as localized keys after key localization
 * with an engine ID.
 *
 * @since 0.1.0
 */
public final class UsmUser {

    private final String userName;
    private final AuthProtocol authProtocol;
    private final byte[] authKey;
    private final PrivProtocol privProtocol;
    private final byte[] privKey;

    /**
     * Creates a USM user with the given credentials.
     *
     * @param userName     the user name (must not be null or empty)
     * @param authProtocol the authentication protocol
     * @param authKey      the localized authentication key (may be empty for NONE)
     * @param privProtocol the privacy protocol
     * @param privKey      the localized privacy key (may be empty for NONE)
     */
    public UsmUser(String userName, AuthProtocol authProtocol, byte[] authKey,
                   PrivProtocol privProtocol, byte[] privKey) {
        if (userName == null || userName.isEmpty()) {
            throw new IllegalArgumentException("User name must not be null or empty");
        }
        if (authProtocol == null) {
            throw new IllegalArgumentException("Auth protocol must not be null");
        }
        if (privProtocol == null) {
            throw new IllegalArgumentException("Priv protocol must not be null");
        }
        this.userName = userName;
        this.authProtocol = authProtocol;
        this.authKey = authKey != null ? authKey.clone() : new byte[0];
        this.privProtocol = privProtocol;
        this.privKey = privKey != null ? privKey.clone() : new byte[0];
    }

    /**
     * Creates a USM user with no authentication or privacy (noAuthNoPriv).
     *
     * @param userName the user name
     * @return the user
     */
    public static UsmUser noAuth(String userName) {
        return new UsmUser(userName, AuthProtocol.NONE, new byte[0],
                PrivProtocol.NONE, new byte[0]);
    }

    /**
     * Creates a USM user with authentication but no privacy (authNoPriv).
     *
     * @param userName     the user name
     * @param authProtocol the authentication protocol
     * @param authKey      the localized authentication key
     * @return the user
     */
    public static UsmUser authNoPriv(String userName, AuthProtocol authProtocol, byte[] authKey) {
        return new UsmUser(userName, authProtocol, authKey, PrivProtocol.NONE, new byte[0]);
    }

    /**
     * Creates a USM user with authentication and privacy (authPriv).
     *
     * @param userName     the user name
     * @param authProtocol the authentication protocol
     * @param authKey      the localized authentication key
     * @param privProtocol the privacy protocol
     * @param privKey      the localized privacy key
     * @return the user
     */
    public static UsmUser authPriv(String userName, AuthProtocol authProtocol, byte[] authKey,
                                   PrivProtocol privProtocol, byte[] privKey) {
        return new UsmUser(userName, authProtocol, authKey, privProtocol, privKey);
    }

    /**
     * Returns the user name.
     *
     * @return the user name
     */
    public String userName() {
        return userName;
    }

    /**
     * Returns the authentication protocol.
     *
     * @return the auth protocol
     */
    public AuthProtocol authProtocol() {
        return authProtocol;
    }

    /**
     * Returns a copy of the localized authentication key.
     *
     * @return the auth key
     */
    public byte[] authKey() {
        return authKey.clone();
    }

    /**
     * Returns the privacy protocol.
     *
     * @return the priv protocol
     */
    public PrivProtocol privProtocol() {
        return privProtocol;
    }

    /**
     * Returns a copy of the localized privacy key.
     *
     * @return the priv key
     */
    public byte[] privKey() {
        return privKey.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UsmUser other && userName.equals(other.userName);
    }

    @Override
    public int hashCode() {
        return userName.hashCode();
    }

    @Override
    public String toString() {
        return "UsmUser[%s, auth=%s, priv=%s]".formatted(userName, authProtocol, privProtocol);
    }
}
