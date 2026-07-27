package ssg.legoflow.http.config;

import ssg.legoflow.http.feature.HttpFeatureSet;
import ssg.legoflow.http.security.SslConfig;

public class ClientConfig extends HttpConfig {

    private SslConfig sslConfig;
    private boolean followRedirects = true;
    private int maxRedirects = 5;

    public ClientConfig(HttpFeatureSet featureSet) {
        super(featureSet);
    }

    public SslConfig getSslConfig() { return sslConfig; }
    public void setSslConfig(SslConfig sslConfig) { this.sslConfig = sslConfig; }
    public boolean isFollowRedirects() { return followRedirects; }
    public void setFollowRedirects(boolean followRedirects) { this.followRedirects = followRedirects; }
    public int getMaxRedirects() { return maxRedirects; }
    public void setMaxRedirects(int maxRedirects) { this.maxRedirects = maxRedirects; }
}
