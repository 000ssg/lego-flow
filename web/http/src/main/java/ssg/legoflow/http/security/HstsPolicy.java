package ssg.legoflow.http.security;

public class HstsPolicy {

    private long maxAge = 31536000;
    private boolean includeSubDomains = true;
    private boolean preload = false;

    public HstsPolicy() {}

    public HstsPolicy(long maxAge, boolean includeSubDomains, boolean preload) {
        this.maxAge = maxAge;
        this.includeSubDomains = includeSubDomains;
        this.preload = preload;
    }

    public String toHeaderValue() {
        var sb = new StringBuilder("max-age=").append(maxAge);
        if (includeSubDomains) sb.append("; includeSubDomains");
        if (preload) sb.append("; preload");
        return sb.toString();
    }

    public static HstsPolicy parse(String header) {
        var policy = new HstsPolicy();
        if (header == null) return policy;
        for (var directive : header.split(";")) {
            var trimmed = directive.trim().toLowerCase();
            if (trimmed.startsWith("max-age=")) {
                policy.maxAge = Long.parseLong(trimmed.substring(8));
            } else if (trimmed.equals("includesubdomains")) {
                policy.includeSubDomains = true;
            } else if (trimmed.equals("preload")) {
                policy.preload = true;
            }
        }
        return policy;
    }

    public long getMaxAge() { return maxAge; }
    public boolean isIncludeSubDomains() { return includeSubDomains; }
    public boolean isPreload() { return preload; }
}
