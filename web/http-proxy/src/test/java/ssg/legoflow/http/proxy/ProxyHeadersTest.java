package ssg.legoflow.http.proxy;

import ssg.legoflow.http.core.HttpHeaders;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyHeadersTest {

    @Test
    void testAddForwardedForSingle() {
        var headers = new HttpHeaders();
        ProxyHeaders.addForwardedFor(headers, "10.0.0.1");
        assertThat(headers.get(ProxyHeaders.X_FORWARDED_FOR)).isEqualTo("10.0.0.1");
    }

    @Test
    void testAddForwardedForMultiple() {
        var headers = new HttpHeaders();
        ProxyHeaders.addForwardedFor(headers, "10.0.0.1");
        ProxyHeaders.addForwardedFor(headers, "10.0.0.2");
        assertThat(headers.get(ProxyHeaders.X_FORWARDED_FOR)).isEqualTo("10.0.0.1, 10.0.0.2");
    }

    @Test
    void testGetForwardedForNull() {
        var headers = new HttpHeaders();
        assertThat(ProxyHeaders.getForwardedFor(headers)).isNull();
    }

    @Test
    void testSetForwardedProto() {
        var headers = new HttpHeaders();
        ProxyHeaders.setForwardedProto(headers, "https");
        assertThat(ProxyHeaders.getForwardedProto(headers)).isEqualTo("https");
    }

    @Test
    void testSetForwardedHost() {
        var headers = new HttpHeaders();
        ProxyHeaders.setForwardedHost(headers, "example.com");
        assertThat(ProxyHeaders.getForwardedHost(headers)).isEqualTo("example.com");
    }

    @Test
    void testAddViaSingle() {
        var headers = new HttpHeaders();
        ProxyHeaders.addVia(headers, "1.1", "proxy1");
        assertThat(ProxyHeaders.getVia(headers)).isEqualTo("1.1 proxy1");
    }

    @Test
    void testAddViaMultiple() {
        var headers = new HttpHeaders();
        ProxyHeaders.addVia(headers, "1.1", "proxy1");
        ProxyHeaders.addVia(headers, "1.1", "proxy2");
        assertThat(ProxyHeaders.getVia(headers)).isEqualTo("1.1 proxy1, 1.1 proxy2");
    }

    @Test
    void testSetRealIp() {
        var headers = new HttpHeaders();
        ProxyHeaders.setRealIp(headers, "192.168.1.1");
        assertThat(ProxyHeaders.getRealIp(headers)).isEqualTo("192.168.1.1");
    }

    @Test
    void testGetRealIpNull() {
        var headers = new HttpHeaders();
        assertThat(ProxyHeaders.getRealIp(headers)).isNull();
    }

    @Test
    void testApplyForwardHeaders() {
        var headers = new HttpHeaders();
        ProxyHeaders.applyForwardHeaders(headers, "10.0.0.1", "https",
                "example.com", "my-proxy");

        assertThat(headers.get(ProxyHeaders.X_FORWARDED_FOR)).isEqualTo("10.0.0.1");
        assertThat(headers.get(ProxyHeaders.X_FORWARDED_PROTO)).isEqualTo("https");
        assertThat(headers.get(ProxyHeaders.X_FORWARDED_HOST)).isEqualTo("example.com");
        assertThat(headers.get(ProxyHeaders.VIA)).isEqualTo("1.1 my-proxy");
        assertThat(headers.get(ProxyHeaders.X_REAL_IP)).isEqualTo("10.0.0.1");
    }

    @Test
    void testApplyForwardHeadersNullHost() {
        var headers = new HttpHeaders();
        ProxyHeaders.applyForwardHeaders(headers, "10.0.0.1", "http", null, "proxy");

        assertThat(headers.get(ProxyHeaders.X_FORWARDED_FOR)).isEqualTo("10.0.0.1");
        assertThat(headers.get(ProxyHeaders.X_FORWARDED_HOST)).isNull();
    }

    @Test
    void testHeaderConstants() {
        assertThat(ProxyHeaders.X_FORWARDED_FOR).isEqualTo("x-forwarded-for");
        assertThat(ProxyHeaders.X_FORWARDED_PROTO).isEqualTo("x-forwarded-proto");
        assertThat(ProxyHeaders.X_FORWARDED_HOST).isEqualTo("x-forwarded-host");
        assertThat(ProxyHeaders.VIA).isEqualTo("via");
        assertThat(ProxyHeaders.X_REAL_IP).isEqualTo("x-real-ip");
        assertThat(ProxyHeaders.PROXY_AUTHORIZATION).isEqualTo("proxy-authorization");
        assertThat(ProxyHeaders.PROXY_AUTHENTICATE).isEqualTo("proxy-authenticate");
    }

    @Test
    void testGetViaNull() {
        var headers = new HttpHeaders();
        assertThat(ProxyHeaders.getVia(headers)).isNull();
    }

    @Test
    void testGetForwardedProtoNull() {
        var headers = new HttpHeaders();
        assertThat(ProxyHeaders.getForwardedProto(headers)).isNull();
    }
}
