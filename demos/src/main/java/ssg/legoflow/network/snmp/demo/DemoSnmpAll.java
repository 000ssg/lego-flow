package ssg.legoflow.network.snmp.demo;

import ssg.legoflow.network.common.oid.ObjectIdentifier;
import ssg.legoflow.network.snmp.client.SnmpManager;
import ssg.legoflow.network.snmp.protocol.*;
import ssg.legoflow.network.snmp.security.*;
import ssg.legoflow.network.snmp.server.MibTree;
import ssg.legoflow.network.snmp.server.SnmpAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.NavigableMap;
/**
 * Comprehensive demo of all SNMP module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link SnmpAgent}</b> — No external dependencies.
 * Runs anywhere without installation. Supports GET, GETNEXT, GETBULK, SET operations,
 * MIB tree with lexicographic traversal, USM security, and VACM access control.
 * Ideal for development, testing, CI/CD, and learning the SNMP protocol.</p>
 *
 * <p><b>Alternative: External Net-SNMP / SNMP4J Agent</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production SNMP monitoring against real network devices</li>
 *   <li>Testing with full MIB-II instrumentation</li>
 *   <li>Integration testing against production SNMP infrastructure</li>
 * </ul>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>GET operation — retrieve specific OID values from agent</li>
 *   <li>GETNEXT operation — lexicographic MIB tree walk</li>
 *   <li>GETBULK operation — efficient bulk retrieval with repeaters</li>
 *   <li>SET operation — write values to the MIB tree</li>
 *   <li>MIB tree — in-memory sorted OID store with subtree queries</li>
 *   <li>SNMPv3 message encoding — BER codec round-trip</li>
 *   <li>USM security engine — authentication and key management</li>
 *   <li>VACM access control — view-based access decisions</li>
 *   <li>Trap/Inform PDU types — notification message construction</li>
 *   <li>SMIv2 value types — all 12 SNMP data types</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoSnmpAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoSnmpAll.class);

    /** Set to {@code true} to connect to an external SNMP agent. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external SNMP agent. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "127.0.0.1";

    /** Port for external SNMP agent. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 161;

    private DemoSnmpAll() {}

    /**
     * Results from running the full demo.
     *
     * @param getOperation       number of VarBinds returned from GET
     * @param getNextOperation   number of VarBinds returned from GETNEXT walk
     * @param getBulkOperation   number of VarBinds returned from GETBULK
     * @param setOperation       true if SET updated the MIB tree value
     * @param mibTreeOperations  true if MIB tree subtree/getNext/contains all succeeded
     * @param messageEncoding    true if SNMPv3 message BER round-trip succeeded
     * @param usmSecurity        true if USM authentication digest computation succeeded
     * @param vacmAccessControl  true if VACM view-based access decisions succeeded
     * @param trapInformPdus     true if Trap and Inform PDU construction succeeded
     * @param valueTypes         number of distinct SMIv2 value types created successfully
     */
    public record Results(
            int getOperation,
            int getNextOperation,
            int getBulkOperation,
            boolean setOperation,
            boolean mibTreeOperations,
            boolean messageEncoding,
            boolean usmSecurity,
            boolean vacmAccessControl,
            boolean trapInformPdus,
            int valueTypes
    ) {}

    /**
     * Runs the comprehensive demo covering all SNMP features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        // Setup MIB tree and agent
        MibTree mibTree = createDemoMibTree();
        byte[] engineId = {(byte) 0x80, 0x00, 0x00, 0x01, 0x03, 0x0A, 0x0B, 0x0C};
        UsmEngine usmEngine = new UsmEngine(engineId);

        try (SnmpAgent agent = new SnmpAgent(mibTree, usmEngine)) {
            agent.start();
            int port = agent.localPort();
            LOG.info("In-house SNMP agent started on port {}", port);

            UsmEngine clientEngine = new UsmEngine(new byte[]{0x01});
            try (SnmpManager manager = new SnmpManager("127.0.0.1", port, clientEngine)) {
                manager.setRemoteEngine(engineId, 0, 0);

                int getResults = demoGetOperation(manager);
                int getNextResults = demoGetNextOperation(manager);
                int getBulkResults = demoGetBulkOperation(manager);
                boolean setResult = demoSetOperation(manager, mibTree);
                boolean mibOps = demoMibTreeOperations(mibTree);
                boolean msgEncoding = demoMessageEncoding();
                boolean usm = demoUsmSecurity(engineId);
                boolean vacm = demoVacmAccessControl();
                boolean trapInform = demoTrapInformPdus();
                int valueTypeCount = demoValueTypes();

                return new Results(getResults, getNextResults, getBulkResults,
                        setResult, mibOps, msgEncoding, usm, vacm, trapInform, valueTypeCount);
            }
        }
    }

    // ======================== MIB Tree Setup ===================================

    /**
     * Creates a demo MIB tree with standard MIB-2 system group entries.
     */
    static MibTree createDemoMibTree() {
        MibTree tree = new MibTree();
        tree.put(SnmpOids.SYS_DESCR, SnmpValue.OctetString.of("Lego Flow SNMP Agent v1.0"));
        tree.put(SnmpOids.SYS_OBJECT_ID, new SnmpValue.Oid(ObjectIdentifier.parse("1.3.6.1.4.1.99999.1")));
        tree.put(SnmpOids.SYS_UP_TIME, new SnmpValue.TimeTicks(123456L));
        tree.put(SnmpOids.SYS_CONTACT, SnmpValue.OctetString.of("admin@example.com"));
        tree.put(SnmpOids.SYS_NAME, SnmpValue.OctetString.of("demo-agent"));
        tree.put(SnmpOids.SYS_LOCATION, SnmpValue.OctetString.of("Lab Room 42"));
        tree.put(SnmpOids.SYS_SERVICES, new SnmpValue.Integer32(72));
        tree.put(SnmpOids.IF_NUMBER, new SnmpValue.Integer32(3));
        return tree;
    }

    // ======================== 1. GET OPERATION ==================================

    /**
     * Demonstrates SNMP GET operation to retrieve specific OIDs.
     *
     * @return the number of VarBinds in the response
     */
    static int demoGetOperation(SnmpManager manager) throws Exception {
        LOG.info("=== 1. GET Operation ===");
        SnmpPdu.Response response = manager.get(SnmpOids.SYS_DESCR, SnmpOids.SYS_NAME);
        int count = response.varBindList().size();
        for (VarBind vb : response.varBindList()) {
            LOG.info("GET {}: {}", vb.oid(), vb.value());
        }
        return count;
    }

    // ======================== 2. GETNEXT OPERATION ==============================

    /**
     * Demonstrates SNMP GETNEXT operation for MIB tree walking.
     *
     * @return the number of VarBinds traversed
     */
    static int demoGetNextOperation(SnmpManager manager) throws Exception {
        LOG.info("=== 2. GETNEXT Operation ===");
        int count = 0;
        ObjectIdentifier currentOid = SnmpOids.SYSTEM;

        // Walk through system group
        for (int i = 0; i < 8; i++) {
            SnmpPdu.Response response = manager.getNext(currentOid);
            VarBind vb = response.varBindList().get(0);
            if (vb.value() instanceof SnmpValue.EndOfMibView) {
                break;
            }
            LOG.info("GETNEXT: {} = {}", vb.oid(), vb.value());
            currentOid = vb.oid();
            count++;
        }
        LOG.info("GETNEXT walked {} entries", count);
        return count;
    }

    // ======================== 3. GETBULK OPERATION ==============================

    /**
     * Demonstrates SNMP GETBULK operation for efficient bulk retrieval.
     *
     * @return the number of VarBinds in the response
     */
    static int demoGetBulkOperation(SnmpManager manager) throws Exception {
        LOG.info("=== 3. GETBULK Operation ===");
        SnmpPdu.Response response = manager.getBulk(0, 5, SnmpOids.SYSTEM);
        int count = response.varBindList().size();
        for (VarBind vb : response.varBindList()) {
            LOG.info("GETBULK: {} = {}", vb.oid(), vb.value());
        }
        LOG.info("GETBULK returned {} entries", count);
        return count;
    }

    // ======================== 4. SET OPERATION ==================================

    /**
     * Demonstrates SNMP SET operation to modify MIB values.
     *
     * @return true if the SET was applied and can be verified
     */
    static boolean demoSetOperation(SnmpManager manager, MibTree mibTree) throws Exception {
        LOG.info("=== 4. SET Operation ===");
        SnmpValue.OctetString newContact = SnmpValue.OctetString.of("ops@example.com");
        VarBind setVb = new VarBind(SnmpOids.SYS_CONTACT, newContact);
        SnmpPdu.Response response = manager.set(setVb);
        boolean noError = response.errorStatus() == 0;

        // Verify by reading back from MIB tree
        SnmpValue stored = mibTree.get(SnmpOids.SYS_CONTACT);
        boolean verified = stored instanceof SnmpValue.OctetString os
                && "ops@example.com".equals(os.asString());
        LOG.info("SET sysContact: noError={} verified={}", noError, verified);
        return noError && verified;
    }

    // ======================== 5. MIB TREE OPERATIONS ============================

    /**
     * Demonstrates MIB tree operations: subtree queries, getNext, contains.
     *
     * @return true if all operations succeeded
     */
    static boolean demoMibTreeOperations(MibTree mibTree) {
        LOG.info("=== 5. MIB Tree Operations ===");

        // Subtree query
        NavigableMap<ObjectIdentifier, SnmpValue> subtree = mibTree.getSubtree(SnmpOids.SYSTEM);
        boolean hasSubtree = subtree.size() >= 7;
        LOG.info("Subtree under system: {} entries", subtree.size());

        // GetNext
        Map.Entry<ObjectIdentifier, SnmpValue> next = mibTree.getNext(SnmpOids.SYS_DESCR);
        boolean hasNext = next != null && next.getKey().compareTo(SnmpOids.SYS_DESCR) > 0;
        LOG.info("GetNext after sysDescr: {}", next != null ? next.getKey() : "null");

        // Contains
        boolean contains = mibTree.contains(SnmpOids.SYS_NAME);
        LOG.info("Contains sysName: {}", contains);

        // Size
        boolean hasEntries = mibTree.size() >= 8;
        LOG.info("MIB tree size: {}", mibTree.size());

        return hasSubtree && hasNext && contains && hasEntries;
    }

    // ======================== 6. MESSAGE ENCODING ===============================

    /**
     * Demonstrates SNMPv3 message BER encoding and decoding round-trip.
     *
     * @return true if the round-trip preserved message fields
     */
    static boolean demoMessageEncoding() {
        LOG.info("=== 6. SNMPv3 Message Encoding ===");
        VarBindList vbl = VarBindList.of(VarBind.ofNull(SnmpOids.SYS_DESCR));
        SnmpPdu pdu = new SnmpPdu.GetRequest(42, 0, 0, vbl);
        ScopedPdu scopedPdu = new ScopedPdu(new byte[]{0x01, 0x02}, "", pdu);

        SnmpMessage msg = SnmpMessage.builder()
                .msgId(100)
                .msgMaxSize(65507)
                .scopedPdu(scopedPdu)
                .build();

        byte[] encoded = SnmpCodec.encodeMessage(msg);
        SnmpMessage decoded = SnmpCodec.decodeMessage(encoded);

        boolean idMatch = decoded.msgId() == 100;
        boolean versionMatch = decoded.msgVersion() == SnmpMessage.VERSION_3;
        boolean pduMatch = decoded.scopedPdu().pdu() instanceof SnmpPdu.GetRequest gr
                && gr.requestId() == 42;
        boolean ok = idMatch && versionMatch && pduMatch;
        LOG.info("Message round-trip: msgId={} version={} pduType={} OK={}",
                decoded.msgId(), decoded.msgVersion(),
                decoded.scopedPdu().pdu().getClass().getSimpleName(), ok);
        return ok;
    }

    // ======================== 7. USM SECURITY ===================================

    /**
     * Demonstrates USM security engine: authentication digest computation and user management.
     *
     * @return true if authentication computation succeeded
     */
    static boolean demoUsmSecurity(byte[] engineId) {
        LOG.info("=== 7. USM Security ===");
        UsmEngine engine = new UsmEngine(engineId);

        // Create a user with HMAC-SHA-96 auth
        byte[] authKey = UsmKeyUtils.deriveLocalizedKey(
                "authPassword123", engineId, AuthProtocol.HMAC_SHA_96);
        UsmUser user = new UsmUser("demoUser", AuthProtocol.HMAC_SHA_96, authKey,
                PrivProtocol.NONE, new byte[0]);
        engine.addUser(user);

        // Compute auth digest on a test message
        byte[] testMessage = new byte[64];
        testMessage[0] = 0x30; // SEQUENCE tag
        byte[] digest = engine.computeAuth(testMessage, user);
        boolean hasDigest = digest.length == 12; // truncated to 12 bytes
        LOG.info("HMAC-SHA-96 digest length: {} (expected 12)", digest.length);

        // Verify the digest
        boolean verified = engine.verifyAuth(testMessage, digest, user);
        LOG.info("Digest verification: {}", verified);

        // User management
        UsmUser found = engine.getUser("demoUser");
        boolean userFound = found != null && found.userName().equals("demoUser");
        LOG.info("User lookup: found={}", userFound);

        return hasDigest && verified && userFound;
    }

    // ======================== 8. VACM ACCESS CONTROL ============================

    /**
     * Demonstrates VACM view-based access control configuration and decisions.
     *
     * @return true if access control decisions were correct
     */
    static boolean demoVacmAccessControl() {
        LOG.info("=== 8. VACM Access Control ===");
        VacmAccessControl vacm = new VacmAccessControl();

        // Configure security-to-group mapping
        vacm.addSecurityToGroup(3, "adminUser", "adminGroup");
        vacm.addSecurityToGroup(3, "readUser", "readGroup");

        // Configure access entries
        vacm.addAccess("adminGroup", "", 3, SecurityLevel.AUTH_NO_PRIV,
                "fullView", "fullView", "fullView");
        vacm.addAccess("readGroup", "", 3, SecurityLevel.NO_AUTH_NO_PRIV,
                "restrictedView", "", "");

        // Configure view tree families
        vacm.addView("fullView", ObjectIdentifier.parse("1.3.6.1"), new byte[0], true);
        vacm.addView("restrictedView", ObjectIdentifier.parse("1.3.6.1.2.1.1"), new byte[0], true);

        // Test access: admin should have full access
        boolean adminRead = vacm.isAccessAllowed(3, "adminUser",
                SecurityLevel.AUTH_NO_PRIV, "",
                VacmAccessControl.AccessType.READ,
                ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"));
        LOG.info("Admin read sysDescr: {}", adminRead);

        // Test access: readUser should have read access to system group
        boolean readAllowed = vacm.isAccessAllowed(3, "readUser",
                SecurityLevel.NO_AUTH_NO_PRIV, "",
                VacmAccessControl.AccessType.READ,
                ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"));
        LOG.info("ReadUser read sysDescr: {}", readAllowed);

        return adminRead && readAllowed;
    }

    // ======================== 9. TRAP/INFORM PDUS ===============================

    /**
     * Demonstrates Trap and Inform PDU type construction.
     *
     * @return true if both PDU types were constructed successfully
     */
    static boolean demoTrapInformPdus() {
        LOG.info("=== 9. Trap/Inform PDUs ===");

        // Construct a TrapV2 PDU
        VarBindList trapVbl = VarBindList.of(
                new VarBind(SnmpOids.SYS_UP_TIME, new SnmpValue.TimeTicks(100)),
                new VarBind(SnmpOids.SNMP_TRAP_OID, new SnmpValue.Oid(SnmpOids.COLD_START))
        );
        SnmpPdu trapPdu = new SnmpPdu.TrapV2(0, 0, 0, trapVbl);
        boolean trapOk = trapPdu.tagNumber() == 7 && trapPdu.varBindList().size() == 2;
        LOG.info("TrapV2: tag={} varBinds={}", trapPdu.tagNumber(), trapPdu.varBindList().size());

        // Construct an InformRequest PDU
        VarBindList informVbl = VarBindList.of(
                new VarBind(SnmpOids.SYS_UP_TIME, new SnmpValue.TimeTicks(200)),
                new VarBind(SnmpOids.SNMP_TRAP_OID, new SnmpValue.Oid(SnmpOids.WARM_START))
        );
        SnmpPdu informPdu = new SnmpPdu.InformRequest(1, 0, 0, informVbl);
        boolean informOk = informPdu.tagNumber() == 6 && informPdu.varBindList().size() == 2;
        LOG.info("InformRequest: tag={} varBinds={}", informPdu.tagNumber(), informPdu.varBindList().size());

        // Encode and decode a trap message
        ScopedPdu scopedPdu = new ScopedPdu(new byte[]{0x01}, "", trapPdu);
        SnmpMessage msg = SnmpMessage.builder()
                .msgId(1)
                .msgMaxSize(65507)
                .scopedPdu(scopedPdu)
                .build();
        byte[] encoded = SnmpCodec.encodeMessage(msg);
        SnmpMessage decoded = SnmpCodec.decodeMessage(encoded);
        boolean roundTrip = decoded.scopedPdu().pdu() instanceof SnmpPdu.TrapV2;
        LOG.info("Trap round-trip: {}", roundTrip);

        return trapOk && informOk && roundTrip;
    }

    // ======================== 10. SMIv2 VALUE TYPES ============================

    /**
     * Demonstrates all 12 SMIv2 SNMP value types.
     *
     * @return the number of value types created successfully
     */
    static int demoValueTypes() {
        LOG.info("=== 10. SMIv2 Value Types ===");
        int count = 0;

        // Integer32
        var i32 = new SnmpValue.Integer32(42);
        if (i32.value() == 42) { count++; LOG.info("Integer32: {}", i32.value()); }

        // Counter32
        var c32 = new SnmpValue.Counter32(4000000000L);
        if (c32.value() == 4000000000L) { count++; LOG.info("Counter32: {}", c32.value()); }

        // Counter64
        var c64 = new SnmpValue.Counter64(Long.MAX_VALUE);
        if (c64.value() == Long.MAX_VALUE) { count++; LOG.info("Counter64: {}", c64.value()); }

        // Gauge32
        var g32 = new SnmpValue.Gauge32(100L);
        if (g32.value() == 100L) { count++; LOG.info("Gauge32: {}", g32.value()); }

        // TimeTicks
        var tt = new SnmpValue.TimeTicks(123456L);
        if (tt.value() == 123456L) { count++; LOG.info("TimeTicks: {}", tt.value()); }

        // OctetString
        var os = SnmpValue.OctetString.of("test");
        if ("test".equals(os.asString())) { count++; LOG.info("OctetString: {}", os.asString()); }

        // Oid
        var oid = SnmpValue.Oid.of("1.3.6.1.2.1");
        if (oid.value().size() == 6) { count++; LOG.info("Oid: {}", oid.value()); }

        // IpAddress
        var ip = SnmpValue.IpAddress.of("192.168.1.1");
        if (ip.address().length == 4) { count++; LOG.info("IpAddress: {}", ip); }

        // Opaque
        var opaque = new SnmpValue.Opaque(new byte[]{0x01, 0x02});
        if (opaque.value().length == 2) { count++; LOG.info("Opaque: {} bytes", opaque.value().length); }

        // Null
        var nul = SnmpValue.Null.INSTANCE;
        count++; LOG.info("Null: {}", nul);

        // NoSuchObject
        var nso = SnmpValue.NoSuchObject.INSTANCE;
        count++; LOG.info("NoSuchObject: {}", nso);

        // EndOfMibView
        var eomv = SnmpValue.EndOfMibView.INSTANCE;
        count++; LOG.info("EndOfMibView: {}", eomv);

        LOG.info("Value types created: {}/12", count);
        return count;
    }
}
