package ssg.legoflow.ssh.auth;

import ssg.legoflow.auth.gssapi.GssContextWrapper;
import ssg.legoflow.auth.gssapi.GssOids;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link GssApiAuth} — GSSAPI authentication method per RFC 4462.
 */
class GssApiAuthTest {

    private GssContextWrapper createClientContext() throws Exception {
        GSSManager manager = GSSManager.getInstance();
        GSSName name = manager.createName("host/server.example.com@EXAMPLE.COM",
                GssOids.KRB5_PRINCIPAL_NAME, GssOids.KERBEROS_V5);
        GSSContext ctx = manager.createContext(name, GssOids.KERBEROS_V5, null, GSSContext.DEFAULT_LIFETIME);
        ctx.requestMutualAuth(true);
        ctx.requestInteg(true);
        return new GssContextWrapper(ctx);
    }

    @Test
    void testMethodName() throws Exception {
        try (GssContextWrapper ctx = createClientContext()) {
            GssApiAuth auth = new GssApiAuth(ctx);
            assertThat(auth.methodName()).isEqualTo("gssapi-with-mic");
        }
    }

    @Test
    void testIsInteractive() throws Exception {
        try (GssContextWrapper ctx = createClientContext()) {
            GssApiAuth auth = new GssApiAuth(ctx);
            assertThat(auth.isInteractive()).isTrue();
        }
    }

    @Test
    void testConstructorNullContextThrows() {
        assertThatThrownBy(() -> new GssApiAuth(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testEncodeRequestStartsWithUserauthMessage() throws Exception {
        try (GssContextWrapper ctx = createClientContext()) {
            GssApiAuth auth = new GssApiAuth(ctx);
            byte[] request = auth.encodeRequest("user", "ssh-connection");
            assertThat(request).isNotEmpty();
            assertThat(request[0]).isEqualTo((byte) 50); // SSH_MSG_USERAUTH_REQUEST
        }
    }

    @Test
    void testEncodeRequestContainsMethodName() throws Exception {
        try (GssContextWrapper ctx = createClientContext()) {
            GssApiAuth auth = new GssApiAuth(ctx);
            byte[] request = auth.encodeRequest("user", "ssh-connection");
            String requestStr = new String(request, StandardCharsets.UTF_8);
            assertThat(requestStr).contains("gssapi-with-mic");
        }
    }

    @Test
    void testEncodeRequestContainsUsername() throws Exception {
        try (GssContextWrapper ctx = createClientContext()) {
            GssApiAuth auth = new GssApiAuth(ctx);
            byte[] request = auth.encodeRequest("testuser", "ssh-connection");
            String requestStr = new String(request, StandardCharsets.UTF_8);
            assertThat(requestStr).contains("testuser");
        }
    }

    @Test
    void testEncodeRequestContainsServiceName() throws Exception {
        try (GssContextWrapper ctx = createClientContext()) {
            GssApiAuth auth = new GssApiAuth(ctx);
            byte[] request = auth.encodeRequest("user", "ssh-connection");
            String requestStr = new String(request, StandardCharsets.UTF_8);
            assertThat(requestStr).contains("ssh-connection");
        }
    }

    @Test
    void testEncodeRequestContainsKerberosOid() throws Exception {
        try (GssContextWrapper ctx = createClientContext()) {
            GssApiAuth auth = new GssApiAuth(ctx);
            byte[] request = auth.encodeRequest("user", "ssh-connection");
            // Kerberos V5 OID DER encoding contains these bytes
            byte[] krbOidDer = GssOids.KERBEROS_V5.getDER();
            assertThat(containsBytes(request, krbOidDer)).isTrue();
        }
    }

    @Test
    void testEncodeRequestContainsOidCount() throws Exception {
        try (GssContextWrapper ctx = createClientContext()) {
            GssApiAuth auth = new GssApiAuth(ctx);
            byte[] request = auth.encodeRequest("user", "ssh-connection");
            // The request should contain uint32(1) for number of OIDs
            // After "gssapi-with-mic" string, there should be 0x00 0x00 0x00 0x01
            assertThat(containsBytes(request, new byte[]{0x00, 0x00, 0x00, 0x01})).isTrue();
        }
    }

    @Test
    void testIsCompleteInitiallyFalse() throws Exception {
        try (GssContextWrapper ctx = createClientContext()) {
            GssApiAuth auth = new GssApiAuth(ctx);
            assertThat(auth.isComplete()).isFalse();
        }
    }

    @Test
    void testHandleResponseNullThrows() throws Exception {
        try (GssContextWrapper ctx = createClientContext()) {
            GssApiAuth auth = new GssApiAuth(ctx);
            assertThatThrownBy(() -> auth.handleResponse(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void testCreateMICNullSessionIdThrows() throws Exception {
        try (GssContextWrapper ctx = createClientContext()) {
            GssApiAuth auth = new GssApiAuth(ctx);
            assertThatThrownBy(() -> auth.createMIC(null, "user", "ssh-connection"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void testCreateMICNullUsernameThrows() throws Exception {
        try (GssContextWrapper ctx = createClientContext()) {
            GssApiAuth auth = new GssApiAuth(ctx);
            assertThatThrownBy(() -> auth.createMIC(new byte[32], null, "ssh-connection"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void testCreateMICNullServiceNameThrows() throws Exception {
        try (GssContextWrapper ctx = createClientContext()) {
            GssApiAuth auth = new GssApiAuth(ctx);
            assertThatThrownBy(() -> auth.createMIC(new byte[32], "user", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- Helpers ----

    private boolean containsBytes(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
