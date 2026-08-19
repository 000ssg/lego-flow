package ssg.legoflow.network.ldap.service;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.user.SimpleServiceUser;
import ssg.legoflow.service.user.UserType;
import java.nio.ByteBuffer;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class LdapServiceTest {

    @Test void testBuilderBasic() {
        var svc = LdapService.builder("localhost", 389)
            .name("my-ldap").priority(50).dependencies("network").build();
        ServiceDescriptor desc = svc.getDescriptor();
        assertThat(desc.name()).isEqualTo("my-ldap");
        assertThat(desc.priority()).isEqualTo(50);
        assertThat(desc.dependencies()).contains("network");
    }

    @Test void testInitialState() {
        var svc = LdapService.builder("localhost", 389).build();
        assertThat(svc.isConnected()).isFalse();
    }

    @Test void testGetServerBeforeConnect() {
        var svc = LdapService.builder("localhost", 389).build();
        assertThat(svc.getServer()).isNull();
    }

    @Test void testStatisticsTracking() {
        var svc = LdapService.builder("localhost", 389).build();
        assertThat(svc.getStatistics()).isNotNull();
    }

    @Test void testChannelHandlerCreation() {
        var svc = LdapService.builder("localhost", 389).build();
        var handler = (LdapChannelHandler) svc.createChannelHandler();
        assertThat(handler).isNotNull();
        assertThat(handler.getLdapService()).isEqualTo(svc);
    }

    @Test void testDataCallbackRegistration() {
        var svc = LdapService.builder("localhost", 389).build();
        final boolean[] cbSet = {false};
        svc.setDataCallback(data -> cbSet[0] = true);
    }

    @Test void testServiceDependencies() {
        var svc = LdapService.builder("localhost", 389)
            .dependencies("network", "directory-backend").build();
        assertThat(svc.getDependencies()).containsExactlyInAnyOrder("network", "directory-backend");
    }

    @Test void testPriorityDefault() {
        var svc = LdapService.builder("localhost", 389).build();
        assertThat(svc.getPriority()).isEqualTo(100);
    }

    @Test void testDisconnectBeforeConnect() {
        var svc = LdapService.builder("localhost", 389).build();
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        assertThatCode(() -> svc.disconnect(ctx)).doesNotThrowAnyException();
    }

    @Test void testStatisticsBeforeProcessing() {
        var svc = LdapService.builder("localhost", 389).build();
        assertThat(svc.getStatistics().getInCount(ByteBuffer.class)).isEqualTo(0);
    }

    @Test void testModeClientDefault() {
        var svc = LdapService.builder("localhost", 389)
            .mode(LdapService.Mode.CLIENT).build();
        assertThat(svc).isNotNull();
    }

    @Test void testModeServer() {
        var svc = LdapService.builder("0.0.0.0", 3893)
            .mode(LdapService.Mode.SERVER).build();
        assertThat(svc).isNotNull();
    }
}
