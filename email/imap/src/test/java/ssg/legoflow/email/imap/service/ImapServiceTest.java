package ssg.legoflow.email.imap.service;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.user.SimpleServiceUser;
import ssg.legoflow.service.user.UserType;

import java.nio.ByteBuffer;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class ImapServiceTest {

    @Test void testBuilderBasic() {
        var svc = ImapService.builder("localhost", 143)
            .name("my-imap").priority(50).dependencies("network").build();
        ServiceDescriptor desc = svc.getDescriptor();
        assertThat(desc.name()).isEqualTo("my-imap");
        assertThat(desc.priority()).isEqualTo(50);
        assertThat(desc.dependencies()).contains("network");
    }

    @Test void testInitialState() {
        var svc = ImapService.builder("localhost", 143).build();
        assertThat(svc.isConnected()).isFalse();
    }

    @Test void testGetServerBeforeConnect() {
        var svc = ImapService.builder("localhost", 143).build();
        assertThat(svc.getServer()).isNull();
    }

    @Test void testStatisticsTracking() {
        var svc = ImapService.builder("localhost", 143).build();
        assertThat(svc.getStatistics()).isNotNull();
    }

    @Test void testChannelHandlerCreation() {
        var svc = ImapService.builder("localhost", 143).build();
        var handler = (ImapChannelHandler) svc.createChannelHandler();
        assertThat(handler).isNotNull();
        assertThat(handler.getImapService()).isEqualTo(svc);
    }

    @Test void testMessageCallbackRegistration() {
        var svc = ImapService.builder("localhost", 143).build();
        final boolean[] cbSet = {false};
        svc.setMessageCallback(data -> cbSet[0] = true);
    }

    @Test void testServiceDependencies() {
        var svc = ImapService.builder("localhost", 143)
            .dependencies("network", "mail-store").build();
        assertThat(svc.getDependencies()).containsExactlyInAnyOrder("network", "mail-store");
    }

    @Test void testPriorityDefault() {
        var svc = ImapService.builder("localhost", 143).build();
        assertThat(svc.getPriority()).isEqualTo(100);
    }

    @Test void testDisconnectBeforeConnect() {
        var svc = ImapService.builder("localhost", 143).build();
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        assertThatCode(() -> svc.disconnect(ctx)).doesNotThrowAnyException();
    }

    @Test void testStatisticsBeforeProcessing() {
        var svc = ImapService.builder("localhost", 143).build();
        assertThat(svc.getStatistics().getInCount(ByteBuffer.class)).isEqualTo(0);
    }

    @Test void testModeClientDefault() {
        var svc = ImapService.builder("localhost", 143)
            .mode(ImapService.Mode.CLIENT).build();
        assertThat(svc).isNotNull();
    }

    @Test void testModeServer() {
        var svc = ImapService.builder("0.0.0.0", 14143)
            .mode(ImapService.Mode.SERVER).build();
        assertThat(svc).isNotNull();
    }
}
