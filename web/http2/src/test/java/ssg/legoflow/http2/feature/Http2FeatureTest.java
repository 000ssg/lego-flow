package ssg.legoflow.http2.feature;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.http.feature.HttpFeatureCategory;
class Http2FeatureTest {

    @Test void testName() {
        var feature = new Http2Feature();
        assertThat(feature.getName()).isEqualTo("http2");
        assertThat(feature.getName()).isEqualTo(Http2Feature.FEATURE_NAME);
    }

    @Test void testCategory() {
        var feature = new Http2Feature();
        assertThat(feature.getCategory()).isEqualTo(HttpFeatureCategory.HTTP2);
    }

    @Test void testNotCore() {
        var feature = new Http2Feature();
        assertThat(feature.isCore()).isFalse();
    }

    @Test void testConfigureDoesNotThrow() {
        var feature = new Http2Feature();
        // Configure with empty params should not throw
        assertThatNoException().isThrownBy(() -> 
            feature.configure(java.util.Map.of()));
    }

    @Test void testConfigureWithParams() {
        var feature = new Http2Feature();
        Map<String, Object> params = Map.of("key", "value");
        // Should not throw with any params (configure is no-op)
        assertThatNoException().isThrownBy(() -> 
            feature.configure(params));
    }

    @Test void testInstallRegistersItself() {
        var feature = new Http2Feature();
        var registry = new ssg.legoflow.http.feature.HttpFeatureRegistry();
        // Install should register without throwing
        assertThatNoException().isThrownBy(() -> feature.install(registry));
        
        // Feature should be registered in the registry
        var registered = registry.getFeature("http2");
        assertThat(registered).isEqualTo(feature);
    }

    @Test void testInstallMultipleFeatures() {
        var f1 = new Http2Feature();
        var f2 = new Http2Feature();
        
        var registry = new ssg.legoflow.http.feature.HttpFeatureRegistry();
        f1.install(registry);
        // Second install should overwrite or handle gracefully
        f2.install(registry);
    }

    @Test void testConstantFieldName() {
        assertThat(Http2Feature.FEATURE_NAME).isEqualTo("http2");
    }
}
