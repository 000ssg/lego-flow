package ssg.legoflow.http.config;

import ssg.legoflow.http.feature.HttpFeature;
import ssg.legoflow.http.feature.HttpFeatureSet;
import java.util.HashMap;
public class FeatureConfigurer {

    private final HttpFeatureSet featureSet;

    public FeatureConfigurer(HttpFeatureSet featureSet) {
        this.featureSet = featureSet;
    }

    public FeatureConfigurer enable(HttpFeature feature) {
        featureSet.add(feature);
        return this;
    }

    public FeatureConfigurer disable(String featureName) {
        featureSet.remove(featureName);
        return this;
    }

    public FeatureConfigurer configure(String featureName, String key, Object value) {
        var feature = featureSet.getFeature(featureName);
        if (feature != null) {
            var params = new HashMap<String, Object>();
            params.put(key, value);
            feature.configure(params);
        }
        return this;
    }

    public HttpFeatureSet build() {
        return featureSet;
    }
}
