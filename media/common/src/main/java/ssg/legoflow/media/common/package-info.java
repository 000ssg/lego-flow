/**
 * Lego Flow Media Common module — shared SDP parser library (RFC 4566).
 *
 * <p>Provides complete SDP (Session Description Protocol) parsing, writing, and
 * construction facilities used by both RTSP and SIP modules. All implementations
 * are JDK-only with no external dependencies.
 *
 * <h2>Packages</h2>
 * <ul>
 *   <li>{@code sdp} — SDP model: session, origin, connection, timing, media descriptions,
 *       attributes, ICE candidates, DTLS fingerprints</li>
 *   <li>{@code codec} — SDP parser (text → model), writer (model → text), and
 *       offer/answer negotiator (RFC 3264)</li>
 *   <li>{@code payload} — Static and dynamic RTP payload type definitions and registry</li>
 *   <li>{@code builder} — Fluent builders for SessionDescription and MediaDescription</li>
 * </ul>
 *
 * @since 0.1.0
 */
package ssg.legoflow.media.common;
