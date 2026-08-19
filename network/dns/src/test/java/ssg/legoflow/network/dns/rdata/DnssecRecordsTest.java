package ssg.legoflow.network.dns.rdata;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.protocol.*;
import ssg.legoflow.network.dns.rdata.dnssec.TypeBitMaps;
import java.time.Instant;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class DnssecRecordsTest {

    @Test
    void testDsRecord() {
        byte[] digest = new byte[32]; // SHA-256 digest
        for (int i = 0; i < 32; i++) digest[i] = (byte) i;
        
        DsRecord ds = new DsRecord(1234, 8, DsRecord.DIGEST_SHA256, digest);
        assertThat(ds.type()).isEqualTo(RecordType.DS);
        assertThat(ds.keyTag()).isEqualTo(1234);
        assertThat(ds.algorithm()).isEqualTo(8); // ECDSA P-256
    }

    @Test
    void testDsDigestClone() {
        byte[] digest = new byte[32];
        DsRecord ds = new DsRecord(100, 8, DsRecord.DIGEST_SHA256, digest);
        byte[] result = ds.digest();
        assertThat(result).isNotSameAs(digest);
    }

    @Test
    void testDnskeyRecord() {
        byte[] publicKey = new byte[]{1, 2, 3, 4, 5};
        DnskeyRecord dk = new DnskeyRecord(256, 3, DnskeyRecord.ALGORITHM_RSA_SHA256, publicKey);
        assertThat(dk.type()).isEqualTo(RecordType.DNSKEY);
        assertThat(dk.flags()).isEqualTo(256); // Zone Signing Key
    }

    @Test
    void testDnskeyPublicConstants() {
        assertThat(DnskeyRecord.FLAG_ZONE_KEY).isEqualTo(256);
        assertThat(DnskeyRecord.FLAG_SEP).isEqualTo(1);
        assertThat(DnskeyRecord.ALGORITHM_RSA_SHA256).isEqualTo(8);
    }

    @Test
    void testRrsigRecord() {
        byte[] signature = new byte[64];
        RrsigRecord rrsig = new RrsigRecord(
            RecordType.A, 8, (short) 2, 3600L,
            Instant.now().plusSeconds(3600), Instant.now(),
            1234, DnsName.of("k.example.com."), signature
        );
        assertThat(rrsig.type()).isEqualTo(RecordType.RRSIG);
    }

    @Test
    void testNsecRecord() {
        Set<RecordType> types = Set.of(RecordType.A, RecordType.NS);
        NsecRecord nsec = new NsecRecord(DnsName.of("next.example.com"), types);
        assertThat(nsec.type()).isEqualTo(RecordType.NSEC);
    }

    @Test
    void testNsec3Record() {
        byte[] salt = new byte[]{1, 2, 3, 4};
        byte[] nextHash = new byte[20]; // SHA-1 hash length
        Set<RecordType> types = Set.of(RecordType.A, RecordType.NS, RecordType.SOA);
        Nsec3Record nsec3 = new Nsec3Record(
            (byte) 1, // SHA-1
            (byte) 0, // flags
            10,       // iterations
            salt,
            nextHash,
            types
        );
        assertThat(nsec3.type()).isEqualTo(RecordType.NSEC3);
    }

    @Test
    void testNsec3ParamRecord() {
        byte[] salt = new byte[]{1, 2, 3};
        Nsec3ParamRecord n3p = new Nsec3ParamRecord((byte) 1, (byte) 0, 5, salt);
        assertThat(n3p.type()).isEqualTo(RecordType.NSEC3PARAM);
    }

    @Test
    void testTypeBitMapsEncode() {
        Set<RecordType> types = Set.of(RecordType.A, RecordType.NS, RecordType.SOA);
        byte[] encoded = TypeBitMaps.encode(types);
        assertThat(encoded).isNotEmpty();
        
        // Verify decoding matches
        Set<RecordType> decoded = TypeBitMaps.decode(encoded, 0, encoded.length);
        assertThat(decoded).containsExactlyInAnyOrderElementsOf(types);
    }

    @Test
    void testTypeBitMapsDecodeEmpty() {
        byte[] empty = new byte[0];
        Set<RecordType> result = TypeBitMaps.decode(empty, 0, 0);
        assertThat(result).isEmpty();
    }
}
