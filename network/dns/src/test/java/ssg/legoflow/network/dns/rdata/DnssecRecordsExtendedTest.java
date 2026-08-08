package ssg.legoflow.network.dns.rdata;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.protocol.*;
import ssg.legoflow.network.dns.rdata.dnssec.TypeBitMaps;
import java.time.Instant;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class DnssecRecordsExtendedTest {

    @Test void testDnskeyIsZoneKey() {
        var dk = new DnskeyRecord(DnskeyRecord.FLAG_ZONE_KEY, 3, 
                DnskeyRecord.ALGORITHM_RSA_SHA256, new byte[]{1,2,3});
        assertThat(dk.isZoneKey()).isTrue();
    }

    @Test void testDnskeyIsSecureEntryPoint() {
        var dk = new DnskeyRecord(DnskeyRecord.FLAG_SEP, 3, 
                DnskeyRecord.ALGORITHM_ECDSA_P256_SHA256, new byte[]{4,5,6});
        assertThat(dk.isSecureEntryPoint()).isTrue();
    }

    @Test void testDnskeyBothFlags() {
        var dk = new DnskeyRecord(DnskeyRecord.FLAG_ZONE_KEY | DnskeyRecord.FLAG_SEP, 3, 
                DnskeyRecord.ALGORITHM_RSA_SHA256, new byte[]{7,8,9});
        assertThat(dk.isZoneKey()).isTrue();
        assertThat(dk.isSecureEntryPoint()).isTrue();
    }

    @Test void testDnskeyNoFlags() {
        var dk = new DnskeyRecord(0, 3, 
                DnskeyRecord.ALGORITHM_RSA_SHA256, new byte[]{10});
        assertThat(dk.isZoneKey()).isFalse();
        assertThat(dk.isSecureEntryPoint()).isFalse();
    }

    @Test void testDnskeyPublicClone() {
        byte[] key = new byte[]{1, 2, 3};
        var dk = new DnskeyRecord(256, 3, 8, key);
        byte[] cloned = dk.publicKey();
        assertThat(cloned).containsExactly(1, 2, 3);
        cloned[0] = 99;
        assertThat(dk.publicKey()[0]).isEqualTo((byte) 1);
    }

    @Test void testDsRecordSha256() {
        byte[] digest = new byte[32];
        var ds = new DsRecord(5678, 8, DsRecord.DIGEST_SHA256, digest);
        assertThat(ds.type()).isEqualTo(RecordType.DS);
    }

    @Test void testDsRecordSha1() {
        byte[] digest = new byte[20];
        var ds = new DsRecord(4321, 5, DsRecord.DIGEST_SHA1, digest);
        assertThat(ds.type()).isEqualTo(RecordType.DS);
    }

    @Test void testDsRecordKeyTagMatches() {
        byte[] digest = new byte[32];
        var ds = new DsRecord(9999, 8, DsRecord.DIGEST_SHA256, digest);
        assertThat(ds.keyTag()).isEqualTo(9999);
    }

    @Test void testRrsigFuture() {
        byte[] sig = new byte[64];
        var rrsig = new RrsigRecord(RecordType.A, 8, (short) 2, 3600,
                Instant.now().plusSeconds(1000), Instant.now().plusSeconds(2000),
                1234, DnsName.of("k.example.com."), sig);
        assertThat(rrsig.originalTtl()).isEqualTo(3600);
    }

    @Test void testNsecRecordTypeBitmap() {
        var nsec = new NsecRecord(DnsName.of("next.example.com"), 
                Set.of(RecordType.A, RecordType.NS));
        assertThat(nsec.nextDomainName()).isEqualTo(DnsName.of("next.example.com"));
    }

    @Test void testNsec3ParamRecord() {
        var np = new Nsec3ParamRecord(1, 0, 10, new byte[]{1,2,3,4});
        assertThat(np.type()).isEqualTo(RecordType.NSEC3PARAM);
        assertThat(np.hashAlgorithm()).isEqualTo(1);
    }

    @Test void testNsec3Flags() {
        byte[] nextHash = new byte[20];
        var nsec3 = new Nsec3Record((byte) 1, (byte) 1, 5, new byte[]{}, nextHash, 
                Set.of(RecordType.A));
        assertThat(nsec3.flags()).isEqualTo(1);
    }

    @Test void testTypeBitMapsEncodeDecode() {
        var types = Set.of(RecordType.A, RecordType.NS, RecordType.MX);
        var bitmaps = TypeBitMaps.encode(types);
        var decoded = TypeBitMaps.decode(bitmaps, 0, bitmaps.length);
        assertThat(decoded).contains(RecordType.A);
        assertThat(decoded).contains(RecordType.NS);
    }

    @Test void testTypeBitMapsSingleRecord() {
        var types = Set.of(RecordType.A);
        var bitmaps = TypeBitMaps.encode(types);
        var decoded = TypeBitMaps.decode(bitmaps, 0, bitmaps.length);
        assertThat(decoded).containsExactly(RecordType.A);
    }

    @Test void testTypeBitMapsManyTypes() {
        Set<RecordType> types = Set.of(
                RecordType.A, RecordType.NS, RecordType.CNAME, RecordType.SOA, 
                RecordType.MX, RecordType.TXT, RecordType.AAAA);
        var bitmaps = TypeBitMaps.encode(types);
        var decoded = TypeBitMaps.decode(bitmaps, 0, bitmaps.length);
        for (var t : types) {
            assertThat(decoded).as("Should contain %s", t).contains(t);
        }
    }

    @Test void testSoaRecord() {
        var soa = new SoaRecord(
                DnsName.of("ns1.example.com."), DnsName.of("admin.example.com."),
                100, 3600, 900, 604800, 86400);
        assertThat(soa.type()).isEqualTo(RecordType.SOA);
        assertThat(soa.serial()).isEqualTo(100);
    }

    @Test void testRawRData() {
        byte[] data = new byte[]{1, 2, 3};
        var raw = new RawRData(RecordType.TXT, data);
        assertThat(raw.type()).isEqualTo(RecordType.TXT);
    }

    @Test void testSrvRecord() {
        var srv = new SrvRecord(10, 20, 443, DnsName.of("server.example.com."));
        assertThat(srv.type()).isEqualTo(RecordType.SRV);
        assertThat(srv.priority()).isEqualTo(10);
    }

    @Test void testNaptrRecord() {
        var naptr = new NaptrRecord(100, 200, "S", "TCP+http", "", 
                DnsName.of("service.example.com."));
        assertThat(naptr.type()).isEqualTo(RecordType.NAPTR);
    }

    @Test void testCaaRecord() {
        var caa = new CaaRecord(0, "issue", "letsencrypt.org");
        assertThat(caa.type()).isEqualTo(RecordType.CAA);
    }
}
