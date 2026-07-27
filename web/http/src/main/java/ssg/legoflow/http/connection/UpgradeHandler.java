package ssg.legoflow.http.connection;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;

public class UpgradeHandler {

    public boolean isUpgradeRequest(HttpRequest request) {
        var upgrade = request.getHeaders().get(HttpHeaders.UPGRADE);
        var connection = request.getHeaders().get(HttpHeaders.CONNECTION);
        return upgrade != null && connection != null
                && connection.toLowerCase().contains("upgrade");
    }

    public String getUpgradeProtocol(HttpRequest request) {
        return request.getHeaders().get(HttpHeaders.UPGRADE);
    }

    public HttpResponse createUpgradeResponse(String protocol) {
        var response = HttpResponse.of(HttpStatus.SWITCHING_PROTOCOLS);
        response.getHeaders().set(HttpHeaders.UPGRADE, protocol);
        response.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        return response;
    }
}
