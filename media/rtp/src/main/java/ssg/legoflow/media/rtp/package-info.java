/**
 * Lego Flow RTP/RTCP module -- Real-time Transport Protocol (RFC 3550).
 *
 * <p>Provides complete RTP and RTCP implementations for real-time media
 * transport including packet encoding/decoding, session management, jitter
 * buffering, and UDP transport.
 *
 * <h2>Packages</h2>
 * <ul>
 *   <li>{@code packet} -- RTP packet model: RtpPacket, RtpHeader, HeaderExtension</li>
 *   <li>{@code rtcp} -- RTCP packet types: SR, RR, SDES, BYE, APP, CompoundPacket</li>
 *   <li>{@code codec} -- RTP/RTCP binary codecs: encode/decode to/from ByteBuffer</li>
 *   <li>{@code session} -- RTP session management: SSRC tracking, collision detection,
 *       participant table, sender/receiver statistics</li>
 *   <li>{@code buffer} -- Jitter buffer: packet reordering, adaptive playout delay,
 *       late/duplicate/out-of-order handling</li>
 *   <li>{@code transport} -- UDP transport: RtpTransport, RtpSender, RtpReceiver,
 *       paired RTP/RTCP ports</li>
 * </ul>
 *
 * @since 0.1.0
 */
package ssg.legoflow.media.rtp;
