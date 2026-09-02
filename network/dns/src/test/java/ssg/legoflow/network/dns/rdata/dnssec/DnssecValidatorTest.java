package ssg.legoflow.network.dns.rdata.dnssec;

import ssg.legoflow.network.dns.protocol.*;
import ssg.legoflow.network.dns.rdata.DnskeyRecord;
import ssg.legoflow.network.dns.rdata.DsRecord;
import ssg.legoflow.network.dns.rdata.RrsigRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for DnssecValidator covering signature verification and DS validation.
 */
@Timeout(10)
class DnssecValidatorTest {

    @Test
    void testVerifyWithNullRecordsReturnsFalse() {
        assertThat(DnssecValidator.verify(null, null, null)).isFalse();
    }

    @Test
    void testVerifyDsWithMatchingDigest() throws Exception {
        DnsName owner = DnsName.of("example.com.");
        
        byte[] publicKey = "testkey".getBytes(StandardCharsets.UTF_8);
        DnskeyRecord dnskey = new DnskeyRecord(DnskeyRecord.FLAG_ZONE_KEY, 3,
            DnskeyRecord.ALGORITHM_RSA_SHA256, publicKey);
        
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] ownerWire = wireFormatName(owner);
        byte[] keyData = buildDnskeyRdata(dnskey);
        md.update(ownerWire);
        md.update(keyData);
        byte[] digest = md.digest();
        
        DsRecord ds = new DsRecord(dnskey.keyTag(), 
            dnskey.algorithm(), 
            DsRecord.DIGEST_SHA256, digest);
        
