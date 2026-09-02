package ssg.legoflow.ws.content;

import org.junit.jupiter.api.Test;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.header.MediaType;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link ContentNegotiator}.
 *
 * @since 0.1.0
 */
class ContentNegotiatorTest {

    private final List<MediaType> supportedTypes = List.of(
            MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML,
            MediaType.TEXT_PLAIN
    );

    @Test
    void testNegotiateWithMatchingAccept() {
        var neg = new ContentNegotiator(supportedTypes);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.ACCEPT, "application/json");
        var result = neg.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testNegotiateWithMatchingXml() {
        var neg = new ContentNegotiator(supportedTypes);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.ACCEPT, "application/xml");
        var result = neg.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_XML);
    }

    @Test
    void testNegotiateNoAcceptHeaderReturnsFirst() {
        var neg = new ContentNegotiator(supportedTypes);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        var result = neg.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testNegotiateEmptyAcceptHeaderReturnsFirst() {
        var neg = new ContentNegotiator(supportedTypes);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.ACCEPT, "   ");
        var result = neg.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testNegotiateWithQualityValues() {
        var neg = new ContentNegotiator(supportedTypes);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.ACCEPT, "text/plain;q=0.5, application/json;q=0.9, application/xml;q=1.0");
        var result = neg.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_XML); // highest quality
    }

    @Test
    void testNegotiateWithWildcard() {
        var neg = new ContentNegotiator(supportedTypes);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.ACCEPT, "*/*");
        var result = neg.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON); // first supported match
    }

    @Test
    void testNegotiateWithSubtypeWildcard() {
        var neg = new ContentNegotiator(supportedTypes);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.ACCEPT, "text/*");
        var result = neg.negotiate(request);
        assertThat(result).isEqualTo(MediaType.TEXT_PLAIN);
    }

    @Test
    void testNegotiateNoMatchReturnsNull() {
        var neg = new ContentNegotiator(supportedTypes);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.ACCEPT, "image/png");
        var result = neg.negotiate(request);
        assertThat(result).isNull();
    }

    @Test
    void testNegotiateOrDefaultReturnsDefaultOnNoMatch() {
        var neg = new ContentNegotiator(supportedTypes);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.ACCEPT, "image/png");
        var result = neg.negotiateOrDefault(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON); // first supported as default
    }

    @Test
    void testNegotiateOrDefaultWithNoAccept() {
        var neg = new ContentNegotiator(supportedTypes);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        var result = neg.negotiateOrDefault(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testNegotiateEmptySupportedListReturnsJsonDefault() {
        var neg = new ContentNegotiator(List.of());
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        var result = neg.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testGetSupportedTypes() {
        var neg = new ContentNegotiator(supportedTypes);
        assertThat(neg.getSupportedTypes()).containsExactlyElementsOf(supportedTypes);
    }

    @Test
    void testParseWithUpperCaseQ() {
        var neg = new ContentNegotiator(supportedTypes);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.ACCEPT, "text/plain;Q=0.5, application/json;Q=0.9");
        var result = neg.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON); // higher quality
    }

    @Test
    void testParseWithInvalidQualityFallback() {
        var neg = new ContentNegotiator(supportedTypes);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.ACCEPT, "application/json;q=invalid");
        // quality parsed as 0.0 on NumberFormatException
        var result = neg.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON); // still matches (quality is low but it's the only one)
    }

    @Test
    void testParseIgnoresInvalidMediaTypeEntries() {
        var neg = new ContentNegotiator(supportedTypes);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.ACCEPT, "invalid-type, application/json, also-invalid");
        var result = neg.negotiate(request);
        assertThat(result).isEqualTo(MediaType.APPLICATION_JSON); // only valid entry matches
    }

    @Test
    void testParseWithEmptyParts() {
        var neg = new ContentNegotiator(supportedTypes);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set(HttpHeaders.ACCEPT, ",application/json,,application/xml,");
        var result = neg.negotiate(request);
        assertThat(result).isNotNull(); // should find at least one match
    }

    @Test
    void testDefensiveCopyOfSupportedTypes() {
        var mutableList = new java.util.ArrayList<>(supportedTypes);
        var neg = new ContentNegotiator(mutableList);
        mutableList.clear();
        assertThat(neg.getSupportedTypes()).isNotEmpty(); // should be copied
    }
}
