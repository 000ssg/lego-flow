package ssg.legoflow.acl.sasl;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

class SaslUtilitiesTest {

    @Test void saslPlainInitial() {
        var response = SaslUtilities.saslPlainInitial("user", "pass");
        assertThat(response).isEqualTo("\0user\0pass".getBytes());
    }

    @Test void saslExternalInitial() {
        var response = SaslUtilities.saslExternalInitial();
        assertThat(response).isEmpty();
    }

    @Test void scramSha256ClientFirst() {
        var first = SaslUtilities.scramSha256ClientFirst("admin", "nonce123");
        assertThat(first.cbind()).isEqualTo("n");
        assertThat(first.gs2()).contains("n=admin");
        assertThat(first.gs2()).contains("r=nonce123");
        assertThat(first.nonce()).isEqualTo("nonce123");
    }

    @Test void scramSha256ClientFinal() {
        var finalMsg = SaslUtilities.scramSha256ClientFinal(
                "client-nonce", "server-nonce", "c2FsdA==", 4096, "password");
        assertThat(finalMsg).contains("c=BI");
        assertThat(finalMsg).contains("r=client-nonceserver-nonce");
        assertThat(finalMsg).contains("p=");
    }

    @Test void scramSha256ServerSignature() {
        var sig = SaslUtilities.scramSha256ServerSignature("password", "c2FsdA==", 4096, "auth-message");
        assertThat(sig).isNotEmpty();
        assertThat(sig.length).isEqualTo(32); // SHA-256
    }

    @Test void postgresMd5Password() {
        var md5 = SaslUtilities.postgresMd5Password("alice", "secret", "abcd");
        assertThat(md5).startsWith("md5");
        assertThat(md5.length()).isGreaterThan(5); // md5 + hex digest
    }

    @Test void mysqlNativePassword() {
        var hash = SaslUtilities.mysqlNativePassword("password");
        assertThat(hash).hasSize(20); // SHA1 = 20 bytes
    }

    @Test void mysqlCachingSha2Response() throws Exception {
        var authData = new byte[20];
        java.security.SecureRandom.getInstanceStrong().nextBytes(authData);
        var response = SaslUtilities.mysqlCachingSha2Response("password", authData);
        assertThat(response).hasSize(32); // SHA256 = 32 bytes
    }

    @Test void wampCraResponse() {
        var response = SaslUtilities.wampCraResponse("secret", "challenge123");
        assertThat(response).hasSize(32); // HMAC-SHA256 = 32 bytes
    }

    @Test void wampCryptosignResponse() {
        var response = SaslUtilities.wampCryptosignResponse("challenge");
        assertThat(response).hasSize(64); // Ed25519 signature = 64 bytes
    }

    @Test void digestMd5Response() {
        var response = SaslUtilities.digestMd5Response(
                "user", "realm", "nonce", "/path", "GET", "auth", "cnonce", 1);
        assertThat(response).hasSize(32); // MD5 hex = 32 chars
    }

    @Test void kerberosPrincipal() {
        var principal = SaslUtilities.kerberosPrincipal("user", "EXAMPLE.COM");
        assertThat(principal).isEqualTo("user@EXAMPLE.COM");
    }

    @Test void oauth2Bearer() {
        var header = SaslUtilities.oauth2Bearer("mytoken123");
        assertThat(header).isEqualTo("Bearer mytoken123");
    }

    @Test void ntlmType1() {
        var ntlm = SaslUtilities.ntlmType1();
        assertThat(ntlm).isEqualTo("TlRMTVNTUAABAAAA");
    }
}
