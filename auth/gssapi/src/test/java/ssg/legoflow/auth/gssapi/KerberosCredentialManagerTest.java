package ssg.legoflow.auth.gssapi;

import org.junit.jupiter.api.Test;
import javax.security.auth.Subject;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link KerberosCredentialManager}. Tests parameter validation and
 * error handling without requiring an actual KDC.
 */
class KerberosCredentialManagerTest {

    @Test
    void testLoginWithKeytabNullPrincipalThrows() {
        assertThatThrownBy(() -> KerberosCredentialManager.loginWithKeytab(null, "/etc/krb5.keytab"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testLoginWithKeytabNullKeytabThrows() {
        assertThatThrownBy(() -> KerberosCredentialManager.loginWithKeytab("user@EXAMPLE.COM", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testLoginWithPasswordNullPrincipalThrows() {
        assertThatThrownBy(() -> KerberosCredentialManager.loginWithPassword(null, "pass".toCharArray()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testLoginWithPasswordNullPasswordThrows() {
        assertThatThrownBy(() -> KerberosCredentialManager.loginWithPassword("user@EXAMPLE.COM", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testLoginWithKeytabFailsWithoutKdc() {
        assertThatThrownBy(() ->
                KerberosCredentialManager.loginWithKeytab("user@EXAMPLE.COM", "/nonexistent.keytab"))
                .isInstanceOf(GssException.class)
                .hasMessageContaining("login failed");
    }

    @Test
    void testLoginWithPasswordFailsWithoutKdc() {
        assertThatThrownBy(() ->
                KerberosCredentialManager.loginWithPassword("user@EXAMPLE.COM", "password".toCharArray()))
                .isInstanceOf(GssException.class)
                .hasMessageContaining("login failed");
    }

    @Test
    void testGetServiceCredentialNullSubjectThrows() {
        GssConfig config = GssConfig.builder()
                .realm("R")
                .kdc("k")
                .servicePrincipal("s")
                .build();
        assertThatThrownBy(() -> KerberosCredentialManager.getServiceCredential(null, config))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGetServiceCredentialNullConfigThrows() {
        assertThatThrownBy(() ->
                KerberosCredentialManager.getServiceCredential(new Subject(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testIsCredentialValidNull() {
        assertThat(KerberosCredentialManager.isCredentialValid(null)).isFalse();
    }

    @Test
    void testRenewCredentialNullThrows() {
        assertThatThrownBy(() -> KerberosCredentialManager.renewCredential(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGetServiceCredentialFailsWithoutKdc() {
        GssConfig config = GssConfig.builder()
                .realm("EXAMPLE.COM")
                .kdc("kdc.example.com")
                .servicePrincipal("host/server.example.com@EXAMPLE.COM")
                .build();
        assertThatThrownBy(() ->
                KerberosCredentialManager.getServiceCredential(new Subject(), config))
                .isInstanceOf(GssException.class);
    }

    @Test
    void testLoginWithKeytabGssExceptionHasCause() {
        try {
            KerberosCredentialManager.loginWithKeytab("user@EXAMPLE.COM", "/nonexistent.keytab");
            fail("Should have thrown GssException");
        } catch (GssException e) {
            assertThat(e.getCause()).isNotNull();
        }
    }

    @Test
    void testLoginWithPasswordGssExceptionMessage() {
        try {
            KerberosCredentialManager.loginWithPassword("test@NONEXISTENT.REALM", "pass".toCharArray());
            fail("Should have thrown GssException");
        } catch (GssException e) {
            assertThat(e.getMessage()).contains("test@NONEXISTENT.REALM");
        }
    }
}
