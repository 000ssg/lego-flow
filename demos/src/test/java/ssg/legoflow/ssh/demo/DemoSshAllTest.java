package ssg.legoflow.ssh.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive SSH demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code SshServer}. To test against
 * an external OpenSSH/Dropbear, set {@code DemoSshAll.USE_EXTERNAL = true}
 * and configure host/port/credentials before running.</p>
 */
class DemoSshAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoSshAll.runAll();

        assertThat(results.keyExchange())
                .as("SSH key exchange and connection")
                .isTrue();

        assertThat(results.passwordAuth())
                .as("Password authentication")
                .isTrue();

        assertThat(results.commandExecution())
                .as("Remote command execution returns output")
                .isTrue();

        assertThat(results.ptyShell())
                .as("PTY allocation and shell session")
                .isTrue();

        assertThat(results.sftpOperations())
                .as("SFTP file operations completed")
                .isGreaterThanOrEqualTo(10);

        assertThat(results.portForwarding())
                .as("Local port forwarding channel created")
                .isTrue();
    }
}
