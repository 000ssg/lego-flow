package ssg.legoflow.network.dns.rdata;
import org.junit.jupiter.api.Timeout;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.RecordType;

import java.util.List;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class RDataCommonTest {

    @Test
    void testNsRecord() {
        NsRecord ns = NsRecord.of("ns1.example.com");
        assertThat(ns.type()).isEqualTo(RecordType.NS);
        assertThat(ns.nameServer().toString()).isEqualTo("ns1.example.com");
    }

    @Test
    void testCnameRecord() {
        CnameRecord cname = new CnameRecord(DnsName.of("canonical.example.com"));
        assertThat(cname.type()).isEqualTo(RecordType.CNAME);
    }

    @Test
    void testPtrRecord() {
        PtrRecord ptr = new PtrRecord(DnsName.of("host.example.com"));
        assertThat(ptr.type()).isEqualTo(RecordType.PTR);
    }

    @Test
    void testMxRecordValid() {
        MxRecord mx = MxRecord.of(10, "mail.example.com");
        assertThat(mx.type()).isEqualTo(RecordType.MX);
        assertThat(mx.preference()).isEqualTo(10);
    }

    @Test
    void testMxRecordInvalidPreference() {
        assertThatThrownBy(() -> new MxRecord(-1, DnsName.of("mail.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MxRecord(65536, DnsName.of("mail.com")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMxRecordComparable() {
        MxRecord mx1 = MxRecord.of(10, "mail1.com");
        MxRecord mx2 = MxRecord.of(20, "mail2.com");
        assertThat(mx1.compareTo(mx2)).isLessThan(0);
    }

    @Test
    void testTxtRecord() {
        TxtRecord txt = new TxtRecord(List.of("v=spf1", "~all"));
        assertThat(txt.type()).isEqualTo(RecordType.TXT);
        assertThat(txt.strings()).hasSize(2);
    }

    @Test
    void testSrvRecordValid() {
        SrvRecord srv = SrvRecord.of(10, 50, 443, "www.example.com");
        assertThat(srv.type()).isEqualTo(RecordType.SRV);
        assertThat(srv.priority()).isEqualTo(10);
        assertThat(srv.weight()).isEqualTo(50);
        assertThat(srv.port()).isEqualTo(443);
    }

    @Test
    void testSrvRecordValidation() {
        assertThatThrownBy(() -> new SrvRecord(-1, 0, 80, DnsName.of("x.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SrvRecord(0, -1, 80, DnsName.of("x.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SrvRecord(0, 0, -1, DnsName.of("x.com")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSrvRecordComparable() {
        SrvRecord s1 = SrvRecord.of(10, 100, 80, "a.com");
        SrvRecord s2 = SrvRecord.of(10, 50, 80, "b.com");
        // Same priority, higher weight first
        assertThat(s1.compareTo(s2)).isLessThan(0);
    }

    @Test
    void testSoaRecord() {
        SoaRecord soa = SoaRecord.of("ns1.example.com", "admin.example.com", 1L, 3600, 900, 604800, 86400);
        assertThat(soa.type()).isEqualTo(RecordType.SOA);
        assertThat(soa.serial()).isEqualTo(1L);
        assertThat(soa.refresh()).isEqualTo(3600);
    }

    @Test
    void testCaaRecordValid() {
        CaaRecord caa = new CaaRecord(0, "issue", "letsencrypt.org");
        assertThat(caa.type()).isEqualTo(RecordType.CAA);
        assertThat(caa.flags()).isEqualTo(0);
    }

    @Test
    void testCaaRecordInvalidFlags() {
        assertThatThrownBy(() -> new CaaRecord(256, "issue", "x.org"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaaRecord(-1, "issue", "x.org"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNaptrRecord() {
        NaptrRecord naptr = NaptrRecord.of(100, 50, "u", "SIP+D2U", "!.*!sip:user@example.com!", ".");
        assertThat(naptr.type()).isEqualTo(RecordType.NAPTR);
        assertThat(naptr.order()).isEqualTo(100);
        assertThat(naptr.preference()).isEqualTo(50);
    }

    @Test
    void testRawRData() {
        byte[] data = new byte[]{1, 2, 3};
        RawRData raw = new RawRData(RecordType.A, data);
        assertThat(raw.type()).isEqualTo(RecordType.A);
        assertThat(raw.data()).containsExactly(1, 2, 3);
    }

    @Test
    void testOptRecordBasic() {
        OptRecord opt = new OptRecord(4096, 0, 0, true, List.of());
        assertThat(opt.type()).isEqualTo(RecordType.OPT);
        assertThat(opt.udpPayloadSize()).isEqualTo(4096);
        assertThat(opt.options()).isEmpty();
    }

    @Test
    void testOptRecordTtlField() {
        OptRecord opt = new OptRecord(4096, 1, 0, true, List.of());
        int ttl = opt.ttlField();
        // Extended RCODE=1 in upper byte
        assertThat((ttl >> 24) & 0xFF).isEqualTo(1);
        // DO bit set
        assertThat(ttl & 0x8000).isEqualTo(0x8000);
    }

    @Test
    void testOptRecordParseTtlField() {
        int ttl = (2 << 24) | (0 << 16) | 0x8000; // extRcode=2, version=0, DO=true
        OptRecord.OptFlags flags = OptRecord.parseTtlField(ttl);
        assertThat(flags.extendedRcode()).isEqualTo(2);
        assertThat(flags.version()).isEqualTo(0);
        assertThat(flags.dnssecOk()).isTrue();
    }

    @Test
    void testOptEdnsOption() {
        OptRecord.EdnsOption option = new OptRecord.EdnsOption(OptRecord.EdnsOption.NSID, new byte[]{1, 2, 3});
        assertThat(option.code()).isEqualTo(OptRecord.EdnsOption.NSID);
        assertThat(option.data()).containsExactly(1, 2, 3);
    }

    @Test
    void testOptEdnsOptionDataClone() {
        byte[] original = new byte[]{4, 5, 6};
        OptRecord.EdnsOption option = new OptRecord.EdnsOption(0x10, original);
        byte[] result = option.data();
        assertThat(result).containsExactly(4, 5, 6);
        assertThat(result).isNotSameAs(original);
    }

    @Test
    void testOptOf() {
        OptRecord opt = OptRecord.of(4096, true);
        assertThat(opt.udpPayloadSize()).isEqualTo(4096);
        assertThat(opt.dnssecOk()).isTrue();
    }
}
