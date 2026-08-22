package ssg.legoflow.network.snmp.protocol;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.snmp.protocol.SnmpPdu.GetRequest;
import ssg.legoflow.network.snmp.protocol.SecurityLevel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link SnmpMessage} and its builder.
 *
 * @since 0.1.0
 */
class SnmpMessageTest {

    private ScopedPdu createScopedPdu() {
        var vb = VarBindList.builder().addNull("1.3.6.1.2.1").build();
        return new ScopedPdu(new byte[]{}, "", new GetRequest(42, 0, 0, vb));
    }

    @Test
    void testConstants() {
        assertThat(SnmpMessage.VERSION_3).isEqualTo(3);
        assertThat(SnmpMessage.VERSION_2C).isEqualTo(1);
        assertThat(SnmpMessage.VERSION_1).isEqualTo(0);
        assertThat(SnmpMessage.SECURITY_MODEL_USM).isEqualTo(3);
    }

    @Test
    void testBuilderBasic() {
        var scopedPdu = createScopedPdu();
        var msg = SnmpMessage.builder()
                .msgId(123)
                .scopedPdu(scopedPdu)
                .build();
        assertThat(msg.msgId()).isEqualTo(123);
        assertThat(msg.msgVersion()).isEqualTo(SnmpMessage.VERSION_3);
        assertThat(msg.msgMaxSize()).isEqualTo(65507);
        assertThat(msg.msgSecurityModel()).isEqualTo(SnmpMessage.SECURITY_MODEL_USM);
    }

    @Test
    void testBuilderAllFields() {
        var scopedPdu = createScopedPdu();
        var securityParams = new byte[]{1, 2, 3};
        var msg = SnmpMessage.builder()
                .msgVersion(3)
                .msgId(999)
                .msgMaxSize(65535)
                .msgFlags(0x07)
                .msgSecurityModel(3)
                .securityParams(securityParams)
                .scopedPdu(scopedPdu)
                .build();
        assertThat(msg.msgVersion()).isEqualTo(3);
        assertThat(msg.msgId()).isEqualTo(999);
        assertThat(msg.msgMaxSize()).isEqualTo(65535);
        assertThat(msg.msgFlags()).isEqualTo(0x07);
        assertThat(msg.securityParams()).containsExactly(1, 2, 3);
    }

    @Test
    void testBuilderWithSecurityLevel() {
        var scopedPdu = createScopedPdu();
        var msg = SnmpMessage.builder()
                .msgId(42)
                .securityLevel(SecurityLevel.AUTH_PRIV)
                .scopedPdu(scopedPdu)
                .build();
        assertThat(msg.securityLevel()).isEqualTo(SecurityLevel.AUTH_PRIV);
    }

    @Test
    void testBuilderWithReportable() {
        var scopedPdu = createScopedPdu();
        var msgReportable = SnmpMessage.builder()
                .msgId(1)
                .reportable(true)
                .scopedPdu(scopedPdu)
                .build();
        assertThat(msgReportable.isReportable()).isTrue();

        var msgNotReportable = SnmpMessage.builder()
                .msgId(2)
                .reportable(false)
                .scopedPdu(scopedPdu)
                .build();
        assertThat(msgNotReportable.isReportable()).isFalse();
    }

    @Test
    void testBuilderWithSecurityLevelAndReportableCombined() {
        var scopedPdu = createScopedPdu();
        var msg = SnmpMessage.builder()
                .msgId(42)
                .securityLevel(SecurityLevel.AUTH_PRIV)
                .reportable(true)
                .scopedPdu(scopedPdu)
                .build();
        assertThat(msg.securityLevel()).isEqualTo(SecurityLevel.AUTH_PRIV);
        assertThat(msg.isReportable()).isTrue();
        // AUTH_PRIV = 0x03, reportable = 0x04, combined = 0x07
        assertThat(msg.msgFlags()).isEqualTo(0x07);
    }

    @Test
    void testBuilderBuildWithoutScopedPduThrows() {
        assertThatThrownBy(() -> SnmpMessage.builder().msgId(1).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Scoped PDU must be set");
    }

    @Test
    void testConstructorNullSecurityParamsThrows() {
        var vb = VarBindList.builder().addNull("1.3.6.1.2.1").build();
        assertThatThrownBy(() -> new SnmpMessage(
                3, 1, 65507, 0, 3, null, createScopedPdu()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testConstructorNullScopedPduThrows() {
        assertThatThrownBy(() -> new SnmpMessage(
                3, 1, 65507, 0, 3, new byte[0], null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSecurityParamsDefensiveCopy() {
        var securityParams = new byte[]{1, 2, 3};
        var msg = SnmpMessage.builder()
                .securityParams(securityParams)
                .scopedPdu(createScopedPdu())
                .build();
        securityParams[0] = 99;
        assertThat(msg.securityParams()[0]).isEqualTo((byte)1);
    }

    @Test
    void testEqualsAndHashCode() {
        var scopedPdu = createScopedPdu();
        var sp = new byte[]{1, 2};
        var msg1 = SnmpMessage.builder().msgId(42).securityParams(sp).scopedPdu(scopedPdu).build();
        var msg2 = SnmpMessage.builder().msgId(42).securityParams(new byte[]{1, 2}).scopedPdu(scopedPdu).build();
        assertThat(msg1).isEqualTo(msg2);
        assertThat(msg1.hashCode()).isEqualTo(msg2.hashCode());

        var msg3 = SnmpMessage.builder().msgId(99).securityParams(sp).scopedPdu(scopedPdu).build();
        assertThat(msg1).isNotEqualTo(msg3);
    }

    @Test
    void testIsReportable() {
        var scopedPdu = createScopedPdu();
        var msg = SnmpMessage.builder().msgFlags(0x04).scopedPdu(scopedPdu).build();
        assertThat(msg.isReportable()).isTrue();

        var msg2 = SnmpMessage.builder().msgFlags(0x03).scopedPdu(scopedPdu).build();
        assertThat(msg2.isReportable()).isFalse();
    }
}
