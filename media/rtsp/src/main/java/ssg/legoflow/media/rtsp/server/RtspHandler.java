package ssg.legoflow.media.rtsp.server;

import ssg.legoflow.media.rtsp.protocol.RtspRequest;
import ssg.legoflow.media.rtsp.protocol.RtspResponse;

/**
 * Handler interface for processing RTSP requests on the server.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface RtspHandler {

    /**
     * Handles an RTSP request and produces a response.
     *
     * @param request the incoming request
     * @param session the RTSP session (may be null for OPTIONS/DESCRIBE)
     * @return the response
     */
    RtspResponse handle(RtspRequest request, RtspSession session);
}