        assertThat(DnssecValidator.verifyDs(ds, dnskey, owner)).isTrue();
    }

    @Test
    void testVerifyDsWithWrongKeyTagReturnsFalse() throws Exception {
        DnsName owner = DnsName.of("example.com.");
        byte[] publicKey = "testkey".getBytes(StandardCharsets.UTF_8);
        
        DnskeyRecord dnskey = new DnskeyRecord(DnskeyRecord.FLAG_ZONE_KEY, 3,
            DnskeyRecord.ALGORITHM_RSA_SHA256, publicKey);
        
        DsRecord ds = new DsRecord(99999, 
            dnskey.algorithm(), 
            DsRecord.DIGEST_SHA256, new byte[32]);
        
        assertThat(DnssecValidator.verifyDs(ds, dnskey, owner)).isFalse();
    }

    @Test
    void testVerifyDsWithWrongAlgorithmReturnsFalse() throws Exception {
        DnsName owner = DnsName.of("example.com.");
        byte[] publicKey = "testkey".getBytes(StandardCharsets.UTF_8);
        
        DnskeyRecord dnskey = new DnskeyRecord(DnskeyRecord.FLAG_ZONE_KEY, 3,
            DnskeyRecord.ALGORITHM_RSA_SHA256, publicKey);
        
        DsRecord ds = new DsRecord(dnskey.keyTag(), 
            DnskeyRecord.ALGORITHM_ECDSA_P256_SHA256, 
            DsRecord.DIGEST_SHA256, new byte[32]);
        
        assertThat(DnssecValidator.verifyDs(ds, dnskey, owner)).isFalse();
    }

    @Test
    void testVerifyDsWithUnknownDigestTypeReturnsFalse() throws Exception {
        DnsName owner = DnsName.of("example.com.");
        byte[] publicKey = "testkey".getBytes(StandardCharsets.UTF_8);
        
        DnskeyRecord dnskey = new DnskeyRecord(DnskeyRecord.FLAG_ZONE_KEY, 3,
            DnskeyRecord.ALGORITHM_RSA_SHA256, publicKey);
        
        DsRecord ds = new DsRecord(dnskey.keyTag(), 
            dnskey.algorithm(), 
            (byte) 99,
            new byte[32]);
        
        assertThat(DnssecValidator.verifyDs(ds, dnskey, owner)).isFalse();
    }

    @Test
    void testVerifyDsWithSha1Digest() throws Exception {
        DnsName owner = DnsName.of("example.com.");
        byte[] publicKey = "testkey".getBytes(StandardCharsets.UTF_8);
        
        DnskeyRecord dnskey = new DnskeyRecord(DnskeyRecord.FLAG_ZONE_KEY, 3,
            DnskeyRecord.ALGORITHM_RSA_SHA256, publicKey);
        
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] ownerWire = wireFormatName(owner);
        byte[] keyData = buildDnskeyRdata(dnskey);
        md.update(ownerWire);
        md.update(keyData);
        byte[] digest = md.digest();
        
        DsRecord ds = new DsRecord(dnskey.keyTag(), 
            dnskey.algorithm(), 
            DsRecord.DIGEST_SHA1, digest);
        
        assertThat(DnssecValidator.verifyDs(ds, dnskey, owner)).isTrue();
    }

    @Test
    void testVerifyExpiredSignatureReturnsFalse() {
        Instant past = Instant.now().minusSeconds(86400);
        Instant olderPast = Instant.now().minusSeconds(172800);
        
        RrsigRecord rrsig = new RrsigRecord(RecordType.A, 
            DnskeyRecord.ALGORITHM_RSA_SHA256, 1, 3600,
            past, olderPast,
            42424, DnsName.of("example.com."), new byte[128]);
        
        assertThat(DnssecValidator.verify(rrsig, null, List.of())).isFalse();
    }

    @Test
    void testVerifyNotYetValidSignatureReturnsFalse() {
        Instant future = Instant.now().plusSeconds(86400);
        Instant furtherFuture = Instant.now().plusSeconds(172800);
        
        RrsigRecord rrsig = new RrsigRecord(RecordType.A, 
            DnskeyRecord.ALGORITHM_RSA_SHA256, 1, 3600,
            furtherFuture, future,
            42424, DnsName.of("example.com."), new byte[128]);
        
        assertThat(DnssecValidator.verify(rrsig, null, List.of())).isFalse();
    }

    @Test
    void testVerifyUnsupportedAlgorithmReturnsFalse() {
        Instant now = Instant.now();
        
        RrsigRecord rrsig = new RrsigRecord(RecordType.A, 
            5, // RSA/SHA-1 - unsupported
            1, 3600,
            now.minusSeconds(3600), now.plusSeconds(3600),
            42424, DnsName.of("example.com."), new byte[128]);
        
        DnskeyRecord dnskey = new DnskeyRecord(DnskeyRecord.FLAG_ZONE_KEY, 3,
            5, "testkey".getBytes(StandardCharsets.UTF_8));
        
        assertThat(DnssecValidator.verify(rrsig, dnskey, List.of())).isFalse();
    }

    @Test
    void testVerifyWithNullDnsKeyReturnsFalse() {
        Instant now = Instant.now();
        RrsigRecord rrsig = new RrsigRecord(RecordType.A, 
            DnskeyRecord.ALGORITHM_RSA_SHA256, 1, 3600,
            now.minusSeconds(3600), now.plusSeconds(3600),
            42424, DnsName.of("example.com."), new byte[128]);
        
        assertThat(DnssecValidator.verify(rrsig, null, List.of())).isFalse();
    }

    @Test
    void testDnskeyConstructorAndProperties() {
        byte[] key = "test".getBytes(StandardCharsets.UTF_8);
        
        DnskeyRecord dnskey = new DnskeyRecord(256, 3, 
            DnskeyRecord.ALGORITHM_RSA_SHA256, key);
        
        assertThat(dnskey.flags()).isEqualTo(256);
        assertThat(dnskey.protocol()).isEqualTo(3);
        assertThat(dnskey.algorithm()).isEqualTo(DnskeyRecord.ALGORITHM_RSA_SHA256);
    }

    private byte[] wireFormatName(DnsName name) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (String label : name.labels()) {
            byte[] bytes = label.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
            out.write(bytes.length);
            out.write(bytes);
        }
        out.write(0);
        return out.toByteArray();
    }

    private byte[] buildDnskeyRdata(DnskeyRecord dnskey) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write((dnskey.flags() >> 8) & 0xFF);
        out.write(dnskey.flags() & 0xFF);
        out.write(dnskey.protocol());
        out.write(dnskey.algorithm());
        out.write(dnskey.publicKey());
        return out.toByteArray();
    }
}
