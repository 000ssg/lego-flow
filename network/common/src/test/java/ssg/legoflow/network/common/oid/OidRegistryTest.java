package ssg.legoflow.network.common.oid;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link OidRegistry}.
 */
class OidRegistryTest {

    @Test
    void testLookupByOid() {
        assertThat(OidRegistry.instance().nameOf(StandardOids.SYS_DESCR))
                .hasValue("sysDescr");
    }

    @Test
    void testLookupByName() {
        assertThat(OidRegistry.instance().oidOf("sysDescr"))
                .hasValue(StandardOids.SYS_DESCR);
    }

    @Test
    void testLookupUnknown() {
        ObjectIdentifier unknown = ObjectIdentifier.parse("1.3.6.1.99.99.99");
        assertThat(OidRegistry.instance().nameOf(unknown)).isEmpty();
    }

    @Test
    void testDisplayNameKnown() {
        String display = OidRegistry.instance().displayName(StandardOids.ID_AT_COMMON_NAME);
        assertThat(display).contains("id-at-commonName").contains("2.5.4.3");
    }

    @Test
    void testDisplayNameUnknown() {
        ObjectIdentifier unknown = ObjectIdentifier.parse("1.3.6.1.99.99.99");
        String display = OidRegistry.instance().displayName(unknown);
        assertThat(display).isEqualTo("1.3.6.1.99.99.99");
    }

    @Test
    void testRegisterCustom() {
        ObjectIdentifier custom = ObjectIdentifier.parse("1.3.6.1.4.1.99999.1");
        OidRegistry.instance().register(custom, "myCustomOid");
        assertThat(OidRegistry.instance().nameOf(custom)).hasValue("myCustomOid");
        assertThat(OidRegistry.instance().oidOf("myCustomOid")).hasValue(custom);
    }

    @Test
    void testSizePositive() {
        assertThat(OidRegistry.instance().size()).isGreaterThan(20);
    }

    @Test
    void testStandardOidsRegistered() {
        assertThat(OidRegistry.instance().nameOf(StandardOids.INTERNET)).hasValue("internet");
        assertThat(OidRegistry.instance().nameOf(StandardOids.MIB_2)).hasValue("mib-2");
        assertThat(OidRegistry.instance().nameOf(StandardOids.ENTERPRISES)).hasValue("enterprises");
        assertThat(OidRegistry.instance().nameOf(StandardOids.RSA_ENCRYPTION)).hasValue("rsaEncryption");
        assertThat(OidRegistry.instance().nameOf(StandardOids.LDAP_START_TLS)).hasValue("startTLS");
    }
}
