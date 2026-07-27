package ssg.legoflow.ftp.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Interface for an FTP data connection.
 *
 * <p>FTP uses a separate TCP connection for data transfers. The data connection
 * can be established in active mode (server connects to client) or passive mode
 * (client connects to server).
 *
 * @since 1.0.0
 */
public interface DataConnection extends AutoCloseable {

    /**
     * Opens the data connection.
     *
     * @return the connected socket
     * @throws IOException if the connection cannot be established
     */
    Socket open() throws IOException;

    /**
     * Returns an input stream for reading data from the connection.
     *
     * @return the input stream
     * @throws IOException if the connection is not open or an I/O error occurs
     */
    InputStream getInputStream() throws IOException;

    /**
     * Returns an output stream for writing data to the connection.
     *
     * @return the output stream
     * @throws IOException if the connection is not open or an I/O error occurs
     */
    OutputStream getOutputStream() throws IOException;

    /**
     * Returns {@code true} if the data connection is currently open.
     *
     * @return true if open
     */
    boolean isOpen();

    /**
     * Closes the data connection and releases resources.
     *
     * @throws IOException if an I/O error occurs during close
     */
    @Override
    void close() throws IOException;
}
