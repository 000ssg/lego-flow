/**
 * SNMPv3 (Simple Network Management Protocol) implementation per RFC 3411-3418.
 *
 * <p>This module provides a complete SNMPv3 protocol stack including:
 * <ul>
 *   <li>Protocol messages — SNMPv3 wrapper, PDU types, VarBinds, SNMP data types</li>
 *   <li>BER encoding/decoding — using the shared {@code ssg.legoflow.network.common} codec</li>
 *   <li>USM security — HMAC-MD5-96, HMAC-SHA-96 auth; DES-CBC, AES-128-CFB privacy</li>
 *   <li>Manager (client) — GET, GETNEXT, GETBULK, SET, trap/inform receiver</li>
 *   <li>Agent (server) — request processing, in-memory MIB tree, trap/inform sender</li>
 *   <li>VACM — view-based access control per RFC 3415</li>
 * </ul>
 *
 * @since 1.0.0
 */
package ssg.legoflow.network.snmp;
