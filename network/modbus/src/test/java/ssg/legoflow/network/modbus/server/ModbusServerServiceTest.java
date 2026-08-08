package ssg.legoflow.network.modbus.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.user.SimpleServiceUser;
import ssg.legoflow.service.user.UserType;

import java.nio.ByteBuffer;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class ModbusServerServiceTest {

    @Test void testBuilderBasic() {
        var svc = ModbusServerService.builder().name("my-modbus").priority(50).build();
        ServiceDescriptor desc = svc.getDescriptor();
        assertThat(desc.name()).isEqualTo("my-modbus");
    }

    @Test void testInitialState() {
        var svc = ModbusServerService.builder().build();
        assertThat(svc.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnect() {
        var svc = ModbusServerService.builder().build();
        var ctx = new DefaultServiceContext(
            new SimpleServiceUser("test", "Test User", UserType.EXACT, Set.of()));
        assertThatCode(() -> svc.disconnect(ctx)).doesNotThrowAnyException();
    }

    @Test void testStatisticsBeforeProcessing() {
        var svc = ModbusServerService.builder().build();
        assertThat(svc.getStatistics().getInCount(ByteBuffer.class)).isEqualTo(0);
    }
}
