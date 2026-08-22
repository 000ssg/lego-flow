package ssg.legoflow.ftp.server;

import ssg.legoflow.ftp.security.FtpsConfig;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class FtpServerConfigTest {

    @Test
    void testDefaultConfiguration() {
        FtpServerConfig config = FtpServerConfig.defaults();
        
        assertThat(config.host()).isEqualTo("0.0.0.0");
        assertThat(config.port()).isEqualTo(21);
        assertThat(config.passivePortMin()).isZero();
        assertThat(config.passivePortMax()).isZero();
        assertThat(config.sessionTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(config.maxConnections()).isEqualTo(100);
        assertThat(config.serverName()).isEqualTo("LegoFlow FTP Server");
        assertThat(config.ftpsConfig()).isNull();
        assertThat(config.isFtpsEnabled()).isFalse();
    }

    @Test
    void testBuilderWithAllSettings() {
        FtpsConfig ftpsConfig = FtpsConfig.trustAll();
        
        FtpServerConfig config = FtpServerConfig.builder()
                .host("127.0.0.1")
                .port(2121)
                .passivePortRange(50000, 50100)
                .sessionTimeout(Duration.ofMinutes(10))
                .maxConnections(200)
                .serverName("Custom FTP Server")
                .ftpsConfig(ftpsConfig)
                .build();
        
        assertThat(config.host()).isEqualTo("127.0.0.1");
        assertThat(config.port()).isEqualTo(2121);
        assertThat(config.passivePortMin()).isEqualTo(50000);
        assertThat(config.passivePortMax()).isEqualTo(50100);
        assertThat(config.sessionTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(config.maxConnections()).isEqualTo(200);
        assertThat(config.serverName()).isEqualTo("Custom FTP Server");
        assertThat(config.ftpsConfig()).isSameAs(ftpsConfig);
        assertThat(config.isFtpsEnabled()).isTrue();
    }

    @Test
    void testBuilderMinimalConfiguration() {
        FtpServerConfig config = FtpServerConfig.builder().build();
        
        // Should use all defaults
        assertThat(config.host()).isEqualTo("0.0.0.0");
        assertThat(config.port()).isEqualTo(21);
        assertThat(config.passivePortMin()).isZero();
        assertThat(config.passivePortMax()).isZero();
        assertThat(config.sessionTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(config.maxConnections()).isEqualTo(100);
        assertThat(config.serverName()).isEqualTo("LegoFlow FTP Server");
    }

    @Test
    void testBuilderWithCustomHostOnly() {
        FtpServerConfig config = FtpServerConfig.builder()
                .host("192.168.1.100")
                .build();
        
        assertThat(config.host()).isEqualTo("192.168.1.100");
        assertThat(config.port()).isEqualTo(21); // default
    }

    @Test
    void testBuilderWithCustomPortOnly() {
        FtpServerConfig config = FtpServerConfig.builder()
                .port(9999)
                .build();
        
        assertThat(config.port()).isEqualTo(9999);
        assertThat(config.host()).isEqualTo("0.0.0.0"); // default
    }

    @Test
    void testBuilderWithPassivePortRangeOnly() {
        FtpServerConfig config = FtpServerConfig.builder()
                .passivePortRange(60000, 65535)
                .build();
        
        assertThat(config.passivePortMin()).isEqualTo(60000);
        assertThat(config.passivePortMax()).isEqualTo(65535);
    }

    @Test
    void testBuilderWithSessionTimeoutOnly() {
        Duration timeout = Duration.ofSeconds(30);
        FtpServerConfig config = FtpServerConfig.builder()
                .sessionTimeout(timeout)
                .build();
        
        assertThat(config.sessionTimeout()).isEqualTo(timeout);
    }

    @Test
    void testBuilderWithMaxConnectionsOnly() {
        FtpServerConfig config = FtpServerConfig.builder()
                .maxConnections(50)
                .build();
        
        assertThat(config.maxConnections()).isEqualTo(50);
    }

    @Test
    void testBuilderWithServerNameOnly() {
        FtpServerConfig config = FtpServerConfig.builder()
                .serverName("My Server v2.0")
                .build();
        
        assertThat(config.serverName()).isEqualTo("My Server v2.0");
    }

    @Test
    void testFtpsDisabledByDefault() {
        FtpServerConfig config = FtpServerConfig.builder().build();
        assertThat(config.isFtpsEnabled()).isFalse();
        assertThat(config.ftpsConfig()).isNull();
    }

    @Test
    void testFtpsEnabledWhenConfigProvided() {
        FtpServerConfig config = FtpServerConfig.builder()
                .ftpsConfig(FtpsConfig.trustAll())
                .build();
        
        assertThat(config.isFtpsEnabled()).isTrue();
        assertThat(config.ftpsConfig()).isNotNull();
    }

    @Test
    void testBuilderReturnsSameInstanceForChaining() {
        FtpServerConfig.Builder builder = FtpServerConfig.builder();
        FtpServerConfig.Builder result = builder.host("127.0.0.1").port(2121);
        assertThat(result).isSameAs(builder);
    }

    @Test
    void testDefaultsIsEquivalentToEmptyBuilder() {
        FtpServerConfig defaults = FtpServerConfig.defaults();
        FtpServerConfig built = FtpServerConfig.builder().build();
        
        assertThat(defaults.host()).isEqualTo(built.host());
        assertThat(defaults.port()).isEqualTo(built.port());
        assertThat(defaults.passivePortMin()).isEqualTo(built.passivePortMin());
        assertThat(defaults.passivePortMax()).isEqualTo(built.passivePortMax());
        assertThat(defaults.sessionTimeout()).isEqualTo(built.sessionTimeout());
        assertThat(defaults.maxConnections()).isEqualTo(built.maxConnections());
        assertThat(defaults.serverName()).isEqualTo(built.serverName());
        assertThat(defaults.isFtpsEnabled()).isEqualTo(built.isFtpsEnabled());
    }

    @Test
    void testZeroPort() {
        FtpServerConfig config = FtpServerConfig.builder().port(0).build();
        assertThat(config.port()).isZero();
    }

    @Test
    void testMaxPortValue() {
        FtpServerConfig config = FtpServerConfig.builder().port(65535).build();
        assertThat(config.port()).isEqualTo(65535);
    }

    @Test
    void testSingleConnectionLimit() {
        FtpServerConfig config = FtpServerConfig.builder().maxConnections(1).build();
        assertThat(config.maxConnections()).isEqualTo(1);
    }
}
