package ssg.legoflow.network.syslog.service;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.user.SimpleServiceUser;
import ssg.legoflow.service.user.UserType;
import java.nio.ByteBuffer;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class SyslogServiceTest {

    @Test void testBuilderBasic() {
        var svc = SyslogService.builder("localhost", 514)
            .name("my-syslog").priority(50).dependencies("network").build();
        ServiceDescriptor desc = svc.getDescriptor();
        assertThat(desc.name()).isEqualTo("my-syslog");
        assertThat(desc.priority()).isEqualTo(50);
        assertThat(desc.dependencies()).contains("network");
    }

    @Test void testInitialState() {
        var svc = SyslogService.builder("localhost", 514).build();
        assertThat(svc.isConnected()).isFalse();
    }

    @Test void testStatisticsTracking() {
        var svc = SyslogService.builder("localhost", 514).build();
        assertThat(svc.getStatistics()).isNotNull();
    }

    @Test void testChannelHandlerCreation() {
        var svc = SyslogService.builder("localhost", 514).build();
        var handler = (SyslogChannelHandler) svc.createChannelHandler();
        assertThat(handler).isNotNull();
        assertThat(handler.getSyslogService()).isEqualTo(svc);
    }

    @Test void testLogCallbackRegistration() {
        var svc = SyslogService.builder("localhost", 514).build();
        final boolean[] cbSet = {false};
        svc.setLogCallback(data -> cbSet[0] = true);
    }

    @Test void testServiceDependencies() {
        var svc = SyslogService.builder("localhost", 514)
            .dependencies("network").build();
        assertThat(svc.getDependencies()).containsExactlyInAnyOrder("network");
    }

    @Test void testPriorityDefault() {
        var svc = SyslogService.builder("localhost", 514).build();
        assertThat(svc.getPriority()).isEqualTo(100);
    }

    @Test void testDisconnectBeforeConnect() {
        var svc = SyslogService.builder("localhost", 514).build();
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        assertThatCode(() -> svc.disconnect(ctx)).doesNotThrowAnyException();
    }

    @Test void testStatisticsBeforeProcessing() {
        var svc = SyslogService.builder("localhost", 514).build();
        assertThat(svc.getStatistics().getInCount(ByteBuffer.class)).isEqualTo(0);
    }

    @Test void testTransportUDPDefault() {
        var svc = SyslogService.builder("localhost", 514)
            .transport(SyslogService.Transport.UDP).build();
        assertThat(svc).isNotNull();
    }

    @Test void testTransportTCP() {
        var svc = SyslogService.builder("localhost", 6514)
            .transport(SyslogService.Transport.TCP).build();
        assertThat(svc).isNotNull();
    }
}
