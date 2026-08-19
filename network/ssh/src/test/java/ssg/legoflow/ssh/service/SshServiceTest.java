package ssg.legoflow.ssh.service;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.user.SimpleServiceUser;
import ssg.legoflow.service.user.UserType;
import java.nio.ByteBuffer;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for the SshService demonstrating service-based SSH integration.
 */
class SshServiceTest {

    @Test void testBuilderBasic() {
        var ssh = SshService.builder("localhost", 22)
            .username("test")
            .password("pass")
            .name("my-ssh")
            .priority(50)
            .dependencies("network")
            .build();
        
        ServiceDescriptor desc = ssh.getDescriptor();
        assertThat(desc.name()).isEqualTo("my-ssh");
        assertThat(desc.priority()).isEqualTo(50);
        assertThat(desc.dependencies()).contains("network");
    }

    @Test void testServiceInitialState() {
        var ssh = SshService.builder("localhost", 22).build();
        
        assertThat(ssh.isConnected()).isFalse();
    }

    @Test void testGetTransportBeforeConnect() {
        var ssh = SshService.builder("localhost", 22).build();
        assertThat(ssh.getTransport()).isNull();
    }

    @Test void testStatisticsTracking() {
        var ssh = SshService.builder("localhost", 22).build();
        var stats = ssh.getStatistics();
        assertThat(stats).isNotNull();
    }

    @Test void testChannelHandlerCreation() {
        var ssh = SshService.builder("localhost", 22)
            .username("admin")
            .build();
        
        var handler = (ssg.legoflow.ssh.service.SshChannelHandler) ssh.createChannelHandler();
        assertThat(handler).isNotNull();
        assertThat(handler.getSshService()).isEqualTo(ssh);
    }

    @Test void testChannelHandlerOnErrorHandler() {
        var ssh = SshService.builder("localhost", 22).build();
        var handler = (ssg.legoflow.ssh.service.SshChannelHandler) ssh.createChannelHandler();
        
        // Should not throw
        handler.onError(null, new RuntimeException("test error"));
    }

    @Test void testDataHandlerRegistration() {
        var ssh = SshService.builder("localhost", 22).build();
        
        final boolean[] received = {false};
        ssh.setChannelDataHandler(data -> received[0] = true);
        assertThat(received[0]).isFalse();
    }

    @Test void testSessionReadyCallback() {
        var ssh = SshService.builder("localhost", 22).build();
        
        final boolean[] callbackInvoked = {false};
        ssh.onSessionReady(s -> callbackInvoked[0] = true);
        assertThat(callbackInvoked[0]).isFalse();
    }

    @Test void testServiceDependencies() {
        var ssh = SshService.builder("localhost", 22)
            .dependencies("network", "crypto")
            .build();
        
        assertThat(ssh.getDependencies()).containsExactlyInAnyOrder("network", "crypto");
    }

    @Test void testPriorityDefault() {
        var ssh = SshService.builder("localhost", 22).build();
        assertThat(ssh.getPriority()).isEqualTo(100);
    }

    @Test void testDisconnectBeforeConnect() {
        var ssh = SshService.builder("localhost", 22).build();
        
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        assertThatCode(() -> ssh.disconnect(ctx)).doesNotThrowAnyException();
    }

    @Test void testConnectionRefusedThrows() {
        var ssh = SshService.builder("nonexistent.invalid.host", 9999)
            .username("test")
            .build();
        
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        
        assertThatThrownBy(() -> ssh.connect(ctx))
            .isInstanceOf(Exception.class);
    }

    @Test void testStatisticsBeforeProcessing() {
        var ssh = SshService.builder("localhost", 22).build();
        var stats = ssh.getStatistics();
        assertThat(stats.getInCount(ByteBuffer.class)).isEqualTo(0);
    }
}
