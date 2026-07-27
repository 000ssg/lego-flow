package ssg.legoflow.auth.gssapi;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Objects;

/**
 * Handles SPNEGO (Simple and Protected GSSAPI Negotiation) token processing
 * including creation and extraction of NegTokenInit and NegTokenResp messages
 * per RFC 4178.
 *
 * <p>SPNEGO tokens use ASN.1 DER encoding. This handler provides methods to
 * create and parse the outer SPNEGO wrapper around inner mechanism tokens
 * (typically Kerberos V5).</p>
 *
 * @since 1.0.0
 */
public final class SpnegoTokenHandler {

    /** ASN.1 application tag for SPNEGO NegTokenInit (context [0]). */
    private static final int NEG_TOKEN_INIT_TAG = 0xa0;

    /** ASN.1 application tag for SPNEGO NegTokenResp (context [1]). */
    private static final int NEG_TOKEN_RESP_TAG = 0xa1;

    /** ASN.1 tag for SEQUENCE. */
    private static final int SEQUENCE_TAG = 0x30;

    /** ASN.1 tag for OID. */
    private static final int OID_TAG = 0x06;

    /** ASN.1 tag for OCTET STRING. */
    private static final int OCTET_STRING_TAG = 0x04;

    /** ASN.1 tag for ENUMERATED. */
    private static final int ENUMERATED_TAG = 0x0a;

    /** SPNEGO OID bytes: 1.3.6.1.5.5.2 */
    private static final byte[] SPNEGO_OID_BYTES = {
            0x06, 0x06, 0x2b, 0x06, 0x01, 0x05, 0x05, 0x02
    };

    /** Kerberos V5 OID bytes: 1.2.840.113554.1.2.2 */
    private static final byte[] KERBEROS_OID_BYTES = {
            0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x12, 0x01, 0x02, 0x02
    };

    /** SPNEGO negResult: accept-completed (0). */
    public static final int ACCEPT_COMPLETED = 0;

    /** SPNEGO negResult: accept-incomplete (1). */
    public static final int ACCEPT_INCOMPLETE = 1;

    /** SPNEGO negResult: reject (2). */
    public static final int REJECT = 2;

    private SpnegoTokenHandler() {
        // utility class
    }

    /**
     * Creates a SPNEGO NegTokenInit wrapping a mechanism token.
     *
     * <p>The NegTokenInit contains the Kerberos V5 OID in the mechTypes list
     * and the provided mechanism token in the mechToken field.</p>
     *
     * @param mechToken the inner mechanism token (e.g., Kerberos AP-REQ)
     * @return the encoded SPNEGO NegTokenInit
     * @since 1.0.0
     */
    public static byte[] createNegTokenInit(byte[] mechToken) {
        Objects.requireNonNull(mechToken, "mechToken must not be null");

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Build mechTypes SEQUENCE containing Kerberos OID
        byte[] mechTypes = wrapSequence(KERBEROS_OID_BYTES);

        // Build mechToken [2] OCTET STRING
        byte[] mechTokenField = wrapContextTag(2, wrapOctetString(mechToken));

        // Build mechTypes [0] wrapper
        byte[] mechTypesField = wrapContextTag(0, mechTypes);

        // Build inner SEQUENCE (mechTypes + mechToken)
        byte[] innerSeq = wrapSequence(concat(mechTypesField, mechTokenField));

        // Build NegTokenInit [0] wrapper
        byte[] negTokenInit = wrapContextTag(0, innerSeq);

        // Build outer APPLICATION [0] with SPNEGO OID
        byte[] spnegoWrapper = concat(SPNEGO_OID_BYTES, negTokenInit);
        return wrapApplication(0x60, spnegoWrapper);
    }

