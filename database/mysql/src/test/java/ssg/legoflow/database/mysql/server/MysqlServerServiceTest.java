package ssg.legoflow.database.mysql.server;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for MySQL server service DP/DF compliance. */
class MysqlServerServiceTest {

    @Test void testBuilderCreatesService() {
        var service = MysqlServerService.builder(3306).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = MysqlServerService.builder(0).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = MysqlServerService.builder(0).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = MysqlServerService.builder(3306).priority(80).build();
        assertThat(service.getPriority()).isEqualTo(80);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = MysqlServerService.builder(3306).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetServerIsNullBeforeConnect() {
        var service = MysqlServerService.builder(0).build();
        assertThat(service.getServer()).isNull();
    }
}
