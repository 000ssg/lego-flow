package ssg.legoflow.http.feature;

import java.util.*;

public class HttpFeatureSet {

    private final String name;
    private final Map<String, HttpFeature> features = new LinkedHashMap<>();

    public HttpFeatureSet(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public HttpFeatureSet add(HttpFeature feature) {
        features.put(feature.getName(), feature);
        return this;
    }

    public HttpFeatureSet remove(String featureName) {
        var feature = features.get(featureName);
        if (feature != null && !feature.isCore()) {
            features.remove(featureName);
        }
        return this;
    }

    public boolean isEnabled(String featureName) {
        return features.containsKey(featureName);
    }

    public HttpFeature getFeature(String featureName) {
        return features.get(featureName);
    }

    public List<HttpFeature> getFeatures() {
        return List.copyOf(features.values());
    }

    public Set<HttpFeatureCategory> getEnabledCategories() {
        var categories = EnumSet.noneOf(HttpFeatureCategory.class);
        features.values().forEach(f -> categories.add(f.getCategory()));
        return Collections.unmodifiableSet(categories);
    }

    public void installAll(HttpFeatureRegistry registry) {
        features.values().forEach(f -> f.install(registry));
    }
}
