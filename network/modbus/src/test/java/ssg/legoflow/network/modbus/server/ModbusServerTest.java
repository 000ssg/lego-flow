package ssg.legoflow.network.modbus.server;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

class ModbusServerTest {

    private ModbusServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new ModbusServer(0);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) try { server.close(); } catch (Exception ignored) {}
    }

    @Test
    void testServerStartStop() throws Exception {
        server.start();
        Thread.sleep(200);
        assertThat(server.localPort()).isGreaterThan(0);
        server.close();
    }

    @Test
    void testLocalPortReturnsPositive() {
        assertThat(server.localPort()).isGreaterThan(0).isLessThan(65536);
    }

    @Test
    void testStartWithDeviceMemory() throws Exception {
        var memory = new DeviceMemory();
        memory.writeCoil(0, true);
        memory.setInputRegister(0, 42);
        
        server.close();
        server = new ModbusServer(0, memory);
        server.start();
        Thread.sleep(200);
    }

    @Test
    void testHandlerReturnsNonNull() {
        assertThat(server.handler()).isNotNull();
    }

    @Test
    void testAutoCloseable() throws Exception {
        try (var s = new ModbusServer(0)) {
            assertThat(s.localPort()).isGreaterThan(0);
        }
    }
}
