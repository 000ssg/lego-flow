package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.http.core.HttpContext;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.DeviceProxy;

import java.util.Objects;

/**
 * Handles device-related REST API requests for the Media Control Center.
 *
 * <p>Provides endpoints for listing all devices, servers, renderers,
 * getting device details by UDN, and triggering device re-discovery.
 *
 * @since 1.0.0
 */
public class MccDeviceHandler {

    private final ControlPoint controlPoint;

    /**
     * Creates a new device handler.
     *
     * @param controlPoint the UPnP control point
     * @since 1.0.0
     */
    public MccDeviceHandler(ControlPoint controlPoint) {
        this.controlPoint = Objects.requireNonNull(controlPoint, "controlPoint must not be null");
    }

    /**
     * Handles GET /api/devices - lists all discovered devices.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with JSON array of devices
     * @since 1.0.0
     */
    public HttpResponse listDevices(HttpContext ctx, HttpRequest request) {
        var devices = controlPoint.getDevices();
        String json = MccJsonSerializer.devicesToJson(devices);
        return jsonResponse(HttpStatus.OK, json);
    }

    /**
     * Handles GET /api/devices/servers - lists all media servers.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with JSON array of servers
     * @since 1.0.0
     */
    public HttpResponse listServers(HttpContext ctx, HttpRequest request) {
        var servers = controlPoint.discoverMediaServers();
        String json = MccJsonSerializer.devicesToJson(servers);
        return jsonResponse(HttpStatus.OK, json);
    }

    /**
     * Handles GET /api/devices/renderers - lists all media renderers.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with JSON array of renderers
     * @since 1.0.0
     */
    public HttpResponse listRenderers(HttpContext ctx, HttpRequest request) {
        var renderers = controlPoint.discoverMediaRenderers();
        String json = MccJsonSerializer.devicesToJson(renderers);
        return jsonResponse(HttpStatus.OK, json);
    }

    /**
     * Handles GET /api/devices/{udn} - gets device details by UDN.
     *
     * <p>The UDN is extracted from the request URI path.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with device JSON or 404
     * @since 1.0.0
     */
    public HttpResponse getDevice(HttpContext ctx, HttpRequest request) {
        String udn = extractUdn(request.getUri(), "/api/devices/");
        if (udn == null || udn.isEmpty()) {
            return jsonResponse(HttpStatus.BAD_REQUEST,
                    MccJsonSerializer.errorToJson("Missing device UDN"));
        }
        var devices = controlPoint.getDevices();
        DeviceProxy found = null;
        for (var device : devices) {
            if (device.getUdn().equals(udn)) {
                found = device;
                break;
            }
        }
        if (found == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Device not found: " + udn));
        }
        return jsonResponse(HttpStatus.OK, MccJsonSerializer.deviceDetailsToJson(found));
    }

    /**
     * Handles GET /api/devices/unrecognized - lists devices that failed during discovery.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with JSON array of failed devices
     * @since 1.0.0
     */
    public HttpResponse listFailedDevices(HttpContext ctx, HttpRequest request) {
        var failed = controlPoint.getFailedDevices();
        String json = MccJsonSerializer.failedDevicesToJson(failed);
        return jsonResponse(HttpStatus.OK, json);
    }

    /**
     * Handles POST /api/devices/refresh - triggers device re-discovery.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response confirming refresh
     * @since 1.0.0
     */
    public HttpResponse refreshDevices(HttpContext ctx, HttpRequest request) {
        controlPoint.refresh();
        return jsonResponse(HttpStatus.OK,
                MccJsonSerializer.messageToJson("Device discovery refreshed"));
    }

    /**
     * Extracts a UDN from a URI path, given the prefix.
     *
     * @param uri    the request URI
     * @param prefix the path prefix before the UDN
     * @return the extracted UDN, or null
     * @since 1.0.0
     */
    static String extractUdn(String uri, String prefix) {
        String path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;
        if (!path.startsWith(prefix)) return null;
        String remainder = path.substring(prefix.length());
        int slashIdx = remainder.indexOf('/');
        return slashIdx >= 0 ? remainder.substring(0, slashIdx) : remainder;
    }

    static HttpResponse jsonResponse(HttpStatus status, String json) {
        HttpResponse response = HttpResponse.of(status, json);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8");
        return response;
    }
}
