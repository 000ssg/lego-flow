package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.http.core.HttpContext;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.upnp.controlpoint.ControlPoint;
import ssg.legoflow.upnp.controlpoint.MediaRendererProxy;

import java.util.Objects;

import static ssg.legoflow.upnp.demo.mccweb.MccDeviceHandler.jsonResponse;

/**
 * Handles volume control REST API requests for the Media Control Center.
 *
 * <p>Provides endpoints for getting and setting volume levels and mute
 * state on media renderers.
 *
 * @since 0.1.0
 */
public class MccVolumeHandler {

    private final ControlPoint controlPoint;

    /**
     * Creates a new volume handler.
     *
     * @param controlPoint the UPnP control point
     * @since 0.1.0
     */
    public MccVolumeHandler(ControlPoint controlPoint) {
        this.controlPoint = Objects.requireNonNull(controlPoint, "controlPoint must not be null");
    }

    /**
     * Handles GET /api/renderers/{udn}/volume - gets current volume and mute state.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with volume JSON
     * @since 0.1.0
     */
    public HttpResponse getVolume(HttpContext ctx, HttpRequest request) {
        String udn = MccPlaybackHandler.extractRendererUdn(request.getUri(), "/volume");
        MediaRendererProxy renderer = findRenderer(udn);
        if (renderer == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Renderer not found: " + udn));
        }

        try {
            int volume = renderer.getVolume();
            boolean muted = renderer.getMute();
            return jsonResponse(HttpStatus.OK, MccJsonSerializer.volumeToJson(volume, muted));
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Get volume failed: " + e.getMessage()));
        }
    }

    /**
     * Handles PUT /api/renderers/{udn}/volume - sets volume level.
     *
     * <p>Request body: {"volume": 75}
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with updated volume JSON
     * @since 0.1.0
     */
    public HttpResponse setVolume(HttpContext ctx, HttpRequest request) {
        String udn = MccPlaybackHandler.extractRendererUdn(request.getUri(), "/volume");
        MediaRendererProxy renderer = findRenderer(udn);
        if (renderer == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Renderer not found: " + udn));
        }

        try {
            String body = request.getBodyAsString();
            int volume = MccJsonSerializer.parseVolumeRequest(body);
            renderer.setVolume(volume);
            return jsonResponse(HttpStatus.OK,
                    MccJsonSerializer.volumeToJson(renderer.getVolume(), renderer.getMute()));
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Set volume failed: " + e.getMessage()));
        }
    }

    /**
     * Handles PUT /api/renderers/{udn}/mute - sets mute state.
     *
     * <p>Request body: {"muted": true}
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with updated volume JSON
     * @since 0.1.0
     */
    public HttpResponse setMute(HttpContext ctx, HttpRequest request) {
        String udn = MccPlaybackHandler.extractRendererUdn(request.getUri(), "/mute");
        MediaRendererProxy renderer = findRenderer(udn);
        if (renderer == null) {
            return jsonResponse(HttpStatus.NOT_FOUND,
                    MccJsonSerializer.errorToJson("Renderer not found: " + udn));
        }

        try {
            String body = request.getBodyAsString();
            boolean muted = MccJsonSerializer.parseMuteRequest(body);
            renderer.setMute(muted);
            return jsonResponse(HttpStatus.OK,
                    MccJsonSerializer.volumeToJson(renderer.getVolume(), renderer.getMute()));
        } catch (Exception e) {
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    MccJsonSerializer.errorToJson("Set mute failed: " + e.getMessage()));
        }
    }

    private MediaRendererProxy findRenderer(String udn) {
        if (udn == null) return null;
        for (var renderer : controlPoint.discoverMediaRenderers()) {
            if (renderer.getUdn().equals(udn)) {
                return renderer;
            }
        }
        return null;
    }
}
