package ssg.legoflow.ftp.demo;

import ssg.legoflow.ftp.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
/**
 * Demonstrates an FTP server with an in-memory virtual filesystem.
 *
 * @since 0.1.0
 */
public final class VirtualFsDemo {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualFsDemo.class);

    private VirtualFsDemo() {}

    /**
     * Creates an in-memory filesystem with sample content.
     *
     * @return the populated filesystem
     * @throws IOException if creation fails
     */
    public static InMemoryFileSystem createSampleFs() throws IOException {
        var fs = new InMemoryFileSystem();
        fs.createDirectory("/documents");
        fs.createDirectory("/images");
        fs.createDirectory("/data");
        fs.putFile("/documents/readme.txt",
                "Welcome to the virtual FTP server.\n".getBytes(StandardCharsets.UTF_8));
        fs.putFile("/documents/notes.txt",
                "Some notes here.\n".getBytes(StandardCharsets.UTF_8));
        fs.putFile("/data/sample.csv",
                "id,name,value\n1,alpha,100\n2,beta,200\n".getBytes(StandardCharsets.UTF_8));
        LOG.info("Created sample filesystem with {} entries", fs.size());
        return fs;
    }

    /**
     * Starts a server with the sample virtual filesystem.
     *
     * @param port the port to bind
     * @return the running server (caller must close)
     * @throws IOException if the server cannot start
     */
    public static FtpServer start(int port) throws IOException {
        var config = FtpServerConfig.builder()
                .host("127.0.0.1")
                .port(port)
                .serverName("LegoFlow Virtual FS Demo")
                .build();

        var fs = createSampleFs();

        var server = new FtpServer(config);
        server.setFileSystem(fs);
        server.setAuthenticator(FtpAuthenticator.acceptAll());
        server.start();

        LOG.info("Virtual FS server started on port {}", server.getPort());
        return server;
    }
}
