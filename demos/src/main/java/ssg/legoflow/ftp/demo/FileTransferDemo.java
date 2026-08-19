package ssg.legoflow.ftp.demo;

import ssg.legoflow.ftp.client.FtpClient;
import ssg.legoflow.ftp.protocol.FtpTransferType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Random;
/**
 * Demonstrates large file transfer with progress tracking.
 *
 * @since 0.1.0
 */
public final class FileTransferDemo {

    private static final Logger LOG = LoggerFactory.getLogger(FileTransferDemo.class);

    private FileTransferDemo() {}

    /**
     * Runs the file transfer demo.
     *
     * @param host the FTP server host
     * @param port the FTP server port
     * @param size the file size in bytes to transfer
     * @return the number of bytes transferred
     * @throws IOException if the transfer fails
     */
    public static long run(String host, int port, int size) throws IOException {
        // Generate test data
        byte[] data = new byte[size];
        new Random(42).nextBytes(data);
        LOG.info("Generated {} bytes of test data", data.length);

        try (var client = new FtpClient()) {
            client.connect(host, port);
            client.login("anonymous", "test@example.com");
            client.setTransferType(FtpTransferType.BINARY);
            client.setPassiveMode(true);

            // Upload
            long startUpload = System.nanoTime();
            long uploaded = client.put(new ByteArrayInputStream(data), "large-file.bin");
            long uploadDuration = (System.nanoTime() - startUpload) / 1_000_000;
            LOG.info("Upload: {} bytes in {} ms", uploaded, uploadDuration);

            // Download and verify
            long startDownload = System.nanoTime();
            byte[] downloaded;
            try (var is = client.get("large-file.bin")) {
                downloaded = is.readAllBytes();
            }
            long downloadDuration = (System.nanoTime() - startDownload) / 1_000_000;
            LOG.info("Download: {} bytes in {} ms", downloaded.length, downloadDuration);

            // Verify integrity
            boolean match = java.util.Arrays.equals(data, downloaded);
            LOG.info("Data integrity check: {}", match ? "PASS" : "FAIL");

            // Cleanup
            client.delete("large-file.bin");

            return uploaded;
        }
    }
}
