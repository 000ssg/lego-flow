package ssg.legoflow.network.dns.rdata;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.RecordType;
import static org.assertj.core.api.Assertions.*;
class DnsRDataExtendedTest {

    @Test void aRecordConstruction() {
        var r = ARecord.of("127.0.0.1");
        assertThat(r.type()).isEqualTo(RecordType.A);
    }

    @Test void nsRecordConstruction() {
        var r = new NsRecord(DnsName.of("ns1.example.com."));
        assertThat(r.type()).isEqualTo(RecordType.NS);
    }

    @Test void cnameRecordConstruction() {
        var r = new CnameRecord(DnsName.of("alias.example.com."));
        assertThat(r.type()).isEqualTo(RecordType.CNAME);
    }

    @Test void ptrRecordConstruction() {
        var r = new PtrRecord(DnsName.of("hostname.example.com."));
        assertThat(r.type()).isEqualTo(RecordType.PTR);
    }

    @Test void mxRecordConstruction() {
        var r = new MxRecord(10, DnsName.of("mail.example.com."));
        assertThat(r.type()).isEqualTo(RecordType.MX);
    }

    @Test void rawRDataConstruction() {
        var r = new RawRData(RecordType.TXT, new byte[]{1, 2, 3});
        assertThat(r.type()).isEqualTo(RecordType.TXT);
    }
}
