package ssg.legoflow.messaging.mqtt.broker.service;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.user.SimpleServiceUser;
import ssg.legoflow.service.user.UserType;
import java.nio.ByteBuffer;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class MqttBrokerServiceTest {

    @Test void testBuilderBasic() {
        var svc = MqttBrokerService.builder("localhost", 1883)
            .name("my-mqtt").priority(50).dependencies("network").build();
        ServiceDescriptor desc = svc.getDescriptor();
        assertThat(desc.name()).isEqualTo("my-mqtt");
        assertThat(desc.priority()).isEqualTo(50);
        assertThat(desc.dependencies()).contains("network");
    }

    @Test void testInitialState() {
        var svc = MqttBrokerService.builder("localhost", 1883).build();
        assertThat(svc.isConnected()).isFalse();
    }

    @Test void testGetBrokerBeforeConnect() {
        var svc = MqttBrokerService.builder("localhost", 1883).build();
        assertThat(svc.getBroker()).isNull();
    }

    @Test void testStatisticsTracking() {
        var svc = MqttBrokerService.builder("localhost", 1883).build();
        assertThat(svc.getStatistics()).isNotNull();
    }

    @Test void testChannelHandlerCreation() {
        var svc = MqttBrokerService.builder("localhost", 1883).build();
        var handler = (MqttBrokerChannelHandler) svc.createChannelHandler();
        assertThat(handler).isNotNull();
        assertThat(handler.getMqttService()).isEqualTo(svc);
    }

    @Test void testMessageCallbackRegistration() {
        var svc = MqttBrokerService.builder("localhost", 1883).build();
        final boolean[] cbSet = {false};
        svc.setMessageCallback(data -> cbSet[0] = true);
    }

    @Test void testServiceDependencies() {
        var svc = MqttBrokerService.builder("localhost", 1883)
            .dependencies("network").build();
        assertThat(svc.getDependencies()).containsExactlyInAnyOrder("network");
    }

    @Test void testPriorityDefault() {
        var svc = MqttBrokerService.builder("localhost", 1883).build();
        assertThat(svc.getPriority()).isEqualTo(100);
    }

    @Test void testDisconnectBeforeConnect() {
        var svc = MqttBrokerService.builder("localhost", 1883).build();
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        assertThatCode(() -> svc.disconnect(ctx)).doesNotThrowAnyException();
    }

    @Test void testStatisticsBeforeProcessing() {
        var svc = MqttBrokerService.builder("localhost", 1883).build();
        assertThat(svc.getStatistics().getInCount(ByteBuffer.class)).isEqualTo(0);
    }
}
