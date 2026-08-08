package ssg.legoflow.http.demo;

import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.feature.HttpFeatureCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Demonstrates StandardProfiles and verifies that each profile level
 * includes the expected feature categories. Profiles progress from
 * minimal (core only) through standard to full.
 */
class FeatureProfileDemoTest {

    @Test
    void testServerMinimalProfile() {
        // Given: the minimal server profile
        var profile = StandardProfiles.serverMinimal();

        // Then: includes only core, transfer, and connection categories
        assertThat(profile.getName()).isEqualTo("SERVER_MINIMAL");
        assertThat(profile.getEnabledCategories())
                .containsExactlyInAnyOrder(
                        HttpFeatureCategory.CORE,
                        HttpFeatureCategory.TRANSFER,
                        HttpFeatureCategory.CONNECTION);
        assertThat(profile.isEnabled("core")).isTrue();
        assertThat(profile.isEnabled("caching")).isFalse();
        assertThat(profile.isEnabled("websocket")).isFalse();
    }

    @Test
    void testServerStandardProfile() {
        // Given: the standard server profile
        var profile = StandardProfiles.serverStandard();

        // Then: includes core, transfer, content, caching, connection, entity, metadata
        assertThat(profile.getName()).isEqualTo("SERVER_STANDARD");
        assertThat(profile.getEnabledCategories())
                .contains(
                        HttpFeatureCategory.CORE,
                        HttpFeatureCategory.TRANSFER,
                        HttpFeatureCategory.CONTENT,
                        HttpFeatureCategory.CACHING,
                        HttpFeatureCategory.CONNECTION,
                        HttpFeatureCategory.ENTITY,
                        HttpFeatureCategory.METADATA);
        assertThat(profile.getEnabledCategories())
                .doesNotContain(HttpFeatureCategory.SECURITY, HttpFeatureCategory.WEBSOCKET);
    }

    @Test
    void testServerFullProfile() {
        // Given: the full server profile
        var profile = StandardProfiles.serverFull();

        // Then: includes all categories
        assertThat(profile.getName()).isEqualTo("SERVER_FULL");
        assertThat(profile.getEnabledCategories())
                .containsExactlyInAnyOrder(
                        HttpFeatureCategory.CORE,
                        HttpFeatureCategory.TRANSFER,
                        HttpFeatureCategory.CONTENT,
                        HttpFeatureCategory.CACHING,
                        HttpFeatureCategory.CONNECTION,
                        HttpFeatureCategory.ENTITY,
                        HttpFeatureCategory.METADATA,
                        HttpFeatureCategory.SECURITY,
                        HttpFeatureCategory.WEBSOCKET,
                        HttpFeatureCategory.STATIC);
        assertThat(profile.getFeatures()).hasSize(10);
    }

    @Test
    void testClientMinimalProfile() {
        // Given: the minimal client profile
        var profile = StandardProfiles.clientMinimal();

        // Then: includes only core and transfer
        assertThat(profile.getName()).isEqualTo("CLIENT_MINIMAL");
        assertThat(profile.getEnabledCategories())
                .containsExactlyInAnyOrder(
                        HttpFeatureCategory.CORE,
                        HttpFeatureCategory.TRANSFER);
        assertThat(profile.getFeatures()).hasSize(2);
    }

    @Test
    void testClientFullProfile() {
        // Given: the full client profile
        var profile = StandardProfiles.clientFull();

        // Then: includes all client categories (no STATIC or CACHING)
        assertThat(profile.getName()).isEqualTo("CLIENT_FULL");
        assertThat(profile.getEnabledCategories())
                .contains(
                        HttpFeatureCategory.CORE,
                        HttpFeatureCategory.TRANSFER,
                        HttpFeatureCategory.CONTENT,
                        HttpFeatureCategory.CONNECTION,
                        HttpFeatureCategory.ENTITY,
                        HttpFeatureCategory.METADATA,
                        HttpFeatureCategory.SECURITY,
                        HttpFeatureCategory.WEBSOCKET);
        assertThat(profile.getEnabledCategories())
                .doesNotContain(HttpFeatureCategory.STATIC, HttpFeatureCategory.CACHING);
    }

    @Test
    void testCoreFeatureCannotBeRemoved() {
        // Given: a profile with the core feature
        var profile = StandardProfiles.serverMinimal();
        assertThat(profile.isEnabled("core")).isTrue();

        // When: attempting to remove the core feature
        profile.remove("core");

        // Then: core feature remains (it is marked as core=true)
        assertThat(profile.isEnabled("core")).isTrue();
    }
}
