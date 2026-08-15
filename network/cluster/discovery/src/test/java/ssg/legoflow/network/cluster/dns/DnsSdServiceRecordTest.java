package ssg.legoflow.network.cluster.dns;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.protocol.DnsRecord;
import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.rdata.ARecord;
import ssg.legoflow.network.dns.rdata.PtrRecord;
import ssg.legoflow.network.dns.rdata.SrvRecord;
import ssg.legoflow.network.dns.rdata.TxtRecord;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class DnsSdServiceRecordTest {

    private static final Duration TTL = Duration.ofSeconds(120);
    private static final InetAddress LOCAL_ADDR;

    static {
        try {
            LOCAL_ADDR = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void constructor_createsValidRecord() {
        DnsSdServiceRecord record = new DnsSdServiceRecord(
                "_http._tcp", "local", "MyServer",
                "localhost", LOCAL_ADDR, 8080, 0, 50,
                Map.of("path", "/"), TTL);

        assertThat(record.serviceType()).isEqualTo("_http._tcp");
        assertThat(record.domain()).isEqualTo("local");
        assertThat(record.instanceName()).isEqualTo("MyServer");
        assertThat(record.targetHostname()).isEqualTo("localhost");
        assertThat(record.targetAddress()).isEqualTo(LOCAL_ADDR);
        assertThat(record.port()).isEqualTo(8080);
        assertThat(record.priority()).isEqualTo(0);
        assertThat(record.weight()).isEqualTo(50);
        assertThat(record.txtAttributes()).containsEntry("path", "/");
        assertThat(record.ttl()).isEqualTo(TTL);
    }

    @Test
    void serviceDomain_correctFormat() {
        DnsSdServiceRecord record = new DnsSdServiceRecord(
                "_grpc._tcp", "local", "MyGrpc", "host", LOCAL_ADDR, 50051, 0, 50,
                Map.of(), TTL);
        assertThat(record.serviceDomain()).isEqualTo("_grpc._tcp.local");
    }

    @Test
    void instanceFqdn_correctFormat() {
        DnsSdServiceRecord record = new DnsSdServiceRecord(
                "_http._tcp", "local", "Web1", "host", LOCAL_ADDR, 80, 0, 50,
                Map.of(), TTL);
        assertThat(record.instanceFqdn()).isEqualTo("Web1._http._tcp.local");
    }

    @Test
    void ptrRecord_correctTypeAndNames() {
        DnsSdServiceRecord record = new DnsSdServiceRecord(
                "_http._tcp", "local", "Web1", "host", LOCAL_ADDR, 80, 0, 50,
                Map.of(), TTL);

        DnsRecord ptr = record.ptrRecord();
        assertThat(ptr.type()).isEqualTo(RecordType.PTR);
        assertThat(((PtrRecord) ptr.rdata()).domainName().toCanonical())
                .isEqualTo("web1._http._tcp.local");
    }

    @Test
    void srvRecord_correctFormat() {
        DnsSdServiceRecord record = new DnsSdServiceRecord(
                "_http._tcp", "local", "Web1", "host", LOCAL_ADDR, 8080, 10, 200,
                Map.of(), TTL);

        DnsRecord srv = record.srvRecord();
        assertThat(srv.type()).isEqualTo(RecordType.SRV);
        SrvRecord srvData = (SrvRecord) srv.rdata();
        assertThat(srvData.priority()).isEqualTo(10);
        assertThat(srvData.weight()).isEqualTo(200);
        assertThat(srvData.port()).isEqualTo(8080);
        assertThat(srvData.target().toCanonical()).isEqualTo("host.local");
    }

    @Test
    void aRecord_correctAddress() {
        DnsSdServiceRecord record = new DnsSdServiceRecord(
                "_http._tcp", "local", "Web1", "host", LOCAL_ADDR, 80, 0, 50,
                Map.of(), TTL);

        DnsRecord a = record.aRecord();
        assertThat(a.type()).isEqualTo(RecordType.A);
        ARecord aData = (ARecord) a.rdata();
        assertThat(aData.address()).isEqualTo(LOCAL_ADDR);
    }

    @Test
    void txtRecord_correctAttributes() {
        Map<String, String> attrs = Map.of("path", "/", "version", "1.0");
        DnsSdServiceRecord record = new DnsSdServiceRecord(
                "_http._tcp", "local", "Web1", "host", LOCAL_ADDR, 80, 0, 50,
                attrs, TTL);

        DnsRecord txt = record.txtRecord();
        assertThat(txt.type()).isEqualTo(RecordType.TXT);
        TxtRecord txtData = (TxtRecord) txt.rdata();
        assertThat(txtData.strings()).containsExactlyInAnyOrder("path=/", "version=1.0");
    }

    @Test
    void allRecords_returnsAllFourRecords() {
        DnsSdServiceRecord record = new DnsSdServiceRecord(
                "_http._tcp", "local", "Web1", "host", LOCAL_ADDR, 80, 0, 50,
                Map.of("key", "val"), TTL);

        List<DnsRecord> all = record.allRecords();
        assertThat(all).hasSize(4);
        assertThat(all.get(0).type()).isEqualTo(RecordType.PTR);
        assertThat(all.get(1).type()).isEqualTo(RecordType.SRV);
        assertThat(all.get(2).type()).isEqualTo(RecordType.A);
        assertThat(all.get(3).type()).isEqualTo(RecordType.TXT);
    }

    @Test
    void equality_sameInstanceFqdn() {
        DnsSdServiceRecord r1 = new DnsSdServiceRecord(
                "_http._tcp", "local", "Web1", "host1", LOCAL_ADDR, 80, 0, 50,
                Map.of(), TTL);
        DnsSdServiceRecord r2 = new DnsSdServiceRecord(
                "_http._tcp", "local", "Web1", "host2", LOCAL_ADDR, 8080, 10, 100,
                Map.of("x", "y"), Duration.ofSeconds(60));

        assertThat(r1).isEqualTo(r2); // Same instance FQDN
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void inequality_differentInstanceFqdn() {
        DnsSdServiceRecord r1 = new DnsSdServiceRecord(
                "_http._tcp", "local", "Web1", "host", LOCAL_ADDR, 80, 0, 50,
                Map.of(), TTL);
        DnsSdServiceRecord r2 = new DnsSdServiceRecord(
                "_http._tcp", "local", "Web2", "host", LOCAL_ADDR, 80, 0, 50,
                Map.of(), TTL);

        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    void toString_containsInstanceName() {
        DnsSdServiceRecord record = new DnsSdServiceRecord(
                "_http._tcp", "local", "Web1", "host", LOCAL_ADDR, 8080, 0, 50,
                Map.of(), TTL);

        String str = record.toString();
        assertThat(str).contains("Web1");
        assertThat(str).contains("_http._tcp.local");
        assertThat(str).contains("8080");
    }

    @Test
    void builder_createsRecord() {
        DnsSdServiceRecord record = DnsSdServiceRecord.builder()
                .serviceType("_http._tcp")
                .domain("local")
                .instanceName("MyServer")
                .targetHostname("localhost")
                .targetAddress(LOCAL_ADDR)
                .port(8080)
                .priority(10)
                .weight(100)
                .addTxtAttribute("node_id", "abc")
                .ttl(Duration.ofSeconds(60))
                .build();

        assertThat(record.instanceFqdn()).isEqualTo("MyServer._http._tcp.local");
        assertThat(record.port()).isEqualTo(8080);
        assertThat(record.priority()).isEqualTo(10);
        assertThat(record.txtAttributes()).containsEntry("node_id", "abc");
    }

    @Test
    void nullServiceType_throws() {
        assertThatThrownBy(() -> new DnsSdServiceRecord(
                null, "local", "name", "host", LOCAL_ADDR, 80, 0, 50, Map.of(), TTL))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankInstanceName_throws() {
        assertThatThrownBy(() -> new DnsSdServiceRecord(
                "_http._tcp", "local", "  ", "host", LOCAL_ADDR, 80, 0, 50, Map.of(), TTL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
