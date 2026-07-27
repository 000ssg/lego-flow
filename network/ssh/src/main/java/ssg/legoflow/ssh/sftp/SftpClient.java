package ssg.legoflow.ssh.sftp;

import ssg.legoflow.ssh.connection.SessionChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SFTP client implementing version 3 of the SFTP protocol.
 *
 * <p>Provides file operations: open, close, read, write, remove, rename,
 * mkdir, rmdir, opendir, readdir, stat, lstat, fstat, setstat, fsetstat,
 * readlink, symlink, realpath.
 *
 * @since 1.0.0
 */
public final class SftpClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SftpClient.class);
    private static final int SFTP_VERSION = 3;

    private final SessionChannel channel;
    private final AtomicInteger requestId = new AtomicInteger(1);
    private int serverVersion;

    /**
     * Creates a new SFTP client over an existing session channel.
     *
     * @param channel the session channel (must have "sftp" subsystem started)
     */
    public SftpClient(SessionChannel channel) {
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    /**
     * Initializes the SFTP session.
     *
     * @throws IOException if initialization fails
     * @throws InterruptedException if interrupted
     */
    public void init() throws IOException, InterruptedException {
        byte[] initPacket = SftpCodec.encode(new SftpPacket.Init(SFTP_VERSION));
        channel.sendData(initPacket);
        byte[] response = channel.receiveData();
        SftpPacket decoded = SftpCodec.decode(response);
        if (decoded instanceof SftpPacket.Version v) {
            serverVersion = v.version();
            LOG.debug("SFTP initialized, server version: {}", serverVersion);
        } else {
            throw new IOException("Expected SFTP version response, got: " + decoded);
        }
    }

    /**
     * Opens a file.
     *
     * @param filename the file path
     * @param pflags   open flags (SSH_FXF_READ, SSH_FXF_WRITE, etc.)
     * @return the file handle
     * @throws IOException if the operation fails
     * @throws InterruptedException if interrupted
     */
    public byte[] open(String filename, int pflags) throws IOException, InterruptedException {
        int id = requestId.getAndIncrement();
        byte[] packet = SftpCodec.encode(new SftpPacket.Open(id, filename, pflags,
                SftpFileAttributes.empty()));
        channel.sendData(packet);
        SftpPacket response = decodeResponse();
        if (response instanceof SftpPacket.Handle h) {
            return h.handle();
        }
        throw toException(response);
    }

    /**
     * Closes a file handle.
     *
     * @param handle the file handle
     * @throws IOException if the operation fails
     * @throws InterruptedException if interrupted
     */
    public void close(byte[] handle) throws IOException, InterruptedException {
        int id = requestId.getAndIncrement();
        byte[] packet = SftpCodec.encode(new SftpPacket.Close(id, handle));
        channel.sendData(packet);
        SftpPacket response = decodeResponse();
        checkStatus(response);
    }

    /**
     * Reads data from a file.
     *
     * @param handle the file handle
     * @param offset the file offset
     * @param length the number of bytes to read
     * @return the data read
     * @throws IOException if the operation fails
     * @throws InterruptedException if interrupted
     */
    public byte[] read(byte[] handle, long offset, int length)
            throws IOException, InterruptedException {
        int id = requestId.getAndIncrement();
        byte[] packet = SftpCodec.encode(new SftpPacket.Read(id, handle, offset, length));
        channel.sendData(packet);
        SftpPacket response = decodeResponse();
        if (response instanceof SftpPacket.Data d) {
            return d.data();
        }
        throw toException(response);
    }

    /**
     * Writes data to a file.
     *
     * @param handle the file handle
     * @param offset the file offset
     * @param data   the data to write
     * @throws IOException if the operation fails
     * @throws InterruptedException if interrupted
     */
    public void write(byte[] handle, long offset, byte[] data)
            throws IOException, InterruptedException {
        int id = requestId.getAndIncrement();
        byte[] packet = SftpCodec.encode(new SftpPacket.Write(id, handle, offset, data));
        channel.sendData(packet);
        SftpPacket response = decodeResponse();
        checkStatus(response);
    }

    /**
     * Removes a file.
     *
     * @param filename the file path
     * @throws IOException if the operation fails
     * @throws InterruptedException if interrupted
     */
    public void remove(String filename) throws IOException, InterruptedException {
        int id = requestId.getAndIncrement();
        byte[] packet = SftpCodec.encode(new SftpPacket.Remove(id, filename));
        channel.sendData(packet);
        checkStatus(decodeResponse());
    }

    /**
     * Renames a file.
     *
     * @param oldPath the old path
     * @param newPath the new path
     * @throws IOException if the operation fails
     * @throws InterruptedException if interrupted
     */
    public void rename(String oldPath, String newPath) throws IOException, InterruptedException {
        int id = requestId.getAndIncrement();
        byte[] packet = SftpCodec.encode(new SftpPacket.Rename(id, oldPath, newPath));
        channel.sendData(packet);
        checkStatus(decodeResponse());
    }

    /**
     * Creates a directory.
     *
     * @param path the directory path
     * @throws IOException if the operation fails
     * @throws InterruptedException if interrupted
     */
    public void mkdir(String path) throws IOException, InterruptedException {
        int id = requestId.getAndIncrement();
        byte[] packet = SftpCodec.encode(new SftpPacket.Mkdir(id, path,
                SftpFileAttributes.empty()));
        channel.sendData(packet);
        checkStatus(decodeResponse());
    }

    /**
     * Removes a directory.
     *
     * @param path the directory path
     * @throws IOException if the operation fails
     * @throws InterruptedException if interrupted
     */
    public void rmdir(String path) throws IOException, InterruptedException {
        int id = requestId.getAndIncrement();
        byte[] packet = SftpCodec.encode(new SftpPacket.Rmdir(id, path));
        channel.sendData(packet);
        checkStatus(decodeResponse());
    }

    /**
     * Opens a directory for reading.
     *
     * @param path the directory path
     * @return the directory handle
     * @throws IOException if the operation fails
     * @throws InterruptedException if interrupted
     */
    public byte[] opendir(String path) throws IOException, InterruptedException {
        int id = requestId.getAndIncrement();
        byte[] packet = SftpCodec.encode(new SftpPacket.Opendir(id, path));
        channel.sendData(packet);
        SftpPacket response = decodeResponse();
        if (response instanceof SftpPacket.Handle h) {
            return h.handle();
        }
        throw toException(response);
    }

    /**
     * Reads directory entries.
     *
     * @param handle the directory handle
     * @return list of directory entries
     * @throws IOException if the operation fails
     * @throws InterruptedException if interrupted
     */
    public List<SftpPacket.NameEntry> readdir(byte[] handle)
            throws IOException, InterruptedException {
        int id = requestId.getAndIncrement();
        byte[] packet = SftpCodec.encode(new SftpPacket.Readdir(id, handle));
        channel.sendData(packet);
        SftpPacket response = decodeResponse();
        if (response instanceof SftpPacket.Name n) {
            return n.entries();
        }
        throw toException(response);
    }

    /**
     * Gets file attributes by path.
     *
     * @param path the file path
     * @return the file attributes
     * @throws IOException if the operation fails
     * @throws InterruptedException if interrupted
     */
    public SftpFileAttributes stat(String path) throws IOException, InterruptedException {
        int id = requestId.getAndIncrement();
        byte[] packet = SftpCodec.encode(new SftpPacket.Stat(id, path));
        channel.sendData(packet);
        SftpPacket response = decodeResponse();
        if (response instanceof SftpPacket.Attrs a) {
            return a.attrs();
        }
        throw toException(response);
    }

    /**
     * Gets file attributes by path (without following symlinks).
     *
     * @param path the file path
     * @return the file attributes
     * @throws IOException if the operation fails
     * @throws InterruptedException if interrupted
     */
    public SftpFileAttributes lstat(String path) throws IOException, InterruptedException {
        int id = requestId.getAndIncrement();
        byte[] packet = SftpCodec.encode(new SftpPacket.Lstat(id, path));
        channel.sendData(packet);
        SftpPacket response = decodeResponse();
        if (response instanceof SftpPacket.Attrs a) {
            return a.attrs();
        }
        throw toException(response);
    }

    /**
     * Resolves a path to its absolute (canonical) form.
     *
     * @param path the path to resolve
     * @return the absolute path
     * @throws IOException if the operation fails
     * @throws InterruptedException if interrupted
     */
    public String realpath(String path) throws IOException, InterruptedException {
        int id = requestId.getAndIncrement();
        byte[] packet = SftpCodec.encode(new SftpPacket.Realpath(id, path));
        channel.sendData(packet);
        SftpPacket response = decodeResponse();
        if (response instanceof SftpPacket.Name n && !n.entries().isEmpty()) {
            return n.entries().getFirst().filename();
        }
        throw toException(response);
    }

    /**
     * Sends an extended request.
     *
     * @param extensionName the extension name (e.g. "posix-rename@openssh.com")
     * @param data          the extension-specific data
     * @return the response packet
     * @throws IOException if the operation fails
     * @throws InterruptedException if interrupted
     */
    public SftpPacket sendExtended(String extensionName, byte[] data)
            throws IOException, InterruptedException {
        int id = requestId.getAndIncrement();
        byte[] packet = SftpCodec.encode(new SftpPacket.Extended(id, extensionName, data));
        channel.sendData(packet);
        return decodeResponse();
    }

    /**
     * Returns the server SFTP version.
     *
     * @return the version number
     */
    public int serverVersion() { return serverVersion; }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private SftpPacket decodeResponse() throws IOException, InterruptedException {
        byte[] data = channel.receiveData();
        return SftpCodec.decode(data);
    }

    private void checkStatus(SftpPacket response) throws IOException {
        if (response instanceof SftpPacket.Status s) {
            if (s.statusCode() != SftpStatusCode.SSH_FX_OK) {
                throw new IOException("SFTP error: " + s.statusCode().description()
                        + " - " + s.message());
            }
        }
    }

    private IOException toException(SftpPacket response) {
        if (response instanceof SftpPacket.Status s) {
            return new IOException("SFTP error: " + s.statusCode().description()
                    + " - " + s.message());
        }
        return new IOException("Unexpected SFTP response: " + response);
    }
}
