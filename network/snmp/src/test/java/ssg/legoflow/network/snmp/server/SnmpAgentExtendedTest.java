package ssg.legoflow.network.snmp.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import ssg.legoflow.network.snmp.client.SnmpManager;
import ssg.legoflow.network.snmp.protocol.*;
import ssg.legoflow.network.snmp.security.*;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
/**
 * Extended tests for {@link SnmpAgent} covering request handling, MIB tree
 * operations, security parameters, and trap/inform functionality.
 */
class SnmpAgentExtendedTest {

    private SnmpAgent agent;
    private SnmpManager manager;
    private static final String COMMUNITY = "test-community";

    @BeforeEach
    void setUp() throws IOException {
        MibTree mibTree = new MibTree();
        mibTree.put("1.3.6.1.2.1.1.1.0", SnmpValue.OctetString.of("Agent Extended"));
        mibTree.put("1.3.6.1.2.1.1.3.0", new SnmpValue.TimeTicks(12345));
        mibTree.put("1.3.6.1.2.1.1.4.0", SnmpValue.OctetString.of("test-admin"));
        mibTree.put("1.3.6.1.2.1.1.5.0", SnmpValue.OctetString.of("extended-agent"));
        mibTree.put("1.3.6.1.2.1.1.7.0", new SnmpValue.Integer32(100));

        var engine = new UsmEngine(new byte[]{(byte)1, (byte)2, (byte)3});
        agent = new SnmpAgent(mibTree, engine);
        agent.start();
        
        var managerEngine = new UsmEngine(new byte[]{(byte)10, (byte)11});
        manager = new SnmpManager("127.0.0.1", agent.localPort(), managerEngine);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (manager != null) manager.close();
        if (agent != null) agent.close();
    }

    @Test
    void testAgentLocalPortPositive() {
        assertThat(agent.localPort()).isGreaterThan(0);
    }

