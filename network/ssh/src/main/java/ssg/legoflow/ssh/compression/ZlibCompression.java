package ssg.legoflow.ssh.compression;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Zlib compression for SSH (zlib) per RFC 1950.
 *
 * <p>Uses {@link java.util.zip.Deflater} and {@link java.util.zip.Inflater}
 * for compression and decompression. The compressor/decompressor state is
 * maintained across packets as required by SSH.
 *
 * @since 0.1.0
 */
public final class ZlibCompression implements SshCompression {

    private final Deflater deflater;
    private final Inflater inflater;

    /**
     * Creates a new zlib compression handler.
     */
    public ZlibCompression() {
        this.deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        this.inflater = new Inflater();
    }

    @Override public String name() { return "zlib"; }
    @Override public boolean isDelayed() { return false; }
    @Override public void setActive(boolean active) { /* always active */ }

    @Override
    public byte[] compress(byte[] data) {
        deflater.setInput(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
        byte[] buf = new byte[4096];
        // Use SYNC_FLUSH to ensure all data is output
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
