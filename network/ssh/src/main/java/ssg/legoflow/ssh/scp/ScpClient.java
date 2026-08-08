package ssg.legoflow.ssh.scp;

import ssg.legoflow.ssh.connection.SessionChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

/**
 * SCP file transfer client for uploading and downloading files.
 *
 * <p>Implements the SCP protocol over SSH session channels.
 *
 * @since 0.1.0
 */
public final class ScpClient {

    private static final Logger LOG = LoggerFactory.getLogger(ScpClient.class);

    private final SessionChannel channel;

    /**
     * Creates a new SCP client over a session channel.
     *
     * @param channel the session channel
     */
    public ScpClient(SessionChannel channel) {
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    /**
     * Uploads a file to the remote server.
     *
     * @param localPath  the local file path
     * @param remotePath the remote destination path
     * @throws IOException if the transfer fails
     * @throws InterruptedException if interrupted
     */
    public void upload(Path localPath, String remotePath) throws IOException, InterruptedException {
        upload(localPath, remotePath, false);
    }

    /**
     * Uploads a file to the remote server with optional timestamp preservation.
     *
     * @param localPath          the local file path
     * @param remotePath         the remote destination path
     * @param preserveTimestamps if true, sends T commands before file data (-p flag)
     * @throws IOException if the transfer fails
     * @throws InterruptedException if interrupted
     */
    public void upload(Path localPath, String remotePath, boolean preserveTimestamps)
            throws IOException, InterruptedException {
        if (!Files.exists(localPath)) {
            throw new FileNotFoundException("Local file not found: " + localPath);
        }

        // Start SCP sink on remote side (with -p flag if preserving timestamps)
        String scpCmd = preserveTimestamps ? "scp -tp " + remotePath : "scp -t " + remotePath;
        channel.requestExec(scpCmd);

        // Wait for initial OK (0 byte)
        byte[] ack = channel.receiveData(5000);

        // Send timestamp command if preserving
        if (preserveTimestamps) {
            BasicFileAttributes attrs = Files.readAttributes(localPath, BasicFileAttributes.class);
            long mtime = attrs.lastModifiedTime().toMillis() / 1000;
            long atime = attrs.lastAccessTime().toMillis() / 1000;
            String tCmd = String.format("T%d 0 %d 0\n", mtime, atime);
            channel.sendData(tCmd.getBytes(StandardCharsets.UTF_8));
            ack = channel.receiveData(5000);
        }

        long fileSize = Files.size(localPath);
        String fileName = localPath.getFileName().toString();

        // Send file header: C<permissions> <size> <filename>\n
        String header = String.format("C0644 %d %s\n", fileSize, fileName);
        channel.sendData(header.getBytes(StandardCharsets.UTF_8));

        // Wait for OK
        ack = channel.receiveData(5000);

        // Send file content
        byte[] content = Files.readAllBytes(localPath);
        channel.sendData(content);

        // Send final null byte
        channel.sendData(new byte[]{0});

        // Wait for OK
        ack = channel.receiveData(5000);

        LOG.debug("Uploaded {} ({} bytes) to {}", fileName, fileSize, remotePath);
    }

    /**
     * Downloads a file from the remote server.
     *
     * @param remotePath the remote file path
     * @param localPath  the local destination path
     * @throws IOException if the transfer fails
     * @throws InterruptedException if interrupted
     */
    public void download(String remotePath, Path localPath)
            throws IOException, InterruptedException {
        download(remotePath, localPath, false);
    }

    /**
     * Downloads a file from the remote server with optional timestamp preservation.
     *
     * @param remotePath         the remote file path
     * @param localPath          the local destination path
     * @param preserveTimestamps if true, requests and applies timestamps from T commands (-p flag)
     * @throws IOException if the transfer fails
     * @throws InterruptedException if interrupted
     */
    public void download(String remotePath, Path localPath, boolean preserveTimestamps)
            throws IOException, InterruptedException {
        // Start SCP source on remote side (with -p flag if preserving timestamps)
        String scpCmd = preserveTimestamps ? "scp -pf " + remotePath : "scp -f " + remotePath;
        channel.requestExec(scpCmd);

        // Send initial OK
        channel.sendData(new byte[]{0});

        // Read file header
        byte[] headerData = channel.receiveData(5000);
        if (headerData == null || headerData.length == 0) {
            throw new IOException("No SCP header received");
        }
        String header = new String(headerData, StandardCharsets.UTF_8).trim();

        // Parse T command if present (timestamp preservation)
        long mtime = -1;
        long atime = -1;
        if (header.startsWith("T")) {
            String[] tParts = header.substring(1).split(" ");
            mtime = Long.parseLong(tParts[0]);
            atime = Long.parseLong(tParts[2]);

            // Send OK to acknowledge T command
            channel.sendData(new byte[]{0});

            // Read the actual C header
            headerData = channel.receiveData(5000);
            if (headerData == null || headerData.length == 0) {
                throw new IOException("No SCP file header received after T command");
            }
            header = new String(headerData, StandardCharsets.UTF_8).trim();
        }

        if (header.startsWith("C")) {
            // Parse: C<permissions> <size> <filename>
            String[] parts = header.substring(1).split(" ", 3);
            long fileSize = Long.parseLong(parts[1]);

            // Send OK
            channel.sendData(new byte[]{0});

            // Read file content
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            long remaining = fileSize;
            while (remaining > 0) {
                byte[] chunk = channel.receiveData(5000);
                if (chunk == null || chunk.length == 0) break;
                int toWrite = (int) Math.min(chunk.length, remaining);
                baos.write(chunk, 0, toWrite);
                remaining -= toWrite;
            }

            Files.write(localPath, baos.toByteArray());

            // Apply timestamps if received from T command
            if (mtime >= 0) {
                Files.setLastModifiedTime(localPath, FileTime.fromMillis(mtime * 1000));
            }

            // Read trailing null byte
            channel.receiveData(1000);

            // Send OK
            channel.sendData(new byte[]{0});

            LOG.debug("Downloaded {} ({} bytes) from {}", localPath.getFileName(),
                    fileSize, remotePath);
        } else if (header.startsWith("") || header.startsWith("")) {
            throw new IOException("SCP error: " + header.substring(1));
        }
    }

    /**
     * Uploads a directory recursively.
     *
     * @param localDir   the local directory
     * @param remotePath the remote destination
     * @throws IOException if the transfer fails
     * @throws InterruptedException if interrupted
     */
    public void uploadDirectory(Path localDir, String remotePath)
            throws IOException, InterruptedException {
        uploadDirectory(localDir, remotePath, false);
    }

    /**
     * Uploads a directory recursively with optional timestamp preservation.
     *
     * @param localDir           the local directory
     * @param remotePath         the remote destination
     * @param preserveTimestamps if true, sends T commands before each file (-p flag)
     * @throws IOException if the transfer fails
     * @throws InterruptedException if interrupted
     */
    public void uploadDirectory(Path localDir, String remotePath, boolean preserveTimestamps)
            throws IOException, InterruptedException {
        if (!Files.isDirectory(localDir)) {
            throw new IOException("Not a directory: " + localDir);
        }

        String scpCmd = preserveTimestamps ? "scp -rpt " + remotePath : "scp -rt " + remotePath;
        channel.requestExec(scpCmd);
        byte[] ack = channel.receiveData(5000);

        // Send timestamp for directory if preserving
        if (preserveTimestamps) {
            BasicFileAttributes dirAttrs = Files.readAttributes(localDir, BasicFileAttributes.class);
            long mtime = dirAttrs.lastModifiedTime().toMillis() / 1000;
            long atime = dirAttrs.lastAccessTime().toMillis() / 1000;
            String tCmd = String.format("T%d 0 %d 0\n", mtime, atime);
            channel.sendData(tCmd.getBytes(StandardCharsets.UTF_8));
            ack = channel.receiveData(5000);
        }

        // Send directory header
        String dirName = localDir.getFileName().toString();
        String header = String.format("D0755 0 %s\n", dirName);
        channel.sendData(header.getBytes(StandardCharsets.UTF_8));
        ack = channel.receiveData(5000);

        // Upload each file
        try (var stream = Files.list(localDir)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (Files.isRegularFile(file)) {
                    // Send timestamp if preserving
                    if (preserveTimestamps) {
                        BasicFileAttributes fAttrs = Files.readAttributes(file,
                                BasicFileAttributes.class);
                        long fMtime = fAttrs.lastModifiedTime().toMillis() / 1000;
                        long fAtime = fAttrs.lastAccessTime().toMillis() / 1000;
                        String tCmd = String.format("T%d 0 %d 0\n", fMtime, fAtime);
                        channel.sendData(tCmd.getBytes(StandardCharsets.UTF_8));
                        channel.receiveData(5000);
                    }

                    long size = Files.size(file);
                    String fHeader = String.format("C0644 %d %s\n", size,
                            file.getFileName().toString());
                    channel.sendData(fHeader.getBytes(StandardCharsets.UTF_8));
                    channel.receiveData(5000);
                    channel.sendData(Files.readAllBytes(file));
                    channel.sendData(new byte[]{0});
                    channel.receiveData(5000);
                }
            }
        }

        // End directory
        channel.sendData("E\n".getBytes(StandardCharsets.UTF_8));
        channel.receiveData(5000);

        LOG.debug("Uploaded directory {} to {}", dirName, remotePath);
    }
}
