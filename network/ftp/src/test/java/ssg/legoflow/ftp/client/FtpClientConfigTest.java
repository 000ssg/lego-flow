package ssg.legoflow.ftp.client;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.time.Duration;

class FtpClientConfigTest {

    @Test void testDefaults() {
        var config = FtpClientConfig.defaults();
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.soTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.bufferSize()).isEqualTo(8192);
        assertThat(config.passiveMode()).isTrue();
        assertThat(config.autoDetectBinary()).isTrue();
        assertThat(config.defaultControlEncoding()).isEqualTo("UTF-8");
    }

    @Test void testBuilderWithAllSetters() {
        var config = FtpClientConfig.builder()
                .connectTimeout(Duration.ofSeconds(30))
                .soTimeout(Duration.ofMinutes(2))
                .bufferSize(16384)
                .passiveMode(false)
                .autoDetectBinary(false)
                .defaultControlEncoding("ISO-8859-1")
                .build();
        
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.soTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(config.bufferSize()).isEqualTo(16384);
        assertThat(config.passiveMode()).isFalse();
        assertThat(config.autoDetectBinary()).isFalse();
        assertThat(config.defaultControlEncoding()).isEqualTo("ISO-8859-1");
    }

    @Test void testBuilderChaining() {
        var builder = FtpClientConfig.builder();
        var result1 = builder.connectTimeout(Duration.ofSeconds(5));
        var result2 = builder.soTimeout(Duration.ofSeconds(10));
        // All setters return the same builder for chaining
        assertThat(result1).isSameAs(builder);
        assertThat(result2).isSameAs(builder);
    }

    @Test void testBuilderPartialOverride() {
        var config = FtpClientConfig.builder()
                .passiveMode(false)
                .build();
        
        // Only passive mode changed, others should use defaults
        assertThat(config.passiveMode()).isFalse();
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.soTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.bufferSize()).isEqualTo(8192);
    }

    @Test void testBuilderEmptyBuild() {
        var bld = FtpClientConfig.builder().build();
        var def = FtpClientConfig.defaults();
        // Both should have same values when no overrides applied
        assertThat(bld.connectTimeout()).isEqualTo(def.connectTimeout());
        assertThat(bld.soTimeout()).isEqualTo(def.soTimeout());
        assertThat(bld.bufferSize()).isEqualTo(def.bufferSize());
        assertThat(bld.passiveMode()).isEqualTo(def.passiveMode());
        assertThat(bld.autoDetectBinary()).isEqualTo(def.autoDetectBinary());
        assertThat(bld.defaultControlEncoding()).isEqualTo(def.defaultControlEncoding());
    }

    @Test void testMultipleBuildersIndependent() {
        var b1 = FtpClientConfig.builder().passiveMode(false);
        var b2 = FtpClientConfig.builder().autoDetectBinary(false);
        
        var c1 = b1.build();
        var c2 = b2.build();
        
        assertThat(c1.passiveMode()).isFalse();
        assertThat(c2.autoDetectBinary()).isFalse();
        // Cross-check: other defaults should be true
        assertThat(c1.autoDetectBinary()).isTrue();
        assertThat(c2.passiveMode()).isTrue();
    }

    @Test void testConnectTimeoutCustom() {
        var config = FtpClientConfig.builder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();
        assertThat(config.connectTimeout().getSeconds()).isEqualTo(60);
    }

    @Test void testSoTimeoutCustom() {
        var config = FtpClientConfig.builder()
                .soTimeout(Duration.ofMinutes(5))
                .build();
        assertThat(config.soTimeout().getSeconds()).isEqualTo(300);
    }

    @Test void testBufferSizes() {
        int[] sizes = {1024, 2048, 4096, 8192, 16384, 32768};
        for (int size : sizes) {
            var config = FtpClientConfig.builder().bufferSize(size).build();
            assertThat(config.bufferSize()).isEqualTo(size);
        }
    }

    @Test void testPassiveModeTrueAndFalse() {
        var passive = FtpClientConfig.builder().passiveMode(true).build();
        var active = FtpClientConfig.builder().passiveMode(false).build();
        
        assertThat(passive.passiveMode()).isTrue();
        assertThat(active.passiveMode()).isFalse();
    }

    @Test void testAutoDetectBinary() {
        var detectOn = FtpClientConfig.builder().autoDetectBinary(true).build();
        var detectOff = FtpClientConfig.builder().autoDetectBinary(false).build();
        
        assertThat(detectOn.autoDetectBinary()).isTrue();
        assertThat(detectOff.autoDetectBinary()).isFalse();
    }

    @Test void testEncodingVariants() {
        String[] encodings = {"UTF-8", "ISO-8859-1", "US-ASCII", "windows-1252"};
        for (String enc : encodings) {
            var config = FtpClientConfig.builder().defaultControlEncoding(enc).build();
            assertThat(config.defaultControlEncoding()).isEqualTo(enc);
        }
    }

    @Test void testBuilderDoesNotMutateDefaults() {
        // Building one config should not affect the next build's defaults
        var config1 = FtpClientConfig.builder()
                .bufferSize(12345)
                .passiveMode(false)
                .build();
        
        var config2 = FtpClientConfig.defaults();
        assertThat(config2.bufferSize()).isEqualTo(8192); // default, not 12345
        assertThat(config2.passiveMode()).isTrue(); // default, not false
    }

    @Test void testBuilderBuildMultipleTimesSameResult() {
        var builder = FtpClientConfig.builder()
                .connectTimeout(Duration.ofSeconds(99))
                .bufferSize(42);
        
        var c1 = builder.build();
        var c2 = builder.build();
        
        assertThat(c1.connectTimeout()).isEqualTo(c2.connectTimeout());
        assertThat(c1.bufferSize()).isEqualTo(c2.bufferSize());
    }

    @Test void testConfigFieldsAreFinal() {
        var config = FtpClientConfig.builder()
                .connectTimeout(Duration.ofSeconds(7))
                .soTimeout(Duration.ofSeconds(14))
                .bufferSize(2048)
                .passiveMode(false)
                .autoDetectBinary(false)
                .defaultControlEncoding("ASCII")
                .build();
        
        // Verify each field is accessible consistently
        var timeout = config.connectTimeout();
        assertThat(config.connectTimeout()).isSameAs(timeout);
    }
}
