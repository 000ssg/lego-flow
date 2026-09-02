package ssg.legoflow.network.ldap.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive LDAP demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code LdapServer}. To test against
 * an external OpenLDAP/Active Directory/389DS, set {@code DemoLdapAll.USE_EXTERNAL = true}
 * and configure host/port before running.</p>
 */
class DemoLdapAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoLdapAll.runAll();

        assertThat(results.simpleBind())
                .as("Simple bind authentication")
                .isTrue();

        assertThat(results.searchBase())
                .as("Base scope search returns single entry")
                .isEqualTo(1);

        assertThat(results.searchOneLevel())
                .as("One-level search returns children")
                .isGreaterThanOrEqualTo(3);

        assertThat(results.searchSubtree())
                .as("Subtree search finds entries with mail attribute")
                .isGreaterThanOrEqualTo(3);

        assertThat(results.addEntries())
                .as("Add entry and verify")
                .isTrue();

        assertThat(results.modifyEntries())
                .as("Modify entry attributes")
                .isTrue();

        assertThat(results.deleteEntries())
                .as("Delete entries and verify removal")
                .isTrue();

        assertThat(results.compareOp())
                .as("Compare operation returns correct true/false")
                .isTrue();

        assertThat(results.modifyDn())
                .as("Modify DN renames entry")
                .isTrue();
    }
}
