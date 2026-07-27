package ssg.legoflow.http.client;

import ssg.legoflow.http.config.ClientConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.connection.ConnectionConfig;
import ssg.legoflow.http.security.SslConfig;

public class HttpClientBuilder {

    private ClientConfig config;

    public HttpClientBuilder() {
        this.config = new ClientConfig(StandardProfiles.clientStandard());
    }

    public HttpClientBuilder minimal() {
        config = new ClientConfig(StandardProfiles.clientMinimal());
        return this;
    }

    public HttpClientBuilder standard() {
        config = new ClientConfig(StandardProfiles.clientStandard());
        return this;
    }

    public HttpClientBuilder full() {
        config = new ClientConfig(StandardProfiles.clientFull());
        return this;
    }

    public HttpClientBuilder ssl(SslConfig sslConfig) {
        config.setSslConfig(sslConfig);
        return this;
    }

    public HttpClientBuilder followRedirects(boolean follow) {
        config.setFollowRedirects(follow);
        return this;
    }

    public HttpClientBuilder connectionConfig(ConnectionConfig connConfig) {
        config.setConnectionConfig(connConfig);
        return this;
    }

    public HttpClient build() {
        return new HttpClient(config);
    }
}
