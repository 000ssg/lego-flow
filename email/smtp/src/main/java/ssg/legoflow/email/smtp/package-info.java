/**
 * Lego Flow SMTP module -- Simple Mail Transfer Protocol (RFC 5321).
 *
 * <p>Provides complete SMTP client and server implementations with ESMTP
 * extension support. All implementations are JDK-only with no external
 * dependencies.
 *
 * <h2>Packages</h2>
 * <ul>
 *   <li>{@code protocol} -- SMTP command/reply codec, ESMTP extensions, dot-stuffing,
 *       enhanced status codes</li>
 *   <li>{@code auth} -- SASL authentication mechanisms: PLAIN, LOGIN, CRAM-MD5, XOAUTH2</li>
 *   <li>{@code server} -- SMTP server with virtual threads, message store, relay control</li>
 *   <li>{@code client} -- SMTP client with STARTTLS, pipelining, chunked transfer</li>
 *   <li>{@code dsn} -- Delivery Status Notifications (RFC 3461/3464)</li>
 *   <li>{@code demo} -- Example applications for common SMTP scenarios</li>
 * </ul>
 *
 * @since 1.0.0
 */
package ssg.legoflow.email.smtp;
