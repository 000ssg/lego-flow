package ssg.legoflow.ftp.demo;

import ssg.legoflow.ftp.client.FtpClient;
import ssg.legoflow.ftp.client.FtpFileEntry;
import ssg.legoflow.ftp.protocol.FtpReply;
import ssg.legoflow.ftp.protocol.FtpTransferType;
import ssg.legoflow.ftp.security.FtpsConfig;
import ssg.legoflow.ftp.security.FtpsHandler;
import ssg.legoflow.ftp.security.FtpsMode;
import ssg.legoflow.ftp.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
/**
 * Comprehensive demo of all FTP module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link FtpServer}</b> — No external dependencies.
 * Runs anywhere without installation. Supports FTP (RFC 959) and FTPS (RFC 4217):
 * connect/login, LIST/NLST directory listing, RETR/STOR file transfer, DELE/MKD/RMD
 * file management, passive/active mode, virtual filesystem, and ASCII/binary transfer modes.
 * Ideal for development, testing, CI/CD, and learning the FTP protocol.</p>
 *
 * <p><b>Alternative: External vsftpd / ProFTPD / FileZilla Server</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production file transfer with real filesystem and OS-level permissions</li>
 *   <li>FTPS with real TLS certificates from a CA</li>
 *   <li>Active mode testing across NAT and firewalls</li>
 *   <li>Integration testing against production FTP infrastructure</li>
 *   <li>Chroot jail and per-user directory isolation</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the server lifecycle (start/stop).
 * All client code (FtpClient) uses the same API regardless of backend.
 * When {@code USE_EXTERNAL=true}, the demo skips server creation and connects directly
 * to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Connect and login — FTP control connection, USER/PASS authentication</li>
 *   <li>Directory listing (LIST) — parsed directory entries with metadata</li>
 *   <li>Filename listing (NLST) — filenames only, for scripting</li>
 *   <li>File upload (STOR) — store file on server</li>
 *   <li>File download (RETR) — retrieve file from server</li>
 *   <li>File management — MKD, RMD, DELE, RENAME operations</li>
 *   <li>Passive mode — PASV data connection (firewall-friendly)</li>
 *   <li>Transfer modes — ASCII vs BINARY transfer types</li>
 *   <li>Virtual filesystem — in-memory filesystem for testing</li>
 *   <li>FTPS configuration — TLS explicit/implicit modes, PBSZ, PROT</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoFtpAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoFtpAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house FtpServer (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for vsftpd/ProFTPD/FileZilla
    // =========================================================================

    /** Set to {@code true} to connect to an external FTP server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external FTP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external FTP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 21;

    private DemoFtpAll() {}

    /**
     * Results from running the full demo.
     *
     * @param connectLogin       true if connect and login succeeded
     * @param directoryListing   number of entries from LIST
     * @param filenameListing    number of filenames from NLST
     * @param fileUpload         number of bytes uploaded
     * @param fileDownload       number of bytes downloaded
     * @param fileManagement     true if MKD/RMD/DELE/RENAME succeeded
     * @param passiveMode        true if passive mode data transfer worked
     * @param transferModes      true if ASCII and BINARY mode switching worked
     * @param virtualFilesystem  number of files in the virtual filesystem
     * @param ftpsConfig         true if FTPS configuration and handler setup succeeded
     */
    public record Results(
            boolean connectLogin,
            int directoryListing,
            int filenameListing,
            long fileUpload,
            long fileDownload,
            boolean fileManagement,
            boolean passiveMode,
            boolean transferModes,
            int virtualFilesystem,
            boolean ftpsConfig
    ) {}

    /**
     * Runs the comprehensive demo covering all FTP features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runAgainstServer(EXTERNAL_HOST, EXTERNAL_PORT,
                    "anonymous", "test@example.com");
        }

        // Set up in-memory filesystem and server
        InMemoryFileSystem fs = createDemoFileSystem();
        var config = FtpServerConfig.builder()
                .host("127.0.0.1")
                .port(0)
                .serverName("LegoFlow Demo FTP Server")
                .build();

        var server = new FtpServer(config);
        server.setFileSystem(fs);
        server.setAuthenticator(FtpAuthenticator.singleUser("demo", "demo123"));
        server.start();
        int port = server.getPort();
        LOG.info("In-house FtpServer started on port {}", port);

        try {
            Results clientResults = runAgainstServer("127.0.0.1", port, "demo", "demo123");

            // Features only testable with in-house server
            int vfsSize = demoVirtualFilesystem();
            boolean ftps = demoFtpsConfig();

            return new Results(
                    clientResults.connectLogin(),
                    clientResults.directoryListing(),
                    clientResults.filenameListing(),
                    clientResults.fileUpload(),
                    clientResults.fileDownload(),
                    clientResults.fileManagement(),
                    clientResults.passiveMode(),
                    clientResults.transferModes(),
                    vfsSize,
                    ftps);
        } finally {
            server.close();
        }
    }

    private static Results runAgainstServer(String host, int port,
                                            String user, String password) throws Exception {
        boolean login = demoConnectLogin(host, port, user, password);
        int listCount = demoDirectoryListing(host, port, user, password);
        int nlstCount = demoFilenameListing(host, port, user, password);
        long uploaded = demoFileUpload(host, port, user, password);
        long downloaded = demoFileDownload(host, port, user, password);
        boolean fileMgmt = demoFileManagement(host, port, user, password);
        boolean passive = demoPassiveMode(host, port, user, password);
        boolean xferModes = demoTransferModes(host, port, user, password);

        return new Results(login, listCount, nlstCount, uploaded, downloaded, fileMgmt,
                passive, xferModes, 0 /* filled later */, false /* filled later */);
    }

    // ======================== Filesystem Setup ===============================

    /**
     * Creates the in-memory filesystem with sample content.
     */
    static InMemoryFileSystem createDemoFileSystem() throws IOException {
        var fs = new InMemoryFileSystem();
        fs.createDirectory("/documents");
        fs.createDirectory("/data");
        fs.putFile("/documents/readme.txt",
                "Welcome to the LegoFlow FTP demo.\n".getBytes(StandardCharsets.UTF_8));
        fs.putFile("/documents/notes.txt",
                "Sample notes file.\n".getBytes(StandardCharsets.UTF_8));
        fs.putFile("/data/sample.csv",
                "id,name,value\n1,alpha,100\n2,beta,200\n".getBytes(StandardCharsets.UTF_8));
        return fs;
    }

    // ======================== 1. CONNECT AND LOGIN ==========================

    /**
     * Demonstrates FTP connection and authentication.
     */
    static boolean demoConnectLogin(String host, int port, String user, String password)
            throws IOException {
        LOG.info("=== 1. Connect and Login ===");
        try (var client = new FtpClient()) {
            FtpReply connectReply = client.connect(host, port);
            LOG.info("Connect reply: {} {}", connectReply.code(), connectReply.text());

            FtpReply loginReply = client.login(user, password);
            boolean success = loginReply.isSuccess();
            LOG.info("Login: {} {}", loginReply.code(), loginReply.text());

            String pwd = client.pwd();
            LOG.info("Working directory: {}", pwd);

            return success;
        }
    }

    // ======================== 2. DIRECTORY LISTING (LIST) ====================

    /**
     * Demonstrates LIST command: parsed directory entries with metadata.
     * <p>
     * <b>Preferred: LIST</b> — detailed entries with size, date, type.
     * <p>
     * <b>Alternative: MLSD (RFC 3659)</b> — machine-readable, standardized format.
     */
    static int demoDirectoryListing(String host, int port, String user, String password)
            throws IOException {
        LOG.info("=== 2. Directory Listing (LIST) ===");
        try (var client = new FtpClient()) {
            client.connect(host, port);
            client.login(user, password);

            List<FtpFileEntry> entries = client.list(null);
            LOG.info("Root directory: {} entries", entries.size());
            for (FtpFileEntry entry : entries) {
                LOG.info("  {} {} {} {}", entry.type(), entry.size(), entry.name(),
                        entry.modified());
            }
            return entries.size();
        }
    }

    // ======================== 3. FILENAME LISTING (NLST) =====================

    /**
     * Demonstrates NLST command: filenames only for scripting.
     */
    static int demoFilenameListing(String host, int port, String user, String password)
            throws IOException {
        LOG.info("=== 3. Filename Listing (NLST) ===");
        try (var client = new FtpClient()) {
            client.connect(host, port);
            client.login(user, password);

            List<String> names = client.nlst(null);
            LOG.info("NLST: {} names", names.size());
            for (String name : names) {
                LOG.info("  {}", name);
            }
            return names.size();
        }
    }

    // ======================== 4. FILE UPLOAD (STOR) ==========================

    /**
     * Demonstrates file upload using STOR command.
     */
    static long demoFileUpload(String host, int port, String user, String password)
            throws IOException {
        LOG.info("=== 4. File Upload (STOR) ===");
        try (var client = new FtpClient()) {
            client.connect(host, port);
            client.login(user, password);
            client.setTransferType(FtpTransferType.BINARY);

            byte[] data = "Upload test content from DemoFtpAll\nSecond line\n"
                    .getBytes(StandardCharsets.UTF_8);
            long uploaded = client.put(new ByteArrayInputStream(data), "/upload-test.txt");
            LOG.info("Uploaded {} bytes to /upload-test.txt", uploaded);
            return uploaded;
        }
    }

    // ======================== 5. FILE DOWNLOAD (RETR) ========================

    /**
     * Demonstrates file download using RETR command.
     */
    static long demoFileDownload(String host, int port, String user, String password)
            throws IOException {
        LOG.info("=== 5. File Download (RETR) ===");
        try (var client = new FtpClient()) {
            client.connect(host, port);
            client.login(user, password);
            client.setTransferType(FtpTransferType.BINARY);

            // Download the file uploaded in the previous demo
            try (InputStream is = client.get("/upload-test.txt")) {
                byte[] downloaded = is.readAllBytes();
                LOG.info("Downloaded {} bytes from /upload-test.txt", downloaded.length);
                return downloaded.length;
            }
        }
    }

    // ======================== 6. FILE MANAGEMENT ============================

    /**
     * Demonstrates file management: MKD, RMD, DELE, RENAME.
     */
    static boolean demoFileManagement(String host, int port, String user, String password)
            throws IOException {
        LOG.info("=== 6. File Management ===");
        try (var client = new FtpClient()) {
            client.connect(host, port);
            client.login(user, password);

            // Create directory
            FtpReply mkdReply = client.mkdir("/test-dir");
            LOG.info("MKD /test-dir: {} {}", mkdReply.code(), mkdReply.text());

            // Upload a file into it
            byte[] data = "test".getBytes(StandardCharsets.UTF_8);
            client.put(new ByteArrayInputStream(data), "/test-dir/file.txt");

            // Rename file
            FtpReply renReply = client.rename("/test-dir/file.txt", "/test-dir/renamed.txt");
            LOG.info("RENAME: {} {}", renReply.code(), renReply.text());

            // Delete file
            FtpReply delReply = client.delete("/test-dir/renamed.txt");
            LOG.info("DELE: {} {}", delReply.code(), delReply.text());

            // Remove directory
            FtpReply rmdReply = client.rmdir("/test-dir");
            LOG.info("RMD: {} {}", rmdReply.code(), rmdReply.text());

            // Clean up upload-test.txt from previous demo
            client.delete("/upload-test.txt");

            boolean allSuccess = mkdReply.isSuccess() && renReply.isSuccess()
                    && delReply.isSuccess() && rmdReply.isSuccess();
            return allSuccess;
        }
    }

    // ======================== 7. PASSIVE MODE ===============================

    /**
     * Demonstrates passive mode data transfer (PASV).
     * <p>
     * <b>Preferred: passive mode</b> — client initiates data connection, works
     * through firewalls and NAT.
     * <p>
     * <b>Alternative: active mode (PORT)</b> — server connects to client for data.
     * Requires client to have an accessible port. Rarely used in modern deployments.
     */
    static boolean demoPassiveMode(String host, int port, String user, String password)
            throws IOException {
        LOG.info("=== 7. Passive Mode ===");
        try (var client = new FtpClient()) {
            client.connect(host, port);
            client.login(user, password);

            // Enable passive mode
            client.setPassiveMode(true);
            LOG.info("Passive mode enabled: {}", client.isPassiveMode());

            // Upload in passive mode
            byte[] data = "Passive mode test data".getBytes(StandardCharsets.UTF_8);
            client.setTransferType(FtpTransferType.BINARY);
            long uploaded = client.put(new ByteArrayInputStream(data), "/passive-test.txt");
            LOG.info("Passive upload: {} bytes", uploaded);

            // Download in passive mode
            try (InputStream is = client.get("/passive-test.txt")) {
                byte[] downloaded = is.readAllBytes();
                boolean match = new String(downloaded, StandardCharsets.UTF_8)
                        .equals("Passive mode test data");
                LOG.info("Passive download: {} bytes, match={}", downloaded.length, match);

                // Clean up
                client.delete("/passive-test.txt");

                return match;
            }
        }
    }

    // ======================== 8. TRANSFER MODES =============================

    /**
     * Demonstrates switching between ASCII and BINARY transfer types.
     * <p>
     * <b>Preferred: BINARY (TYPE I)</b> — byte-for-byte transfer, no conversion.
     * Use for all non-text files.
     * <p>
     * <b>Alternative: ASCII (TYPE A)</b> — converts line endings (CRLF on wire).
     * Use only for plain text files when platform line ending conversion is needed.
     */
    static boolean demoTransferModes(String host, int port, String user, String password)
            throws IOException {
        LOG.info("=== 8. Transfer Modes ===");
        try (var client = new FtpClient()) {
            client.connect(host, port);
            client.login(user, password);

            // Set ASCII mode
            FtpReply asciiReply = client.setTransferType(FtpTransferType.ASCII);
            LOG.info("TYPE A: {} (mode={})", asciiReply.code(), client.getTransferType());

            // Set BINARY mode
            FtpReply binaryReply = client.setTransferType(FtpTransferType.BINARY);
            LOG.info("TYPE I: {} (mode={})", binaryReply.code(), client.getTransferType());

            boolean asciiOk = asciiReply.isSuccess();
            boolean binaryOk = binaryReply.isSuccess();
            return asciiOk && binaryOk;
        }
    }

    // ======================== 9. VIRTUAL FILESYSTEM ==========================

    /**
     * Demonstrates the in-memory virtual filesystem used by the in-house server.
     */
    static int demoVirtualFilesystem() throws IOException {
        LOG.info("=== 9. Virtual Filesystem ===");
        var fs = new InMemoryFileSystem();
        fs.createDirectory("/vfs-demo");
        fs.putFile("/vfs-demo/file1.txt", "Content 1".getBytes(StandardCharsets.UTF_8));
        fs.putFile("/vfs-demo/file2.txt", "Content 2".getBytes(StandardCharsets.UTF_8));
        fs.putFile("/vfs-demo/file3.txt", "Content 3".getBytes(StandardCharsets.UTF_8));
        fs.createDirectory("/vfs-demo/sub");
        fs.putFile("/vfs-demo/sub/nested.txt", "Nested content".getBytes(StandardCharsets.UTF_8));

        int size = fs.size();
        LOG.info("Virtual filesystem size: {} entries", size);
        return size;
    }

    // ======================== 10. FTPS CONFIGURATION ========================

    /**
     * Demonstrates FTPS (FTP over TLS) configuration for explicit and implicit modes.
     * <p>
     * <b>Preferred: Explicit FTPS (AUTH TLS)</b> — upgrades plain connection to TLS on port 21.
     * Compatible with clients that may not support TLS.
     * <p>
     * <b>Alternative: Implicit FTPS</b> — TLS from the start on port 990.
     * Simpler but requires all clients to support TLS.
     */
    static boolean demoFtpsConfig() throws Exception {
        LOG.info("=== 10. FTPS Configuration ===");

        // Explicit FTPS configuration
        var explicitConfig = FtpsConfig.builder()
                .mode(FtpsMode.EXPLICIT)
                .protocols("TLSv1.2", "TLSv1.3")
                .build();
        LOG.info("Explicit FTPS: mode={} defaultPort={}",
                explicitConfig.mode(), explicitConfig.mode().defaultPort());

        // Implicit FTPS configuration
        var implicitConfig = FtpsConfig.builder()
                .mode(FtpsMode.IMPLICIT)
                .protocols("TLSv1.3")
                .build();
        LOG.info("Implicit FTPS: mode={} defaultPort={}",
                implicitConfig.mode(), implicitConfig.mode().defaultPort());

        // Create handler and simulate PBSZ/PROT commands
        var handler = new FtpsHandler(explicitConfig);
        LOG.info("Handler: encrypted={} dataProtected={}",
                handler.isControlEncrypted(), handler.isDataProtected());

        handler.handlePbsz(0);
        LOG.info("After PBSZ 0: bufferSize={}", handler.getProtectionBufferSize());

        handler.handleProt("P");
        boolean dataProtected = handler.isDataProtected();
        LOG.info("After PROT P: dataProtected={}", dataProtected);

        handler.handleProt("C");
        boolean dataClear = !handler.isDataProtected();
        LOG.info("After PROT C: dataProtected={}", handler.isDataProtected());

        return dataProtected && dataClear;
    }
}