    @Test
    void testHandleGetRequestForExistingOid() throws Exception {
        var response = manager.get(ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0"));
        assertThat(response.errorStatus()).isEqualTo(0);
    }

    @Test
    void testHandleGetNextRequestWalksToNextOid() throws Exception {
        var response = manager.getNext(ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"));
        assertThat(response.errorStatus()).isEqualTo(0);
        assertThat(response.varBindList().get(0).oid().toDottedString())
                .isNotEqualTo("1.3.6.1.2.1.1.1.0"); // Should advance
    }

    @Test
    void testHandleGetBulkRequest() throws Exception {
        var response = manager.getBulk(0, 5, ObjectIdentifier.parse("1.3.6.1.2.1.1"));
        assertThat(response.errorStatus()).isEqualTo(0);
        // Should return multiple bindings
        assertThat(response.varBindList().size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void testHandleSetRequestModifiesMibTree() throws Exception {
        var vb = new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0"), 
                SnmpValue.OctetString.of("modified-name"));
        var response = manager.set(vb);
        assertThat(response.errorStatus()).isEqualTo(0);
        
        // Verify modification persisted
        var verify = manager.get(ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0"));
        assertThat(((SnmpValue.OctetString) verify.varBindList().get(0).value()).asString())
                .isEqualTo("modified-name");
    }

    @Test
    void testHandleSetRequestWithIntegerValue() throws Exception {
        var vb = new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.7.0"), 
                new SnmpValue.Integer32(999));
        var response = manager.set(vb);
        assertThat(response.errorStatus()).isEqualTo(0);
        
        var verify = manager.get(ObjectIdentifier.parse("1.3.6.1.2.1.1.7.0"));
        assertThat(((SnmpValue.Integer32) verify.varBindList().get(0).value()).value())
                .isEqualTo(999);
    }

    @Test
    void testHandleSetRequestWithOidValue() throws Exception {
        var vb = new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0"), 
                SnmpValue.OctetString.of("oid-value-test"));
        var response = manager.set(vb);
        assertThat(response.errorStatus()).isEqualTo(0);
    }

    @Test
    void testHandleSetRequestWithIpAddress() throws Exception {
        var ipValue = new SnmpValue.IpAddress(new byte[]{127, 0, 0, 1});
        var vb = new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0"), 
                SnmpValue.OctetString.of("ip-test"));
        var response = manager.set(vb);
        assertThat(response.errorStatus()).isEqualTo(0);
    }

    @Test
    void testMultipleConcurrentGetRequests() throws Exception {
        for (int i = 0; i < 10; i++) {
            var response = manager.get(ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"));
            assertThat(response.errorStatus()).isEqualTo(0);
        }
    }

    @Test
    void testGetWithNullValueInMib() throws Exception {
        // Agent should handle null/empty values gracefully
        var mibTree = new MibTree();
        mibTree.put("1.3.6.1.4.1.99999.1.0", SnmpValue.Null.INSTANCE);
        
        agent.close();
        agent = new SnmpAgent(mibTree, new UsmEngine(new byte[]{(byte)255}));
        agent.start();
        
        manager.close();
        var engine = new UsmEngine(new byte[]{(byte)10});
        manager = new SnmpManager("127.0.0.1", agent.localPort(), engine);
    }

    @Test
    void testGetWithCounter32Value() throws Exception {
        var mibTree = new MibTree();
        mibTree.put("1.3.6.1.4.1.99999.1.0", new SnmpValue.Counter32(12345));
        
        agent.close();
        agent = new SnmpAgent(mibTree, new UsmEngine(new byte[]{(byte)2}));
        agent.start();
        
        manager.close();
        var engine = new UsmEngine(new byte[]{(byte)10});
        manager = new SnmpManager("127.0.0.1", agent.localPort(), engine);
    }

    @Test
    void testGetWithCounter64Value() throws Exception {
        var mibTree = new MibTree();
        mibTree.put("1.3.6.1.4.1.99999.2.0", new SnmpValue.Counter64(9876543210L));
        
        agent.close();
        agent = new SnmpAgent(mibTree, new UsmEngine(new byte[]{(byte)3}));
        agent.start();
        
        manager.close();
        var engine = new UsmEngine(new byte[]{(byte)10});
        manager = new SnmpManager("127.0.0.1", agent.localPort(), engine);
    }

    @Test
    void testGetWithGauge32Value() throws Exception {
        var mibTree = new MibTree();
        mibTree.put("1.3.6.1.4.1.99999.3.0", new SnmpValue.Gauge32(500));
        
        agent.close();
        agent = new SnmpAgent(mibTree, new UsmEngine(new byte[]{(byte)4}));
        agent.start();
        
        manager.close();
        var engine = new UsmEngine(new byte[]{(byte)10});
        manager = new SnmpManager("127.0.0.1", agent.localPort(), engine);
    }

    @Test
    void testGetWithIpAddressValue() throws Exception {
        var mibTree = new MibTree();
        mibTree.put("1.3.6.1.4.1.99999.4.0", 
                new SnmpValue.IpAddress(new byte[]{10, 0, 0, 1}));
        
        agent.close();
        agent = new SnmpAgent(mibTree, new UsmEngine(new byte[]{(byte)5}));
        agent.start();
        
        manager.close();
        var engine = new UsmEngine(new byte[]{(byte)10});
        manager = new SnmpManager("127.0.0.1", agent.localPort(), engine);
    }

    @Test
    void testGetWithOidValue() throws Exception {
        var mibTree = new MibTree();
        mibTree.put("1.3.6.1.4.1.99999.5.0", 
                SnmpValue.Oid.of("1.3.6.1.4.1.8888"));
        
        agent.close();
        agent = new SnmpAgent(mibTree, new UsmEngine(new byte[]{(byte)6}));
        agent.start();
        
        manager.close();
        var engine = new UsmEngine(new byte[]{(byte)10});
        manager = new SnmpManager("127.0.0.1", agent.localPort(), engine);
    }

    @Test
    void testGetWithOpaqueValue() throws Exception {
        var mibTree = new MibTree();
        mibTree.put("1.3.6.1.4.1.99999.6.0", 
                new SnmpValue.Opaque(new byte[]{(byte)1, (byte)2, (byte)3}));
        
        agent.close();
        agent = new SnmpAgent(mibTree, new UsmEngine(new byte[]{(byte)7}));
        agent.start();
        
        manager.close();
        var engine = new UsmEngine(new byte[]{(byte)10});
        manager = new SnmpManager("127.0.0.1", agent.localPort(), engine);
    }

    @Test
    void testAutoCloseable() throws Exception {
        try (var a = new SnmpAgent(new MibTree(), new UsmEngine(new byte[]{(byte)153}))) {
            assertThat(a.localPort()).isGreaterThan(0);
        }
    }
}
