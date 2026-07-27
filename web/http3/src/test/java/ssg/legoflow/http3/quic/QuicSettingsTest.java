package ssg.legoflow.http3.quic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link QuicSettings} — defaults, builder, and validation.
 *
 * @since 1.0.0
 */
class QuicSettingsTest {

    @Test
    void testDefaults() {
        var settings = new QuicSettings();

        assertThat(settings.maxIdleTimeout()).isEqualTo(30_000);
        assertThat(settings.maxUdpPayloadSize()).isEqualTo(1200);
        assertThat(settings.initialMaxData()).isEqualTo(1_048_576);
        assertThat(settings.initialMaxStreamDataBidiLocal()).isEqualTo(262_144);
        assertThat(settings.initialMaxStreamDataBidiRemote()).isEqualTo(262_144);
        assertThat(settings.initialMaxStreamDataUni()).isEqualTo(262_144);
        assertThat(settings.initialMaxStreamsBidi()).isEqualTo(100);
        assertThat(settings.initialMaxStreamsUni()).isEqualTo(100);
        assertThat(settings.ackDelayExponent()).isEqualTo(3);
        assertThat(settings.maxAckDelay()).isEqualTo(25);
        assertThat(settings.disableActiveMigration()).isFalse();
        assertThat(settings.activeConnectionIdLimit()).isEqualTo(2);
    }

    @Test
    void testBuilderDefaults() {
        var settings = QuicSettings.builder().build();

        assertThat(settings.maxIdleTimeout()).isEqualTo(30_000);
        assertThat(settings.maxUdpPayloadSize()).isEqualTo(1200);
        assertThat(settings.initialMaxData()).isEqualTo(1_048_576);
    }

    @Test
    void testBuilderCustomValues() {
        var settings = QuicSettings.builder()
                .maxIdleTimeout(60_000)
                .maxUdpPayloadSize(1500)
                .initialMaxData(2_097_152)
                .initialMaxStreamDataBidiLocal(524_288)
                .initialMaxStreamDataBidiRemote(524_288)
                .initialMaxStreamDataUni(524_288)
                .initialMaxStreamsBidi(200)
                .initialMaxStreamsUni(50)
                .ackDelayExponent(5)
                .maxAckDelay(50)
                .disableActiveMigration(true)
                .activeConnectionIdLimit(4)
                .build();

        assertThat(settings.maxIdleTimeout()).isEqualTo(60_000);
        assertThat(settings.maxUdpPayloadSize()).isEqualTo(1500);
        assertThat(settings.initialMaxData()).isEqualTo(2_097_152);
        assertThat(settings.initialMaxStreamDataBidiLocal()).isEqualTo(524_288);
        assertThat(settings.initialMaxStreamDataBidiRemote()).isEqualTo(524_288);
        assertThat(settings.initialMaxStreamDataUni()).isEqualTo(524_288);
        assertThat(settings.initialMaxStreamsBidi()).isEqualTo(200);
        assertThat(settings.initialMaxStreamsUni()).isEqualTo(50);
        assertThat(settings.ackDelayExponent()).isEqualTo(5);
        assertThat(settings.maxAckDelay()).isEqualTo(50);
        assertThat(settings.disableActiveMigration()).isTrue();
        assertThat(settings.activeConnectionIdLimit()).isEqualTo(4);
    }

    @Test
    void testValidationNegativeIdleTimeout() {
        assertThatThrownBy(() -> QuicSettings.builder().maxIdleTimeout(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxIdleTimeout");
    }

    @Test
    void testValidationUdpPayloadTooSmall() {
        assertThatThrownBy(() -> QuicSettings.builder().maxUdpPayloadSize(1199).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxUdpPayloadSize");
    }

    @Test
    void testValidationUdpPayloadTooLarge() {
        assertThatThrownBy(() -> QuicSettings.builder().maxUdpPayloadSize(65528).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxUdpPayloadSize");
    }

    @Test
    void testValidationNegativeMaxData() {
        assertThatThrownBy(() -> QuicSettings.builder().initialMaxData(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialMaxData");
    }

    @Test
    void testValidationNegativeStreamDataBidiLocal() {
        assertThatThrownBy(() -> QuicSettings.builder().initialMaxStreamDataBidiLocal(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialMaxStreamDataBidiLocal");
    }

    @Test
    void testValidationNegativeStreamDataBidiRemote() {
        assertThatThrownBy(() -> QuicSettings.builder().initialMaxStreamDataBidiRemote(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialMaxStreamDataBidiRemote");
    }

    @Test
    void testValidationNegativeStreamDataUni() {
        assertThatThrownBy(() -> QuicSettings.builder().initialMaxStreamDataUni(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialMaxStreamDataUni");
    }

    @Test
    void testValidationNegativeStreamsBidi() {
        assertThatThrownBy(() -> QuicSettings.builder().initialMaxStreamsBidi(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialMaxStreamsBidi");
    }

    @Test
    void testValidationNegativeStreamsUni() {
        assertThatThrownBy(() -> QuicSettings.builder().initialMaxStreamsUni(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialMaxStreamsUni");
    }

    @Test
    void testValidationAckDelayExponentTooHigh() {
        assertThatThrownBy(() -> QuicSettings.builder().ackDelayExponent(21).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ackDelayExponent");
    }

    @Test
    void testValidationAckDelayExponentNegative() {
        assertThatThrownBy(() -> QuicSettings.builder().ackDelayExponent(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ackDelayExponent");
    }

    @Test
    void testValidationMaxAckDelayTooHigh() {
        assertThatThrownBy(() -> QuicSettings.builder().maxAckDelay(16384).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAckDelay");
    }

    @Test
    void testValidationActiveConnectionIdLimitTooLow() {
        assertThatThrownBy(() -> QuicSettings.builder().activeConnectionIdLimit(1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("activeConnectionIdLimit");
    }

    @Test
    void testValidationBoundaryValues() {
        // Min valid UDP payload size
        var min = QuicSettings.builder().maxUdpPayloadSize(1200).build();
        assertThat(min.maxUdpPayloadSize()).isEqualTo(1200);

        // Max valid UDP payload size
        var max = QuicSettings.builder().maxUdpPayloadSize(65527).build();
        assertThat(max.maxUdpPayloadSize()).isEqualTo(65527);

        // Zero idle timeout is valid
        var zero = QuicSettings.builder().maxIdleTimeout(0).build();
        assertThat(zero.maxIdleTimeout()).isEqualTo(0);
    }

    @Test
    void testRecordEquality() {
        var s1 = new QuicSettings();
        var s2 = new QuicSettings();

        assertThat(s1).isEqualTo(s2);
    }

    @Test
    void testRecordToString() {
        var settings = new QuicSettings();

        assertThat(settings.toString()).contains("maxIdleTimeout=30000");
    }
}
