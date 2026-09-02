package ssg.legoflow.http2.demo;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http2.feature.Http2UpgradeHandler;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class H2cUpgradeDemoTest {

    @Test
    void testCreateH2cUpgradeRequest() {
        var demo = new H2cUpgradeDemo();
        var request = demo.createH2cUpgradeRequest("/hello");

        assertThat(request.getUri()).isEqualTo("/hello");
        assertThat(request.getHeaders().get(HttpHeaders.UPGRADE)).isEqualTo(Http2UpgradeHandler.H2C_PROTOCOL);
        assertThat(request.getHeaders().get(HttpHeaders.CONNECTION)).contains("Upgrade");
        assertThat(request.getHeaders().get("http2-settings")).isNotNull();
    }

    @Test
    void testIsH2cUpgradeRequest() {
        var demo = new H2cUpgradeDemo();
        var request = demo.createH2cUpgradeRequest("/test");

        assertThat(demo.upgradeHandler().isH2cUpgradeRequest(request)).isTrue();
    }

    @Test
    void testPerformUpgrade() {
        var demo = new H2cUpgradeDemo();
        var request = demo.createH2cUpgradeRequest("/test");

        var connection = demo.performUpgrade(request);

        assertThat(connection).isNotNull();
        assertThat(connection.isServer()).isTrue();
    }

    @Test
    void testUpgradeResponse() {
        var demo = new H2cUpgradeDemo();
        var response = demo.createUpgradeResponse();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SWITCHING_PROTOCOLS);
        assertThat(response.getHeaders().get(HttpHeaders.UPGRADE)).isEqualTo(Http2UpgradeHandler.H2C_PROTOCOL);
        assertThat(response.getHeaders().get(HttpHeaders.CONNECTION)).isEqualTo("Upgrade");
    }

    @Test
    void testNonUpgradeRequestRejected() {
        var demo = new H2cUpgradeDemo();
        var request = ssg.legoflow.http.core.HttpRequest.of(
                ssg.legoflow.http.core.HttpMethod.GET, "/normal");

        assertThatThrownBy(() -> demo.performUpgrade(request))
                .isInstanceOf(IllegalStateException.class);
    }
}
