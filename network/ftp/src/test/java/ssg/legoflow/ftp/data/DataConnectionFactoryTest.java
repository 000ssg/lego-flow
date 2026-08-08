package ssg.legoflow.ftp.data;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.*;

class DataConnectionFactoryTest {

    @Test
    void testCreateActiveServer() throws Exception {
        InetAddress addr = InetAddress.getByName("127.0.0.1");
        ActiveDataConnection conn = DataConnectionFactory.createActiveServer(addr, 12345);
        assertThat(conn).isNotNull();
        assertThat(conn.getAddress()).isEqualTo(addr);
        assertThat(conn.getPort()).isEqualTo(12345);
    }

    @Test
    void testCreateActiveClient() throws Exception {
        InetAddress addr = InetAddress.getByName("0.0.0.0");
        ActiveDataConnection conn = DataConnectionFactory.createActiveClient(addr, 0);
        assertThat(conn).isNotNull();
        assertThat(conn.getAddress()).isEqualTo(addr);
        assertThat(conn.getPort()).isEqualTo(0);
    }

    @Test
    void testCreatePassiveServer() throws Exception {
        InetAddress addr = InetAddress.getByName("127.0.0.1");
        PassiveDataConnection conn = DataConnectionFactory.createPassiveServer(addr, 54321);
        assertThat(conn).isNotNull();
        assertThat(conn.getAddress()).isEqualTo(addr);
    }

    @Test
    void testCreatePassiveClient() throws Exception {
        InetAddress addr = InetAddress.getByName("10.0.0.1");
        PassiveDataConnection conn = DataConnectionFactory.createPassiveClient(addr, 9876);
        assertThat(conn).isNotNull();
        assertThat(conn.getAddress()).isEqualTo(addr);
    }

    @Test
    void testFactoryWithLoopbackAddress() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        
        ActiveDataConnection activeServer = DataConnectionFactory.createActiveServer(loopback, 1000);
        assertThat(activeServer).isInstanceOf(ActiveDataConnection.class);
        assertThat(activeServer.getAddress()).isEqualTo(loopback);
        
        ActiveDataConnection activeClient = DataConnectionFactory.createActiveClient(loopback, 2000);
        assertThat(activeClient).isInstanceOf(ActiveDataConnection.class);
        
        PassiveDataConnection passiveServer = DataConnectionFactory.createPassiveServer(loopback, 3000);
        assertThat(passiveServer).isInstanceOf(PassiveDataConnection.class);
        
        PassiveDataConnection passiveClient = DataConnectionFactory.createPassiveClient(loopback, 4000);
        assertThat(passiveClient).isInstanceOf(PassiveDataConnection.class);
    }

    @Test
    void testFactoryWithAnyLocalAddress() throws Exception {
        InetAddress any = InetAddress.getByName("0.0.0.0");
        
        ActiveDataConnection conn1 = DataConnectionFactory.createActiveServer(any, 100);
        assertThat(conn1.getPort()).isEqualTo(100);
        
        PassiveDataConnection conn2 = DataConnectionFactory.createPassiveClient(any, 200);
        assertThat(conn2.getAddress()).isEqualTo(any);
    }

    @Test
    void testFactoryWithHighPorts() throws Exception {
        InetAddress addr = InetAddress.getByName("192.168.1.1");
        
        ActiveDataConnection active = DataConnectionFactory.createActiveClient(addr, 65535);
        assertThat(active.getPort()).isEqualTo(65535);
        
        PassiveDataConnection passive = DataConnectionFactory.createPassiveServer(addr, 65534);
        assertThat(passive.getAddress()).isEqualTo(addr);
    }

    @Test
    void testActiveServerConnectionIsCorrectType() throws Exception {
        InetAddress addr = InetAddress.getByName("127.0.0.1");
        DataConnection conn = DataConnectionFactory.createActiveServer(addr, 12345);
        assertThat(conn).isInstanceOf(ActiveDataConnection.class);
        assertThat(conn).isNotInstanceOf(PassiveDataConnection.class);
    }

    @Test
    void testPassiveClientConnectionIsCorrectType() throws Exception {
        InetAddress addr = InetAddress.getByName("10.0.0.1");
        DataConnection conn = DataConnectionFactory.createPassiveClient(addr, 9876);
        assertThat(conn).isInstanceOf(PassiveDataConnection.class);
        assertThat(conn).isNotInstanceOf(ActiveDataConnection.class);
    }
}
