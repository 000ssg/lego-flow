package ssg.legoflow.network.dns.client;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.user.SimpleServiceUser;
import ssg.legoflow.service.user.UserType;

import java.nio.ByteBuffer;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class DnsClientServiceTest {

    @Test void testBuilderBasic() {
        var svc = DnsClientService.builder("8.8.8.8", 53).name("my-dns").priority(50).build();
        ServiceDescriptor desc = svc.getDescriptor();
        assertThat(desc.name()).isEqualTo("my-dns");
    }

    @Test void testInitialState() {
        var svc = DnsClientService.builder("8.8.8.8", 53).build();
        assertThat(svc.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnect() {
        var svc = DnsClientService.builder("8.8.8.8", 53).build();
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        assertThatCode(() -> svc.disconnect(ctx)).doesNotThrowAnyException();
    }

    @Test void testStatisticsBeforeProcessing() {
        var svc = DnsClientService.builder("8.8.8.8", 53).build();
        assertThat(svc.getStatistics().getInCount(ByteBuffer.class)).isEqualTo(0);
    }
}
