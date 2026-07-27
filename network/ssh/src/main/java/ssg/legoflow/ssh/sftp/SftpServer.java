package ssg.legoflow.ssh.sftp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import ssg.legoflow.ssh.transport.SshTransportCodec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SFTP server subsystem handler.
 *
 * <p>Implements SFTP version 3 protocol handling for file operations
 * on the server's local filesystem.
 *
 * @since 1.0.0
 */
public final class SftpServer {

    private static final Logger LOG = LoggerFactory.getLogger(SftpServer.class);
    private static final int SFTP_VERSION = 3;

    private final Path rootDirectory;
    private final Map<String, Object> handles = new ConcurrentHashMap<>();
    private final AtomicInteger handleCounter = new AtomicInteger(0);

    /**
     * Creates a new SFTP server rooted at the given directory.
     *
     * @param rootDirectory the root directory for file operations
     */
    public SftpServer(Path rootDirectory) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory");
    }

    /**
     * Handles an incoming SFTP packet and returns the response.
     *
     * @param data the raw SFTP packet data
     * @return the response packet data
     */
    public byte[] handlePacket(byte[] data) {
        SftpPacket packet;
        try {
            packet = SftpCodec.decode(data);
        } catch (Exception e) {
            return SftpCodec.encode(new SftpPacket.Status(0,
                    SftpStatusCode.SSH_FX_BAD_MESSAGE, e.getMessage(), ""));
        }

        return switch (packet) {
            case SftpPacket.Init init -> handleInit(init);
            case SftpPacket.Open open -> handleOpen(open);
            case SftpPacket.Close close -> handleClose(close);
            case SftpPacket.Read read -> handleRead(read);
            case SftpPacket.Write write -> handleWrite(write);
            case SftpPacket.Stat stat -> handleStat(stat);
            case SftpPacket.Lstat lstat -> handleLstat(lstat);
            case SftpPacket.Opendir opendir -> handleOpendir(opendir);
            case SftpPacket.Readdir readdir -> handleReaddir(readdir);
            case SftpPacket.Remove remove -> handleRemove(remove);
            case SftpPacket.Mkdir mkdir -> handleMkdir(mkdir);
            case SftpPacket.Rmdir rmdir -> handleRmdir(rmdir);
            case SftpPacket.Realpath realpath -> handleRealpath(realpath);
            case SftpPacket.Rename rename -> handleRename(rename);
            case SftpPacket.Extended extended -> handleExtended(extended);
            default -> SftpCodec.encode(new SftpPacket.Status(0,
                    SftpStatusCode.SSH_FX_OP_UNSUPPORTED, "Not supported", ""));
        };
    }

    private byte[] handleInit(SftpPacket.Init init) {
        return SftpCodec.encode(new SftpPacket.Version(SFTP_VERSION));
    }

    private byte[] handleOpen(SftpPacket.Open open) {
        try {
            Path path = resolve(open.filename());
            String handleId = "fh" + handleCounter.incrementAndGet();
            Set<OpenOption> options = new HashSet<>();
            if ((open.pflags() & SftpCodec.SSH_FXF_READ) != 0) options.add(StandardOpenOption.READ);
            if ((open.pflags() & SftpCodec.SSH_FXF_WRITE) != 0) options.add(StandardOpenOption.WRITE);
            if ((open.pflags() & SftpCodec.SSH_FXF_CREAT) != 0) options.add(StandardOpenOption.CREATE);
            if ((open.pflags() & SftpCodec.SSH_FXF_TRUNC) != 0) options.add(StandardOpenOption.TRUNCATE_EXISTING);
            if ((open.pflags() & SftpCodec.SSH_FXF_APPEND) != 0) options.add(StandardOpenOption.APPEND);

            RandomAccessFile raf = new RandomAccessFile(path.toFile(),
                    (open.pflags() & SftpCodec.SSH_FXF_WRITE) != 0 ? "rw" : "r");
            handles.put(handleId, raf);
            return SftpCodec.encode(new SftpPacket.Handle(open.id(),
                    handleId.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return statusResponse(open.id(), SftpStatusCode.SSH_FX_NO_SUCH_FILE, e.getMessage());
        }
    }

    private byte[] handleClose(SftpPacket.Close close) {
        String handleId = new String(close.handle(), StandardCharsets.UTF_8);
        Object handle = handles.remove(handleId);
        if (handle instanceof RandomAccessFile raf) {
            try { raf.close(); } catch (IOException ignored) {}
        }
        return statusResponse(close.id(), SftpStatusCode.SSH_FX_OK, "");
    }

    private byte[] handleRead(SftpPacket.Read read) {
        String handleId = new String(read.handle(), StandardCharsets.UTF_8);
        Object handle = handles.get(handleId);
        if (handle instanceof RandomAccessFile raf) {
            try {
                raf.seek(read.offset());
                byte[] buf = new byte[read.length()];
                int n = raf.read(buf);
                if (n == -1) {
                    return statusResponse(read.id(), SftpStatusCode.SSH_FX_EOF, "");
                }
                byte[] data = n == buf.length ? buf : Arrays.copyOf(buf, n);
                return SftpCodec.encode(new SftpPacket.Data(read.id(), data));
            } catch (IOException e) {
                return statusResponse(read.id(), SftpStatusCode.SSH_FX_FAILURE, e.getMessage());
            }
        }
        return statusResponse(read.id(), SftpStatusCode.SSH_FX_FAILURE, "Invalid handle");
    }

    private byte[] handleWrite(SftpPacket.Write write) {
        String handleId = new String(write.handle(), StandardCharsets.UTF_8);
        Object handle = handles.get(handleId);
        if (handle instanceof RandomAccessFile raf) {
            try {
                raf.seek(write.offset());
                raf.write(write.data());
                return statusResponse(write.id(), SftpStatusCode.SSH_FX_OK, "");
            } catch (IOException e) {
                return statusResponse(write.id(), SftpStatusCode.SSH_FX_FAILURE, e.getMessage());
            }
        }
        return statusResponse(write.id(), SftpStatusCode.SSH_FX_FAILURE, "Invalid handle");
    }

    private byte[] handleStat(SftpPacket.Stat stat) {
        try {
            Path path = resolve(stat.path());
            BasicFileAttributes bfa = Files.readAttributes(path, BasicFileAttributes.class);
            return SftpCodec.encode(new SftpPacket.Attrs(stat.id(), toAttrs(bfa)));
        } catch (IOException e) {
            return statusResponse(stat.id(), SftpStatusCode.SSH_FX_NO_SUCH_FILE, e.getMessage());
        }
    }

    private byte[] handleLstat(SftpPacket.Lstat lstat) {
        try {
            Path path = resolve(lstat.path());
            BasicFileAttributes bfa = Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            return SftpCodec.encode(new SftpPacket.Attrs(lstat.id(), toAttrs(bfa)));
        } catch (IOException e) {
            return statusResponse(lstat.id(), SftpStatusCode.SSH_FX_NO_SUCH_FILE, e.getMessage());
        }
    }

    private byte[] handleOpendir(SftpPacket.Opendir opendir) {
        try {
            Path path = resolve(opendir.path());
            if (!Files.isDirectory(path)) {
                return statusResponse(opendir.id(), SftpStatusCode.SSH_FX_NO_SUCH_FILE,
                        "Not a directory");
            }
            String handleId = "dh" + handleCounter.incrementAndGet();
            handles.put(handleId, path);
            return SftpCodec.encode(new SftpPacket.Handle(opendir.id(),
                    handleId.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return statusResponse(opendir.id(), SftpStatusCode.SSH_FX_FAILURE, e.getMessage());
        }
    }

    private byte[] handleReaddir(SftpPacket.Readdir readdir) {
        String handleId = new String(readdir.handle(), StandardCharsets.UTF_8);
        Object handle = handles.get(handleId);
        if (handle instanceof Path dirPath) {
            try {
                List<SftpPacket.NameEntry> entries = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
                    for (Path entry : stream) {
                        BasicFileAttributes attrs = Files.readAttributes(entry,
                                BasicFileAttributes.class);
                        entries.add(new SftpPacket.NameEntry(
                                entry.getFileName().toString(),
                                entry.getFileName().toString(),
                                toAttrs(attrs)));
                    }
                }
                handles.remove(handleId); // consumed
                if (entries.isEmpty()) {
                    return statusResponse(readdir.id(), SftpStatusCode.SSH_FX_EOF, "");
                }
                return SftpCodec.encode(new SftpPacket.Name(readdir.id(), entries));
            } catch (IOException e) {
                return statusResponse(readdir.id(), SftpStatusCode.SSH_FX_FAILURE, e.getMessage());
            }
        }
        return statusResponse(readdir.id(), SftpStatusCode.SSH_FX_FAILURE, "Invalid handle");
    }

    private byte[] handleRemove(SftpPacket.Remove remove) {
        try {
            Files.delete(resolve(remove.filename()));
            return statusResponse(remove.id(), SftpStatusCode.SSH_FX_OK, "");
        } catch (IOException e) {
            return statusResponse(remove.id(), SftpStatusCode.SSH_FX_NO_SUCH_FILE, e.getMessage());
        }
    }

    private byte[] handleMkdir(SftpPacket.Mkdir mkdir) {
        try {
            Files.createDirectory(resolve(mkdir.path()));
            return statusResponse(mkdir.id(), SftpStatusCode.SSH_FX_OK, "");
        } catch (IOException e) {
            return statusResponse(mkdir.id(), SftpStatusCode.SSH_FX_FAILURE, e.getMessage());
        }
    }

    private byte[] handleRmdir(SftpPacket.Rmdir rmdir) {
        try {
            Files.delete(resolve(rmdir.path()));
            return statusResponse(rmdir.id(), SftpStatusCode.SSH_FX_OK, "");
        } catch (IOException e) {
            return statusResponse(rmdir.id(), SftpStatusCode.SSH_FX_NO_SUCH_FILE, e.getMessage());
        }
    }

    private byte[] handleRealpath(SftpPacket.Realpath realpath) {
        try {
            Path resolved = resolve(realpath.path()).toRealPath();
            List<SftpPacket.NameEntry> entries = List.of(
                    new SftpPacket.NameEntry(resolved.toString(), resolved.toString(),
                            SftpFileAttributes.empty()));
            return SftpCodec.encode(new SftpPacket.Name(realpath.id(), entries));
        } catch (IOException e) {
            return statusResponse(realpath.id(), SftpStatusCode.SSH_FX_NO_SUCH_FILE, e.getMessage());
        }
    }

    private byte[] handleRename(SftpPacket.Rename rename) {
        try {
            Files.move(resolve(rename.oldPath()), resolve(rename.newPath()));
            return statusResponse(rename.id(), SftpStatusCode.SSH_FX_OK, "");
        } catch (IOException e) {
            return statusResponse(rename.id(), SftpStatusCode.SSH_FX_FAILURE, e.getMessage());
        }
    }

    private byte[] handleExtended(SftpPacket.Extended ext) {
        return switch (ext.extendedRequest()) {
            case "posix-rename@openssh.com" -> handlePosixRename(ext);
            case "statvfs@openssh.com" -> handleStatvfs(ext);
            default -> statusResponse(ext.id(), SftpStatusCode.SSH_FX_OP_UNSUPPORTED,
                    "Unsupported extension: " + ext.extendedRequest());
        };
    }

    private byte[] handlePosixRename(SftpPacket.Extended ext) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(ext.data());
            String oldPath = SshTransportCodec.readString(buf);
            String newPath = SshTransportCodec.readString(buf);
            Files.move(resolve(oldPath), resolve(newPath), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            return statusResponse(ext.id(), SftpStatusCode.SSH_FX_OK, "");
        } catch (IOException e) {
            return statusResponse(ext.id(), SftpStatusCode.SSH_FX_FAILURE, e.getMessage());
        }
    }

    private byte[] handleStatvfs(SftpPacket.Extended ext) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(ext.data());
            String path = SshTransportCodec.readString(buf);
            Path resolved = resolve(path);
            FileStore store = Files.getFileStore(resolved);

            long blockSize = 4096; // standard block size
            long totalBlocks = store.getTotalSpace() / blockSize;
            long freeBlocks = store.getUnallocatedSpace() / blockSize;
            long availBlocks = store.getUsableSpace() / blockSize;

            // statvfs reply format: 11 uint64 fields
            ByteBuffer reply = ByteBuffer.allocate(88);
            reply.putLong(blockSize);   // f_bsize
            reply.putLong(blockSize);   // f_frsize (fragment size)
            reply.putLong(totalBlocks); // f_blocks
            reply.putLong(freeBlocks);  // f_bfree
            reply.putLong(availBlocks); // f_bavail
            reply.putLong(0);           // f_files (unknown)
            reply.putLong(0);           // f_ffree (unknown)
            reply.putLong(0);           // f_favail (unknown)
            reply.putLong(0);           // f_fsid
            reply.putLong(0x02);        // f_flag (ST_NOSUID)
            reply.putLong(255);         // f_namemax
            reply.flip();
            byte[] replyData = new byte[reply.remaining()];
            reply.get(replyData);

            return SftpCodec.encode(new SftpPacket.ExtendedReply(ext.id(), replyData));
        } catch (IOException e) {
            return statusResponse(ext.id(), SftpStatusCode.SSH_FX_FAILURE, e.getMessage());
        }
    }

    private Path resolve(String path) {
        if (path.startsWith("/")) {
            return rootDirectory.resolve(path.substring(1));
        }
        return rootDirectory.resolve(path);
    }

    private SftpFileAttributes toAttrs(BasicFileAttributes bfa) {
        int flags = SftpFileAttributes.SSH_FILEXFER_ATTR_SIZE
                | SftpFileAttributes.SSH_FILEXFER_ATTR_PERMISSIONS
                | SftpFileAttributes.SSH_FILEXFER_ATTR_ACMODTIME;
        int perms = bfa.isDirectory() ? 0040755 : 0100644;
        return new SftpFileAttributes(flags, bfa.size(), 0, 0, perms,
                bfa.lastAccessTime().toMillis() / 1000,
                bfa.lastModifiedTime().toMillis() / 1000);
    }

    private byte[] statusResponse(int id, SftpStatusCode code, String message) {
        return SftpCodec.encode(new SftpPacket.Status(id, code, message, ""));
    }
}
