package ssg.legoflow.network.ldap.control;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.ldap.control.LdapControl;

import static org.assertj.core.api.Assertions.assertThat;

class SortControlTest {

    @Test
    void testSortKeyAscending() {
        var key = SortControl.SortKey.ascending("cn");
        assertThat(key.attributeType()).isEqualTo("cn");
        assertThat(key.orderingRule()).isNull();
        assertThat(key.reverseOrder()).isFalse();
    }

    @Test
    void testSortKeyDescending() {
        var key = SortControl.SortKey.descending("sn");
        assertThat(key.attributeType()).isEqualTo("sn");
        assertThat(key.reverseOrder()).isTrue();
    }

    @Test
    void testSortKeyWithOrderingRule() {
        var key = new SortControl.SortKey("mail", "caseIgnoreMatch", false);
        assertThat(key.orderingRule()).isEqualTo("caseIgnoreMatch");
    }

    @Test
    void testRequestSingleAscending() {
        var control = SortControl.request(false,
                SortControl.SortKey.ascending("cn"));
        assertThat(control.oid()).isEqualTo(SortControl.REQUEST_OID);
        assertThat(control.criticality()).isFalse();
        assertThat(control.value()).isNotNull();
    }

    @Test
    void testRequestMultipleKeys() {
        var control = SortControl.request(true,
                SortControl.SortKey.descending("sn"),
                SortControl.SortKey.ascending("givenName"));
        assertThat(control.criticality()).isTrue();
        assertThat(control.value().length).isGreaterThan(0);
    }

    @Test
    void testRequestWithOrderingRule() {
        var key = new SortControl.SortKey("userPassword", "2.5.13.16", true);
        var control = SortControl.request(false, key);
        assertThat(control.value().length).isGreaterThan(0);
    }

    @Test
    void testOidConstants() {
        assertThat(SortControl.REQUEST_OID).isEqualTo("1.2.840.113556.1.4.473");
        assertThat(SortControl.RESPONSE_OID).isEqualTo("1.2.840.113556.1.4.474");
    }

    @Test
    void testRequestProducesNonEmptyValue() {
        var control = SortControl.request(true,
                SortControl.SortKey.ascending("displayName"));
        assertThat(control.value()).isNotEmpty();
    }
}
