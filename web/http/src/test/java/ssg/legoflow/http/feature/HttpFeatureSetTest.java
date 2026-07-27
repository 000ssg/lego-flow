package ssg.legoflow.http.feature;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class HttpFeatureSetTest {

    @Test
    void testAddFeature() {
        // Given
        var set = new HttpFeatureSet("test");
        var feature = createFeature("transfer", HttpFeatureCategory.TRANSFER, false);

        // When
        set.add(feature);

        // Then
        assertThat(set.isEnabled("transfer")).isTrue();
        assertThat(set.getFeature("transfer")).isNotNull();
    }

    @Test
    void testRemoveFeature() {
        // Given
        var set = new HttpFeatureSet("test");
        set.add(createFeature("transfer", HttpFeatureCategory.TRANSFER, false));

        // When
        set.remove("transfer");

        // Then
        assertThat(set.isEnabled("transfer")).isFalse();
    }

    @Test
    void testRemoveCoreFeatureIsIgnored() {
        // Given
        var set = new HttpFeatureSet("test");
        set.add(createFeature("core", HttpFeatureCategory.CORE, true));

        // When
        set.remove("core");

        // Then
        assertThat(set.isEnabled("core")).isTrue();
    }

    @Test
    void testGetFeatures() {
        // Given
        var set = new HttpFeatureSet("test");
        set.add(createFeature("core", HttpFeatureCategory.CORE, true));
        set.add(createFeature("transfer", HttpFeatureCategory.TRANSFER, false));

        // Then
        assertThat(set.getFeatures()).hasSize(2);
    }

    @Test
    void testGetEnabledCategories() {
        // Given
        var set = new HttpFeatureSet("test");
        set.add(createFeature("core", HttpFeatureCategory.CORE, true));
        set.add(createFeature("transfer", HttpFeatureCategory.TRANSFER, false));
        set.add(createFeature("caching", HttpFeatureCategory.CACHING, false));

        // Then
        assertThat(set.getEnabledCategories()).containsExactlyInAnyOrder(
                HttpFeatureCategory.CORE, HttpFeatureCategory.TRANSFER, HttpFeatureCategory.CACHING);
    }

    @Test
    void testGetName() {
        // Given
        var set = new HttpFeatureSet("MY_PROFILE");

        // Then
        assertThat(set.getName()).isEqualTo("MY_PROFILE");
    }

    @Test
    void testIsEnabledReturnsFalseForMissing() {
        // Given
        var set = new HttpFeatureSet("test");

        // Then
        assertThat(set.isEnabled("nonexistent")).isFalse();
    }

    @Test
    void testInstallAll() {
        // Given
        var set = new HttpFeatureSet("test");
        var feature = createFeature("transfer", HttpFeatureCategory.TRANSFER, false);
        set.add(feature);
        var registry = new HttpFeatureRegistry();

        // When
        set.installAll(registry);

        // Then
        assertThat(registry.isEnabled("transfer")).isTrue();
    }

    private HttpFeature createFeature(String name, HttpFeatureCategory category, boolean core) {
        return new HttpFeature() {
            @Override public String getName() { return name; }
            @Override public HttpFeatureCategory getCategory() { return category; }
            @Override public boolean isCore() { return core; }
            @Override public void configure(Map<String, Object> params) {}
            @Override public void install(HttpFeatureRegistry registry) { registry.register(this); }
        };
    }
}
