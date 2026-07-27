package ssg.legoflow.http.caching;

import java.util.*;

public class CacheControl {

    private boolean noCache;
    private boolean noStore;
    private boolean mustRevalidate;
    private boolean isPublic;
    private boolean isPrivate;
    private int maxAge = -1;
    private int sMaxAge = -1;
    private boolean noTransform;
    private boolean proxyRevalidate;

    public static CacheControl parse(String header) {
        var cc = new CacheControl();
        if (header == null || header.isBlank()) return cc;
        for (var directive : header.split(",")) {
            var trimmed = directive.trim().toLowerCase();
            if (trimmed.equals("no-cache")) cc.noCache = true;
            else if (trimmed.equals("no-store")) cc.noStore = true;
            else if (trimmed.equals("must-revalidate")) cc.mustRevalidate = true;
            else if (trimmed.equals("public")) cc.isPublic = true;
            else if (trimmed.equals("private")) cc.isPrivate = true;
            else if (trimmed.equals("no-transform")) cc.noTransform = true;
            else if (trimmed.equals("proxy-revalidate")) cc.proxyRevalidate = true;
            else if (trimmed.startsWith("max-age=")) cc.maxAge = Integer.parseInt(trimmed.substring(8));
            else if (trimmed.startsWith("s-maxage=")) cc.sMaxAge = Integer.parseInt(trimmed.substring(9));
        }
        return cc;
    }

    public boolean isNoCache() { return noCache; }
    public boolean isNoStore() { return noStore; }
    public boolean isMustRevalidate() { return mustRevalidate; }
    public boolean isPublic() { return isPublic; }
    public boolean isPrivate() { return isPrivate; }
    public int getMaxAge() { return maxAge; }
    public int getSMaxAge() { return sMaxAge; }
    public boolean isNoTransform() { return noTransform; }
    public boolean isProxyRevalidate() { return proxyRevalidate; }

    public CacheControl noCache(boolean v) { noCache = v; return this; }
    public CacheControl noStore(boolean v) { noStore = v; return this; }
    public CacheControl mustRevalidate(boolean v) { mustRevalidate = v; return this; }
    public CacheControl setPublic(boolean v) { isPublic = v; return this; }
    public CacheControl setPrivate(boolean v) { isPrivate = v; return this; }
    public CacheControl maxAge(int v) { maxAge = v; return this; }
    public CacheControl sMaxAge(int v) { sMaxAge = v; return this; }

    @Override
    public String toString() {
        var parts = new ArrayList<String>();
        if (noCache) parts.add("no-cache");
        if (noStore) parts.add("no-store");
        if (mustRevalidate) parts.add("must-revalidate");
        if (isPublic) parts.add("public");
        if (isPrivate) parts.add("private");
        if (maxAge >= 0) parts.add("max-age=" + maxAge);
        if (sMaxAge >= 0) parts.add("s-maxage=" + sMaxAge);
        if (noTransform) parts.add("no-transform");
        if (proxyRevalidate) parts.add("proxy-revalidate");
        return String.join(", ", parts);
    }
}
