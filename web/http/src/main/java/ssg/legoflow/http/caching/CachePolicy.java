package ssg.legoflow.http.caching;

import ssg.legoflow.http.core.HttpMethod;
import java.util.Set;
public class CachePolicy {

    private int defaultMaxAge = 3600;
    private Set<HttpMethod> cacheableMethods = Set.of(HttpMethod.GET, HttpMethod.HEAD);
    private Set<Integer> cacheableStatusCodes = Set.of(200, 203, 204, 206, 300, 301, 404, 405, 410, 414, 501);
    private boolean cachePrivate = false;

    public boolean isCacheable(HttpMethod method, int statusCode, CacheControl cacheControl) {
        if (cacheControl != null && cacheControl.isNoStore()) return false;
        if (cacheControl != null && cacheControl.isPrivate() && !cachePrivate) return false;
        if (!cacheableMethods.contains(method)) return false;
        return cacheableStatusCodes.contains(statusCode);
    }

    public int getEffectiveMaxAge(CacheControl cacheControl) {
        if (cacheControl != null && cacheControl.getMaxAge() >= 0) return cacheControl.getMaxAge();
        return defaultMaxAge;
    }

    public void setDefaultMaxAge(int seconds) { this.defaultMaxAge = seconds; }
    public int getDefaultMaxAge() { return defaultMaxAge; }
    public void setCachePrivate(boolean cachePrivate) { this.cachePrivate = cachePrivate; }
}
