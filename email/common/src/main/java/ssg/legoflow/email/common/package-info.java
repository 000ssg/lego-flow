/**
 * Lego Flow Email Common module — shared MIME parsing library (RFC 2045-2049).
 *
 * <p>Provides complete MIME message parsing, writing, and construction facilities
 * used by both SMTP and IMAP modules. All implementations are JDK-only with no
 * external dependencies.
 *
 * <h2>Packages</h2>
 * <ul>
 *   <li>{@code mime} — MIME message, parts, multipart containers, parser, and writer</li>
 *   <li>{@code encoding} — Base64, Quoted-Printable, RFC 2047 encoded-word, charset utilities</li>
 *   <li>{@code address} — RFC 5322 email address, mailbox, group, and address list parsing</li>
 *   <li>{@code header} — Header fields, date-time parsing, message-id, parameter parsing</li>
 *   <li>{@code builder} — Fluent builders for MIME messages, parts, and multipart containers</li>
 * </ul>
 *
 * @since 1.0.0
 */
package ssg.legoflow.email.common;
