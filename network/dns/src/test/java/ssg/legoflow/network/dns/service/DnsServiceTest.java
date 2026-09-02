package ssg.legoflow.network.dns.service;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.user.SimpleServiceUser;
import ssg.legoflow.service.user.UserType;
import java.nio.ByteBuffer;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for the DnsService demonstrating service-based DNS integration via DP/DF pipeline.
 */
class DnsServiceTest {

    @Test void testBuilderBasic() {
        var dns = DnsService.builder("localhost", 53)
            .name("my-dns")
            .priority(50)
            .dependencies("network")
            .build();

        ServiceDescriptor desc = dns.getDescriptor();
        assertThat(desc.name()).isEqualTo("my-dns");
        assertThat(desc.priority()).isEqualTo(50);
        assertThat(desc.dependencies()).contains("network");
    }

    @Test void testServiceInitialState() {
        var dns = DnsService.builder("localhost", 53).build();
        assertThat(dns.isConnected()).isFalse();
    }

    @Test void testGetServerBeforeConnect() {
        var dns = DnsService.builder("localhost", 53).build();
        assertThat(dns.getServer()).isNull();
    }

    @Test void testStatisticsTracking() {
        var dns = DnsService.builder("localhost", 53).build();
        var stats = dns.getStatistics();
        assertThat(stats).isNotNull();
    }

    @Test void testChannelHandlerCreation() {
        var dns = DnsService.builder("localhost", 53).build();
        var handler = (DnsChannelHandler) dns.createChannelHandler();
        assertThat(handler).isNotNull();
        assertThat(handler.getDnsService()).isEqualTo(dns);
    }

    @Test void testChannelHandlerOnErrorHandler() {
        var dns = DnsService.builder("localhost", 53).build();
        var handler = (DnsChannelHandler) dns.createChannelHandler();
        // Should not throw when passed null channel
        handler.onError(null, new RuntimeException("test error"));
    }

    @Test void testQueryCallbackRegistration() {
        var dns = DnsService.builder("localhost", 53).build();
        final boolean[] callbackSet = {false};
        dns.setQueryCallback(msg -> callbackSet[0] = true);
        // Callback is registered but not invoked until query arrives
    }

    @Test void testResponseCallbackRegistration() {
        var dns = DnsService.builder("localhost", 53).build();
        final boolean[] callbackSet = {false};
        dns.setResponseCallback(msg -> callbackSet[0] = true);
    }

    @Test void testServiceDependencies() {
        var dns = DnsService.builder("localhost", 53)
            .dependencies("network", "dns-cache")
            .build();
        assertThat(dns.getDependencies()).containsExactlyInAnyOrder("network", "dns-cache");
    }

    @Test void testPriorityDefault() {
        var dns = DnsService.builder("localhost", 53).build();
        assertThat(dns.getPriority()).isEqualTo(100);
    }

    @Test void testDisconnectBeforeConnect() {
        var dns = DnsService.builder("localhost", 53).build();
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        assertThatCode(() -> dns.disconnect(ctx)).doesNotThrowAnyException();
    }

    @Test void testConnectionRefusedThrows() {
        var dns = DnsService.builder("nonexistent.invalid.host", 59999).build();
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        assertThatThrownBy(() -> dns.connect(ctx))
            .isInstanceOf(Exception.class);
    }

    @Test void testStatisticsBeforeProcessing() {
        var dns = DnsService.builder("localhost", 53).build();
        var stats = dns.getStatistics();
        assertThat(stats.getInCount(ByteBuffer.class)).isEqualTo(0);
    }

    @Test void testModeClientDefault() {
        var dns = DnsService.builder("8.8.8.8", 53)
            .mode(DnsService.Mode.CLIENT)
            .build();
        assertThat(dns).isNotNull();
    }

    @Test void testModeServer() {
        var dns = DnsService.builder("0.0.0.0", 5353)
            .mode(DnsService.Mode.SERVER)
            .build();
        assertThat(dns).isNotNull();
    }

    @Test void testChannelHandlerSendDataNotConnected() {
        var dns = DnsService.builder("localhost", 53).build();
        var handler = (DnsChannelHandler) dns.createChannelHandler();
        // sendData on non-connected service should not throw
        assertThatCode(() -> handler.sendData(null, ByteBuffer.wrap(new byte[]{1, 2})))
            .doesNotThrowAnyException();
    }
}
