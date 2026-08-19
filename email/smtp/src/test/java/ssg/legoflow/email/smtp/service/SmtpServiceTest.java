package ssg.legoflow.email.smtp.service;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.user.SimpleServiceUser;
import ssg.legoflow.service.user.UserType;
import java.nio.ByteBuffer;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class SmtpServiceTest {

    @Test void testBuilderBasic() {
        var svc = SmtpService.builder("localhost", 25)
            .name("my-smtp").priority(50).dependencies("network").build();
        ServiceDescriptor desc = svc.getDescriptor();
        assertThat(desc.name()).isEqualTo("my-smtp");
        assertThat(desc.priority()).isEqualTo(50);
        assertThat(desc.dependencies()).contains("network");
    }

    @Test void testInitialState() {
        var svc = SmtpService.builder("localhost", 25).build();
        assertThat(svc.isConnected()).isFalse();
    }

    @Test void testGetServerBeforeConnect() {
        var svc = SmtpService.builder("localhost", 25).build();
        assertThat(svc.getServer()).isNull();
    }

    @Test void testStatisticsTracking() {
        var svc = SmtpService.builder("localhost", 25).build();
        assertThat(svc.getStatistics()).isNotNull();
    }

    @Test void testChannelHandlerCreation() {
        var svc = SmtpService.builder("localhost", 25).build();
        var handler = (SmtpChannelHandler) svc.createChannelHandler();
        assertThat(handler).isNotNull();
        assertThat(handler.getSmtpService()).isEqualTo(svc);
    }

    @Test void testMailCallbackRegistration() {
        var svc = SmtpService.builder("localhost", 25).build();
        final boolean[] cbSet = {false};
        svc.setMailCallback(data -> cbSet[0] = true);
    }

    @Test void testServiceDependencies() {
        var svc = SmtpService.builder("localhost", 25)
            .dependencies("network", "email-store").build();
        assertThat(svc.getDependencies()).containsExactlyInAnyOrder("network", "email-store");
    }

    @Test void testPriorityDefault() {
        var svc = SmtpService.builder("localhost", 25).build();
        assertThat(svc.getPriority()).isEqualTo(100);
    }

    @Test void testDisconnectBeforeConnect() {
        var svc = SmtpService.builder("localhost", 25).build();
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        assertThatCode(() -> svc.disconnect(ctx)).doesNotThrowAnyException();
    }

    @Test void testStatisticsBeforeProcessing() {
        var svc = SmtpService.builder("localhost", 25).build();
        assertThat(svc.getStatistics().getInCount(ByteBuffer.class)).isEqualTo(0);
    }

    @Test void testModeClientDefault() {
        var svc = SmtpService.builder("localhost", 25)
            .mode(SmtpService.Mode.CLIENT).build();
        assertThat(svc).isNotNull();
    }

    @Test void testModeServer() {
        var svc = SmtpService.builder("0.0.0.0", 2525)
            .mode(SmtpService.Mode.SERVER).build();
        assertThat(svc).isNotNull();
    }
}
