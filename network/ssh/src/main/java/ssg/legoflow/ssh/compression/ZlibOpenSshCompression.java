package ssg.legoflow.ssh.compression;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Delayed zlib compression for SSH (zlib@openssh.com).
 *
 * <p>Same as standard zlib compression, but only activates after user authentication
 * completes. Before authentication, data passes through uncompressed.
 *
 * @since 1.0.0
 */
public final class ZlibOpenSshCompression implements SshCompression {

    private final Deflater deflater;
    private final Inflater inflater;
    private volatile boolean active;

    /**
     * Creates a new delayed zlib compression handler.
     */
    public ZlibOpenSshCompression() {
        this.deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        this.inflater = new Inflater();
        this.active = false;
    }

    @Override public String name() { return "zlib@openssh.com"; }
    @Override public boolean isDelayed() { return true; }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public byte[] compress(byte[] data) {
        if (!active) return data;
        deflater.setInput(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
        byte[] buf = new byte[4096];
        while (!deflater.needsInput()) {
            int n = deflater.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH);
            if (n > 0) {
                baos.write(buf, 0, n);
            } else {
                break;
            }
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] decompress(byte[] data) {
        if (!active) return data;
        inflater.setInput(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length * 2);
        byte[] buf = new byte[4096];
        try {
            while (!inflater.needsInput()) {
                int n = inflater.inflate(buf);
                if (n > 0) {
                    baos.write(buf, 0, n);
                } else {
                    break;
                }
            }
        } catch (DataFormatException e) {
            throw new RuntimeException("Zlib decompression failed", e);
        }
        return baos.toByteArray();
    }
}
