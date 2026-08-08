package ssg.legoflow.ssh.compression;

/**
 * No-op compression (none) for SSH.
 *
 * @since 0.1.0
 */
public final class NoneCompression implements SshCompression {

    @Override public String name() { return "none"; }
    @Override public boolean isDelayed() { return false; }
    @Override public void setActive(boolean active) { /* no-op */ }

    @Override
    public byte[] compress(byte[] data) {
        return data;
    }

    @Override
    public byte[] decompress(byte[] data) {
        return data;
    }
}
