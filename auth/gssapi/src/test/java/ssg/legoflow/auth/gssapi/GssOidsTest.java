package ssg.legoflow.auth.gssapi;

import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link GssOids} constants.
 */
class GssOidsTest {

    @Test
    void testKerberosV5OidValue() throws GSSException {
        assertThat(GssOids.KERBEROS_V5).isEqualTo(new Oid("1.2.840.113554.1.2.2"));
    }

    @Test
    void testSpnegoOidValue() throws GSSException {
        assertThat(GssOids.SPNEGO).isEqualTo(new Oid("1.3.6.1.5.5.2"));
    }

    @Test
    void testKrb5PrincipalNameOidValue() throws GSSException {
        assertThat(GssOids.KRB5_PRINCIPAL_NAME).isEqualTo(new Oid("1.2.840.113554.1.2.2.1"));
    }

    @Test
    void testKerberosV5OidNotNull() {
        assertThat(GssOids.KERBEROS_V5).isNotNull();
    }

    @Test
    void testSpnegoOidNotNull() {
        assertThat(GssOids.SPNEGO).isNotNull();
    }

    @Test
    void testKrb5PrincipalNameOidNotNull() {
        assertThat(GssOids.KRB5_PRINCIPAL_NAME).isNotNull();
    }

    @Test
    void testKerberosV5OidString() {
        assertThat(GssOids.KERBEROS_V5.toString()).isEqualTo("1.2.840.113554.1.2.2");
    }

    @Test
    void testSpnegoOidString() {
        assertThat(GssOids.SPNEGO.toString()).isEqualTo("1.3.6.1.5.5.2");
    }

    @Test
    void testKrb5PrincipalNameOidString() {
        assertThat(GssOids.KRB5_PRINCIPAL_NAME.toString()).isEqualTo("1.2.840.113554.1.2.2.1");
    }

    @Test
    void testOidsAreDifferent() {
        assertThat(GssOids.KERBEROS_V5).isNotEqualTo(GssOids.SPNEGO);
        assertThat(GssOids.KERBEROS_V5).isNotEqualTo(GssOids.KRB5_PRINCIPAL_NAME);
        assertThat(GssOids.SPNEGO).isNotEqualTo(GssOids.KRB5_PRINCIPAL_NAME);
    }

    @Test
    void testKerberosV5OidDerEncoding() throws GSSException {
        byte[] der = GssOids.KERBEROS_V5.getDER();
        assertThat(der).isNotEmpty();
        // OID tag is 0x06
        assertThat(der[0]).isEqualTo((byte) 0x06);
    }

    @Test
    void testSpnegoOidDerEncoding() throws GSSException {
        byte[] der = GssOids.SPNEGO.getDER();
        assertThat(der).isNotEmpty();
        assertThat(der[0]).isEqualTo((byte) 0x06);
    }

    @Test
    void testKerberosV5ContainedInOid() throws GSSException {
        Oid kerb = new Oid("1.2.840.113554.1.2.2");
        assertThat(GssOids.KERBEROS_V5.containedIn(new Oid[]{kerb, GssOids.SPNEGO})).isTrue();
    }
}
