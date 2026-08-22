package ssg.legoflow.ftp.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive FTP demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code FtpServer}. To test against
 * an external vsftpd/ProFTPD/FileZilla Server, set {@code DemoFtpAll.USE_EXTERNAL = true}
 * and configure host/port before running.</p>
 */
class DemoFtpAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoFtpAll.runAll();

        assertThat(results.connectLogin())
                .as("FTP connect and login")
                .isTrue();

        assertThat(results.directoryListing())
                .as("LIST returns directory entries")
                .isGreaterThanOrEqualTo(2);

        assertThat(results.filenameListing())
                .as("NLST returns filenames")
                .isGreaterThanOrEqualTo(2);

        assertThat(results.fileUpload())
                .as("STOR uploads bytes")
                .isGreaterThan(0);

        assertThat(results.fileDownload())
                .as("RETR downloads bytes")
                .isGreaterThan(0);

        assertThat(results.fileManagement())
                .as("MKD/RMD/DELE/RENAME file operations")
                .isTrue();

        assertThat(results.passiveMode())
                .as("Passive mode data transfer")
                .isTrue();

        assertThat(results.transferModes())
                .as("ASCII and BINARY transfer mode switching")
                .isTrue();

        assertThat(results.virtualFilesystem())
                .as("Virtual filesystem entries")
                .isGreaterThanOrEqualTo(5);

        assertThat(results.ftpsConfig())
                .as("FTPS configuration and handler")
                .isTrue();
    }
}
