package ssg.legoflow.ftp.server;

import ssg.legoflow.ftp.client.FtpFileEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Filesystem implementation backed by the real local filesystem.
 *
 * <p>All paths are resolved relative to a base (chroot) directory.
 * Path traversal attacks (e.g., "../../etc/passwd") are prevented
 * by normalizing and validating that resolved paths remain within the base directory.
 *
 * @since 0.1.0
 */
public final class LocalFileSystem implements FtpFileSystem {

    private static final Logger LOG = LoggerFactory.getLogger(LocalFileSystem.class);
    private final Path baseDir;

    /**
     * Creates a local filesystem rooted at the given directory.
     *
     * @param baseDir the root directory (chroot)
     * @throws IOException if the directory does not exist or is not a directory
     */
    public LocalFileSystem(Path baseDir) throws IOException {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.baseDir)) {
            throw new IOException("Base directory does not exist or is not a directory: " + this.baseDir);
        }
    }

    @Override
    public List<FtpFileEntry> listFiles(String path) throws IOException {
        Path dir = resolve(path);
        if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + path);
        }
        List<FtpFileEntry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                entries.add(toFileEntry(entry));
            }
        }
        return entries;
    }

    @Override
    public FtpFileEntry getFile(String path) throws IOException {
        Path resolved = resolve(path);
        if (!Files.exists(resolved)) {
            return null;
        }
        return toFileEntry(resolved);
    }

    @Override
    public boolean exists(String path) throws IOException {
        return Files.exists(resolve(path));
    }

    @Override
    public InputStream readFile(String path) throws IOException {
        Path resolved = resolve(path);
        if (!Files.isRegularFile(resolved)) {
            throw new IOException("Not a regular file: " + path);
        }
        return Files.newInputStream(resolved);
    }

    @Override
    public OutputStream writeFile(String path) throws IOException {
        Path resolved = resolve(path);
        return Files.newOutputStream(resolved, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    @Override
    public OutputStream appendFile(String path) throws IOException {
        Path resolved = resolve(path);
        return Files.newOutputStream(resolved, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND, StandardOpenOption.WRITE);
    }

    @Override
    public void createFile(String path) throws IOException {
        Path resolved = resolve(path);
        Files.createFile(resolved);
    }

    @Override
    public void deleteFile(String path) throws IOException {
        Path resolved = resolve(path);
        if (!Files.isRegularFile(resolved)) {
            throw new IOException("Not a regular file: " + path);
        }
        Files.delete(resolved);
    }

    @Override
    public void createDirectory(String path) throws IOException {
        Path resolved = resolve(path);
        Files.createDirectories(resolved);
    }

    @Override
    public void deleteDirectory(String path) throws IOException {
        Path resolved = resolve(path);
        if (!Files.isDirectory(resolved)) {
            throw new IOException("Not a directory: " + path);
        }
        Files.delete(resolved);
    }

    @Override
    public void rename(String from, String to) throws IOException {
        Path source = resolve(from);
        Path target = resolve(to);
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public long getSize(String path) throws IOException {
        return Files.size(resolve(path));
    }

    @Override
    public LocalDateTime getModificationTime(String path) throws IOException {
        return LocalDateTime.ofInstant(
                Files.getLastModifiedTime(resolve(path)).toInstant(),
                ZoneId.systemDefault());
    }

    @Override
    public String getPermissions(String path) throws IOException {
        Path resolved = resolve(path);
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(resolved);
            return PosixFilePermissions.toString(perms);
        } catch (UnsupportedOperationException e) {
            // Windows doesn't support POSIX permissions
            return null;
        }
    }

    /**
     * Returns the base directory.
     *
     * @return the chroot base path
     */
    public Path baseDir() {
        return baseDir;
    }

    /**
     * Resolves and validates a virtual path against the chroot base.
     *
     * @param virtualPath the FTP path
     * @return the resolved real path
     * @throws IOException if the path escapes the chroot
     */
    Path resolve(String virtualPath) throws IOException {
        if (virtualPath == null || virtualPath.isEmpty() || "/".equals(virtualPath)) {
            return baseDir;
        }
        // Remove leading slash for relative resolution
        String cleaned = virtualPath.startsWith("/") ? virtualPath.substring(1) : virtualPath;
        Path resolved = baseDir.resolve(cleaned).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IOException("Path traversal attempt blocked: " + virtualPath);
        }
        return resolved;
    }

    private FtpFileEntry toFileEntry(Path path) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        FtpFileEntry.Type type;
        if (attrs.isDirectory()) {
            type = FtpFileEntry.Type.DIRECTORY;
        } else if (attrs.isSymbolicLink()) {
            type = FtpFileEntry.Type.SYMLINK;
        } else {
            type = FtpFileEntry.Type.FILE;
        }

        LocalDateTime modified = LocalDateTime.ofInstant(
                attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());

        String perms = null;
        try {
            perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
        } catch (UnsupportedOperationException ignored) {
            // Windows
        }

        return new FtpFileEntry(
                path.getFileName().toString(),
                attrs.size(),
                modified,
                type,
                perms,
                null,
                null,
                0,
                null
        );
    }
}
