/**
 * Lego Flow RTSP 2.0 module -- Real-Time Streaming Protocol (RFC 7826).
 *
 * <p>Provides complete RTSP 2.0 client and server implementations for controlling
 * real-time media streams. The protocol is text-based with HTTP-like syntax and
 * supports interleaved binary data (RTP over RTSP TCP).
 *
 * <h2>Packages</h2>
 * <ul>
 *   <li>{@code protocol} -- RTSP message model: requests, responses, methods, status codes,
 *       headers, transport header parsing, range header parsing, codec</li>
 *   <li>{@code server} -- RTSP server: TCP listener, session management, media source
 *       abstraction, stream controller state machine</li>
 *   <li>{@code client} -- RTSP client: connection management, session tracking, setup results</li>
 *   <li>{@code interleaved} -- Interleaved binary framing ($ channel length data) for
 *       RTP-over-RTSP-TCP transport</li>
 *   <li>{@code demo} -- Demo applications: streaming server, client playback control</li>
 * </ul>
 *
 * @since 0.1.0
 */
package ssg.legoflow.media.rtsp;
