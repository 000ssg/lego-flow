/**
 * Shared BER/ASN.1 codec for network protocols (LDAP, SNMP, X.509).
 *
 * <p>Provides a complete implementation of Basic Encoding Rules (BER) and
 * Distinguished Encoding Rules (DER) for ASN.1 types, including a sealed
 * type hierarchy, TLV encoding/decoding, and an OID registry.
 *
 * <h2>Packages</h2>
 * <ul>
 *   <li>{@code asn1} — Sealed ASN.1 type hierarchy (BOOLEAN, INTEGER, SEQUENCE, etc.)</li>
 *   <li>{@code ber} — BER encoder/decoder with TLV tag and length handling</li>
 *   <li>{@code der} — DER encoder with canonical ordering and definite lengths</li>
 *   <li>{@code oid} — OID value object, registry, and standard OID constants</li>
 * </ul>
 *
 * @since 0.1.0
 */
package ssg.legoflow.network.common;
