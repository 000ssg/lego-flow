package ssg.legoflow.http.client;

import ssg.legoflow.http.config.ClientConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.*;
import ssg.legoflow.http.security.SslConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HttpClientTest {

    @Test
    void testClientCreation() {
        var config = new ClientConfig(StandardProfiles.clientMinimal());
        var client = new HttpClient(config);

        assertThat(client.getConfig()).isSameAs(config);
    }

    @Test
    void testClientSendReturnsOk() {
        var client = new HttpClient(new ClientConfig(StandardProfiles.clientMinimal()));
        var request = HttpRequest.of(HttpMethod.GET, "/test");

        var response = client.send(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testClientBuilderMinimal() {
        var client = new HttpClientBuilder().minimal().build();

        assertThat(client.getConfig().getFeatureSet().getName()).isEqualTo("CLIENT_MINIMAL");
    }

    @Test
    void testClientBuilderStandard() {
        var client = new HttpClientBuilder().standard().build();

        assertThat(client.getConfig().getFeatureSet().getName()).isEqualTo("CLIENT_STANDARD");
    }

    @Test
    void testClientBuilderFull() {
        var client = new HttpClientBuilder().full().build();

        assertThat(client.getConfig().getFeatureSet().getName()).isEqualTo("CLIENT_FULL");
    }

    @Test
    void testClientBuilderWithSsl() {
        var ssl = new SslConfig();
        ssl.setTruststorePath("/test/truststore.jks");
        var client = new HttpClientBuilder().full().ssl(ssl).build();

        assertThat(client.getConfig().getSslConfig()).isSameAs(ssl);
        assertThat(client.getConfig().getSslConfig().getTruststorePath()).isEqualTo("/test/truststore.jks");
    }

    @Test
    void testClientBuilderFollowRedirects() {
        var client = new HttpClientBuilder().standard().followRedirects(false).build();

        assertThat(client.getConfig().isFollowRedirects()).isFalse();
    }

    @Test
    void testClientDefaultFollowRedirects() {
        var client = new HttpClientBuilder().standard().build();

        assertThat(client.getConfig().isFollowRedirects()).isTrue();
    }
}
