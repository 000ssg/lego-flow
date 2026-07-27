/**
 * Lego Flow SIP module -- Session Initiation Protocol (RFC 3261).
 *
 * <p>Provides a complete SIP implementation for signaling and session management
 * in VoIP and multimedia communication. The protocol is text-based with HTTP-like
 * syntax and supports UDP and TCP transports.
 *
 * <h2>Packages</h2>
 * <ul>
 *   <li>{@code protocol} -- SIP message model: sealed interface with request/response records,
 *       methods, status codes, codec for parsing/serialization</li>
 *   <li>{@code uri} -- SIP URI parsing: {@code sip:}, {@code sips:}, {@code tel:} URI schemes
 *       with parameters and headers</li>
 *   <li>{@code header} -- Typed SIP headers: Via, From/To with tag, Call-ID, CSeq, Contact,
 *       Route, Record-Route, Authorization, compact form support</li>
 *   <li>{@code transaction} -- Transaction layer (RFC 3261 section 17): INVITE and non-INVITE
 *       client/server transactions with state machines and retransmission timers</li>
 *   <li>{@code dialog} -- Dialog management: early/confirmed/terminated states, creation from
 *       INVITE/response pairs, in-dialog routing, CSeq tracking</li>
 *   <li>{@code registration} -- Registration: client REGISTER flow and registrar server
 *       with binding storage and expiration</li>
 *   <li>{@code transport} -- Transport layer: UDP (DatagramChannel) and TCP (SocketChannel)
 *       with Via header management</li>
 *   <li>{@code agent} -- SIP User Agent: combined UAC/UAS with call setup and teardown</li>
 * </ul>
 *
 * @since 1.0.0
 */
package ssg.legoflow.media.sip;
