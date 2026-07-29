package ssg.legoflow.http2.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http2.connection.Http2Connection;
import ssg.legoflow.http2.connection.Http2Settings;
import ssg.legoflow.http2.feature.Http2UpgradeHandler;

public class H2cUpgradeDemo {

    private final Http2UpgradeHandler upgradeHandler;

    public H2cUpgradeDemo() {
        this.upgradeHandler = new Http2UpgradeHandler();
    }

    public Http2UpgradeHandler upgradeHandler() {
        return upgradeHandler;
    }

    public HttpRequest createH2cUpgradeRequest(String path) {
        var request = HttpRequest.of(HttpMethod.GET, path);
        request.getHeaders().set(HttpHeaders.HOST, "localhost");
        request.getHeaders().set(HttpHeaders.UPGRADE, Http2UpgradeHandler.H2C_PROTOCOL);
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade, HTTP2-Settings");
        var settings = new Http2Settings();
        request.getHeaders().set("http2-settings",
                upgradeHandler.encodeSettingsForUpgrade(settings));
        return request;
    }

    public Http2Connection performUpgrade(HttpRequest request) {
        if (!upgradeHandler.isH2cUpgradeRequest(request)) {
            throw new IllegalStateException("Not an h2c upgrade request");
        }
        return upgradeHandler.upgradeToH2c(request);
    }

    public HttpResponse createUpgradeResponse() {
        return upgradeHandler.createH2cUpgradeResponse();
    }
}
