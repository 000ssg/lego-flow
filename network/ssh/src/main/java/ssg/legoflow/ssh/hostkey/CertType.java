package ssg.legoflow.ssh.hostkey;

/**
 * SSH certificate type per OpenSSH PROTOCOL.certkeys.
 *
 * @since 0.1.0
 */
public enum CertType {

    /** User certificate (type 1). */
    USER(1),

    /** Host certificate (type 2). */
    HOST(2);

    private final int value;

    CertType(int value) {
        this.value = value;
    }

    /**
     * Returns the wire format value.
     *
     * @return 1 for USER, 2 for HOST
     */
    public int value() { return value; }

    /**
     * Returns the CertType for the given wire value.
     *
     * @param value the wire format value
     * @return the corresponding CertType
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static CertType fromValue(int value) {
        return switch (value) {
            case 1 -> USER;
            case 2 -> HOST;
            default -> throw new IllegalArgumentException("Unknown cert type: " + value);
        };
    }
}
