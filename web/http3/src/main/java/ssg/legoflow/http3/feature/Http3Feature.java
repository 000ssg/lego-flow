package ssg.legoflow.http3.feature;

import ssg.legoflow.http.feature.HttpFeature;
import ssg.legoflow.http.feature.HttpFeatureCategory;
import ssg.legoflow.http.feature.HttpFeatureRegistry;

import java.util.Map;

/**
 * HTTP/3 feature for the HTTP feature system.
 *
 * <p>Registers the HTTP/3 capability with the feature registry,
 * allowing HTTP/3 to be included in feature sets and profiles.</p>
 *
 * @since 0.1.0
 */
public class Http3Feature implements HttpFeature {

    /** The feature name. */
    public static final String FEATURE_NAME = "http3";

    @Override
    public String getName() {
        return FEATURE_NAME;
    }

    @Override
    public HttpFeatureCategory getCategory() {
        return HttpFeatureCategory.HTTP3;
    }

    @Override
    public boolean isCore() {
        return false;
    }

    @Override
    public void configure(Map<String, Object> params) {
        // HTTP/3 configuration is handled via Http3Config
    }

    @Override
    public void install(HttpFeatureRegistry registry) {
        registry.register(this);
    }
}
