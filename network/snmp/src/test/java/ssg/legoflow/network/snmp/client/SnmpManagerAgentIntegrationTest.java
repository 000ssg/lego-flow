package ssg.legoflow.network.snmp.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import ssg.legoflow.network.snmp.protocol.*;
import ssg.legoflow.network.snmp.security.UsmEngine;
import ssg.legoflow.network.snmp.server.MibTree;
import ssg.legoflow.network.snmp.server.SnmpAgent;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Integration tests for {@link SnmpManager} and {@link SnmpAgent}.
 *
 * @since 0.1.0
 */
class SnmpManagerAgentIntegrationTest {

    private SnmpAgent agent;
    private SnmpManager manager;

    @BeforeEach
    void setUp() throws IOException {
        MibTree mibTree = new MibTree();
        mibTree.put("1.3.6.1.2.1.1.1.0", SnmpValue.OctetString.of("Integration Test Agent"));
        mibTree.put("1.3.6.1.2.1.1.2.0", SnmpValue.Oid.of("1.3.6.1.4.1.99"));
        mibTree.put("1.3.6.1.2.1.1.3.0", new SnmpValue.TimeTicks(50000));
        mibTree.put("1.3.6.1.2.1.1.4.0", SnmpValue.OctetString.of("admin@test.com"));
        mibTree.put("1.3.6.1.2.1.1.5.0", SnmpValue.OctetString.of("test-agent"));
        mibTree.put("1.3.6.1.2.1.1.6.0", SnmpValue.OctetString.of("Lab Room"));
        mibTree.put("1.3.6.1.2.1.1.7.0", new SnmpValue.Integer32(72));

        byte[] engineId = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        UsmEngine agentEngine = new UsmEngine(engineId);
        agent = new SnmpAgent(mibTree, agentEngine);
        agent.start();

        UsmEngine managerEngine = new UsmEngine(new byte[]{0x0A, 0x0B, 0x0C});
        manager = new SnmpManager("127.0.0.1", agent.localPort(), managerEngine,
                3000, 1);
    }

    @AfterEach
    void tearDown() {
        if (manager != null) manager.close();
        if (agent != null) agent.close();
    }

    @Test
    void testGetSingleOid() throws IOException {
        SnmpPdu.Response response = manager.get(ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"));
        assertThat(response.errorStatus()).isEqualTo(0);
        assertThat(response.varBindList().size()).isEqualTo(1);
        assertThat(response.varBindList().get(0).value()).isInstanceOf(SnmpValue.OctetString.class);
        assertThat(((SnmpValue.OctetString) response.varBindList().get(0).value()).asString())
                .isEqualTo("Integration Test Agent");
    }

    @Test
    void testGetMultipleOids() throws IOException {
        SnmpPdu.Response response = manager.get(
                ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"),
                ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0")
        );
        assertThat(response.errorStatus()).isEqualTo(0);
        assertThat(response.varBindList().size()).isEqualTo(2);
    }

    @Test
    void testGetNext() throws IOException {
        SnmpPdu.Response response = manager.getNext(
                ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"));
        assertThat(response.errorStatus()).isEqualTo(0);
        assertThat(response.varBindList().get(0).oid().toDottedString())
                .isEqualTo("1.3.6.1.2.1.1.2.0");
    }

    @Test
    void testGetBulk() throws IOException {
        SnmpPdu.Response response = manager.getBulk(0, 5,
                ObjectIdentifier.parse("1.3.6.1.2.1.1"));
        assertThat(response.errorStatus()).isEqualTo(0);
        assertThat(response.varBindList().size()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void testSet() throws IOException {
        VarBind vb = new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0"),
                SnmpValue.OctetString.of("updated-host"));
        SnmpPdu.Response response = manager.set(vb);
        assertThat(response.errorStatus()).isEqualTo(0);

        // Verify the set worked
        SnmpPdu.Response verify = manager.get(ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0"));
        assertThat(((SnmpValue.OctetString) verify.varBindList().get(0).value()).asString())
                .isEqualTo("updated-host");
    }
}