    /**
     * Creates a SPNEGO NegTokenResp wrapping a mechanism token.
     *
     * @param mechToken the inner mechanism response token
     * @param complete  true if authentication is complete, false if continuing
     * @return the encoded SPNEGO NegTokenResp
     * @since 1.0.0
     */
    public static byte[] createNegTokenResp(byte[] mechToken, boolean complete) {
        Objects.requireNonNull(mechToken, "mechToken must not be null");

        // Build negResult [0] ENUMERATED
        byte[] negResult = wrapContextTag(0, new byte[]{
                ENUMERATED_TAG, 0x01, (byte) (complete ? ACCEPT_COMPLETED : ACCEPT_INCOMPLETE)
        });

        // Build supportedMech [1] — Kerberos OID
        byte[] supportedMech = wrapContextTag(1, KERBEROS_OID_BYTES);

        // Build responseToken [2] OCTET STRING
        byte[] responseToken = wrapContextTag(2, wrapOctetString(mechToken));

        // Build inner SEQUENCE
        byte[] innerSeq = wrapSequence(concat(negResult, concat(supportedMech, responseToken)));

        // Build NegTokenResp [1] wrapper
        return wrapContextTag(NEG_TOKEN_RESP_TAG & 0xff, innerSeq);
    }

    /**
     * Extracts the inner mechanism token from a SPNEGO token (either NegTokenInit or NegTokenResp).
     *
     * @param spnegoToken the SPNEGO token to extract from
     * @return the inner mechanism token
     * @throws GssException if the token format is invalid
     * @since 1.0.0
     */
    public static byte[] extractMechToken(byte[] spnegoToken) throws GssException {
        Objects.requireNonNull(spnegoToken, "spnegoToken must not be null");
        if (spnegoToken.length < 2) {
            throw new GssException("SPNEGO token too short");
        }

        int pos = 0;
        int tag = spnegoToken[pos] & 0xff;

        // Skip outer APPLICATION wrapper if present (0x60)
        if (tag == 0x60) {
            pos++;
            int len = readDerLength(spnegoToken, pos);
            pos += derLengthSize(len);
            // Skip SPNEGO OID
            if (pos < spnegoToken.length && (spnegoToken[pos] & 0xff) == OID_TAG) {
                int oidLen = spnegoToken[pos + 1] & 0xff;
                pos += 2 + oidLen;
            }
            tag = spnegoToken[pos] & 0xff;
        }

        // Now we should be at either NegTokenInit [0] or NegTokenResp [1]
        if (tag == NEG_TOKEN_INIT_TAG || tag == (NEG_TOKEN_RESP_TAG & 0xff)) {
            pos++;
            int len = readDerLength(spnegoToken, pos);
            pos += derLengthSize(len);
        }

        // Search for mechToken field — context tag [2] containing OCTET STRING
        return findMechTokenInSequence(spnegoToken, pos);
    }

    /**
     * Returns whether the given token appears to be a SPNEGO token.
     *
     * <p>Checks for the SPNEGO application wrapper (0x60) followed by the SPNEGO OID,
     * or a NegTokenResp context tag (0xa1).</p>
     *
     * @param token the token to check
     * @return true if the token appears to be SPNEGO
     * @since 1.0.0
     */
    public static boolean isSpnegoToken(byte[] token) {
        if (token == null || token.length < 2) {
            return false;
        }
        int firstByte = token[0] & 0xff;
        // APPLICATION [0] IMPLICIT (NegTokenInit wrapped)
        if (firstByte == 0x60 && token.length > SPNEGO_OID_BYTES.length + 2) {
            int pos = 1;
            int len = readDerLength(token, pos);
            pos += derLengthSize(len);
            // Check for SPNEGO OID
            if (pos + SPNEGO_OID_BYTES.length <= token.length) {
                for (int i = 0; i < SPNEGO_OID_BYTES.length; i++) {
                    if (token[pos + i] != SPNEGO_OID_BYTES[i]) {
                        return false;
                    }
                }
                return true;
            }
        }
        // NegTokenResp [1]
        return firstByte == (NEG_TOKEN_RESP_TAG & 0xff);
    }

