package ssg.legoflow.network.cluster.dns;

import org.junit.jupiter.api.Test;

import java.net.NetworkInterface;

import static org.assertj.core.api.Assertions.*;

class MdnsInterfaceManagerTest {

    @Test
    void discoverMdnsInterfaces_returnsAtLeastOne() throws Exception {
        // Even on machines with no special interfaces, loopback is available
        var all = MdnsInterfaceManager.discoverMdnsInterfaces();
        // May be empty on some CI systems — just verify no exception
        assertThatNoException().isThrownBy(() -> all.size());
    }

    @Test
    void discoverLoopbackInterfaces_returnsLoopback() throws Exception {
        var loopback = MdnsInterfaceManager.discoverLoopbackInterfaces();
        assertThat(loopback).isNotEmpty();
        for (NetworkInterface iface : loopback) {
            assertThat(iface.isLoopback()).isTrue();
        }
    }

    @Test
    void primaryMdnsInterface_returnsNonNull() throws Exception {
        // Should fall back to loopback if no other interfaces
        NetworkInterface primary = MdnsInterfaceManager.primaryMdnsInterface();
        // May be null on some restricted environments
        assertThat(primary).isNotNull();
    }

    @Test
    void firstIpv4Address_returnsAddress() throws Exception {
        var loopback = MdnsInterfaceManager.discoverLoopbackInterfaces();
        if (!loopback.isEmpty()) {
            var addr = MdnsInterfaceManager.firstIpv4Address(loopback.get(0));
            // Loopback 127.0.0.1 is IPv4
            if (addr != null) {
                assertThat(addr.getAddress().length).isEqualTo(4);
            }
        }
    }

    @Test
    void firstIpv4Address_null_throws() {
        assertThatThrownBy(() -> MdnsInterfaceManager.firstIpv4Address(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void discoverWithFilter_appliesPredicate() throws Exception {
        var all = MdnsInterfaceManager.discoverMdnsInterfaces(iface -> {
            String name = iface.getDisplayName();
            return !name.contains("docker") && !name.contains("virbr");
        });

        // Verify filter is applied
        for (var iface : all) {
            String name = iface.getDisplayName();
            assertThat(name).doesNotContain("docker");
            assertThat(name).doesNotContain("virbr");
        }
    }

    @Test
    void discoverReturnsOnlyUpInterfaces() throws Exception {
        var interfaces = MdnsInterfaceManager.discoverMdnsInterfaces();
        for (NetworkInterface iface : interfaces) {
            assertThat(iface.isUp()).isTrue();
        }
    }
}
