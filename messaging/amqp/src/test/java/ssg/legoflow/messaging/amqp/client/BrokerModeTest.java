package ssg.legoflow.messaging.amqp.client;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link BrokerMode} and mode-aware {@link ClientConfig}.
 */
class BrokerModeTest {

    @Test void standardMode() {
        var mode = BrokerMode.STANDARD;
        assertThat(mode.saslFirst()).isTrue();
        assertThat(mode.addressPrefix()).isEmpty();
        assertThat(mode.sndSettleMode()).isEqualTo(0);
        assertThat(mode.rcvSettleMode()).isEqualTo(0);
    }

    @Test void rabbitmqMode() {
        var mode = BrokerMode.RABBITMQ;
        assertThat(mode.saslFirst()).isTrue();
        assertThat(mode.addressPrefix()).isEqualTo("/queues/");
        assertThat(mode.sndSettleMode()).isEqualTo(0);
        assertThat(mode.rcvSettleMode()).isEqualTo(1);
    }

    @Test void artemisMode() {
        var mode = BrokerMode.ARTEMIS;
        assertThat(mode.saslFirst()).isTrue();
        assertThat(mode.addressPrefix()).isEmpty();
        assertThat(mode.sndSettleMode()).isEqualTo(0);
        assertThat(mode.rcvSettleMode()).isEqualTo(0);
    }

    @Test void qpidDispatchMode() {
        var mode = BrokerMode.QPID_DISPATCH;
        assertThat(mode.saslFirst()).isTrue();
        assertThat(mode.addressPrefix()).isEqualTo("closest:");
        assertThat(mode.sndSettleMode()).isEqualTo(0);
        assertThat(mode.rcvSettleMode()).isEqualTo(0);
    }

    @Test void ibmMqMode() {
        var mode = BrokerMode.IBM_MQ;
        assertThat(mode.saslFirst()).isTrue();
        assertThat(mode.addressPrefix()).isEmpty();
        assertThat(mode.sndSettleMode()).isEqualTo(0);
        assertThat(mode.rcvSettleMode()).isEqualTo(0);
    }

    @Test void formatAddressRabbitmq() {
        assertThat(BrokerMode.RABBITMQ.formatAddress("myqueue")).isEqualTo("/queues/myqueue");
        assertThat(BrokerMode.RABBITMQ.formatAddress("/queues/myqueue")).isEqualTo("/queues/myqueue"); // no double prefix
    }

    @Test void formatAddressQpid() {
        assertThat(BrokerMode.QPID_DISPATCH.formatAddress("myqueue")).isEqualTo("closest:myqueue");
        assertThat(BrokerMode.QPID_DISPATCH.formatAddress("closest:myqueue")).isEqualTo("closest:myqueue");
    }

    @Test void formatAddressStandard() {
        assertThat(BrokerMode.STANDARD.formatAddress("myqueue")).isEqualTo("myqueue");
        assertThat(BrokerMode.ARTEMIS.formatAddress("myqueue")).isEqualTo("myqueue");
        assertThat(BrokerMode.IBM_MQ.formatAddress("myqueue")).isEqualTo("myqueue");
    }

    @Test void configForBrokerMode() {
        var config = ClientConfig.builder()
                .brokerMode(BrokerMode.RABBITMQ)
                .build();
        assertThat(config.brokerMode()).isEqualTo(BrokerMode.RABBITMQ);
        assertThat(config.sndSettleMode()).isEqualTo(0);
        assertThat(config.rcvSettleMode()).isEqualTo(1);
    }

    @Test void configQpidMode() {
        var config = ClientConfig.builder()
                .brokerMode(BrokerMode.QPID_DISPATCH)
                .build();
        assertThat(config.brokerMode()).isEqualTo(BrokerMode.QPID_DISPATCH);
        assertThat(config.sndSettleMode()).isEqualTo(0);
        assertThat(config.rcvSettleMode()).isEqualTo(0);
    }

    @Test void configCanOverrideSettleModes() {
        var config = ClientConfig.builder()
                .brokerMode(BrokerMode.RABBITMQ)
                .sndSettleMode(1)
                .rcvSettleMode(0)
                .build();
        assertThat(config.sndSettleMode()).isEqualTo(1);
        assertThat(config.rcvSettleMode()).isEqualTo(0);
    }

    @Test void configDefaultsToStandard() {
        var config = ClientConfig.builder().build();
        assertThat(config.brokerMode()).isEqualTo(BrokerMode.STANDARD);
        assertThat(config.sndSettleMode()).isEqualTo(0);
        assertThat(config.rcvSettleMode()).isEqualTo(0);
    }
}
