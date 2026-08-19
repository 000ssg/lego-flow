package ssg.legoflow.auth.gssapi;

import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link GssContextFactory}. Since no actual KDC is available, these
 * tests validate parameter checking, OID construction, and context creation.
 */
class GssContextFactoryTest {

    private GssConfig sampleConfig() {
        return GssConfig.builder()
                .realm("EXAMPLE.COM")
                .kdc("kdc.example.com")
                .servicePrincipal("host/server.example.com@EXAMPLE.COM")
                .build();
    }

    @Test
    void testCreateClientContextNullConfigThrows() {
        assertThatThrownBy(() -> GssContextFactory.createClientContext(null, "principal"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCreateClientContextNullTargetThrows() {
        assertThatThrownBy(() -> GssContextFactory.createClientContext(sampleConfig(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCreateServerContextNullConfigThrows() {
        assertThatThrownBy(() -> GssContextFactory.createServerContext(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCreateClientContextReturnsWrapper() throws GssException {
        // Client context creation succeeds (actual exchange requires KDC)
        try (GssContextWrapper wrapper = GssContextFactory.createClientContext(
                sampleConfig(), "host/server.example.com@EXAMPLE.COM")) {
            assertThat(wrapper).isNotNull();
            assertThat(wrapper.isEstablished()).isFalse();
            assertThat(wrapper.getContext()).isNotNull();
        }
    }

    @Test
    void testCreateServerContextThrowsGssExceptionWithoutCredentials() {
        // Server context requires valid credentials; without KDC this fails
        assertThatThrownBy(() -> GssContextFactory.createServerContext(sampleConfig()))
                .isInstanceOf(GssException.class);
    }

    @Test
    void testCreateSpnegoClientContextNullConfigThrows() {
        assertThatThrownBy(() -> GssContextFactory.createSpnegoClientContext(null, "principal"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCreateSpnegoClientContextNullTargetThrows() {
        assertThatThrownBy(() -> GssContextFactory.createSpnegoClientContext(sampleConfig(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCreateSpnegoClientContextReturnsWrapper() throws GssException {
        try (GssContextWrapper wrapper = GssContextFactory.createSpnegoClientContext(
                sampleConfig(), "host/server@EXAMPLE.COM")) {
            assertThat(wrapper).isNotNull();
            assertThat(wrapper.isEstablished()).isFalse();
        }
    }

    @Test
    void testKerberosOidIsValid() {
        assertThat(GssOids.KERBEROS_V5.toString()).isEqualTo("1.2.840.113554.1.2.2");
    }

    @Test
    void testSpnegoOidIsValid() {
        assertThat(GssOids.SPNEGO.toString()).isEqualTo("1.3.6.1.5.5.2");
    }

    @Test
    void testGssManagerAvailable() {
        GSSManager manager = GSSManager.getInstance();
        assertThat(manager).isNotNull();
    }

    @Test
    void testGssManagerSupportsMechanisms() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        Oid[] mechs = manager.getMechs();
        assertThat(mechs).isNotNull();
    }

    @Test
    void testClientContextMutualAuthRequested() throws GssException {
        try (GssContextWrapper wrapper = GssContextFactory.createClientContext(
                sampleConfig(), "host/server.example.com@EXAMPLE.COM")) {
            assertThat(wrapper.getMutualAuth()).isTrue();
        }
    }
}
