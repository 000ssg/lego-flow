package ssg.legoflow.network.modbus.client;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.modbus.server.ModbusServer;
import ssg.legoflow.network.modbus.server.DeviceMemory;
import static org.assertj.core.api.Assertions.*;
class ModbusClientTest {

    private static ModbusServer server;
    private static int port;
    private ModbusClient client;

    @BeforeAll
    static void startServer() throws Exception {
        var memory = new DeviceMemory();
        for (int i = 0; i < 16; i++) {
            memory.writeCoil(i, i % 2 == 0);
            memory.setDiscreteInput(i, i % 3 == 0);
            memory.setInputRegister(i, i * 10);
            memory.writeHoldingRegister(i, i * 100);
        }
        
        server = new ModbusServer(0, memory);
        server.start();
        port = server.localPort();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) server.close();
    }

    @BeforeEach
    void connect() throws Exception {
        client = new ModbusClient("127.0.0.1", port);
    }

    @AfterEach
    void disconnect() throws Exception {
        if (client != null) client.close();
    }

    @Test
    void testReadCoils() throws Exception {
        var coils = client.readCoils(0, 8);
        assertThat(coils).hasSize(8);
        for (int i = 0; i < 8; i++) {
            assertThat(coils[i]).isEqualTo(i % 2 == 0);
        }
    }

    @Test
    void testReadDiscreteInputs() throws Exception {
        var inputs = client.readDiscreteInputs(0, 8);
        assertThat(inputs).hasSize(8);
        for (int i = 0; i < 8; i++) {
            assertThat(inputs[i]).isEqualTo(i % 3 == 0);
        }
    }

    @Test
    void testReadInputRegisters() throws Exception {
        var registers = client.readInputRegisters(0, 4);
        assertThat(registers).hasSize(4);
        for (int i = 0; i < 4; i++) {
            assertThat(registers[i]).isEqualTo(i * 10);
        }
    }

    @Test
    void testReadHoldingRegisters() throws Exception {
        var registers = client.readHoldingRegisters(0, 4);
        assertThat(registers).hasSize(4);
        for (int i = 0; i < 4; i++) {
            assertThat(registers[i]).isEqualTo(i * 100);
        }
    }

    @Test
    void testWriteSingleCoil() throws Exception {
        client.writeSingleCoil(2, false);
        var coils = client.readCoils(2, 1);
        assertThat(coils[0]).isFalse();
        client.writeSingleCoil(2, true);
    }

    @Test
    void testWriteSingleRegister() throws Exception {
        int initial = client.readHoldingRegisters(2, 1)[0];
        client.writeSingleRegister(2, 999);
        var registers = client.readHoldingRegisters(2, 1);
        assertThat(registers[0]).isEqualTo(999);
        client.writeSingleRegister(2, initial);
    }

    @Test
    void testWriteMultipleCoils() throws Exception {
        boolean[] values = new boolean[4];
        for (int i = 0; i < 4; i++) values[i] = (i + 10) % 2 == 0;
        client.writeMultipleCoils(10, values);
        var coils = client.readCoils(10, 4);
        for (int i = 0; i < 4; i++) {
            assertThat(coils[i]).isEqualTo(values[i]);
        }
    }

    @Test
    void testWriteMultipleRegisters() throws Exception {
        int[] values = new int[] {111, 222, 333};
        client.writeMultipleRegisters(10, values);
        var registers = client.readHoldingRegisters(10, 3);
        for (int i = 0; i < 3; i++) {
            assertThat(registers[i]).isEqualTo(values[i]);
        }
    }

    @Test
    void testMultipleRequests() throws Exception {
        for (int i = 0; i < 5; i++) {
            var regs = client.readHoldingRegisters(0, 1);
            assertThat(regs).hasSize(1);
        }
    }

    @Test
    void testConnectToNonExistentServer() {
        assertThatThrownBy(() -> new ModbusClient("127.0.0.1", 59999))
                .isInstanceOf(Exception.class);
    }

    @Test
    void testConstructorWithUnitId() throws Exception {
        var c = new ModbusClient("127.0.0.1", port, 2);
        var coils = c.readCoils(0, 1);
        assertThat(coils).hasSize(1);
        c.close();
    }

    @Test
    void testAutoCloseable() throws Exception {
        try (var c = new ModbusClient("127.0.0.1", port)) {
            var regs = c.readHoldingRegisters(0, 1);
            assertThat(regs).hasSize(1);
        }
    }
}
