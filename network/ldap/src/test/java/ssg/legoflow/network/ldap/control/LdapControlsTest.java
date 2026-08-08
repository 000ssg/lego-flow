package ssg.legoflow.network.ldap.control;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LdapControlsTest {

    @Test
    void testLdapControl() {
        var control = new LdapControl("1.2.3.4", false, new byte[]{0x01, 0x02});
        assertThat(control.oid()).isEqualTo("1.2.3.4");
        assertThat(control.criticality()).isFalse();
    }

    @Test
    void testCriticalControl() {
        var control = new LdapControl("1.2.840.113556.1.4.319", true, null);
        assertThat(control.criticality()).isTrue();
    }

    @Test
    void testPagedResultsRequest() {
        var control = PagedResultsControl.request(100);
        assertThat(control.oid()).isEqualTo(PagedResultsControl.OID);
    }

    @Test
    void testPagedResultsWithCookie() {
        var cookie = new byte[]{0x01, 0x02};
        var control = PagedResultsControl.request(50, cookie);
    }

    @Test
    void testSortKeyAscending() {
        var key = new SortControl.SortKey("cn", null, false);
        assertThat(key.attributeType()).isEqualTo("cn");
    }

    @Test
    void testSortKeyDescending() {
        var key = new SortControl.SortKey("mail", "2.5.13.2", true);
        assertThat(key.reverseOrder()).isTrue();
    }

    @Test
    void testSortControlConstants() {
        assertThat(SortControl.REQUEST_OID).isEqualTo("1.2.840.113556.1.4.473");
        assertThat(SortControl.RESPONSE_OID).isEqualTo("1.2.840.113556.1.4.474");
    }
}
