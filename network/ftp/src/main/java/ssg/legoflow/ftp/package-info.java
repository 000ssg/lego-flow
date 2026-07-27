/**
 * Lego Flow FTP module — File Transfer Protocol (RFC 959) and FTPS (RFC 4217).
 *
 * <p>Provides complete client and server implementations of the FTP protocol,
 * including TLS security (FTPS), active and passive data connections,
 * directory listings (LIST, NLST, MLSD), and file transfers.
 *
 * <h2>Packages</h2>
 * <ul>
 *   <li>{@code protocol} — FTP command/reply encoding, transfer types, structure, mode</li>
 *   <li>{@code data} — Active and passive data connections, transfer handling</li>
 *   <li>{@code security} — FTPS/TLS configuration and negotiation (RFC 4217)</li>
 *   <li>{@code client} — FTP client with directory listing parsers</li>
 *   <li>{@code server} — FTP server with pluggable filesystem and authentication</li>
 *   <li>{@code demo} — Example applications</li>
 * </ul>
 *
 * @since 1.0.0
 */
package ssg.legoflow.ftp;
