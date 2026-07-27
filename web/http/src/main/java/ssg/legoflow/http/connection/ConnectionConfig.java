package ssg.legoflow.http.connection;

public class ConnectionConfig {

    private int maxConnections = 100;
    private int keepAliveTimeout = 30;
    private int maxKeepAliveRequests = 100;
    private boolean pipeliningEnabled = false;
    private int connectionTimeout = 10000;
    private int readTimeout = 30000;

    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
    public int getKeepAliveTimeout() { return keepAliveTimeout; }
    public void setKeepAliveTimeout(int seconds) { this.keepAliveTimeout = seconds; }
    public int getMaxKeepAliveRequests() { return maxKeepAliveRequests; }
    public void setMaxKeepAliveRequests(int max) { this.maxKeepAliveRequests = max; }
    public boolean isPipeliningEnabled() { return pipeliningEnabled; }
    public void setPipeliningEnabled(boolean enabled) { this.pipeliningEnabled = enabled; }
    public int getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(int millis) { this.connectionTimeout = millis; }
    public int getReadTimeout() { return readTimeout; }
    public void setReadTimeout(int millis) { this.readTimeout = millis; }
}
