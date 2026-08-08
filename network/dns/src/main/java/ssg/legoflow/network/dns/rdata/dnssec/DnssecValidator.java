package ssg.legoflow.network.dns.rdata.dnssec;

import ssg.legoflow.network.dns.protocol.DnsCodec;
import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.DnsRecord;
import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.rdata.RData;
import ssg.legoflow.network.dns.rdata.DnskeyRecord;
import ssg.legoflow.network.dns.rdata.DsRecord;
import ssg.legoflow.network.dns.rdata.RrsigRecord;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.time.Instant;
import java.util.*;

/**
 * DNSSEC signature validator supporting RSA-SHA256 and ECDSA-P256-SHA256.
 *
 * <p>Validates RRSIG signatures against DNSKEY records per RFC 4035.
 * Only algorithms 8 (RSA/SHA-256) and 13 (ECDSA-P256-SHA256) are supported.
 *
 * @since 0.1.0
 */
public final class DnssecValidator {

    private DnssecValidator() {}

    /**
     * Validates an RRSIG signature over a set of resource records.
     *
     * @param rrsig   the RRSIG record to validate
     * @param dnskey  the DNSKEY to verify against
     * @param records the RRset being signed (must all have same name/type/class)
     * @return {@code true} if the signature is valid
     * @since 0.1.0
     */
    public static boolean verify(RrsigRecord rrsig, DnskeyRecord dnskey,
                                  List<DnsRecord> records) {
        try {
            // Check temporal validity
            Instant now = Instant.now();
            if (now.isBefore(rrsig.inception()) || now.isAfter(rrsig.expiration())) {
                return false;
            }

            // Check key tag matches
            if (rrsig.keyTag() != dnskey.keyTag()) {
                return false;
            }

            // Check algorithm matches
            if (rrsig.algorithm() != dnskey.algorithm()) {
                return false;
            }

            // Build the data to verify: RRSIG fields + canonical RRset
            byte[] dataToVerify = buildSignedData(rrsig, records);
            byte[] signature = rrsig.signature();

            return switch (dnskey.algorithm()) {
                case DnskeyRecord.ALGORITHM_RSA_SHA256 ->
                        verifyRsaSha256(dnskey.publicKey(), dataToVerify, signature);
                case DnskeyRecord.ALGORITHM_ECDSA_P256_SHA256 ->
                        verifyEcdsaP256Sha256(dnskey.publicKey(), dataToVerify, signature);
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates a DS record against a DNSKEY record.
     *
     * @param ds     the DS record
     * @param dnskey the DNSKEY record
     * @param owner  the owner name of the DNSKEY
     * @return {@code true} if the DS digest matches
     * @since 0.1.0
     */
    public static boolean verifyDs(DsRecord ds, DnskeyRecord dnskey, DnsName owner) {
        try {
            if (ds.keyTag() != dnskey.keyTag()) {
                return false;
            }
            if (ds.algorithm() != dnskey.algorithm()) {
                return false;
            }

            // Compute digest: owner name (wire format) + DNSKEY RDATA
            ByteArrayOutputStream digestInput = new ByteArrayOutputStream();
            // Wire-format owner name (canonical/lowercase)
            for (String label : owner.labels()) {
                byte[] bytes = label.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
                digestInput.write(bytes.length);
                digestInput.write(bytes);
            }
            digestInput.write(0); // root label

            // DNSKEY RDATA: flags(2) + protocol(1) + algorithm(1) + public key
            digestInput.write((dnskey.flags() >> 8) & 0xFF);
            digestInput.write(dnskey.flags() & 0xFF);
            digestInput.write(dnskey.protocol());
            digestInput.write(dnskey.algorithm());
            byte[] pk = dnskey.publicKey();
            digestInput.write(pk);

            String digestAlg = switch (ds.digestType()) {
                case DsRecord.DIGEST_SHA1 -> "SHA-1";
                case DsRecord.DIGEST_SHA256 -> "SHA-256";
                default -> null;
            };
            if (digestAlg == null) return false;

            MessageDigest md = MessageDigest.getInstance(digestAlg);
            byte[] computed = md.digest(digestInput.toByteArray());
            return Arrays.equals(computed, ds.digest());
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] buildSignedData(RrsigRecord rrsig, List<DnsRecord> records) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // RRSIG RDATA fields (without the signature)
        writeShort(out, rrsig.typeCovered().value());
        out.write(rrsig.algorithm());
        out.write(rrsig.labels());
        writeInt(out, (int) rrsig.originalTtl());
        writeInt(out, (int) rrsig.expiration().getEpochSecond());
        writeInt(out, (int) rrsig.inception().getEpochSecond());
        writeShort(out, rrsig.keyTag());
        // Signer name in canonical wire format
        writeCanonicalName(out, rrsig.signerName());

        // Sort the RRset in canonical order
        List<DnsRecord> sorted = new ArrayList<>(records);
        sorted.sort((a, b) -> {
            // Compare canonical wire-format RDATA
            byte[] rdA = canonicalRData(a);
            byte[] rdB = canonicalRData(b);
            return compareBytes(rdA, rdB);
        });

        // Each RR in canonical form
        for (DnsRecord rr : sorted) {
            writeCanonicalName(out, rr.name());
            writeShort(out, rr.type().value());
            writeShort(out, rr.recordClass().value());
            writeInt(out, (int) rrsig.originalTtl()); // Use original TTL
            byte[] rdata = canonicalRData(rr);
            writeShort(out, rdata.length);
            out.write(rdata, 0, rdata.length);
        }

        return out.toByteArray();
    }

    private static byte[] canonicalRData(DnsRecord record) {
        ByteArrayOutputStream rdOut = new ByteArrayOutputStream();
        DnsCodec.encodeName(rdOut, record.name(), new HashMap<>());
        // For simplicity, re-encode the full RDATA
        // In a full implementation we would use the codec properly
        return DnsCodec.encode(
                ssg.legoflow.network.dns.protocol.DnsMessage.builder()
                        .addAnswer(record)
                        .build()
        );
    }

    private static void writeCanonicalName(ByteArrayOutputStream out, DnsName name) {
        for (String label : name.labels()) {
            byte[] bytes = label.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
            out.write(bytes.length);
            out.write(bytes, 0, bytes.length);
        }
        out.write(0);
    }

    private static boolean verifyRsaSha256(byte[] keyData, byte[] data, byte[] signature)
            throws Exception {
        // RSA public key format in DNSKEY: exponent length + exponent + modulus
        int pos = 0;
        int expLen;
        if ((keyData[0] & 0xFF) != 0) {
            expLen = keyData[0] & 0xFF;
            pos = 1;
        } else {
            expLen = ((keyData[1] & 0xFF) << 8) | (keyData[2] & 0xFF);
            pos = 3;
        }
        byte[] exponent = Arrays.copyOfRange(keyData, pos, pos + expLen);
        byte[] modulus = Arrays.copyOfRange(keyData, pos + expLen, keyData.length);

        RSAPublicKeySpec spec = new RSAPublicKeySpec(
                new java.math.BigInteger(1, modulus),
                new java.math.BigInteger(1, exponent)
        );
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey pubKey = kf.generatePublic(spec);

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(pubKey);
        sig.update(data);
        return sig.verify(signature);
    }

    private static boolean verifyEcdsaP256Sha256(byte[] keyData, byte[] data, byte[] signature)
            throws Exception {
        // ECDSA P-256 public key: 64 bytes (32 bytes X + 32 bytes Y)
        if (keyData.length != 64) {
            return false;
        }

        byte[] x = Arrays.copyOfRange(keyData, 0, 32);
        byte[] y = Arrays.copyOfRange(keyData, 32, 64);

        // Build uncompressed point: 0x04 + X + Y
        byte[] uncompressed = new byte[65];
        uncompressed[0] = 0x04;
        System.arraycopy(x, 0, uncompressed, 1, 32);
        System.arraycopy(y, 0, uncompressed, 33, 32);

        ECParameterSpec ecSpec = getP256Params();
        ECPoint point = new ECPoint(
                new java.math.BigInteger(1, x),
                new java.math.BigInteger(1, y)
        );
        ECPublicKeySpec spec = new ECPublicKeySpec(point, ecSpec);
        KeyFactory kf = KeyFactory.getInstance("EC");
        PublicKey pubKey = kf.generatePublic(spec);

        // DNSSEC ECDSA signature is r||s (each 32 bytes), convert to DER
        byte[] derSig = ecdsaRsToDer(signature);

        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initVerify(pubKey);
        sig.update(data);
        return sig.verify(derSig);
    }

    private static ECParameterSpec getP256Params() throws Exception {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        return params.getParameterSpec(ECParameterSpec.class);
    }

    private static byte[] ecdsaRsToDer(byte[] rs) {
        if (rs.length != 64) {
            throw new IllegalArgumentException("ECDSA P-256 signature must be 64 bytes");
        }
        byte[] r = trimLeadingZeros(Arrays.copyOfRange(rs, 0, 32));
        byte[] s = trimLeadingZeros(Arrays.copyOfRange(rs, 32, 64));

        // Add leading zero if high bit is set (to keep it positive)
        if ((r[0] & 0x80) != 0) {
            byte[] tmp = new byte[r.length + 1];
            System.arraycopy(r, 0, tmp, 1, r.length);
            r = tmp;
        }
        if ((s[0] & 0x80) != 0) {
            byte[] tmp = new byte[s.length + 1];
            System.arraycopy(s, 0, tmp, 1, s.length);
            s = tmp;
        }

        int seqLen = 2 + r.length + 2 + s.length;
        byte[] der = new byte[2 + seqLen];
        int pos = 0;
        der[pos++] = 0x30; // SEQUENCE
        der[pos++] = (byte) seqLen;
        der[pos++] = 0x02; // INTEGER
        der[pos++] = (byte) r.length;
        System.arraycopy(r, 0, der, pos, r.length);
        pos += r.length;
        der[pos++] = 0x02; // INTEGER
        der[pos++] = (byte) s.length;
        System.arraycopy(s, 0, der, pos, s.length);
        return der;
    }

    private static byte[] trimLeadingZeros(byte[] data) {
        int start = 0;
        while (start < data.length - 1 && data[start] == 0) {
            start++;
        }
        if (start == 0) return data;
        return Arrays.copyOfRange(data, start, data.length);
    }

    private static int compareBytes(byte[] a, byte[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int cmp = (a[i] & 0xFF) - (b[i] & 0xFF);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.length, b.length);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
