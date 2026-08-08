package ssg.legoflow.network.modbus.client.service;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** Tests for Modbus client service DP/DF compliance. */
class ModbusClientServiceTest {

    @Test void testBuilderCreatesService() {
        var service = ModbusClientService.builder("localhost", 502).build();
        assertThat(service).isNotNull();
    }

    @Test void testInitialStateIsDisconnected() {
        var service = ModbusClientService.builder("localhost", 502).build();
        assertThat(service.isConnected()).isFalse();
    }

    @Test void testDisconnectBeforeConnectDoesNotThrow() {
        var service = ModbusClientService.builder("localhost", 502).build();
        try { service.disconnect(service.getServiceContext()); } catch (Exception e) { fail("should not throw"); }
    }

    @Test void testBuilderWithPriority() {
        var service = ModbusClientService.builder("modbus.local", 502).priority(90).build();
        assertThat(service.getPriority()).isEqualTo(90);
    }

    @Test void testChannelHandlerCanBeCreated() {
        var service = ModbusClientService.builder("localhost", 502).build();
        var handler = service.createChannelHandler();
        assertThat(handler).isNotNull();
    }

    @Test void testGetClientIsNullBeforeConnect() {
        var service = ModbusClientService.builder("localhost", 502).build();
        assertThat(service.getClient()).isNull();
    }
}
