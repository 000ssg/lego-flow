package ssg.legoflow.http.config;

import ssg.legoflow.http.connection.ConnectionConfig;
import ssg.legoflow.http.feature.HttpFeatureSet;

public class HttpConfig {

    private HttpFeatureSet featureSet;
    private ConnectionConfig connectionConfig = new ConnectionConfig();
    private int port = 8080;
    private String host = "0.0.0.0";

    public HttpConfig(HttpFeatureSet featureSet) {
        this.featureSet = featureSet;
    }

    public HttpFeatureSet getFeatureSet() { return featureSet; }
    public void setFeatureSet(HttpFeatureSet featureSet) { this.featureSet = featureSet; }
    public ConnectionConfig getConnectionConfig() { return connectionConfig; }
    public void setConnectionConfig(ConnectionConfig config) { this.connectionConfig = config; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
}
