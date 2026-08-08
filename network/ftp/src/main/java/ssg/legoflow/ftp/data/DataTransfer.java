package ssg.legoflow.ftp.data;

import ssg.legoflow.ftp.protocol.FtpTransferType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Objects;

/**
 * Handles actual data transfer over an FTP data connection.
 *
 * <p>Supports ASCII mode (with CRLF line-ending conversion) and binary
 * (IMAGE) mode (byte-for-byte transfer).
 *
 * @since 0.1.0
 */
public final class DataTransfer {

    private static final Logger LOG = LoggerFactory.getLogger(DataTransfer.class);
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final FtpTransferType transferType;
    private final int bufferSize;

    /**
     * Creates a data transfer handler.
     *
     * @param transferType the transfer type (ASCII or BINARY)
     */
    public DataTransfer(FtpTransferType transferType) {
        this(transferType, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Creates a data transfer handler with a custom buffer size.
     *
     * @param transferType the transfer type
     * @param bufferSize   the buffer size for transfers
     */
    public DataTransfer(FtpTransferType transferType, int bufferSize) {
        this.transferType = Objects.requireNonNull(transferType, "transferType");
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive: " + bufferSize);
        }
        this.bufferSize = bufferSize;
    }

    /**
     * Sends data from an input stream to an output stream.
     *
     * <p>In ASCII mode, local line endings are converted to CRLF on the wire.
     * In binary mode, data is passed through unchanged.
     *
     * @param source the local data source
     * @param dest   the data connection output stream (wire)
     * @return the number of bytes transferred
     * @throws IOException if an I/O error occurs
     */
    public long send(InputStream source, OutputStream dest) throws IOException {
        if (transferType == FtpTransferType.ASCII) {
            return sendAscii(source, dest);
        } else {
            return sendBinary(source, dest);
        }
    }

    /**
     * Receives data from an input stream to an output stream.
     *
     * <p>In ASCII mode, CRLF line endings from the wire are converted to local line endings.
     * In binary mode, data is passed through unchanged.
     *
     * @param source the data connection input stream (wire)
     * @param dest   the local data destination
     * @return the number of bytes transferred
     * @throws IOException if an I/O error occurs
     */
    public long receive(InputStream source, OutputStream dest) throws IOException {
        if (transferType == FtpTransferType.ASCII) {
            return receiveAscii(source, dest);
        } else {
            return receiveBinary(source, dest);
        }
    }

    /**
     * Converts local text to wire format (local line endings to CRLF).
     *
     * @param text the local text
     * @return the text with CRLF line endings
     */
    public static String localToWire(String text) {
        // Normalize to LF first, then convert to CRLF
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        return normalized.replace("\n", "\r\n");
    }

    /**
     * Converts wire text to local format (CRLF to local line endings).
     *
     * @param text the wire text (CRLF)
     * @return the text with local line endings
     */
    public static String wireToLocal(String text) {
        return text.replace("\r\n", System.lineSeparator());
    }

    private long sendAscii(InputStream source, OutputStream dest) throws IOException {
        long totalBytes = 0;
        try (var reader = new BufferedReader(new InputStreamReader(source));
             var writer = new BufferedOutputStream(dest)) {
            int ch;
            boolean lastWasCr = false;
            while ((ch = reader.read()) != -1) {
                if (ch == '\n') {
                    // Convert LF (or CRLF) to CRLF on wire
                    if (!lastWasCr) {
                        writer.write('\r');
                        totalBytes++;
                    }
                    writer.write('\n');
                    totalBytes++;
                    lastWasCr = false;
                } else if (ch == '\r') {
                    writer.write('\r');
                    totalBytes++;
                    lastWasCr = true;
                } else {
                    writer.write(ch);
                    totalBytes++;
                    lastWasCr = false;
                }
            }
            writer.flush();
        }
        LOG.debug("ASCII send: {} bytes", totalBytes);
        return totalBytes;
    }

    private long receiveAscii(InputStream source, OutputStream dest) throws IOException {
        long totalBytes = 0;
        String lineSep = System.lineSeparator();
        byte[] lineSepBytes = lineSep.getBytes();
        try (var buffered = new BufferedInputStream(source);
             var out = new BufferedOutputStream(dest)) {
            int ch;
            while ((ch = buffered.read()) != -1) {
                if (ch == '\r') {
                    // Peek at next character
                    buffered.mark(1);
                    int next = buffered.read();
                    if (next == '\n') {
                        // CRLF -> local line separator
                        out.write(lineSepBytes);
                        totalBytes += lineSepBytes.length;
                    } else {
                        // Bare CR -> local line separator
                        out.write(lineSepBytes);
                        totalBytes += lineSepBytes.length;
                        if (next != -1) {
                            buffered.reset();
                        }
                    }
                } else if (ch == '\n') {
                    // Bare LF -> local line separator
                    out.write(lineSepBytes);
                    totalBytes += lineSepBytes.length;
                } else {
                    out.write(ch);
                    totalBytes++;
                }
            }
            out.flush();
        }
        LOG.debug("ASCII receive: {} bytes", totalBytes);
        return totalBytes;
    }

    private long sendBinary(InputStream source, OutputStream dest) throws IOException {
        long totalBytes = 0;
        byte[] buffer = new byte[bufferSize];
        int bytesRead;
        while ((bytesRead = source.read(buffer)) != -1) {
            dest.write(buffer, 0, bytesRead);
            totalBytes += bytesRead;
        }
        dest.flush();
        LOG.debug("Binary send: {} bytes", totalBytes);
        return totalBytes;
    }

    private long receiveBinary(InputStream source, OutputStream dest) throws IOException {
        long totalBytes = 0;
        byte[] buffer = new byte[bufferSize];
        int bytesRead;
        while ((bytesRead = source.read(buffer)) != -1) {
            dest.write(buffer, 0, bytesRead);
            totalBytes += bytesRead;
        }
        dest.flush();
        LOG.debug("Binary receive: {} bytes", totalBytes);
        return totalBytes;
    }
}
