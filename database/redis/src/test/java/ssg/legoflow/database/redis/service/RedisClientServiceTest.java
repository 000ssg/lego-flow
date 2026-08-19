package ssg.legoflow.database.redis.service;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.user.SimpleServiceUser;
import ssg.legoflow.service.user.UserType;
import java.nio.ByteBuffer;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class RedisClientServiceTest {

    @Test void testBuilderBasic() {
        var svc = RedisClientService.builder("localhost", 6379)
            .name("my-redis").priority(50).dependencies("network").build();
        ServiceDescriptor desc = svc.getDescriptor();
        assertThat(desc.name()).isEqualTo("my-redis");
        assertThat(desc.priority()).isEqualTo(50);
        assertThat(desc.dependencies()).contains("network");
    }

    @Test void testInitialState() {
        var svc = RedisClientService.builder("localhost", 6379).build();
        assertThat(svc.isConnected()).isFalse();
    }

    @Test void testGetClientBeforeConnect() {
        var svc = RedisClientService.builder("localhost", 6379).build();
        assertThat(svc.getClient()).isNull();
    }

    @Test void testChannelHandlerCreation() {
        var svc = RedisClientService.builder("localhost", 6379).build();
        var handler = (RedisClientChannelHandler) svc.createChannelHandler();
        assertThat(handler).isNotNull();
        assertThat(handler.getRedisClientService()).isEqualTo(svc);
    }

    @Test void testCommandCallbackRegistration() {
        var svc = RedisClientService.builder("localhost", 6379).build();
        final boolean[] cbSet = {false};
        svc.setCommandCallback(result -> cbSet[0] = true);
    }

    @Test void testDisconnectBeforeConnect() {
        var svc = RedisClientService.builder("localhost", 6379).build();
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        assertThatCode(() -> svc.disconnect(ctx)).doesNotThrowAnyException();
    }

    @Test void testConnectionRefusedThrows() {
        var svc = RedisClientService.builder("nonexistent.invalid.host", 9999).build();
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        assertThatThrownBy(() -> svc.connect(ctx)).isInstanceOf(Exception.class);
    }

    @Test void testStatisticsBeforeProcessing() {
        var svc = RedisClientService.builder("localhost", 6379).build();
        assertThat(svc.getStatistics().getInCount(ByteBuffer.class)).isEqualTo(0);
    }

    @Test void testPriorityDefault() {
        var svc = RedisClientService.builder("localhost", 6379).build();
        assertThat(svc.getPriority()).isEqualTo(100);
    }
}
