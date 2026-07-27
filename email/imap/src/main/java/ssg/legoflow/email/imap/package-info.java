/**
 * Lego Flow IMAP module -- IMAP4rev2 (RFC 9051) client and server.
 *
 * <p>Provides a complete IMAP4rev2 implementation with extensions for
 * IDLE (RFC 2177), CONDSTORE (RFC 7162), NAMESPACE (RFC 2342),
 * SORT/THREAD (RFC 5256), MOVE (RFC 6851), and LIST-EXTENDED (RFC 5258).
 *
 * <h2>Packages</h2>
 * <ul>
 *   <li>{@code protocol} -- IMAP command/response codec, tags, status, fetch/search/sort criteria</li>
 *   <li>{@code server} -- IMAP server with virtual threads, mailbox management, search engine</li>
 *   <li>{@code client} -- IMAP client with connection lifecycle, folder operations, IDLE support</li>
 *   <li>{@code condstore} -- CONDSTORE extension: modification sequences, conditional store</li>
 *   <li>{@code demo} -- Demo applications showcasing client and server usage</li>
 * </ul>
 *
 * @since 1.0.0
 */
package ssg.legoflow.email.imap;
