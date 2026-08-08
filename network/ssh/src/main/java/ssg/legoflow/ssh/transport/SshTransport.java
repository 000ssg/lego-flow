package ssg.legoflow.ssh.transport;

import ssg.legoflow.ssh.cipher.CipherFactory;
import ssg.legoflow.ssh.cipher.SshCipher;
import ssg.legoflow.ssh.compression.SshCompression;
import ssg.legoflow.ssh.compression.NoneCompression;
import ssg.legoflow.ssh.compression.ZlibCompression;
import ssg.legoflow.ssh.compression.ZlibOpenSshCompression;
import ssg.legoflow.ssh.kex.*;
import ssg.legoflow.ssh.mac.MacFactory;
import ssg.legoflow.ssh.mac.SshMac;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SSH transport layer implementation per RFC 4253.
 *
 * <p>Handles version exchange, algorithm negotiation (KEXINIT), key exchange,
 * service requests, and manages rekeying. This is the foundation layer that
 * all higher SSH services (authentication, connection) build upon.
 *
 * @since 0.1.0
 */
public final class SshTransport implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SshTransport.class);

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final SshTransportCodec codec;
    private final boolean isServer;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final ReentrantLock readLock = new ReentrantLock();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private SshVersion localVersion;
    private SshVersion remoteVersion;
    private byte[] sessionId;
    private KexInit localKexInit;
    private KexInit remoteKexInit;

    // Negotiated algorithms
    private String kexAlgorithm;
    private String hostKeyAlgorithm;
    private String cipherClientToServer;
    private String cipherServerToClient;
    private String macClientToServer;
    private String macServerToClient;
    private String compressionClientToServer;
    private String compressionServerToClient;

    /**
     * Creates a new SSH transport layer over an existing socket.
     *
     * @param socket   the underlying TCP socket
     * @param isServer true if this is the server side
     * @throws IOException if an I/O error occurs
     */
    public SshTransport(Socket socket, boolean isServer) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.in = new BufferedInputStream(socket.getInputStream());
        this.out = new BufferedOutputStream(socket.getOutputStream());
        this.codec = new SshTransportCodec();
        this.isServer = isServer;
        this.localVersion = SshVersion.defaultVersion();
    }

    /**
     * Performs the SSH version exchange.
     *
     * @return the remote peer's version
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if the remote version is incompatible
     */
    public SshVersion exchangeVersions() throws IOException {
        // Send our version
        byte[] versionBytes = localVersion.toBytes();
        out.write(versionBytes);
        out.flush();
        LOG.debug("Sent version: {}", localVersion);

        // Read remote version (skip lines not starting with SSH-)
        String remoteLine = readLine();
        while (remoteLine != null && !remoteLine.startsWith("SSH-")) {
            remoteLine = readLine();
        }
        if (remoteLine == null) {
            throw new IOException("Connection closed before version exchange");
        }

        remoteVersion = SshVersion.parse(remoteLine);
        LOG.debug("Received version: {}", remoteVersion);

        if (!remoteVersion.isCompatible()) {
            throw new IllegalArgumentException("Incompatible SSH version: " + remoteVersion);
        }

        return remoteVersion;
    }

    /**
     * Sends an SSH_MSG_KEXINIT message.
     *
     * @param kexInit the KEXINIT message to send
     * @throws IOException if an I/O error occurs
     */
    public void sendKexInit(KexInit kexInit) throws IOException {
        this.localKexInit = kexInit;
        byte[] payload = kexInit.encode();
        sendPacket(payload);
        LOG.debug("Sent KEXINIT");
    }

    /**
     * Reads and returns the next SSH packet payload.
     *
     * @return the payload bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] readPacket() throws IOException {
        readLock.lock();
        try {
            // Read the first block (or 4 bytes for packet length if unencrypted)
            int blockSize = 8; // minimum block size
            byte[] firstBlock = readExact(blockSize);

            ByteBuffer fb = ByteBuffer.wrap(firstBlock);
            int packetLength = fb.getInt();

            if (packetLength < 1 || packetLength > SshTransportCodec.MAX_PACKET_SIZE) {
                throw new IOException("Invalid packet length: " + packetLength);
            }

            // Read the rest: packetLength - (blockSize - 4) bytes + MAC
            int remaining = packetLength - (blockSize - 4);
            int macLength = 0; // MAC handled by codec

            byte[] restData = remaining > 0 ? readExact(remaining) : new byte[0];

            // Combine into full packet
            byte[] fullPacket = new byte[4 + packetLength];
            System.arraycopy(firstBlock, 0, fullPacket, 0, blockSize);
            if (remaining > 0) {
                System.arraycopy(restData, 0, fullPacket, blockSize, remaining);
            }

            return codec.decode(fullPacket);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Sends a raw payload as an SSH binary packet.
     *
     * @param payload the payload bytes
     * @throws IOException if an I/O error occurs
     */
    public void sendPacket(byte[] payload) throws IOException {
        writeLock.lock();
        try {
            byte[] packet = codec.encode(payload);
            out.write(packet);
            out.flush();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Sends a disconnect message and closes the connection.
     *
     * @param reasonCode  the disconnect reason code
     * @param description the disconnect description
     * @throws IOException if an I/O error occurs
     */
    public void disconnect(int reasonCode, String description) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(256);
        buf.put((byte) SshMessageType.SSH_MSG_DISCONNECT.code());
        buf.putInt(reasonCode);
        SshTransportCodec.writeString(buf, description);
        SshTransportCodec.writeString(buf, "");
        buf.flip();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        try {
            sendPacket(payload);
        } finally {
            close();
        }
    }

    /**
     * Sends a service request message.
     *
     * @param serviceName the service name (e.g., "ssh-userauth", "ssh-connection")
     * @throws IOException if an I/O error occurs
     */
    public void sendServiceRequest(String serviceName) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(256);
        buf.put((byte) SshMessageType.SSH_MSG_SERVICE_REQUEST.code());
        SshTransportCodec.writeString(buf, serviceName);
        buf.flip();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        sendPacket(payload);
        LOG.debug("Sent service request: {}", serviceName);
    }

    /**
     * Sends a service accept message.
     *
     * @param serviceName the service name
     * @throws IOException if an I/O error occurs
     */
    public void sendServiceAccept(String serviceName) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(256);
        buf.put((byte) SshMessageType.SSH_MSG_SERVICE_ACCEPT.code());
        SshTransportCodec.writeString(buf, serviceName);
        buf.flip();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        sendPacket(payload);
        LOG.debug("Sent service accept: {}", serviceName);
    }

    /**
     * Sends SSH_MSG_NEWKEYS to signal switch to new encryption.
     *
     * @throws IOException if an I/O error occurs
     */
    public void sendNewKeys() throws IOException {
        sendPacket(new byte[]{(byte) SshMessageType.SSH_MSG_NEWKEYS.code()});
        LOG.debug("Sent NEWKEYS");
    }

    /**
     * Negotiates algorithms from local and remote KEXINIT messages.
     *
     * @param local  local KEXINIT message
     * @param remote remote KEXINIT message
     * @return map of algorithm type to negotiated algorithm name
     * @throws IllegalStateException if no common algorithm is found
     */
    public Map<String, String> negotiateAlgorithms(KexInit local, KexInit remote) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("kex", negotiate(local.kexAlgorithms(), remote.kexAlgorithms(), "kex"));
        result.put("hostkey", negotiate(local.serverHostKeyAlgorithms(), remote.serverHostKeyAlgorithms(), "host key"));
        result.put("cipher_c2s", negotiate(local.encryptionAlgorithmsClientToServer(), remote.encryptionAlgorithmsClientToServer(), "cipher c2s"));
        result.put("cipher_s2c", negotiate(local.encryptionAlgorithmsServerToClient(), remote.encryptionAlgorithmsServerToClient(), "cipher s2c"));
        result.put("mac_c2s", negotiate(local.macAlgorithmsClientToServer(), remote.macAlgorithmsClientToServer(), "mac c2s"));
        result.put("mac_s2c", negotiate(local.macAlgorithmsServerToClient(), remote.macAlgorithmsServerToClient(), "mac s2c"));
        result.put("comp_c2s", negotiate(local.compressionAlgorithmsClientToServer(), remote.compressionAlgorithmsClientToServer(), "compression c2s"));
        result.put("comp_s2c", negotiate(local.compressionAlgorithmsServerToClient(), remote.compressionAlgorithmsServerToClient(), "compression s2c"));

        this.kexAlgorithm = result.get("kex");
        this.hostKeyAlgorithm = result.get("hostkey");
        this.cipherClientToServer = result.get("cipher_c2s");
        this.cipherServerToClient = result.get("cipher_s2c");
        this.macClientToServer = result.get("mac_c2s");
        this.macServerToClient = result.get("mac_s2c");
        this.compressionClientToServer = result.get("comp_c2s");
        this.compressionServerToClient = result.get("comp_s2c");

        LOG.debug("Negotiated algorithms: {}", result);
        return result;
    }

    /**
     * Applies new keys derived from key exchange.
     *
     * @param kexResult the key exchange result containing shared secret and exchange hash
     */
    public void applyNewKeys(KexResult kexResult) {
        if (sessionId == null) {
            sessionId = kexResult.exchangeHash().clone();
        }
        // Keys would be derived here using the KDF from RFC 4253 §7.2
        // For now, store the session ID
        LOG.debug("New keys applied, session ID established");
    }

    /**
     * Returns the session ID (exchange hash from first key exchange).
     *
     * @return the session ID, or null if key exchange has not completed
     */
    public byte[] sessionId() {
        return sessionId != null ? sessionId.clone() : null;
    }

    /**
     * Returns the local version.
     *
     * @return the local SSH version
     */
    public SshVersion localVersion() {
        return localVersion;
    }

    /**
     * Returns the remote version.
     *
     * @return the remote SSH version, or null if not yet exchanged
     */
    public SshVersion remoteVersion() {
        return remoteVersion;
    }

    /**
     * Returns whether this transport is the server side.
     *
     * @return true if server side
     */
    public boolean isServer() {
        return isServer;
    }

    /**
     * Returns the underlying codec.
     *
     * @return the transport codec
     */
    public SshTransportCodec codec() {
        return codec;
    }

    /**
     * Returns whether the transport is closed.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            try {
                socket.close();
            } catch (IOException e) {
                LOG.debug("Error closing socket", e);
            }
        }
    }

    // --- Private helpers ---

    private String negotiate(List<String> clientList, List<String> serverList, String type) {
        List<String> client = isServer ? serverList : clientList;
        List<String> server = isServer ? clientList : serverList;
        for (String alg : client) {
            if (server.contains(alg)) {
                return alg;
            }
        }
        throw new IllegalStateException("No common " + type + " algorithm found. Client: " + clientList + ", Server: " + serverList);
    }

    private String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = in.read()) != -1) {
            if (ch == '\n') {
                // Strip trailing CR if present
                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '\r') {
                    sb.setLength(sb.length() - 1);
                }
                return sb.toString();
            }
            sb.append((char) ch);
            if (sb.length() > SshVersion.MAX_VERSION_LENGTH) {
                throw new IOException("Version string too long");
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private byte[] readExact(int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(buf, offset, length - offset);
            if (n == -1) {
                throw new IOException("Unexpected end of stream");
            }
            offset += n;
        }
        return buf;
    }
}
