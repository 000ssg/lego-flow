package ssg.legoflow.ftp.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link FtpsHandler}.
 */
class FtpsHandlerTest {

    @Test
    void testCreateWithTrustAllConfig() throws Exception {
        var config = FtpsConfig.trustAll();
        var handler = new FtpsHandler(config);
        assertThat(handler.isControlEncrypted()).isFalse();
        assertThat(handler.isDataProtected()).isFalse();
        assertThat(handler.getProtectionBufferSize()).isEqualTo(0);
    }

    @Test
    void testHandlePbsz() throws Exception {
        var handler = new FtpsHandler(FtpsConfig.trustAll());
        assertThat(handler.handlePbsz(0)).isTrue();
        assertThat(handler.getProtectionBufferSize()).isEqualTo(0);
    }

    @Test
    void testHandlePbszNonZero() throws Exception {
        // RFC 4217: For TLS, the only valid value is 0
        var handler = new FtpsHandler(FtpsConfig.trustAll());
        assertThat(handler.handlePbsz(1024)).isTrue();
        // Should be forced to 0 for TLS
        assertThat(handler.getProtectionBufferSize()).isEqualTo(0);
    }

    @Test
    void testHandleProtP() throws Exception {
        var handler = new FtpsHandler(FtpsConfig.trustAll());
        assertThat(handler.handleProt("P")).isTrue();
        assertThat(handler.isDataProtected()).isTrue();
    }

    @Test
    void testHandleProtC() throws Exception {
        var handler = new FtpsHandler(FtpsConfig.trustAll());
        handler.handleProt("P");
        assertThat(handler.isDataProtected()).isTrue();
        assertThat(handler.handleProt("C")).isTrue();
        assertThat(handler.isDataProtected()).isFalse();
    }

    @Test
    void testHandleProtInvalid() throws Exception {
        var handler = new FtpsHandler(FtpsConfig.trustAll());
        assertThat(handler.handleProt("X")).isFalse();
        assertThat(handler.isDataProtected()).isFalse();
    }

    @Test
    void testHandleProtNull() throws Exception {
        var handler = new FtpsHandler(FtpsConfig.trustAll());
        assertThat(handler.handleProt(null)).isFalse();
    }

    @Test
    void testHandleProtCaseInsensitive() throws Exception {
        var handler = new FtpsHandler(FtpsConfig.trustAll());
        assertThat(handler.handleProt("p")).isTrue();
        assertThat(handler.isDataProtected()).isTrue();
        assertThat(handler.handleProt("c")).isTrue();
        assertThat(handler.isDataProtected()).isFalse();
    }

    @Test
    void testReset() throws Exception {
        var handler = new FtpsHandler(FtpsConfig.trustAll());
        handler.handleProt("P");
        handler.handlePbsz(0);
        assertThat(handler.isDataProtected()).isTrue();
        handler.reset();
        assertThat(handler.isControlEncrypted()).isFalse();
        assertThat(handler.isDataProtected()).isFalse();
        assertThat(handler.getProtectionBufferSize()).isEqualTo(0);
    }

    @Test
    void testSslContextNotNull() throws Exception {
        var handler = new FtpsHandler(FtpsConfig.trustAll());
        assertThat(handler.sslContext()).isNotNull();
    }

    @Test
    void testConfigAccessor() throws Exception {
        var config = FtpsConfig.trustAll();
        var handler = new FtpsHandler(config);
        assertThat(handler.config()).isSameAs(config);
    }

    @Test
    void testFtpsConfigBuilder() {
        var config = FtpsConfig.builder()
                .mode(FtpsMode.IMPLICIT)
                .protocols("TLSv1.3")
                .build();
        assertThat(config.mode()).isEqualTo(FtpsMode.IMPLICIT);
        assertThat(config.protocols()).containsExactly("TLSv1.3");
    }

    @Test
    void testFtpsModeDefaultPorts() {
        assertThat(FtpsMode.IMPLICIT.defaultPort()).isEqualTo(990);
        assertThat(FtpsMode.EXPLICIT.defaultPort()).isEqualTo(21);
    }

    @Test
    void testFtpsConfigDefaults() {
        var config = FtpsConfig.builder().build();
        assertThat(config.mode()).isEqualTo(FtpsMode.EXPLICIT);
        assertThat(config.protocols()).contains("TLSv1.2", "TLSv1.3");
        assertThat(config.keystorePath()).isNull();
        assertThat(config.truststorePath()).isNull();
        assertThat(config.clientAuth()).isFalse();
        assertThat(config.cipherSuites()).isNull();
    }

    @Test
    void testFtpsConfigCreateSslContext() throws Exception {
        var config = FtpsConfig.trustAll();
        var ctx = config.createSslContext();
        assertThat(ctx).isNotNull();
        assertThat(ctx.getProtocol()).isEqualTo("TLS");
    }

    @Test
    void testFtpsConfigCreateSocketFactory() throws Exception {
        var config = FtpsConfig.trustAll();
        var factory = config.createSocketFactory();
        assertThat(factory).isNotNull();
    }

    @Test
    void testFtpsConfigCreateServerSocketFactory() throws Exception {
        var config = FtpsConfig.trustAll();
        var factory = config.createServerSocketFactory();
        assertThat(factory).isNotNull();
    }
}
