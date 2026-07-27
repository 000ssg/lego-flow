package ssg.legoflow.ftp.demo;

import ssg.legoflow.ftp.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Demonstrates starting an FTP server with in-memory filesystem.
 *
 * @since 1.0.0
 */
public final class FtpServerDemo {

    private static final Logger LOG = LoggerFactory.getLogger(FtpServerDemo.class);

    private FtpServerDemo() {}

    /**
     * Starts an FTP server on the given port.
     *
     * @param port the port to bind
     * @return the running server (caller must close)
     * @throws IOException if the server cannot start
     */
    public static FtpServer start(int port) throws IOException {
        var config = FtpServerConfig.builder()
                .host("127.0.0.1")
                .port(port)
                .serverName("LegoFlow Demo FTP Server")
                .build();

        var fs = new InMemoryFileSystem();
        fs.createDirectory("/pub");
        fs.putFile("/pub/readme.txt", "Welcome to the FTP server demo!\n".getBytes());

        var server = new FtpServer(config);
        server.setFileSystem(fs);
        server.setAuthenticator(FtpAuthenticator.anonymous());
        server.start();

        LOG.info("FTP server started on port {}", server.getPort());
        return server;
    }

    /**
     * Runs the demo: starts a server, waits, then stops.
     *
     * @param port the port to bind
     * @throws Exception if an error occurs
     */
    public static void run(int port) throws Exception {
        try (var server = start(port)) {
            LOG.info("Server running. Press Ctrl+C to stop.");
            Thread.sleep(60_000);
        }
    }
}
