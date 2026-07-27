package ssg.legoflow.ws.content;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.header.MediaType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ContentNegotiatorTest {

    private final ContentNegotiator negotiator = new ContentNegotiator(
            List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.TEXT_PLAIN));

    @Test
    void testNegotiateJson() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(HttpHeaders.ACCEPT, "application/json");
        var result = negotiator.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testNegotiateXml() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(HttpHeaders.ACCEPT, "text/xml");
        var result = negotiator.negotiate(request);
        assertThat(result).isEqualTo(MediaType.TEXT_XML);
    }

    @Test
    void testNegotiatePlainText() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(HttpHeaders.ACCEPT, "text/plain");
        var result = negotiator.negotiate(request);
        assertThat(result).isEqualTo(MediaType.TEXT_PLAIN);
    }

    @Test
    void testNegotiateWithQualityValues() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(HttpHeaders.ACCEPT, "text/xml;q=0.5, application/json;q=1.0");
        var result = negotiator.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testNegotiateWithQualityPrefersHigher() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(HttpHeaders.ACCEPT, "text/plain;q=0.9, text/xml;q=0.1");
        var result = negotiator.negotiate(request);
        assertThat(result).isEqualTo(MediaType.TEXT_PLAIN);
    }

    @Test
    void testNegotiateWildcard() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(HttpHeaders.ACCEPT, "*/*");
        var result = negotiator.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testNegotiateNoAcceptHeader() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        var result = negotiator.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testNegotiateUnsupported() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(HttpHeaders.ACCEPT, "image/png");
        var result = negotiator.negotiate(request);
        assertThat(result).isNull();
    }

    @Test
    void testNegotiateOrDefaultFallback() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(HttpHeaders.ACCEPT, "image/png");
        var result = negotiator.negotiateOrDefault(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testGetSupportedTypes() {
        assertThat(negotiator.getSupportedTypes()).hasSize(3);
    }

    @Test
    void testMultipleAcceptTypesFirstMatch() {
        var request = HttpRequest.of(HttpMethod.GET, "/test");
        request.getHeaders().set(HttpHeaders.ACCEPT, "text/html, application/json, text/xml");
        var result = negotiator.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON);
    }
}
