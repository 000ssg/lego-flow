package ssg.legoflow.http2.feature;

import ssg.legoflow.http.feature.HttpFeature;
import ssg.legoflow.http.feature.HttpFeatureCategory;
import ssg.legoflow.http.feature.HttpFeatureRegistry;

import java.util.Map;

public class Http2Feature implements HttpFeature {

    public static final String FEATURE_NAME = "http2";

    @Override
    public String getName() {
        return FEATURE_NAME;
    }

    @Override
    public HttpFeatureCategory getCategory() {
        return HttpFeatureCategory.HTTP2;
    }

    @Override
    public boolean isCore() {
        return false;
    }

    @Override
    public void configure(Map<String, Object> params) {
    }

    @Override
    public void install(HttpFeatureRegistry registry) {
        registry.register(this);
    }
}
