package ssg.legoflow.network.snmp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import ssg.legoflow.network.snmp.protocol.SecurityLevel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link VacmAccessControl} access control.
 *
 * @since 0.1.0
 */
class VacmAccessControlTest {

    private VacmAccessControl vacm;

    @BeforeEach
    void setUp() {
        vacm = new VacmAccessControl();
    }

    @Test
    void testSecurityToGroupMapping() {
        vacm.addSecurityToGroup(3, "admin", "adminGroup");
        assertThat(vacm.getGroup(3, "admin")).isEqualTo("adminGroup");
    }

    @Test
    void testSecurityToGroupNotFound() {
        assertThat(vacm.getGroup(3, "unknown")).isNull();
    }

    @Test
    void testAccessTableLookup() {
        vacm.addAccess("admins", "", 3, SecurityLevel.AUTH_PRIV,
                "fullView", "fullView", "fullView");

        String viewName = vacm.getViewName("admins", "", 3,
                SecurityLevel.AUTH_PRIV, VacmAccessControl.AccessType.READ);
        assertThat(viewName).isEqualTo("fullView");
    }

    @Test
    void testAccessTableWriteView() {
        vacm.addAccess("operators", "", 3, SecurityLevel.AUTH_NO_PRIV,
                "readView", "writeView", "notifyView");

        assertThat(vacm.getViewName("operators", "", 3,
                SecurityLevel.AUTH_NO_PRIV, VacmAccessControl.AccessType.WRITE))
                .isEqualTo("writeView");
    }

    @Test
    void testAccessTableNotifyView() {
        vacm.addAccess("monitors", "", 3, SecurityLevel.NO_AUTH_NO_PRIV,
                "readView", "", "notifyView");

        assertThat(vacm.getViewName("monitors", "", 3,
                SecurityLevel.NO_AUTH_NO_PRIV, VacmAccessControl.AccessType.NOTIFY))
                .isEqualTo("notifyView");
    }

    @Test
    void testViewInclusionNoMask() {
        ObjectIdentifier subtree = ObjectIdentifier.parse("1.3.6.1.2.1");
        vacm.addView("fullView", subtree, null, true);

        assertThat(vacm.isInView("fullView", ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0")))
                .isTrue();
        assertThat(vacm.isInView("fullView", ObjectIdentifier.parse("1.3.6.1.4.1.99")))
                .isFalse();
    }

    @Test
    void testViewExclusion() {
        ObjectIdentifier mib2 = ObjectIdentifier.parse("1.3.6.1.2.1");
        ObjectIdentifier snmpGroup = ObjectIdentifier.parse("1.3.6.1.2.1.11");

        vacm.addView("restrictedView", mib2, null, true);
        vacm.addView("restrictedView", snmpGroup, null, false); // exclude

        assertThat(vacm.isInView("restrictedView", ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0")))
                .isTrue();
        assertThat(vacm.isInView("restrictedView", ObjectIdentifier.parse("1.3.6.1.2.1.11.1.0")))
                .isFalse();
    }

    @Test
    void testViewWithMask() {
        // Mask = 0xFF 0xE0 = 11111111 11100000
        // Matches first 11 arcs exactly
        ObjectIdentifier subtree = ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0");
        byte[] mask = new byte[]{(byte) 0xFF, (byte) 0xE0};
        vacm.addView("maskedView", subtree, mask, true);

        assertThat(vacm.isInView("maskedView",
                ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0"))).isTrue();
    }

    @Test
    void testNonExistentViewReturnsNotInView() {
        assertThat(vacm.isInView("noSuchView", ObjectIdentifier.parse("1.3.6.1"))).isFalse();
    }

    @Test
    void testIsAccessAllowed() {
        vacm.addSecurityToGroup(3, "admin", "adminGroup");
        vacm.addAccess("adminGroup", "", 3, SecurityLevel.AUTH_PRIV,
                "fullView", "fullView", "fullView");
        vacm.addView("fullView", ObjectIdentifier.parse("1.3.6.1"), null, true);

        assertThat(vacm.isAccessAllowed(3, "admin", SecurityLevel.AUTH_PRIV, "",
                VacmAccessControl.AccessType.READ, ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0")))
                .isTrue();
    }

    @Test
    void testIsAccessDeniedForUnknownUser() {
        assertThat(vacm.isAccessAllowed(3, "unknown", SecurityLevel.NO_AUTH_NO_PRIV, "",
                VacmAccessControl.AccessType.READ, ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0")))
                .isFalse();
    }

    @Test
    void testIsAccessDeniedForEmptyViewName() {
        vacm.addSecurityToGroup(3, "readonly", "readGroup");
        vacm.addAccess("readGroup", "", 3, SecurityLevel.AUTH_NO_PRIV,
                "readView", "", ""); // No write or notify

        vacm.addView("readView", ObjectIdentifier.parse("1.3.6.1"), null, true);

        assertThat(vacm.isAccessAllowed(3, "readonly", SecurityLevel.AUTH_NO_PRIV, "",
                VacmAccessControl.AccessType.READ, ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0")))
                .isTrue();
        assertThat(vacm.isAccessAllowed(3, "readonly", SecurityLevel.AUTH_NO_PRIV, "",
                VacmAccessControl.AccessType.WRITE, ObjectIdentifier.parse("1.3.6.1.2.1.1.1.0")))
                .isFalse();
    }
}
