package ssg.legoflow.http3.feature;

import ssg.legoflow.http.feature.HttpFeatureCategory;
import ssg.legoflow.http.feature.HttpFeatureRegistry;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class Http3FeatureTest {

    @Test
    void testFeatureName() {
        // Given
        var feature = new Http3Feature();

        // When/Then
        assertThat(feature.getName()).isEqualTo("http3");
    }

    @Test
    void testFeatureCategory() {
        // Given
        var feature = new Http3Feature();

        // When/Then
        assertThat(feature.getCategory()).isEqualTo(HttpFeatureCategory.HTTP3);
    }

    @Test
    void testIsNotCore() {
        // Given
        var feature = new Http3Feature();

        // When/Then
        assertThat(feature.isCore()).isFalse();
    }

    @Test
    void testInstall() {
        // Given
        var feature = new Http3Feature();
        var registry = new HttpFeatureRegistry();

        // When
        feature.install(registry);

        // Then
        assertThat(registry.isEnabled("http3")).isTrue();
        assertThat(registry.getFeature("http3")).isSameAs(feature);
    }

    @Test
    void testConfigure() {
        // Given
        var feature = new Http3Feature();

        // When/Then: configure should not throw
        feature.configure(Map.of());
    }

    @Test
    void testRegistryByCategory() {
        // Given
        var feature = new Http3Feature();
        var registry = new HttpFeatureRegistry();
        feature.install(registry);

        // When
        var http3Features = registry.getByCategory(HttpFeatureCategory.HTTP3);

        // Then
        assertThat(http3Features).hasSize(1);
        assertThat(http3Features.get(0).getName()).isEqualTo("http3");
    }
}
