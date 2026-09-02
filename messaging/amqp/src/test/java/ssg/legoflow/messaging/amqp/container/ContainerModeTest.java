package ssg.legoflow.messaging.amqp.container;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ContainerMode} and mode-aware {@link ContainerConfig}.
 */
class ContainerModeTest {

    @Test void standardModeDefaults() {
        var mode = ContainerMode.STANDARD;
        assertThat(mode.saslRequired).isFalse();
        assertThat(mode.proto0Accepted).isTrue();
        assertThat(mode.authzidMustBeEmpty).isFalse();
        assertThat(mode.channelMax).isEqualTo(65535);
        assertThat(mode.idleTimeout).isEqualTo(0);
        assertThat(mode.saslMechanisms.split(",")).contains("PLAIN", "ANONYMOUS", "EXTERNAL");
    }

    @Test void rabbitmqModeDefaults() {
        var mode = ContainerMode.RABBITMQ;
        assertThat(mode.saslRequired).isTrue();
        assertThat(mode.proto0Accepted).isFalse();
        assertThat(mode.authzidMustBeEmpty).isTrue();
        assertThat(mode.channelMax).isEqualTo(65535);
        assertThat(mode.idleTimeout).isEqualTo(60_000L);
        assertThat(mode.saslMechanisms.split(",")).contains("PLAIN", "ANONYMOUS");
    }

    @Test void artemisModeDefaults() {
        var mode = ContainerMode.ARTEMIS;
        assertThat(mode.saslRequired).isFalse();
        assertThat(mode.proto0Accepted).isTrue();
        assertThat(mode.authzidMustBeEmpty).isFalse();
        assertThat(mode.channelMax).isEqualTo(65535);
        assertThat(mode.idleTimeout).isEqualTo(0);
        assertThat(mode.saslMechanisms.split(",")).contains("PLAIN", "ANONYMOUS", "EXTERNAL", "GSSAPI");
    }

    @Test void qpidDispatchModeDefaults() {
        var mode = ContainerMode.QPID_DISPATCH;
        assertThat(mode.saslRequired).isFalse();
        assertThat(mode.proto0Accepted).isTrue();
        assertThat(mode.authzidMustBeEmpty).isFalse();
        assertThat(mode.channelMax).isEqualTo(32767);
        assertThat(mode.idleTimeout).isEqualTo(8_000L);
        assertThat(mode.saslMechanisms).isEqualTo("ANONYMOUS");
    }

    @Test void ibmMqModeDefaults() {
        var mode = ContainerMode.IBM_MQ;
        assertThat(mode.saslRequired).isFalse();
        assertThat(mode.proto0Accepted).isTrue();
        assertThat(mode.authzidMustBeEmpty).isFalse();
        assertThat(mode.channelMax).isEqualTo(65535);
        assertThat(mode.idleTimeout).isEqualTo(0);
        assertThat(mode.saslMechanisms.split(",")).contains("PLAIN", "ANONYMOUS", "EXTERNAL");
    }

    @Test void configForModeAppliesDefaults() {
        var config = ContainerConfig.forMode(ContainerMode.RABBITMQ);
        assertThat(config.mode()).isEqualTo(ContainerMode.RABBITMQ);
        assertThat(config.requireSasl()).isTrue();
        assertThat(config.channelMax()).isEqualTo(65535);
        assertThat(config.idleTimeout()).isEqualTo(60_000L);
        assertThat(config.proto0Accepted()).isFalse();
        assertThat(config.authzidMustBeEmpty()).isTrue();
    }

    @Test void configForQpidDispatchAppliesChannelMax() {
        var config = ContainerConfig.forMode(ContainerMode.QPID_DISPATCH);
        assertThat(config.channelMax()).isEqualTo(32767);
        assertThat(config.idleTimeout()).isEqualTo(8_000L);
    }

    @Test void configDefaultsIsStandard() {
        var config = ContainerConfig.defaults();
        assertThat(config.mode()).isEqualTo(ContainerMode.STANDARD);
        assertThat(config.requireSasl()).isFalse();
        assertThat(config.proto0Accepted()).isTrue();
        assertThat(config.authzidMustBeEmpty()).isFalse();
    }

    @Test void configBuilderCanOverrideModeDefaults() {
        var config = ContainerConfig.builder()
                .mode(ContainerMode.RABBITMQ)
                .channelMax(1000)
                .idleTimeout(30_000L)
                .build();
        assertThat(config.mode()).isEqualTo(ContainerMode.RABBITMQ);
        assertThat(config.requireSasl()).isTrue(); // from mode
        assertThat(config.channelMax()).isEqualTo(1000); // overridden
        assertThat(config.idleTimeout()).isEqualTo(30_000L); // overridden
    }
}
