package ssg.legoflow.ftp.demo;

import ssg.legoflow.ftp.security.FtpsConfig;
import ssg.legoflow.ftp.security.FtpsHandler;
import ssg.legoflow.ftp.security.FtpsMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

/**
 * Demonstrates FTPS (FTP over TLS) configuration and handshake.
 *
 * @since 0.1.0
 */
public final class FtpsDemo {

    private static final Logger LOG = LoggerFactory.getLogger(FtpsDemo.class);

    private FtpsDemo() {}

    /**
     * Demonstrates creating FTPS configurations.
     *
     * @throws Exception if an error occurs
     */
    public static void run() throws Exception {
        // Explicit FTPS configuration (AUTH TLS on port 21)
        var explicitConfig = FtpsConfig.builder()
                .mode(FtpsMode.EXPLICIT)
                .protocols("TLSv1.2", "TLSv1.3")
                .build();

        LOG.info("Explicit FTPS config: mode={}, default port={}",
                explicitConfig.mode(), explicitConfig.mode().defaultPort());

        SSLContext ctx = explicitConfig.createSslContext();
        LOG.info("SSL context created: protocol={}", ctx.getProtocol());

        // Implicit FTPS configuration (TLS from the start on port 990)
        var implicitConfig = FtpsConfig.builder()
                .mode(FtpsMode.IMPLICIT)
                .protocols("TLSv1.3")
                .build();

        LOG.info("Implicit FTPS config: mode={}, default port={}",
                implicitConfig.mode(), implicitConfig.mode().defaultPort());

        // Create handler
        var handler = new FtpsHandler(explicitConfig);
        LOG.info("FTPS handler created: encrypted={}, dataProtected={}",
                handler.isControlEncrypted(), handler.isDataProtected());

        // Simulate PBSZ and PROT
        handler.handlePbsz(0);
        LOG.info("After PBSZ 0: bufferSize={}", handler.getProtectionBufferSize());

        handler.handleProt("P");
        LOG.info("After PROT P: dataProtected={}", handler.isDataProtected());

        handler.handleProt("C");
        LOG.info("After PROT C: dataProtected={}", handler.isDataProtected());

        LOG.info("FTPS demo completed");
    }
}
