package ssg.legoflow.http.feature;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class HttpFeatureRegistryTest {

    @Test
    void testRegisterAndGet() {
        // Given
        var registry = new HttpFeatureRegistry();
        var feature = createFeature("transfer", HttpFeatureCategory.TRANSFER, false);

        // When
        registry.register(feature);

        // Then
        assertThat(registry.getFeature("transfer")).isNotNull();
        assertThat(registry.isEnabled("transfer")).isTrue();
    }

    @Test
    void testUnregister() {
        // Given
        var registry = new HttpFeatureRegistry();
        registry.register(createFeature("transfer", HttpFeatureCategory.TRANSFER, false));

        // When
        registry.unregister("transfer");

        // Then
        assertThat(registry.isEnabled("transfer")).isFalse();
    }

    @Test
    void testUnregisterCoreFeatureIsIgnored() {
        // Given
        var registry = new HttpFeatureRegistry();
        registry.register(createFeature("core", HttpFeatureCategory.CORE, true));

        // When
        registry.unregister("core");

        // Then
        assertThat(registry.isEnabled("core")).isTrue();
    }

    @Test
    void testGetByCategory() {
        // Given
        var registry = new HttpFeatureRegistry();
        registry.register(createFeature("core", HttpFeatureCategory.CORE, true));
        registry.register(createFeature("transfer", HttpFeatureCategory.TRANSFER, false));
        registry.register(createFeature("chunked", HttpFeatureCategory.TRANSFER, false));

        // When
        var transferFeatures = registry.getByCategory(HttpFeatureCategory.TRANSFER);

        // Then
        assertThat(transferFeatures).hasSize(2);
        assertThat(transferFeatures).extracting(HttpFeature::getName)
                .containsExactlyInAnyOrder("transfer", "chunked");
    }

    @Test
    void testGetFeatureNames() {
        // Given
        var registry = new HttpFeatureRegistry();
        registry.register(createFeature("core", HttpFeatureCategory.CORE, true));
        registry.register(createFeature("transfer", HttpFeatureCategory.TRANSFER, false));

        // Then
        assertThat(registry.getFeatureNames()).containsExactlyInAnyOrder("core", "transfer");
    }

    @Test
    void testGetFeatureReturnsNullForMissing() {
        // Given
        var registry = new HttpFeatureRegistry();

        // Then
        assertThat(registry.getFeature("nonexistent")).isNull();
    }

    @Test
    void testGetFeatures() {
        // Given
        var registry = new HttpFeatureRegistry();
        registry.register(createFeature("a", HttpFeatureCategory.CORE, true));
        registry.register(createFeature("b", HttpFeatureCategory.TRANSFER, false));

        // Then
        assertThat(registry.getFeatures()).hasSize(2);
    }

    @Test
    void testGetByCategoryEmpty() {
        // Given
        var registry = new HttpFeatureRegistry();

        // Then
        assertThat(registry.getByCategory(HttpFeatureCategory.WEBSOCKET)).isEmpty();
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
