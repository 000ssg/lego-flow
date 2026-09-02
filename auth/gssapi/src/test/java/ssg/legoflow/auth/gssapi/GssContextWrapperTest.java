package ssg.legoflow.auth.gssapi;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link GssContextWrapper}. Uses real GSS contexts where possible
 * and validates parameter checking, lifecycle, and error handling.
 * Actual authentication requires a KDC and is not tested here.
 */
class GssContextWrapperTest {

    @Test
    void testConstructorNullContextThrows() {
        assertThatThrownBy(() -> new GssContextWrapper(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInitSecContextNullInputThrows() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThatThrownBy(() -> wrapper.initSecContext(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void testAcceptSecContextNullInputThrows() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThatThrownBy(() -> wrapper.acceptSecContext(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void testIsEstablishedFalseBeforeExchange() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThat(wrapper.isEstablished()).isFalse();
        }
    }

    @Test
    void testGetContextReturnsUnderlyingContext() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThat(wrapper.getContext()).isSameAs(ctx);
        }
    }

    @Test
    void testAutoCloseableDoesNotThrow() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        assertThatCode(() -> {
            try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
                // auto-close
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void testDisposeDoesNotThrow() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        GssContextWrapper wrapper = new GssContextWrapper(ctx);
        assertThatCode(wrapper::dispose).doesNotThrowAnyException();
    }

    @Test
    void testDoubleDisposeDoesNotThrow() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        GssContextWrapper wrapper = new GssContextWrapper(ctx);
        wrapper.dispose();
        assertThatCode(wrapper::dispose).doesNotThrowAnyException();
    }

    @Test
    void testWrapNullThrows() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThatThrownBy(() -> wrapper.wrap(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void testUnwrapNullThrows() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThatThrownBy(() -> wrapper.unwrap(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void testGetMICNullThrows() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThatThrownBy(() -> wrapper.getMIC(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void testVerifyMICNullDataThrows() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThatThrownBy(() -> wrapper.verifyMIC(null, new byte[]{1}))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void testVerifyMICNullMicThrows() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThatThrownBy(() -> wrapper.verifyMIC(new byte[]{1}, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void testInitSecContextFailsWithoutKdc() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThatThrownBy(() -> wrapper.initSecContext(new byte[0]))
                    .isInstanceOf(GssException.class);
        }
    }

    @Test
    void testAcceptSecContextFailsWithInvalidToken() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThatThrownBy(() -> wrapper.acceptSecContext(new byte[]{1, 2, 3}))
                    .isInstanceOf(GssException.class);
        }
    }

    @Test
    void testWrapFailsWithoutEstablishedContext() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThatThrownBy(() -> wrapper.wrap(new byte[]{1, 2, 3}))
                    .isInstanceOf(GssException.class);
        }
    }

    @Test
    void testUnwrapFailsWithoutEstablishedContext() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThatThrownBy(() -> wrapper.unwrap(new byte[]{1, 2}))
                    .isInstanceOf(GssException.class);
        }
    }

    @Test
    void testGetMICFailsWithoutEstablishedContext() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThatThrownBy(() -> wrapper.getMIC(new byte[]{1}))
                    .isInstanceOf(GssException.class);
        }
    }

    @Test
    void testVerifyMICReturnsFalseWithoutEstablishedContext() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            assertThat(wrapper.verifyMIC(new byte[]{1}, new byte[]{2})).isFalse();
        }
    }

    @Test
    void testGetMutualAuthBeforeEstablishment() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        ctx.requestMutualAuth(true);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            // Before establishment, returns the requested value
            assertThat(wrapper.getMutualAuth()).isTrue();
        }
    }

    @Test
    void testGetSrcNameFailsBeforeEstablishment() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        try (GssContextWrapper wrapper = new GssContextWrapper(ctx)) {
            // Before context establishment, getSrcName throws (either GssException wrapping
            // GSSException, or NPE from the JDK implementation when mechCtxt is null)
            assertThatThrownBy(wrapper::getSrcName).isInstanceOf(Exception.class);
        }
    }
}
