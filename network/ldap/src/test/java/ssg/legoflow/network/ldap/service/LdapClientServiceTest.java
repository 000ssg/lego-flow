package ssg.legoflow.network.ldap.service;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.user.SimpleServiceUser;
import ssg.legoflow.service.user.UserType;

import java.nio.ByteBuffer;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class LdapClientServiceTest {

    @Test void testBuilderBasic() {
        var svc = LdapClientService.builder("localhost", 389)
            .name("my-ldap").priority(50).dependencies("network").build();
        ServiceDescriptor desc = svc.getDescriptor();
        assertThat(desc.name()).isEqualTo("my-ldap");
        assertThat(desc.priority()).isEqualTo(50);
    }

    @Test void testInitialState() {
        var svc = LdapClientService.builder("localhost", 389).build();
        assertThat(svc.isConnected()).isFalse();
    }

    @Test void testGetClientBeforeConnect() {
        var svc = LdapClientService.builder("localhost", 389).build();
        assertThat(svc.getClient()).isNull();
    }

    @Test void testDisconnectBeforeConnect() {
        var svc = LdapClientService.builder("localhost", 389).build();
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        assertThatCode(() -> svc.disconnect(ctx)).doesNotThrowAnyException();
    }

    @Test void testStatisticsBeforeProcessing() {
        var svc = LdapClientService.builder("localhost", 389).build();
        assertThat(svc.getStatistics().getInCount(ByteBuffer.class)).isEqualTo(0);
    }
}
