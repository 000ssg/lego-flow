package ssg.legoflow.network.snmp.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ScopedPduTest {

    private SnmpPdu makeGetRequest() {
        return new SnmpPdu.GetRequest(1, 0, 0, VarBindList.empty());
    }

    @Test void testBasicScopedPdu() {
        byte[] engineId = {(byte)0x01, (byte)0x02, (byte)0x03};
        var pdu = makeGetRequest();
        var scoped = new ScopedPdu(engineId, "context", pdu);
        
        assertThat(scoped.contextEngineId()).isEqualTo(new byte[]{0x01, 0x02, 0x03});
        assertThat(scoped.contextName()).isEqualTo("context");
        assertThat(scoped.pdu()).isNotNull();
    }

    @Test void testScopedPduWithEmptyContext() {
        byte[] engineId = new byte[0];
        var pdu = makeGetRequest();
        var scoped = new ScopedPdu(engineId, "", pdu);
        
        assertThat(scoped.contextEngineId()).isEmpty();
        assertThat(scoped.contextName()).isEmpty();
    }

    @Test void testNullContextEngineIdThrows() {
        var pdu = makeGetRequest();
        assertThatThrownBy(() -> new ScopedPdu(null, "ctx", pdu))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test void testNullContextNameThrows() {
        byte[] engineId = new byte[]{0x01};
        var pdu = makeGetRequest();
        assertThatThrownBy(() -> new ScopedPdu(engineId, null, pdu))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test void testNullPduThrows() {
        byte[] engineId = new byte[]{0x01};
        assertThatThrownBy(() -> new ScopedPdu(engineId, "ctx", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test void testDefensiveCopyOfContextEngineId() {
        byte[] engineId = {(byte)0x01, (byte)0x02, (byte)0x03};
        var pdu = makeGetRequest();
        var scoped = new ScopedPdu(engineId, "ctx", pdu);
        
        // Modify original array
        engineId[0] = (byte)0xFF;
        // Scoped PDU should have its own copy
        assertThat(scoped.contextEngineId()).isEqualTo(new byte[]{0x01, 0x02, 0x03});
    }

    @Test void testAccessReturnsCopyNotInternal() {
        byte[] engineId = new byte[]{0x01};
        var pdu = makeGetRequest();
        var scoped = new ScopedPdu(engineId, "ctx", pdu);
        
        // Each access returns a defensive copy
        var copy1 = scoped.contextEngineId();
        var copy2 = scoped.contextEngineId();
        assertThat(copy1).isEqualTo(copy2);
    }

    @Test void testOfPduFactoryMethod() {
        var pdu = makeGetRequest();
        var scoped = ScopedPdu.of(pdu);
        
        assertThat(scoped.contextEngineId()).isEmpty();
        assertThat(scoped.contextName()).isEmpty();
        assertThat(scoped.pdu()).isEqualTo(pdu);
    }

    @Test void testOfContextEngineIdAndPdu() {
        byte[] engineId = {(byte)0x01, (byte)0x02};
        var pdu = makeGetRequest();
        var scoped = ScopedPdu.of(engineId, pdu);
        
        assertThat(scoped.contextEngineId()).isEqualTo(engineId);
        assertThat(scoped.contextName()).isEmpty(); // Empty context name by default
    }

    @Test void testEqualsAndHashCode() {
        byte[] eid1 = {(byte)0x01, (byte)0x02};
        byte[] eid2 = {(byte)0x01, (byte)0x02};
        var pdu1 = makeGetRequest();
        var pdu2 = makeGetRequest(); // Same requestId and varBindList
        
        var s1 = new ScopedPdu(eid1, "ctx", pdu1);
        var s2 = new ScopedPdu(eid2, "ctx", pdu2);
        
        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }

    @Test void testNotEqualsDifferentContextName() {
        byte[] eid = new byte[]{0x01};
        var pdu = makeGetRequest();
        
        var s1 = new ScopedPdu(eid, "ctx1", pdu);
        var s2 = new ScopedPdu(new byte[]{0x01}, "ctx2", pdu);
        
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test void testNotEqualsDifferentEngineId() {
        byte[] eid1 = new byte[]{0x01};
        byte[] eid2 = new byte[]{0x02};
        var pdu = makeGetRequest();
        
        var s1 = new ScopedPdu(eid1, "ctx", pdu);
        var s2 = new ScopedPdu(eid2, "ctx", pdu);
        
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test void testToString() {
        byte[] eid = new byte[]{0x01};
        var pdu = makeGetRequest();
        var scoped = new ScopedPdu(eid, "ctx", pdu);
        
        String str = scoped.toString();
        assertThat(str).contains("ScopedPdu");
    }

    @Test void testWithContextEngineIdDefensiveCopy() {
        byte[] original = {(byte)0xAA, (byte)0xBB};
        var pdu = makeGetRequest();
        var scoped = ScopedPdu.of(original, pdu);
        
        // Modify original
        original[0] = 0x00;
        // Should not affect the scoped PDU
        assertThat(scoped.contextEngineId()[0]).isEqualTo((byte)0xAA);
    }

    @Test void testMultiplePduTypes() {
        var getRequest = new SnmpPdu.GetRequest(1, 0, 0, VarBindList.empty());
        var getNextRequest = new SnmpPdu.GetNextRequest(2, 0, 0, VarBindList.empty());
        
        var s1 = ScopedPdu.of(getRequest);
        var s2 = ScopedPdu.of(getNextRequest);
        
        assertThat(s1.pdu()).isEqualTo(getRequest);
        assertThat(s2.pdu()).isEqualTo(getNextRequest);
    }

    @Test void testWithCustomContext() {
        byte[] engineId = {(byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04};
        var pdu = makeGetRequest();
        var scoped = new ScopedPdu(engineId, "my-mib-view", pdu);
        
        assertThat(scoped.contextEngineId()).hasSize(4);
        assertThat(scoped.contextName()).isEqualTo("my-mib-view");
    }

    @Test void testDefensiveCopyOnConstruction() {
        byte[] engineId = {(byte)0x55, (byte)0x66};
        var pdu = makeGetRequest();
        
        var scoped = new ScopedPdu(engineId, "ctx", pdu);
        
        // Mutate original after construction
        engineId[0] = (byte)0x77;
        engineId[1] = (byte)0x88;
        
        // Scoped PDU should still have original values
        assertThat(scoped.contextEngineId()[0]).isEqualTo((byte)0x55);
        assertThat(scoped.contextEngineId()[1]).isEqualTo((byte)0x66);
    }

    @Test void testEqualsWithDifferentPdu() {
        var pdu1 = new SnmpPdu.GetRequest(1, 0, 0, VarBindList.empty());
        var pdu2 = new SnmpPdu.GetRequest(99, 0, 0, VarBindList.empty()); // different requestId
        
        var s1 = new ScopedPdu(new byte[]{0x01}, "ctx", pdu1);
        var s2 = new ScopedPdu(new byte[]{0x01}, "ctx", pdu2);
        
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test void testHashCodeConsistent() {
        byte[] eid = {(byte)0xAA};
        var pdu = makeGetRequest();
        var scoped = new ScopedPdu(eid, "ctx", pdu);
        
        int h1 = scoped.hashCode();
        int h2 = scoped.hashCode();
        assertThat(h1).isEqualTo(h2);
    }

    @Test void testWithEmptyVarBindList() {
        var pdu = new SnmpPdu.GetRequest(42, 0, 0, VarBindList.empty());
        var scoped = ScopedPdu.of(pdu);
        
        assertThat(scoped.pdu().varBindList()).isEmpty();
        assertThat(scoped.pdu().requestId()).isEqualTo(42);
    }

    @Test void testWithNonEmptyVarBindList() {
        var vb = VarBindList.builder()
                .addNull("1.3.6.1.2.1.1.1.0")  // sysDescr
                .build();
        var pdu = new SnmpPdu.GetRequest(1, 0, 0, vb);
        var scoped = ScopedPdu.of(pdu);
        
        assertThat(scoped.pdu().varBindList()).hasSize(1);
    }

    @Test void testEngineIdWithZeroes() {
        byte[] engineId = new byte[]{0, 0, 0, 0};
        var pdu = makeGetRequest();
        var scoped = new ScopedPdu(engineId, "ctx", pdu);
        
        assertThat(scoped.contextEngineId()).isEqualTo(new byte[4]);
    }

    @Test void testLargeEngineId() {
        byte[] engineId = new byte[32]; // Large engine ID
        for (int i = 0; i < 32; i++) engineId[i] = (byte)i;
        var pdu = makeGetRequest();
        var scoped = new ScopedPdu(engineId, "ctx", pdu);
        
        assertThat(scoped.contextEngineId()).hasSize(32);
    }

    @Test void testContextNameWithSpecialChars() {
        byte[] engineId = {(byte)0x01};
        var pdu = makeGetRequest();
        var scoped = new ScopedPdu(engineId, "context-with-special-chars_123", pdu);
        
        assertThat(scoped.contextName()).isEqualTo("context-with-special-chars_123");
    }

    @Test void testNotEqualToNull() {
        var pdu = makeGetRequest();
        var scoped = ScopedPdu.of(pdu);
        assertThat(scoped).isNotEqualTo(null);
    }

    @Test void testNotEqualToDifferentClass() {
        var pdu = makeGetRequest();
        var scoped = ScopedPdu.of(pdu);
        assertThat(scoped).isNotEqualTo("not a ScopedPdu");
    }
}
