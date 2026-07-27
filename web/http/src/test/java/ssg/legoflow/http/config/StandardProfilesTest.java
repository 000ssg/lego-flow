package ssg.legoflow.http.config;

import ssg.legoflow.http.feature.HttpFeatureCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StandardProfilesTest {

    @Test
    void testServerMinimalHasCoreAndBasicCategories() {
        // When
        var set = StandardProfiles.serverMinimal();

        // Then
        assertThat(set.getName()).isEqualTo("SERVER_MINIMAL");
        assertThat(set.getEnabledCategories()).contains(
                HttpFeatureCategory.CORE,
                HttpFeatureCategory.TRANSFER,
                HttpFeatureCategory.CONNECTION);
        assertThat(set.getEnabledCategories()).doesNotContain(
                HttpFeatureCategory.SECURITY,
                HttpFeatureCategory.WEBSOCKET);
    }

    @Test
    void testServerStandardHasContentAndCaching() {
        // When
        var set = StandardProfiles.serverStandard();

        // Then
        assertThat(set.getName()).isEqualTo("SERVER_STANDARD");
        assertThat(set.getEnabledCategories()).contains(
                HttpFeatureCategory.CORE,
                HttpFeatureCategory.TRANSFER,
                HttpFeatureCategory.CONTENT,
                HttpFeatureCategory.CACHING,
                HttpFeatureCategory.CONNECTION,
                HttpFeatureCategory.ENTITY,
                HttpFeatureCategory.METADATA);
    }

    @Test
    void testServerFullHasAllCategories() {
        // When
        var set = StandardProfiles.serverFull();

        // Then
        assertThat(set.getName()).isEqualTo("SERVER_FULL");
        assertThat(set.getEnabledCategories()).contains(
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
    }

    @Test
    void testServerMinimalFeatureCount() {
        // When
        var set = StandardProfiles.serverMinimal();

        // Then - core + fixed-length-transfer + connection-basic
        assertThat(set.getFeatures()).hasSize(3);
    }

    @Test
    void testServerFullFeatureCount() {
        // When
        var set = StandardProfiles.serverFull();

        // Then - core + 9 features
        assertThat(set.getFeatures()).hasSize(10);
    }

    @Test
    void testCoreFeatureIsCore() {
        // When
        var set = StandardProfiles.serverMinimal();

        // Then
        assertThat(set.getFeature("core")).isNotNull();
        assertThat(set.getFeature("core").isCore()).isTrue();
    }

    @Test
    void testClientMinimalProfile() {
        // When
        var set = StandardProfiles.clientMinimal();

        // Then
        assertThat(set.getName()).isEqualTo("CLIENT_MINIMAL");
        assertThat(set.getEnabledCategories()).contains(
                HttpFeatureCategory.CORE,
                HttpFeatureCategory.TRANSFER);
    }

    @Test
    void testClientFullProfile() {
        // When
        var set = StandardProfiles.clientFull();

        // Then
        assertThat(set.getName()).isEqualTo("CLIENT_FULL");
        assertThat(set.getEnabledCategories()).contains(
                HttpFeatureCategory.SECURITY,
                HttpFeatureCategory.WEBSOCKET);
    }
}
