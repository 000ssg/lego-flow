package ssg.legoflow.http.feature;

import java.util.Map;

public interface HttpFeature {

    String getName();

    HttpFeatureCategory getCategory();

    boolean isCore();

    void configure(Map<String, Object> params);

    void install(HttpFeatureRegistry registry);
}
