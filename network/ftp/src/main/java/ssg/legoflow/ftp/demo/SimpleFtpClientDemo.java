package ssg.legoflow.ftp.demo;

import ssg.legoflow.ftp.client.FtpClient;
import ssg.legoflow.ftp.client.FtpFileEntry;
import ssg.legoflow.ftp.protocol.FtpTransferType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Demonstrates basic FTP client operations: connect, login, list, upload, download.
 *
 * @since 1.0.0
 */
public final class SimpleFtpClientDemo {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleFtpClientDemo.class);

    private SimpleFtpClientDemo() {}

    /**
     * Runs the demo against a server at the given host and port.
     *
     * @param host     the FTP server host
     * @param port     the FTP server port
     * @param username the username
     * @param password the password
     * @throws IOException if any operation fails
     */
    public static void run(String host, int port, String username, String password) throws IOException {
        try (var client = new FtpClient()) {
            // Connect and login
            client.connect(host, port);
            client.login(username, password);
            LOG.info("Connected and logged in");

            // Show current directory
            String pwd = client.pwd();
            LOG.info("Current directory: {}", pwd);

            // Set binary transfer type
            client.setTransferType(FtpTransferType.BINARY);

            // Upload a test file
            byte[] data = "Hello from LegoFlow FTP!".getBytes(StandardCharsets.UTF_8);
            client.put(new ByteArrayInputStream(data), "test.txt");
            LOG.info("Uploaded test.txt ({} bytes)", data.length);

            // List files
            List<FtpFileEntry> files = client.list(null);
            LOG.info("Directory listing ({} entries):", files.size());
            for (FtpFileEntry entry : files) {
                LOG.info("  {} {} {}", entry.type(), entry.size(), entry.name());
            }

            // Download the file back
            try (var is = client.get("test.txt")) {
                byte[] downloaded = is.readAllBytes();
                LOG.info("Downloaded test.txt: {} bytes", downloaded.length);
            }

            // Clean up
            client.delete("test.txt");
            LOG.info("Deleted test.txt");

            LOG.info("Demo completed successfully");
        }
    }
}
