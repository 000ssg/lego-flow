package ssg.legoflow.ftp.server;

import ssg.legoflow.ftp.client.FtpFileEntry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Interface for the virtual filesystem used by the FTP server.
 *
 * <p>Provides an abstraction over file operations, allowing implementations
 * backed by real filesystems, in-memory storage, or cloud storage.
 *
 * @since 1.0.0
 */
public interface FtpFileSystem {

    /**
     * Lists files in the given directory.
     *
     * @param path the directory path
     * @return the file entries
     * @throws IOException if the directory cannot be listed
     */
    List<FtpFileEntry> listFiles(String path) throws IOException;

    /**
     * Returns metadata for a single file or directory.
     *
     * @param path the file path
     * @return the file entry, or {@code null} if not found
     * @throws IOException if the lookup fails
     */
    FtpFileEntry getFile(String path) throws IOException;

    /**
     * Checks whether a file or directory exists.
     *
     * @param path the path to check
     * @return {@code true} if the path exists
     * @throws IOException if the check fails
     */
    boolean exists(String path) throws IOException;

    /**
     * Returns an input stream for reading a file.
     *
     * @param path the file path
     * @return the input stream (caller must close)
     * @throws IOException if the file cannot be opened for reading
     */
    InputStream readFile(String path) throws IOException;

    /**
     * Returns an output stream for writing a file (creates or overwrites).
     *
     * @param path the file path
     * @return the output stream (caller must close)
     * @throws IOException if the file cannot be opened for writing
     */
    OutputStream writeFile(String path) throws IOException;

    /**
     * Returns an output stream for appending to a file.
     *
     * @param path the file path
     * @return the output stream (caller must close)
     * @throws IOException if the file cannot be opened for appending
     */
    OutputStream appendFile(String path) throws IOException;

    /**
     * Creates a new file (empty).
     *
     * @param path the file path
     * @throws IOException if the file cannot be created
     */
    void createFile(String path) throws IOException;

    /**
     * Deletes a file.
     *
     * @param path the file path
     * @throws IOException if the file cannot be deleted
     */
    void deleteFile(String path) throws IOException;

    /**
     * Creates a directory.
     *
     * @param path the directory path
     * @throws IOException if the directory cannot be created
     */
    void createDirectory(String path) throws IOException;

    /**
     * Deletes a directory.
     *
     * @param path the directory path
     * @throws IOException if the directory cannot be deleted
     */
    void deleteDirectory(String path) throws IOException;

    /**
     * Renames a file or directory.
     *
     * @param from the current path
     * @param to   the new path
     * @throws IOException if the rename fails
     */
    void rename(String from, String to) throws IOException;

    /**
     * Returns the file size in bytes.
     *
     * @param path the file path
     * @return the size in bytes
     * @throws IOException if the size cannot be determined
     */
    long getSize(String path) throws IOException;

    /**
     * Returns the last modification time.
     *
     * @param path the file path
     * @return the modification time
     * @throws IOException if the time cannot be determined
     */
    LocalDateTime getModificationTime(String path) throws IOException;

    /**
     * Returns the permissions string (e.g., "rwxr-xr-x").
     *
     * @param path the file path
     * @return the permissions string, or {@code null} if not available
     * @throws IOException if the permissions cannot be determined
     */
    String getPermissions(String path) throws IOException;
}
