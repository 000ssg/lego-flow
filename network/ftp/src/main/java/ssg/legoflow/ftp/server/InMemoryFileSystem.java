package ssg.legoflow.ftp.server;

import ssg.legoflow.ftp.client.FtpFileEntry;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory filesystem implementation for testing and virtual FTP servers.
 *
 * <p>Stores all file data in memory using a flat path map. Directories are
 * represented as entries with no data.
 *
 * @since 1.0.0
 */
public final class InMemoryFileSystem implements FtpFileSystem {

    private final Map<String, FileNode> nodes = new ConcurrentHashMap<>();

    /**
     * Creates an in-memory filesystem with a root directory.
     */
    public InMemoryFileSystem() {
        nodes.put("/", new FileNode("/", true, new byte[0], LocalDateTime.now()));
    }

    @Override
    public List<FtpFileEntry> listFiles(String path) throws IOException {
        String normalized = normalizePath(path);
        FileNode dirNode = nodes.get(normalized);
        if (dirNode == null || !dirNode.isDirectory) {
            throw new IOException("Not a directory: " + path);
        }
        List<FtpFileEntry> entries = new ArrayList<>();
        String prefix = normalized.endsWith("/") ? normalized : normalized + "/";
        for (Map.Entry<String, FileNode> entry : nodes.entrySet()) {
            String entryPath = entry.getKey();
            if (entryPath.equals(normalized)) continue;
            if (entryPath.startsWith(prefix)) {
                // Only direct children (no deeper nesting)
                String remainder = entryPath.substring(prefix.length());
                if (!remainder.contains("/")) {
                    FileNode node = entry.getValue();
                    entries.add(nodeToEntry(node));
                }
            }
        }
        return entries;
    }

    @Override
    public FtpFileEntry getFile(String path) throws IOException {
        String normalized = normalizePath(path);
        FileNode node = nodes.get(normalized);
        if (node == null) return null;
        return nodeToEntry(node);
    }

    @Override
    public boolean exists(String path) throws IOException {
        return nodes.containsKey(normalizePath(path));
    }

    @Override
    public InputStream readFile(String path) throws IOException {
        String normalized = normalizePath(path);
        FileNode node = nodes.get(normalized);
        if (node == null || node.isDirectory) {
            throw new IOException("Not a readable file: " + path);
        }
        return new ByteArrayInputStream(node.data);
    }

    @Override
    public OutputStream writeFile(String path) throws IOException {
        String normalized = normalizePath(path);
        return new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                super.close();
                nodes.put(normalized, new FileNode(
                        extractName(normalized), false, toByteArray(), LocalDateTime.now()));
            }
        };
    }

    @Override
    public OutputStream appendFile(String path) throws IOException {
        String normalized = normalizePath(path);
        FileNode existing = nodes.get(normalized);
        byte[] existingData = (existing != null && !existing.isDirectory) ? existing.data : new byte[0];
        return new ByteArrayOutputStream() {
            {
                write(existingData);
            }

            @Override
            public void close() throws IOException {
                super.close();
                nodes.put(normalized, new FileNode(
                        extractName(normalized), false, toByteArray(), LocalDateTime.now()));
            }
        };
    }

    @Override
    public void createFile(String path) throws IOException {
        String normalized = normalizePath(path);
        if (nodes.containsKey(normalized)) {
            throw new IOException("File already exists: " + path);
        }
        nodes.put(normalized, new FileNode(extractName(normalized), false, new byte[0], LocalDateTime.now()));
    }

    @Override
    public void deleteFile(String path) throws IOException {
        String normalized = normalizePath(path);
        FileNode node = nodes.get(normalized);
        if (node == null) {
            throw new IOException("File not found: " + path);
        }
        if (node.isDirectory) {
            throw new IOException("Not a file: " + path);
        }
        nodes.remove(normalized);
    }

    @Override
    public void createDirectory(String path) throws IOException {
        String normalized = normalizePath(path);
        nodes.put(normalized, new FileNode(extractName(normalized), true, new byte[0], LocalDateTime.now()));
    }

    @Override
    public void deleteDirectory(String path) throws IOException {
        String normalized = normalizePath(path);
        FileNode node = nodes.get(normalized);
        if (node == null) {
            throw new IOException("Directory not found: " + path);
        }
        if (!node.isDirectory) {
            throw new IOException("Not a directory: " + path);
        }
        // Check if empty
        String prefix = normalized.endsWith("/") ? normalized : normalized + "/";
        for (String key : nodes.keySet()) {
            if (key.startsWith(prefix)) {
                throw new IOException("Directory not empty: " + path);
            }
        }
        nodes.remove(normalized);
    }

    @Override
    public void rename(String from, String to) throws IOException {
        String normalizedFrom = normalizePath(from);
        String normalizedTo = normalizePath(to);
        FileNode node = nodes.get(normalizedFrom);
        if (node == null) {
            throw new IOException("Source not found: " + from);
        }
        nodes.remove(normalizedFrom);
        nodes.put(normalizedTo, new FileNode(
                extractName(normalizedTo), node.isDirectory, node.data, node.modified));
    }

    @Override
    public long getSize(String path) throws IOException {
        String normalized = normalizePath(path);
        FileNode node = nodes.get(normalized);
        if (node == null) {
            throw new IOException("File not found: " + path);
        }
        return node.data.length;
    }

    @Override
    public LocalDateTime getModificationTime(String path) throws IOException {
        String normalized = normalizePath(path);
        FileNode node = nodes.get(normalized);
        if (node == null) {
            throw new IOException("File not found: " + path);
        }
        return node.modified;
    }

    @Override
    public String getPermissions(String path) throws IOException {
        String normalized = normalizePath(path);
        FileNode node = nodes.get(normalized);
        if (node == null) {
            throw new IOException("File not found: " + path);
        }
        return node.isDirectory ? "rwxr-xr-x" : "rw-r--r--";
    }

    /**
     * Writes raw data to a file directly (convenience for testing).
     *
     * @param path the file path
     * @param data the file contents
     */
    public void putFile(String path, byte[] data) {
        String normalized = normalizePath(path);
        nodes.put(normalized, new FileNode(extractName(normalized), false, data, LocalDateTime.now()));
    }

    /**
     * Reads raw data from a file directly (convenience for testing).
     *
     * @param path the file path
     * @return the file contents, or {@code null} if not found
     */
    public byte[] getFileData(String path) {
        String normalized = normalizePath(path);
        FileNode node = nodes.get(normalized);
        return node != null ? node.data : null;
    }

    /**
     * Returns the number of entries in the filesystem.
     *
     * @return the entry count
     */
    public int size() {
        return nodes.size();
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        // Remove trailing slash (unless root)
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String extractName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < path.length() - 1 ?
                path.substring(lastSlash + 1) : path;
    }

    private FtpFileEntry nodeToEntry(FileNode node) {
        return FtpFileEntry.of(
                node.name,
                node.data.length,
                node.modified,
                node.isDirectory ? FtpFileEntry.Type.DIRECTORY : FtpFileEntry.Type.FILE
        );
    }

    private record FileNode(String name, boolean isDirectory, byte[] data, LocalDateTime modified) {
    }
}
