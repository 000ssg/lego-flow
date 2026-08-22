package ssg.legoflow.ssh.scp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
/**
 * SCP server command handler.
 *
 * <p>Handles "scp -t" (sink/upload) and "scp -f" (source/download) commands.
 *
 * @since 0.1.0
 */
public final class ScpServer {

    private static final Logger LOG = LoggerFactory.getLogger(ScpServer.class);

    private final Path rootDirectory;

    /**
     * Creates a new SCP server handler.
     *
     * @param rootDirectory the root directory for file operations
     */
    public ScpServer(Path rootDirectory) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory");
    }

    /**
     * Handles an SCP sink command (upload from client).
     *
     * @param remotePath the destination path
     * @param input      the input stream (file data from client)
     * @param output     the output stream (ACKs to client)
     * @throws IOException if an I/O error occurs
     */
    public void handleSink(String remotePath, InputStream input, OutputStream output)
            throws IOException {
        // Send initial OK
        output.write(0);
        output.flush();

        // Read header (may be T command or C command)
        String header = readLine(input);
        if (header == null) {
            output.write(1);
            output.flush();
            return;
        }

        // Parse T command if present (timestamp preservation)
        long mtime = -1;
        long atime = -1;
        if (header.startsWith("T")) {
            String[] tParts = header.substring(1).split(" ");
            mtime = Long.parseLong(tParts[0]);
            atime = Long.parseLong(tParts[2]);

            // Send OK to acknowledge T command
            output.write(0);
            output.flush();

            // Read the actual C header
            header = readLine(input);
        }

        if (header == null || !header.startsWith("C")) {
            output.write(1);
            output.flush();
            return;
        }

        // Parse header: C<perms> <size> <filename>
        String[] parts = header.substring(1).split(" ", 3);
        long fileSize = Long.parseLong(parts[1]);
        String filename = parts[2];

        // Send OK
        output.write(0);
        output.flush();

        // Read file content
        Path destPath = rootDirectory.resolve(remotePath).resolve(filename);
        try (OutputStream fos = Files.newOutputStream(destPath)) {
            long remaining = fileSize;
            byte[] buf = new byte[8192];
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int n = input.read(buf, 0, toRead);
                if (n == -1) break;
                fos.write(buf, 0, n);
                remaining -= n;
            }
        }

        // Apply timestamps if received
        if (mtime >= 0) {
            Files.setLastModifiedTime(destPath, FileTime.fromMillis(mtime * 1000));
        }

        // Read trailing null byte
        input.read();

        // Send OK
        output.write(0);
        output.flush();

        LOG.debug("Received file: {} ({} bytes)", filename, fileSize);
    }

    /**
     * Handles an SCP source command (download to client).
     *
     * @param remotePath the source file path
     * @param input      the input stream (ACKs from client)
     * @param output     the output stream (file data to client)
     * @throws IOException if an I/O error occurs
     */
    public void handleSource(String remotePath, InputStream input, OutputStream output)
            throws IOException {
        handleSource(remotePath, input, output, false);
    }

    /**
     * Handles an SCP source command with optional timestamp preservation.
     *
     * @param remotePath         the source file path
     * @param input              the input stream (ACKs from client)
     * @param output             the output stream (file data to client)
     * @param preserveTimestamps if true, sends T command before file header
     * @throws IOException if an I/O error occurs
     */
    public void handleSource(String remotePath, InputStream input, OutputStream output,
                             boolean preserveTimestamps) throws IOException {
        Path sourcePath = rootDirectory.resolve(remotePath);

        if (!Files.exists(sourcePath)) {
            String err = "scp: " + remotePath + ": No such file\n";
            output.write(err.getBytes(StandardCharsets.UTF_8));
            output.flush();
            return;
        }

        // Wait for initial OK from client
        input.read();

        long fileSize = Files.size(sourcePath);
        String filename = sourcePath.getFileName().toString();

        // Send timestamp if preserving
        if (preserveTimestamps) {
            java.nio.file.attribute.BasicFileAttributes attrs =
                    Files.readAttributes(sourcePath, java.nio.file.attribute.BasicFileAttributes.class);
            long mtime = attrs.lastModifiedTime().toMillis() / 1000;
            long atime = attrs.lastAccessTime().toMillis() / 1000;
            String tCmd = String.format("T%d 0 %d 0\n", mtime, atime);
            output.write(tCmd.getBytes(StandardCharsets.UTF_8));
            output.flush();
            input.read(); // Wait for OK
        }

        // Send header
        String header = String.format("C0644 %d %s\n", fileSize, filename);
        output.write(header.getBytes(StandardCharsets.UTF_8));
        output.flush();

        // Wait for OK
        input.read();

        // Send file content
        try (InputStream fis = Files.newInputStream(sourcePath)) {
            fis.transferTo(output);
        }

        // Send trailing null byte
        output.write(0);
        output.flush();

        // Wait for OK
        input.read();

        LOG.debug("Sent file: {} ({} bytes)", filename, fileSize);
    }

    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = in.read()) != -1 && ch != '\n') {
            sb.append((char) ch);
        }
        return sb.isEmpty() && ch == -1 ? null : sb.toString();
    }
}
