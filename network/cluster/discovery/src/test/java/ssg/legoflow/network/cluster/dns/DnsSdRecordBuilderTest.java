package ssg.legoflow.network.cluster.dns;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.DnsRecord;
import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.protocol.ResponseCode;
import ssg.legoflow.network.dns.rdata.ARecord;
import ssg.legoflow.network.dns.rdata.PtrRecord;
import ssg.legoflow.network.dns.rdata.SrvRecord;
import ssg.legoflow.network.dns.rdata.TxtRecord;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class DnsSdRecordBuilderTest {

    private static final InetAddress LOCAL_ADDR;

    static {
        try {
            LOCAL_ADDR = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void buildPtr_correctFormat() {
        DnsRecord record = DnsSdRecordBuilder.buildPtr(
                "_http._tcp.local.", "MyServer._http._tcp.local.", 120);

        assertThat(record.type()).isEqualTo(RecordType.PTR);
        PtrRecord ptr = (PtrRecord) record.rdata();
        assertThat(ptr.domainName().toCanonical()).isEqualTo("myserver._http._tcp.local");
        assertThat(record.ttl()).isEqualTo(120);
    }

    @Test
    void buildSrv_correctFormat() {
        DnsRecord record = DnsSdRecordBuilder.buildSrv(
                "MyServer._http._tcp.local.", 10, 200, 8080, "localhost", "local", 120);

        assertThat(record.type()).isEqualTo(RecordType.SRV);
        SrvRecord srv = (SrvRecord) record.rdata();
        assertThat(srv.priority()).isEqualTo(10);
        assertThat(srv.weight()).isEqualTo(200);
        assertThat(srv.port()).isEqualTo(8080);
        assertThat(srv.target().toCanonical()).isEqualTo("localhost.local");
    }

    @Test
    void buildA_correctFormat() {
        DnsRecord record = DnsSdRecordBuilder.buildA(
                "localhost", "local", LOCAL_ADDR, 120);

        assertThat(record.type()).isEqualTo(RecordType.A);
        ARecord a = (ARecord) record.rdata();
        assertThat(a.address()).isEqualTo(LOCAL_ADDR);
        assertThat(record.ttl()).isEqualTo(120);
    }

    @Test
    void buildTxt_correctFormat() {
        Map<String, String> attrs = Map.of("path", "/", "version", "1.0");
        DnsRecord record = DnsSdRecordBuilder.buildTxt(
                "MyServer._http._tcp.local.", attrs, 120);

        assertThat(record.type()).isEqualTo(RecordType.TXT);
        TxtRecord txt = (TxtRecord) record.rdata();
        assertThat(txt.strings()).containsExactlyInAnyOrder("path=/", "version=1.0");
    }

    @Test
    void buildResponse_authoritativeWithAllRecords() {
        DnsSdServiceRecord serviceRecord = DnsSdServiceRecord.builder()
                .serviceType("_http._tcp")
                .domain("local")
                .instanceName("Test")
                .targetHostname("localhost")
                .targetAddress(LOCAL_ADDR)
                .port(8080)
                .ttl(Duration.ofSeconds(120))
                .build();

        DnsMessage response = DnsSdRecordBuilder.buildResponse(serviceRecord, 42);

        assertThat(response.header().id()).isEqualTo(42);
        assertThat(response.header().qr()).isTrue();
        assertThat(response.header().aa()).isTrue();
        assertThat(response.header().rd()).isFalse();
        assertThat(response.header().rCode()).isEqualTo(ResponseCode.NOERROR);
        assertThat(response.answers()).hasSize(4);
    }

    @Test
    void escapeTxtValue_escapesBackslash() {
        assertThat(DnsSdRecordBuilder.escapeTxtValue("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    void escapeTxtValue_escapesQuote() {
        assertThat(DnsSdRecordBuilder.escapeTxtValue("a\"b")).isEqualTo("a\\\"b");
    }

    @Test
    void escapeTxtValue_noEscapeNeeded() {
        assertThat(DnsSdRecordBuilder.escapeTxtValue("plain")).isEqualTo("plain");
    }

    @Test
    void unescapeTxtValue_reversesEscaping() {
        assertThat(DnsSdRecordBuilder.unescapeTxtValue("a\\\\b")).isEqualTo("a\\b");
        assertThat(DnsSdRecordBuilder.unescapeTxtValue("a\\\"b")).isEqualTo("a\"b");
    }

    @Test
    void unescapeTxtValue_trailingBackslash() {
        assertThat(DnsSdRecordBuilder.unescapeTxtValue("a\\")).isEqualTo("a\\");
    }

    @Test
    void nullTxtValue_throws() {
        assertThatThrownBy(() -> DnsSdRecordBuilder.escapeTxtValue(null))
                .isInstanceOf(NullPointerException.class);
    }
}
