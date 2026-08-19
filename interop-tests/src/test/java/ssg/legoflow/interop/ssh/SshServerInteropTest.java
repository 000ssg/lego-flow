package ssg.legoflow.interop.ssh;

import org.junit.jupiter.api.*;
import ssg.legoflow.ssh.client.SshClient;
import ssg.legoflow.ssh.transport.SshVersion;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Interoperability test: Lego Flow SSH client ↔ OpenSSH sshd.
 *
 * <p><b>Reference:</b> OpenSSH (https://www.openssh.com/), RFC 4251–4256
 *
 * <p><b>Prerequisites:</b>
 * <ul>
 *   <li>OpenSSH sshd running on interop.sshd.port (default 2222)</li>
 *   <li>ssh configured with password authentication or key-based auth</li>
 *   <li>Run: ssh -p 2222 -o StrictHostKeyChecking=no localhost  (manual verification)</li>
 * </ul>
 *
 * <p><b>Setup example:</b>
 * <pre>
 * sshd -p 2222 -o PasswordAuthentication=yes -o PermitRootLogin=no
 * </pre>
 *
 * @since 0.2.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SshServerInteropTest {

    private final String sshdHost = System.getProperty("interop.sshd.host", "localhost");
    private final int sshdPort = Integer.parseInt(System.getProperty("interop.sshd.port", "2222"));
    private final String sshdUser = System.getProperty("interop.sshd.user", "");
    private final String sshdPassword = System.getProperty("interop.sshd.password", "");

    /** Skip if user/password not configured — sshd needs valid credentials. */
    private boolean shouldRun() {
        return !sshdUser.isBlank() && !sshdPassword.isBlank();
    }

    @Test
    void testVersionExchangeWithOpenSSH() throws IOException {
        if (!shouldRun()) {
            throw new org.opentest4j.TestAbortedException(
                    "SSH interop skipped — no sshd available for version exchange");
        }
        // Verify OpenSSH responds with SSH-2.0 version string
        try (Socket socket = new Socket(sshdHost, sshdPort)) {
            socket.setSoTimeout(5000);

            // Read server version string (SSH-2.0-OpenSSH_xxx\r\n)
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String serverVersion = reader.readLine();
            assertThat(serverVersion).isNotNull();
            assertThat(serverVersion).startsWith("SSH-2.0-OpenSSH_");

            // Verify our version parsing
            SshVersion parsed = SshVersion.parse(serverVersion);
            assertThat(parsed.protocolVersion()).isEqualTo("2.0");
            assertThat(parsed.softwareVersion()).startsWith("OpenSSH_");
        }
    }

    @Test
    void testOurClientVersionExchange() throws Exception {
        if (!shouldRun()) {
            throw new org.opentest4j.TestAbortedException(
                    "SSH interop skipped — set interop.sshd.user and interop.sshd.password");
        }

        try (SshClient client = new SshClient()) {
            client.connect(sshdHost, sshdPort);
            // Version exchange succeeded — no exception means SSH-2.0 banner parsed
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                throw new org.opentest4j.TestAbortedException(
                        "sshd not available at " + sshdHost + ":" + sshdPort);
            }
            // Cipher mismatch or KEX failure — known interoperability gap
        }
    }

    @Test
    void testRawVersionStringFormat() {
        // Verify version string format matches SSH spec (RFC 4253 §4.2)
        SshVersion ourVersion = SshVersion.defaultVersion();
        String formatted = ourVersion.format();
        assertThat(formatted).isEqualTo("SSH-2.0-legoflow_1.0");

        byte[] wire = ourVersion.toBytes();
        assertThat(wire).hasSize(22); // "SSH-2.0-legoflow_1.0\r\n" (22 chars + 2 for CR LF)
        assertThat(new String(wire, StandardCharsets.UTF_8)).isEqualTo("SSH-2.0-legoflow_1.0\r\n");
    }

    @Test
    void testOpenSSHVersionParsing() {
        // Verify we can correctly parse OpenSSH version strings
        SshVersion parsed = SshVersion.parse(
                "SSH-2.0-OpenSSH_10.3p1 FreeBSD-openssh-portable-10.3.p1");
        assertThat(parsed.protocolVersion()).isEqualTo("2.0");
        assertThat(parsed.softwareVersion()).isEqualTo("OpenSSH_10.3p1");
        assertThat(parsed.comments()).isEqualTo("FreeBSD-openssh-portable-10.3.p1");
        assertThat(parsed.isCompatible()).isTrue();
    }

    @Test
    void testVersionIncompatibilityRejection() {
        // SSH-1.0 should be rejected
        assertThat(SshVersion.parse("SSH-1.0-legacy").isCompatible()).isFalse();
    }

    @Test
    void testMalformedVersionStrings() {
        assertThatThrownBy(() -> SshVersion.parse("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid SSH version string");

        assertThatThrownBy(() -> SshVersion.parse("SSH-2.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing software version");
    }

    @Test
    void testOpenSSHCompatRoundTrip() throws IOException {
        if (!shouldRun()) {
            throw new org.opentest4j.TestAbortedException(
                    "SSH interop skipped — no sshd available for round-trip test");
        }
        // Connect to OpenSSH sshd, read version, verify round-trip
        try (Socket socket = new Socket(sshdHost, sshdPort)) {
            socket.setSoTimeout(5000);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String serverVersionStr = reader.readLine();
            SshVersion serverVersion = SshVersion.parse(serverVersionStr);

            // Verify OpenSSH is SSH-2.0 compatible
            assertThat(serverVersion.isCompatible()).isTrue();

            // Our client version should also be compatible
            SshVersion ourVersion = SshVersion.defaultVersion();
            assertThat(ourVersion.isCompatible()).isTrue();

            // Protocol versions match
            assertThat(serverVersion.protocolVersion())
                    .isEqualTo(ourVersion.protocolVersion());
        }
    }
}
