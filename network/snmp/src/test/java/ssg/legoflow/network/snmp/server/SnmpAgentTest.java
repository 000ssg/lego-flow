package ssg.legoflow.network.snmp.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import ssg.legoflow.network.snmp.protocol.*;
import ssg.legoflow.network.snmp.security.UsmEngine;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SnmpAgent} request processing.
 *
 * @since 0.1.0
 */
class SnmpAgentTest {

    private MibTree mibTree;
    private SnmpAgent agent;

    @BeforeEach
    void setUp() throws IOException {
        mibTree = new MibTree();
        mibTree.put("1.3.6.1.2.1.1.1.0", SnmpValue.OctetString.of("Test Agent"));
        mibTree.put("1.3.6.1.2.1.1.3.0", new SnmpValue.TimeTicks(5000));
        mibTree.put("1.3.6.1.2.1.1.5.0", SnmpValue.OctetString.of("test-host"));
        mibTree.put("1.3.6.1.2.1.1.7.0", new SnmpValue.Integer32(72));

        UsmEngine engine = new UsmEngine(new byte[]{0x01, 0x02, 0x03});
        agent = new SnmpAgent(mibTree, engine);
    }

    @AfterEach
    void tearDown() {
        agent.close();
    }

    @Test
    void testProcessGetRequest() {
        VarBindList vbl = VarBindList.of(VarBind.ofNull("1.3.6.1.2.1.1.1.0"));
        SnmpPdu request = new SnmpPdu.GetRequest(1, 0, 0, vbl);
        SnmpPdu result = agent.processRequest(request);

        assertThat(result).isInstanceOf(SnmpPdu.Response.class);
        SnmpPdu.Response resp = (SnmpPdu.Response) result;
        assertThat(resp.requestId()).isEqualTo(1);
        assertThat(resp.errorStatus()).isEqualTo(0);
        assertThat(resp.varBindList().size()).isEqualTo(1);
        assertThat(resp.varBindList().get(0).value()).isInstanceOf(SnmpValue.OctetString.class);
    }

    @Test
    void testProcessGetRequestNotFound() {
        VarBindList vbl = VarBindList.of(VarBind.ofNull("1.3.6.1.2.1.99.0"));
        SnmpPdu request = new SnmpPdu.GetRequest(2, 0, 0, vbl);
        SnmpPdu.Response resp = (SnmpPdu.Response) agent.processRequest(request);

        assertThat(resp.varBindList().get(0).value()).isInstanceOf(SnmpValue.NoSuchObject.class);
    }

    @Test
    void testProcessGetNextRequest() {
        VarBindList vbl = VarBindList.of(VarBind.ofNull("1.3.6.1.2.1.1.1.0"));
        SnmpPdu request = new SnmpPdu.GetNextRequest(3, 0, 0, vbl);
        SnmpPdu.Response resp = (SnmpPdu.Response) agent.processRequest(request);

        assertThat(resp.errorStatus()).isEqualTo(0);
        assertThat(resp.varBindList().get(0).oid().toDottedString())
                .isEqualTo("1.3.6.1.2.1.1.3.0");
    }

    @Test
    void testProcessGetNextEndOfMib() {
        VarBindList vbl = VarBindList.of(VarBind.ofNull("1.3.6.1.2.1.1.7.0"));
        SnmpPdu request = new SnmpPdu.GetNextRequest(4, 0, 0, vbl);
        SnmpPdu.Response resp = (SnmpPdu.Response) agent.processRequest(request);

        assertThat(resp.varBindList().get(0).value()).isInstanceOf(SnmpValue.EndOfMibView.class);
    }

    @Test
    void testProcessGetBulkRequest() {
        VarBindList vbl = VarBindList.of(VarBind.ofNull("1.3.6.1.2.1.1"));
        SnmpPdu request = new SnmpPdu.GetBulkRequest(5, 0, 10, vbl);
        SnmpPdu.Response resp = (SnmpPdu.Response) agent.processRequest(request);

        assertThat(resp.errorStatus()).isEqualTo(0);
        assertThat(resp.varBindList().size()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void testProcessSetRequest() {
        VarBindList vbl = VarBindList.of(
                new VarBind(ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0"),
                        SnmpValue.OctetString.of("new-name"))
        );
        SnmpPdu request = new SnmpPdu.SetRequest(6, 0, 0, vbl);
        SnmpPdu.Response resp = (SnmpPdu.Response) agent.processRequest(request);

        assertThat(resp.errorStatus()).isEqualTo(0);
        assertThat(((SnmpValue.OctetString) mibTree.get(
                ObjectIdentifier.parse("1.3.6.1.2.1.1.5.0"))).asString())
                .isEqualTo("new-name");
    }

    @Test
    void testProcessInformRequest() {
        VarBindList vbl = VarBindList.of(
                new VarBind(SnmpOids.SYS_UP_TIME, new SnmpValue.TimeTicks(1000))
        );
        SnmpPdu request = new SnmpPdu.InformRequest(7, 0, 0, vbl);
        SnmpPdu.Response resp = (SnmpPdu.Response) agent.processRequest(request);

        assertThat(resp.errorStatus()).isEqualTo(0);
        assertThat(resp.requestId()).isEqualTo(7);
    }

    @Test
    void testProcessMultipleVarBindsGet() {
        VarBindList vbl = VarBindList.of(
                VarBind.ofNull("1.3.6.1.2.1.1.1.0"),
                VarBind.ofNull("1.3.6.1.2.1.1.3.0"),
                VarBind.ofNull("1.3.6.1.2.1.1.5.0")
        );
        SnmpPdu request = new SnmpPdu.GetRequest(8, 0, 0, vbl);
        SnmpPdu.Response resp = (SnmpPdu.Response) agent.processRequest(request);

        assertThat(resp.varBindList().size()).isEqualTo(3);
    }

    @Test
    void testAgentLocalPort() {
        assertThat(agent.localPort()).isGreaterThan(0);
    }

    @Test
    void testMibTreeAccessor() {
        assertThat(agent.mibTree()).isSameAs(mibTree);
    }
}
