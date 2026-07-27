package ssg.legoflow.ssh.hostkey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class KnownHostsTest {

    @TempDir Path tempDir;

    @Test
    void testEmptyKnownHosts() {
        KnownHosts kh = new KnownHosts(List.of());
        assertThat(kh.entries()).isEmpty();
    }

    @Test
    void testVerifyNotFound() {
        KnownHosts kh = new KnownHosts(List.of());
        assertThat(kh.verify("host", 22, "ssh-ed25519", new byte[]{1}))
                .isEqualTo(KnownHosts.VerifyResult.NOT_FOUND);
    }

    @Test
    void testAddAndVerifyOk() {
        KnownHosts kh = new KnownHosts(new java.util.ArrayList<>());
        byte[] blob = {1, 2, 3};
        kh.addEntry("host.example.com", 22, "ssh-ed25519", blob);
        assertThat(kh.verify("host.example.com", 22, "ssh-ed25519", blob))
                .isEqualTo(KnownHosts.VerifyResult.OK);
    }

    @Test
    void testVerifyChanged() {
        KnownHosts kh = new KnownHosts(new java.util.ArrayList<>());
        kh.addEntry("host.example.com", 22, "ssh-ed25519", new byte[]{1, 2, 3});
        assertThat(kh.verify("host.example.com", 22, "ssh-ed25519", new byte[]{4, 5, 6}))
                .isEqualTo(KnownHosts.VerifyResult.CHANGED);
    }

    @Test
    void testNonStandardPort() {
        KnownHosts kh = new KnownHosts(new java.util.ArrayList<>());
        byte[] blob = {1, 2, 3};
        kh.addEntry("host", 2222, "ssh-ed25519", blob);
        assertThat(kh.verify("host", 2222, "ssh-ed25519", blob))
                .isEqualTo(KnownHosts.VerifyResult.OK);
    }

    @Test
    void testSaveAndLoad() throws Exception {
        Path file = tempDir.resolve("known_hosts");
        KnownHosts kh = new KnownHosts(new java.util.ArrayList<>());
        kh.addEntry("host1", 22, "ssh-ed25519", new byte[]{1, 2, 3});
        kh.addEntry("host2", 22, "rsa-sha2-256", new byte[]{4, 5, 6});
        kh.save(file);

        KnownHosts loaded = KnownHosts.load(file);
        assertThat(loaded.entries()).hasSize(2);
    }

    @Test
    void testLoadNonexistent() throws Exception {
        Path file = tempDir.resolve("nonexistent");
        KnownHosts loaded = KnownHosts.load(file);
        assertThat(loaded.entries()).isEmpty();
    }

    @Test
    void testEntryParse() {
        KnownHosts.Entry entry = KnownHosts.Entry.parse(
                "host.example.com ssh-ed25519 AQID");
        assertThat(entry.hostPattern()).isEqualTo("host.example.com");
        assertThat(entry.keyType()).isEqualTo("ssh-ed25519");
    }

    @Test
    void testEntryMatchesHost() {
        KnownHosts.Entry entry = new KnownHosts.Entry("host1,host2", "ssh-ed25519", new byte[]{1});
        assertThat(entry.matchesHost("host1")).isTrue();
        assertThat(entry.matchesHost("host2")).isTrue();
        assertThat(entry.matchesHost("host3")).isFalse();
    }
}
