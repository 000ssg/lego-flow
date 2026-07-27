package ssg.legoflow.http.config;

import ssg.legoflow.http.feature.HttpFeatureSet;
import ssg.legoflow.http.security.SslConfig;
import ssg.legoflow.http.staticcontent.StaticContentConfig;

public class ServerConfig extends HttpConfig {

    private SslConfig sslConfig;
    private StaticContentConfig staticContentConfig;

    public ServerConfig(HttpFeatureSet featureSet) {
        super(featureSet);
    }

    public SslConfig getSslConfig() { return sslConfig; }
    public void setSslConfig(SslConfig sslConfig) { this.sslConfig = sslConfig; }
    public boolean isSslEnabled() { return sslConfig != null; }
    public StaticContentConfig getStaticContentConfig() { return staticContentConfig; }
    public void setStaticContentConfig(StaticContentConfig config) { this.staticContentConfig = config; }
}
