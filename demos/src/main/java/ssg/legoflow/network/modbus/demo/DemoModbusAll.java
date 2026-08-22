package ssg.legoflow.network.modbus.demo;

import ssg.legoflow.network.modbus.client.ModbusClient;
import ssg.legoflow.network.modbus.data.Coil;
import ssg.legoflow.network.modbus.data.DiscreteInput;
import ssg.legoflow.network.modbus.data.InputRegister;
import ssg.legoflow.network.modbus.data.Register;
import ssg.legoflow.network.modbus.protocol.FunctionCode;
import ssg.legoflow.network.modbus.protocol.MbapHeader;
import ssg.legoflow.network.modbus.protocol.ModbusCodec;
import ssg.legoflow.network.modbus.protocol.ModbusFrame;
import ssg.legoflow.network.modbus.server.DeviceMemory;
import ssg.legoflow.network.modbus.server.ModbusServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Arrays;
/**
 * Comprehensive demo of all Modbus TCP module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link ModbusServer}</b> — No external dependencies.
 * Runs anywhere without installation. Supports all 9 function codes, four data tables,
 * and virtual-thread-based connection handling.
 * Ideal for development, testing, CI/CD, and learning the Modbus protocol.</p>
 *
 * <p><b>Alternative: External Modbus TCP simulator / PLC</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production communication with real PLCs and industrial devices</li>
 *   <li>Testing with hardware-in-the-loop simulation</li>
 *   <li>Integration testing against industrial automation infrastructure</li>
 * </ul>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Read coils (FC 01) — read single-bit coil values</li>
 *   <li>Read discrete inputs (FC 02) — read single-bit input values</li>
 *   <li>Read holding registers (FC 03) — read 16-bit register values</li>
 *   <li>Read input registers (FC 04) — read 16-bit input register values</li>
 *   <li>Write single coil (FC 05) and register (FC 06)</li>
 *   <li>Write multiple coils (FC 15) and registers (FC 16)</li>
 *   <li>Read/write multiple registers (FC 23) — combined operation</li>
 *   <li>Device memory — thread-safe four-table data store</li>
 *   <li>MBAP header and frame encoding — wire format construction</li>
 *   <li>Function codes and data types — enum coverage and validation</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoModbusAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoModbusAll.class);

    /** Set to {@code true} to connect to an external Modbus TCP device. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external Modbus device. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "127.0.0.1";

    /** Port for external Modbus device. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 502;

    private DemoModbusAll() {}

    /**
     * Results from running the full demo.
     *
     * @param readCoils             number of coils read back correctly
     * @param readDiscreteInputs    number of discrete inputs read back correctly
     * @param readHoldingRegisters  number of holding registers read back correctly
     * @param readInputRegisters    number of input registers read back correctly
     * @param writeSingleValues     true if write single coil and register succeeded
     * @param writeMultipleValues   true if write multiple coils and registers succeeded
     * @param readWriteRegisters    true if combined read/write registers succeeded
     * @param deviceMemory          true if DeviceMemory four-table operations succeeded
     * @param frameEncoding         true if MBAP header and frame construction succeeded
     * @param functionCodesAndTypes true if function code enum and data types validated
     */
    public record Results(
            int readCoils,
            int readDiscreteInputs,
            int readHoldingRegisters,
            int readInputRegisters,
            boolean writeSingleValues,
            boolean writeMultipleValues,
            boolean readWriteRegisters,
            boolean deviceMemory,
            boolean frameEncoding,
            boolean functionCodesAndTypes
    ) {}

    /**
     * Runs the comprehensive demo covering all Modbus features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        // Pre-populate device memory
        DeviceMemory memory = new DeviceMemory();
        setupDemoData(memory);

        try (ModbusServer server = new ModbusServer(0, memory)) {
            server.start();
            int port = server.localPort();
            LOG.info("In-house Modbus server started on port {}", port);

            try (ModbusClient client = new ModbusClient("127.0.0.1", port)) {
                int coils = demoReadCoils(client);
                int discreteInputs = demoReadDiscreteInputs(client);
                int holdingRegs = demoReadHoldingRegisters(client);
                int inputRegs = demoReadInputRegisters(client);
                boolean singleWrites = demoWriteSingleValues(client, memory);
                boolean multiWrites = demoWriteMultipleValues(client, memory);
                boolean readWrite = demoReadWriteRegisters(client, memory);
                boolean memOps = demoDeviceMemory();
                boolean frames = demoFrameEncoding();
                boolean fcTypes = demoFunctionCodesAndTypes();

                return new Results(coils, discreteInputs, holdingRegs, inputRegs,
                        singleWrites, multiWrites, readWrite, memOps, frames, fcTypes);
            }
        }
    }

    // ======================== Data Setup =======================================

    /**
     * Pre-populates the device memory with demo data across all four tables.
     */
    static void setupDemoData(DeviceMemory memory) {
        // Coils: addresses 0-7
        memory.writeCoils(0, new boolean[]{true, false, true, true, false, true, false, true});

        // Discrete inputs: addresses 0-3
        memory.setDiscreteInput(0, true);
        memory.setDiscreteInput(1, false);
        memory.setDiscreteInput(2, true);
        memory.setDiscreteInput(3, true);

        // Holding registers: addresses 0-4
        memory.writeHoldingRegisters(0, new int[]{100, 200, 300, 400, 500});

        // Input registers: addresses 0-3
        memory.setInputRegister(0, 1000);
        memory.setInputRegister(1, 2000);
        memory.setInputRegister(2, 3000);
        memory.setInputRegister(3, 4000);
    }

    // ======================== 1. READ COILS (FC 01) ============================

    /**
     * Demonstrates reading coils (FC 01) from the server.
     *
     * @return the number of coils that match expected values
     */
    static int demoReadCoils(ModbusClient client) throws Exception {
        LOG.info("=== 1. Read Coils (FC 01) ===");
        boolean[] coils = client.readCoils(0, 8);
        boolean[] expected = {true, false, true, true, false, true, false, true};
        int matching = 0;
        for (int i = 0; i < Math.min(coils.length, expected.length); i++) {
            if (coils[i] == expected[i]) matching++;
        }
        LOG.info("Read {} coils, {} matching expected", coils.length, matching);
        return matching;
    }

    // ======================== 2. READ DISCRETE INPUTS (FC 02) ==================

    /**
     * Demonstrates reading discrete inputs (FC 02) from the server.
     *
     * @return the number of inputs that match expected values
     */
    static int demoReadDiscreteInputs(ModbusClient client) throws Exception {
        LOG.info("=== 2. Read Discrete Inputs (FC 02) ===");
        boolean[] inputs = client.readDiscreteInputs(0, 4);
        boolean[] expected = {true, false, true, true};
        int matching = 0;
        for (int i = 0; i < Math.min(inputs.length, expected.length); i++) {
            if (inputs[i] == expected[i]) matching++;
        }
        LOG.info("Read {} discrete inputs, {} matching expected", inputs.length, matching);
        return matching;
    }

    // ======================== 3. READ HOLDING REGISTERS (FC 03) ================

    /**
     * Demonstrates reading holding registers (FC 03) from the server.
     *
     * @return the number of registers that match expected values
     */
    static int demoReadHoldingRegisters(ModbusClient client) throws Exception {
        LOG.info("=== 3. Read Holding Registers (FC 03) ===");
        int[] regs = client.readHoldingRegisters(0, 5);
        int[] expected = {100, 200, 300, 400, 500};
        int matching = 0;
        for (int i = 0; i < Math.min(regs.length, expected.length); i++) {
            if (regs[i] == expected[i]) matching++;
        }
        LOG.info("Read {} holding registers: {} matching expected", regs.length, matching);
        return matching;
    }

    // ======================== 4. READ INPUT REGISTERS (FC 04) ==================

    /**
     * Demonstrates reading input registers (FC 04) from the server.
     *
     * @return the number of registers that match expected values
     */
    static int demoReadInputRegisters(ModbusClient client) throws Exception {
        LOG.info("=== 4. Read Input Registers (FC 04) ===");
        int[] regs = client.readInputRegisters(0, 4);
        int[] expected = {1000, 2000, 3000, 4000};
        int matching = 0;
        for (int i = 0; i < Math.min(regs.length, expected.length); i++) {
            if (regs[i] == expected[i]) matching++;
        }
        LOG.info("Read {} input registers: {} matching expected", regs.length, matching);
        return matching;
    }

    // ======================== 5. WRITE SINGLE VALUES ============================

    /**
     * Demonstrates writing a single coil (FC 05) and single register (FC 06).
     *
     * @return true if both writes succeeded and can be verified
     */
    static boolean demoWriteSingleValues(ModbusClient client, DeviceMemory memory) throws Exception {
        LOG.info("=== 5. Write Single Values (FC 05, 06) ===");

        // Write single coil at address 10
        client.writeSingleCoil(10, true);
        boolean coilOk = memory.readCoils(10, 1)[0];
        LOG.info("Write single coil @10=true: verified={}", coilOk);

        // Write single register at address 10
        client.writeSingleRegister(10, 12345);
        int regValue = memory.readHoldingRegisters(10, 1)[0];
        boolean regOk = regValue == 12345;
        LOG.info("Write single register @10=12345: verified={}", regOk);

        return coilOk && regOk;
    }

    // ======================== 6. WRITE MULTIPLE VALUES ==========================

    /**
     * Demonstrates writing multiple coils (FC 15) and registers (FC 16).
     *
     * @return true if both writes succeeded and can be verified
     */
    static boolean demoWriteMultipleValues(ModbusClient client, DeviceMemory memory) throws Exception {
        LOG.info("=== 6. Write Multiple Values (FC 15, 16) ===");

        // Write multiple coils starting at address 20
        boolean[] coilValues = {true, true, false, true, false};
        client.writeMultipleCoils(20, coilValues);
        boolean[] readBack = memory.readCoils(20, 5);
        boolean coilsOk = Arrays.equals(readBack, coilValues);
        LOG.info("Write multiple coils @20: verified={}", coilsOk);

        // Write multiple registers starting at address 20
        int[] regValues = {1111, 2222, 3333};
        client.writeMultipleRegisters(20, regValues);
        int[] regReadBack = memory.readHoldingRegisters(20, 3);
        boolean regsOk = Arrays.equals(regReadBack, regValues);
        LOG.info("Write multiple registers @20: verified={}", regsOk);

        return coilsOk && regsOk;
    }

    // ======================== 7. READ/WRITE REGISTERS (FC 23) ==================

    /**
     * Demonstrates combined read/write multiple registers (FC 23).
     *
     * @return true if the operation read and wrote correctly
     */
    static boolean demoReadWriteRegisters(ModbusClient client, DeviceMemory memory) throws Exception {
        LOG.info("=== 7. Read/Write Multiple Registers (FC 23) ===");

        // Write values at address 30, read from address 0
        int[] writeValues = {9999, 8888};
        int[] readResults = client.readWriteMultipleRegisters(0, 3, 30, writeValues);

        // Verify read values (from pre-populated data)
        boolean readOk = readResults.length >= 3
                && readResults[0] == 100 && readResults[1] == 200 && readResults[2] == 300;

        // Verify written values
        int[] writtenBack = memory.readHoldingRegisters(30, 2);
        boolean writeOk = writtenBack[0] == 9999 && writtenBack[1] == 8888;

        LOG.info("ReadWrite FC23: read={} write={}", readOk, writeOk);
        return readOk && writeOk;
    }

    // ======================== 8. DEVICE MEMORY =================================

    /**
     * Demonstrates the DeviceMemory four-table data store operations.
     *
     * @return true if all four tables operated correctly
     */
    static boolean demoDeviceMemory() {
        LOG.info("=== 8. Device Memory ===");
        DeviceMemory mem = new DeviceMemory(1024);

        // Coils
        mem.writeCoil(0, true);
        mem.writeCoil(1, false);
        boolean coilsOk = mem.readCoils(0, 2)[0] && !mem.readCoils(0, 2)[1];
        LOG.info("Coils: OK={}", coilsOk);

        // Discrete inputs
        mem.setDiscreteInput(0, true);
        boolean discreteOk = mem.readDiscreteInputs(0, 1)[0];
        LOG.info("Discrete inputs: OK={}", discreteOk);

        // Holding registers
        mem.writeHoldingRegister(0, 0xFFFF);
        boolean holdingOk = mem.readHoldingRegisters(0, 1)[0] == 0xFFFF;
        LOG.info("Holding registers: OK={}", holdingOk);

        // Input registers
        mem.setInputRegister(0, 42000);
        boolean inputOk = mem.readInputRegisters(0, 1)[0] == 42000;
        LOG.info("Input registers: OK={}", inputOk);

        // Batch operations
        mem.writeCoils(100, new boolean[]{true, true, true});
        boolean batchCoilOk = mem.readCoils(100, 3)[2];
        mem.writeHoldingRegisters(100, new int[]{10, 20, 30});
        boolean batchRegOk = mem.readHoldingRegisters(100, 3)[2] == 30;
        LOG.info("Batch operations: coils={} regs={}", batchCoilOk, batchRegOk);

        return coilsOk && discreteOk && holdingOk && inputOk && batchCoilOk && batchRegOk;
    }

    // ======================== 9. FRAME ENCODING ================================

    /**
     * Demonstrates MBAP header and Modbus frame construction.
     *
     * @return true if frame construction and header fields are correct
     */
    static boolean demoFrameEncoding() {
        LOG.info("=== 9. MBAP Header and Frame Encoding ===");

        // Create an MBAP header
        byte[] pdu = ModbusCodec.buildReadHoldingRegistersRequest(0, 10);
        MbapHeader header = MbapHeader.request(1, 1, pdu.length);
        boolean headerOk = header.transactionId() == 1
                && header.protocolId() == 0
                && header.unitId() == 1
                && header.length() == pdu.length + 1; // +1 for unitId

        LOG.info("MBAP header: txId={} proto={} unit={} len={} OK={}",
                header.transactionId(), header.protocolId(),
                header.unitId(), header.length(), headerOk);

        // Create a frame
        ModbusFrame frame = new ModbusFrame(header, pdu);
        boolean frameOk = frame.header().equals(header) && frame.pdu().length == pdu.length;
        LOG.info("ModbusFrame: headerOk={} pduLength={}", frameOk, frame.pdu().length);

        return headerOk && frameOk;
    }

    // ======================== 10. FUNCTION CODES AND DATA TYPES ================

    /**
     * Demonstrates function code enum and data type record validation.
     *
     * @return true if all function codes and data types validated correctly
     */
    static boolean demoFunctionCodesAndTypes() {
        LOG.info("=== 10. Function Codes and Data Types ===");

        // Verify all 9 function codes
        boolean fc01 = FunctionCode.of(0x01) == FunctionCode.READ_COILS;
        boolean fc02 = FunctionCode.of(0x02) == FunctionCode.READ_DISCRETE_INPUTS;
        boolean fc03 = FunctionCode.of(0x03) == FunctionCode.READ_HOLDING_REGISTERS;
        boolean fc04 = FunctionCode.of(0x04) == FunctionCode.READ_INPUT_REGISTERS;
        boolean fc05 = FunctionCode.of(0x05) == FunctionCode.WRITE_SINGLE_COIL;
        boolean fc06 = FunctionCode.of(0x06) == FunctionCode.WRITE_SINGLE_REGISTER;
        boolean fc15 = FunctionCode.of(0x0F) == FunctionCode.WRITE_MULTIPLE_COILS;
        boolean fc16 = FunctionCode.of(0x10) == FunctionCode.WRITE_MULTIPLE_REGISTERS;
        boolean fc23 = FunctionCode.of(0x17) == FunctionCode.READ_WRITE_MULTIPLE_REGISTERS;
        boolean allFcOk = fc01 && fc02 && fc03 && fc04 && fc05 && fc06 && fc15 && fc16 && fc23;
        LOG.info("Function codes: all 9 valid={}", allFcOk);

        // Exception code detection
        boolean isExc = FunctionCode.isException(0x81);
        boolean fromExc = FunctionCode.fromException(0x81) == FunctionCode.READ_COILS;
        LOG.info("Exception code: isException(0x81)={} fromException=READ_COILS={}", isExc, fromExc);

        // Data type records
        Coil coil = new Coil(0, true);
        DiscreteInput di = new DiscreteInput(1, false);
        Register reg = new Register(100, 42);
        InputRegister ir = new InputRegister(200, 1000);
        boolean typesOk = coil.value() && !di.value()
                && reg.value() == 42 && ir.value() == 1000;
        LOG.info("Data types: coil={} discrete={} register={} inputReg={} OK={}",
                coil.value(), di.value(), reg.value(), ir.value(), typesOk);

        return allFcOk && isExc && fromExc && typesOk;
    }
}
