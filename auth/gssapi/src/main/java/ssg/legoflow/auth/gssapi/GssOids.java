package ssg.legoflow.auth.gssapi;

import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;

/**
 * Standard OID constants for GSS-API mechanisms.
 *
 * @since 0.1.0
 */
public final class GssOids {

    /** Kerberos V5 mechanism OID (1.2.840.113554.1.2.2). */
    public static final Oid KERBEROS_V5;

    /** SPNEGO mechanism OID (1.3.6.1.5.5.2). */
    public static final Oid SPNEGO;

    /** Kerberos V5 principal name type OID (1.2.840.113554.1.2.2.1). */
    public static final Oid KRB5_PRINCIPAL_NAME;

    static {
        try {
            KERBEROS_V5 = new Oid("1.2.840.113554.1.2.2");
            SPNEGO = new Oid("1.3.6.1.5.5.2");
            KRB5_PRINCIPAL_NAME = new Oid("1.2.840.113554.1.2.2.1");
        } catch (GSSException e) {
            throw new ExceptionInInitializerError("Failed to create GSS OIDs: " + e.getMessage());
        }
    }

    private GssOids() {
        // utility class
    }
}
