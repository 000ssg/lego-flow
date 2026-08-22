package ssg.legoflow.http2.feature;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http2.connection.Http2Connection;
import ssg.legoflow.http2.connection.Http2ConnectionPreface;
import ssg.legoflow.http2.connection.Http2Settings;
import java.nio.ByteBuffer;
import java.util.Base64;
public class Http2UpgradeHandler {

    public static final String H2C_PROTOCOL = "h2c";
    public static final String H2_PROTOCOL = "h2";

    public boolean isH2cUpgradeRequest(HttpRequest request) {
        var upgrade = request.getHeaders().get(HttpHeaders.UPGRADE);
        var connection = request.getHeaders().get(HttpHeaders.CONNECTION);
        return upgrade != null && H2C_PROTOCOL.equalsIgnoreCase(upgrade.trim())
                && connection != null && connection.toLowerCase().contains("upgrade");
    }

    public HttpResponse createH2cUpgradeResponse() {
        var response = HttpResponse.of(HttpStatus.SWITCHING_PROTOCOLS);
        response.getHeaders().set(HttpHeaders.UPGRADE, H2C_PROTOCOL);
        response.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        return response;
    }

    public Http2Settings decodeSettingsFromUpgrade(HttpRequest request) {
        var settingsHeader = request.getHeaders().get("http2-settings");
        if (settingsHeader == null) {
            return new Http2Settings();
        }
        byte[] decoded = Base64.getUrlDecoder().decode(settingsHeader);
        return Http2Settings.decode(ByteBuffer.wrap(decoded));
    }

    public Http2Connection upgradeToH2c(HttpRequest request) {
        var clientSettings = decodeSettingsFromUpgrade(request);
        var connection = new Http2Connection(true);
        connection.processFrame(
                ssg.legoflow.http2.frame.Http2Frame.settings(clientSettings.encode()));
        return connection;
    }

    public boolean isDirectH2(ByteBuffer data) {
        return Http2ConnectionPreface.isClientPreface(data);
    }

    public String encodeSettingsForUpgrade(Http2Settings settings) {
        var encoded = settings.encode();
        byte[] bytes = new byte[encoded.remaining()];
        encoded.duplicate().get(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
