package ssg.legoflow.http2.feature;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.core.HttpHeaders;

class Http2UpgradeHandlerTest {

    @Test void testConstants() {
        assertThat(Http2UpgradeHandler.H2C_PROTOCOL).isEqualTo("h2c");
        assertThat(Http2UpgradeHandler.H2_PROTOCOL).isEqualTo("h2");
    }

    @Test void testIsH2cUpgradeRequestTrue() {
        var handler = new Http2UpgradeHandler();
        var request = HttpRequest.of(HttpMethod.GET, "/upgrade");
        request.getHeaders().set(HttpHeaders.UPGRADE, "h2c");
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        
        assertThat(handler.isH2cUpgradeRequest(request)).isTrue();
    }

    @Test void testIsH2cUpgradeRequestFalseNoUpgradeHeader() {
        var handler = new Http2UpgradeHandler();
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        // No Upgrade header
        
        assertThat(handler.isH2cUpgradeRequest(request)).isFalse();
    }

    @Test void testIsH2cUpgradeRequestFalseNoConnectionHeader() {
        var handler = new Http2UpgradeHandler();
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set(HttpHeaders.UPGRADE, "h2c");
        // No Connection header
        
        assertThat(handler.isH2cUpgradeRequest(request)).isFalse();
    }

    @Test void testIsH2cUpgradeRequestFalseWrongUpgradeValue() {
        var handler = new Http2UpgradeHandler();
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set(HttpHeaders.UPGRADE, "h2");  // not h2c
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        
        assertThat(handler.isH2cUpgradeRequest(request)).isFalse();
    }

    @Test void testIsH2cUpgradeRequestCaseInsensitive() {
        var handler = new Http2UpgradeHandler();
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set(HttpHeaders.UPGRADE, "H2C");  // uppercase
        request.getHeaders().set(HttpHeaders.CONNECTION, "UPGRADE");  // uppercase
        
        assertThat(handler.isH2cUpgradeRequest(request)).isTrue();
    }

    @Test void testCreateH2cUpgradeResponse() {
        var handler = new Http2UpgradeHandler();
        var response = handler.createH2cUpgradeResponse();
        
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SWITCHING_PROTOCOLS);
        assertThat(response.getHeaders().get(HttpHeaders.UPGRADE)).isEqualTo("h2c");
        assertThat(response.getHeaders().get(HttpHeaders.CONNECTION)).isEqualTo("Upgrade");
    }

    @Test void testDecodeSettingsFromUpgradeWithNoHeader() {
        var handler = new Http2UpgradeHandler();
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        // No http2-settings header
        
        var settings = handler.decodeSettingsFromUpgrade(request);
        assertThat(settings).isNotNull();
        // Should return default settings
    }

    @Test void testDecodeSettingsFromUpgradeWithValidHeader() {
        var handler = new Http2UpgradeHandler();
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        // Base64-encoded empty settings frame
        String settingsData = java.util.Base64.getUrlEncoder().encodeToString(new byte[6]);
        request.getHeaders().set("http2-settings", settingsData);
        
        assertThatNoException().isThrownBy(() -> handler.decodeSettingsFromUpgrade(request));
    }

    @Test void testUpgradeToH2cBasic() {
        var handler = new Http2UpgradeHandler();
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        // No settings header - should use defaults
        
        assertThatNoException().isThrownBy(() -> handler.upgradeToH2c(request));
    }

    @Test void testMultipleHandlersIndependent() {
        var h1 = new Http2UpgradeHandler();
        var h2 = new Http2UpgradeHandler();
        
        var response1 = h1.createH2cUpgradeResponse();
        var response2 = h2.createH2cUpgradeResponse();
        
        assertThat(response1.getStatus()).isEqualTo(response2.getStatus());
    }

    @Test void testIsH2cUpgradeRequestConnectionContainsUpgrade() {
        var handler = new Http2UpgradeHandler();
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set(HttpHeaders.UPGRADE, "h2c");
        request.getHeaders().set(HttpHeaders.CONNECTION, "keep-alive, Upgrade");
        
        // Connection header contains "upgrade" in list format
        assertThat(handler.isH2cUpgradeRequest(request)).isTrue();
    }

    @Test void testIsH2cUpgradeRequestUpgradeWithWhitespace() {
        var handler = new Http2UpgradeHandler();
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set(HttpHeaders.UPGRADE, " h2c ");  // with whitespace
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        
        assertThat(handler.isH2cUpgradeRequest(request)).isTrue();
    }
}