    /**
     * Encodes a token to Base64.
     *
     * @param token the token bytes
     * @return the Base64-encoded string
     * @since 1.0.0
     */
    public static String encodeBase64(byte[] token) {
        Objects.requireNonNull(token, "token must not be null");
        return Base64.getEncoder().encodeToString(token);
    }

    /**
     * Decodes a Base64-encoded token.
     *
     * @param encoded the Base64-encoded string
     * @return the decoded token bytes
     * @since 1.0.0
     */
    public static byte[] decodeBase64(String encoded) {
        Objects.requireNonNull(encoded, "encoded must not be null");
        return Base64.getDecoder().decode(encoded);
    }

    // ---- ASN.1 DER encoding helpers ----

    private static byte[] wrapSequence(byte[] content) {
        return wrapTag(SEQUENCE_TAG, content);
    }

    private static byte[] wrapOctetString(byte[] content) {
        return wrapTag(OCTET_STRING_TAG, content);
    }

    private static byte[] wrapContextTag(int tagNumber, byte[] content) {
        return wrapTag(0xa0 | tagNumber, content);
    }

    private static byte[] wrapApplication(int tag, byte[] content) {
        return wrapTag(tag, content);
    }

    private static byte[] wrapTag(int tag, byte[] content) {
        byte[] lengthBytes = encodeDerLength(content.length);
        byte[] result = new byte[1 + lengthBytes.length + content.length];
        result[0] = (byte) tag;
        System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length);
        System.arraycopy(content, 0, result, 1 + lengthBytes.length, content.length);
        return result;
    }

    private static byte[] encodeDerLength(int length) {
        if (length < 0x80) {
            return new byte[]{(byte) length};
        } else if (length < 0x100) {
            return new byte[]{(byte) 0x81, (byte) length};
        } else if (length < 0x10000) {
            return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) length};
        } else {
            return new byte[]{(byte) 0x83, (byte) (length >> 16), (byte) (length >> 8), (byte) length};
        }
    }

    static int readDerLength(byte[] data, int offset) {
        if (offset >= data.length) return 0;
        int first = data[offset] & 0xff;
        if (first < 0x80) {
            return first;
        }
        int numBytes = first & 0x7f;
        int length = 0;
        for (int i = 0; i < numBytes && (offset + 1 + i) < data.length; i++) {
            length = (length << 8) | (data[offset + 1 + i] & 0xff);
        }
        return length;
    }

    static int derLengthSize(int length) {
        if (length < 0x80) return 1;
        if (length < 0x100) return 2;
        if (length < 0x10000) return 3;
        return 4;
    }

    private static byte[] findMechTokenInSequence(byte[] data, int pos) throws GssException {
        // Look for SEQUENCE, then scan for context tag [2]
        if (pos < data.length && (data[pos] & 0xff) == SEQUENCE_TAG) {
            pos++;
            int seqLen = readDerLength(data, pos);
            pos += derLengthSize(seqLen);
        }

        int end = data.length;
        while (pos < end) {
            int tag = data[pos] & 0xff;
            pos++;
            if (pos >= end) break;
            int len = readDerLength(data, pos);
            pos += derLengthSize(len);

            if (tag == 0xa2) {
                // Found mechToken field [2] — should contain OCTET STRING
                if (pos < end && (data[pos] & 0xff) == OCTET_STRING_TAG) {
                    pos++;
                    int octetLen = readDerLength(data, pos);
                    pos += derLengthSize(octetLen);
                    byte[] result = new byte[octetLen];
                    System.arraycopy(data, pos, result, 0, Math.min(octetLen, end - pos));
                    return result;
                }
                // Might be directly the token bytes
                byte[] result = new byte[len];
                System.arraycopy(data, pos, result, 0, Math.min(len, end - pos));
                return result;
            }
            pos += len;
        }
        throw new GssException("No mechanism token found in SPNEGO token");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
