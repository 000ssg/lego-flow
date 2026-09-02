package ssg.legoflow.ftp.demo;

import ssg.legoflow.ftp.security.FtpsConfig;
import ssg.legoflow.ftp.security.FtpsHandler;
import ssg.legoflow.ftp.security.FtpsMode;
import org.junit.jupiter.api.Test;
import javax.net.ssl.SSLContext;
import static org.assertj.core.api.Assertions.*;
/**
 * Integration tests for FTPS functionality.
 */
class FtpsIntegrationTest {

    @Test
    void testFtpsDemoRuns() {
        assertThatNoException().isThrownBy(FtpsDemo::run);
    }

    @Test
    void testExplicitModeConfig() {
        var config = FtpsConfig.builder()
                .mode(FtpsMode.EXPLICIT)
                .protocols("TLSv1.2", "TLSv1.3")
                .build();
        assertThat(config.mode()).isEqualTo(FtpsMode.EXPLICIT);
        assertThat(config.mode().defaultPort()).isEqualTo(21);
    }

    @Test
    void testImplicitModeConfig() {
        var config = FtpsConfig.builder()
                .mode(FtpsMode.IMPLICIT)
                .build();
        assertThat(config.mode()).isEqualTo(FtpsMode.IMPLICIT);
        assertThat(config.mode().defaultPort()).isEqualTo(990);
    }

    @Test
    void testSslContextCreation() throws Exception {
        var config = FtpsConfig.trustAll();
        SSLContext ctx = config.createSslContext();
        assertThat(ctx).isNotNull();
        assertThat(ctx.getProtocol()).isEqualTo("TLS");
    }

    @Test
    void testHandlerPbszProtFlow() throws Exception {
        var handler = new FtpsHandler(FtpsConfig.trustAll());
        assertThat(handler.isControlEncrypted()).isFalse();
        assertThat(handler.isDataProtected()).isFalse();

        handler.handlePbsz(0);
        assertThat(handler.getProtectionBufferSize()).isEqualTo(0);

        handler.handleProt("P");
        assertThat(handler.isDataProtected()).isTrue();

        handler.handleProt("C");
        assertThat(handler.isDataProtected()).isFalse();
    }

    @Test
    void testHandlerReset() throws Exception {
        var handler = new FtpsHandler(FtpsConfig.trustAll());
        handler.handleProt("P");
        assertThat(handler.isDataProtected()).isTrue();

        handler.reset();
        assertThat(handler.isControlEncrypted()).isFalse();
        assertThat(handler.isDataProtected()).isFalse();
    }

    @Test
    void testConfigWithCustomProtocols() {
        var config = FtpsConfig.builder()
                .protocols("TLSv1.3")
                .build();
        assertThat(config.protocols()).containsExactly("TLSv1.3");
    }

    @Test
    void testConfigWithCipherSuites() {
        var config = FtpsConfig.builder()
                .cipherSuites("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384")
                .build();
        assertThat(config.cipherSuites()).hasSize(2);
    }

    @Test
    void testConfigClientAuth() {
        var config = FtpsConfig.builder()
                .clientAuth(true)
                .build();
        assertThat(config.clientAuth()).isTrue();
    }

    @Test
    void testSocketFactoryCreation() throws Exception {
        var config = FtpsConfig.trustAll();
        assertThat(config.createSocketFactory()).isNotNull();
        assertThat(config.createServerSocketFactory()).isNotNull();
    }

    @Test
    void testImplicitFtpsServerConfig() {
        var ftpsConfig = FtpsConfig.builder()
                .mode(FtpsMode.IMPLICIT)
                .build();
        var serverConfig = ssg.legoflow.ftp.server.FtpServerConfig.builder()
                .port(990)
                .ftpsConfig(ftpsConfig)
                .build();
        assertThat(serverConfig.isFtpsEnabled()).isTrue();
        assertThat(serverConfig.ftpsConfig().mode()).isEqualTo(FtpsMode.IMPLICIT);
        assertThat(serverConfig.port()).isEqualTo(990);
    }

    @Test
    void testImplicitFtpsDefaultPort() {
        assertThat(FtpsMode.IMPLICIT.defaultPort()).isEqualTo(990);
        assertThat(FtpsMode.EXPLICIT.defaultPort()).isEqualTo(21);
    }
}
