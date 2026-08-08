package ssg.legoflow.ftp.server;

import ssg.legoflow.ftp.protocol.FtpTransferType;

import java.net.InetAddress;

/**
 * Per-client FTP session state.
 *
 * <p>Tracks the current directory, transfer type, authentication state,
 * rename state, and data connection mode for each connected client.
 *
 * @since 0.1.0
 */
public final class FtpSession {

    /**
     * Session state.
     */
    public enum State {
        /** Awaiting USER command. */
        NOT_AUTHENTICATED,
        /** USER received, awaiting PASS. */
        USER_PROVIDED,
        /** Fully authenticated. */
        AUTHENTICATED
    }

    /**
     * Data connection mode.
     */
    public enum DataMode {
        /** Active mode (PORT/EPRT). */
        ACTIVE,
        /** Passive mode (PASV/EPSV). */
        PASSIVE
    }

    private volatile State state = State.NOT_AUTHENTICATED;
    private volatile String username;
    private volatile String currentDirectory = "/";
    private volatile FtpTransferType transferType = FtpTransferType.BINARY;
    private volatile DataMode dataMode = DataMode.PASSIVE;
    private volatile String renameFrom;
    private volatile InetAddress dataAddress;
    private volatile int dataPort;
    private volatile boolean tlsEnabled = false;
    private volatile boolean dataProtected = false;
    private volatile long restartOffset = 0;
    private volatile boolean cccIssued = false;
    private volatile String account;
    private final String clientAddress;

    /**
     * Creates a new session for a client connection.
     *
     * @param clientAddress the client's address string for logging
     */
    public FtpSession(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    /** Returns the session authentication state. */
    public State state() { return state; }
    /** Sets the session state. */
    public void setState(State state) { this.state = state; }

    /** Returns the authenticated username, or {@code null}. */
    public String username() { return username; }
    /** Sets the username. */
    public void setUsername(String username) { this.username = username; }

    /** Returns the current working directory. */
    public String currentDirectory() { return currentDirectory; }
    /** Sets the current working directory. */
    public void setCurrentDirectory(String dir) { this.currentDirectory = dir; }

    /** Returns the current transfer type. */
    public FtpTransferType transferType() { return transferType; }
    /** Sets the transfer type. */
    public void setTransferType(FtpTransferType type) { this.transferType = type; }

    /** Returns the current data connection mode. */
    public DataMode dataMode() { return dataMode; }
    /** Sets the data connection mode. */
    public void setDataMode(DataMode mode) { this.dataMode = mode; }

    /** Returns the pending rename source path, or {@code null}. */
    public String renameFrom() { return renameFrom; }
    /** Sets the pending rename source path. */
    public void setRenameFrom(String path) { this.renameFrom = path; }

    /** Returns the data connection address (for active mode). */
    public InetAddress dataAddress() { return dataAddress; }
    /** Sets the data connection address. */
    public void setDataAddress(InetAddress addr) { this.dataAddress = addr; }

    /** Returns the data connection port. */
    public int dataPort() { return dataPort; }
    /** Sets the data connection port. */
    public void setDataPort(int port) { this.dataPort = port; }

    /** Returns whether TLS is enabled on the control connection. */
    public boolean isTlsEnabled() { return tlsEnabled; }
    /** Sets TLS state. */
    public void setTlsEnabled(boolean enabled) { this.tlsEnabled = enabled; }

    /** Returns whether data connections should be TLS-protected. */
    public boolean isDataProtected() { return dataProtected; }
    /** Sets data protection state. */
    public void setDataProtected(boolean protect) { this.dataProtected = protect; }

    /** Returns the restart offset for resumed transfers (REST command). */
    public long restartOffset() { return restartOffset; }
    /** Sets the restart offset and returns the old value. */
    public long setRestartOffset(long offset) {
        long old = this.restartOffset;
        this.restartOffset = offset;
        return old;
    }
    /** Clears the restart offset (sets it to 0) and returns the old value. */
    public long consumeRestartOffset() {
        long old = this.restartOffset;
        this.restartOffset = 0;
        return old;
    }

    /** Returns whether the CCC command has been issued (control channel to revert to plaintext). */
    public boolean isCccIssued() { return cccIssued; }
    /** Sets the CCC issued flag. */
    public void setCccIssued(boolean issued) { this.cccIssued = issued; }

    /** Returns the account information set by the ACCT command, or {@code null}. */
    public String account() { return account; }
    /** Sets the account information. */
    public void setAccount(String account) { this.account = account; }

    /** Returns the client address. */
    public String clientAddress() { return clientAddress; }

    /** Returns whether the session is authenticated. */
    public boolean isAuthenticated() { return state == State.AUTHENTICATED; }

    /**
     * Resolves a path relative to the current directory.
     *
     * @param path the path (absolute or relative)
     * @return the resolved absolute path
     */
    public String resolvePath(String path) {
        if (path == null || path.isEmpty()) {
            return currentDirectory;
        }
        if (path.startsWith("/")) {
            return normalizePath(path);
        }
        String base = currentDirectory.endsWith("/") ? currentDirectory : currentDirectory + "/";
        return normalizePath(base + path);
    }

    /**
     * Normalizes a path by resolving . and .. components.
     *
     * @param path the path to normalize
     * @return the normalized path
     */
    public static String normalizePath(String path) {
        String[] parts = path.split("/");
        var stack = new java.util.ArrayDeque<String>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!stack.isEmpty()) stack.pollLast();
            } else {
                stack.addLast(part);
            }
        }
        if (stack.isEmpty()) return "/";
        var sb = new StringBuilder();
        for (String p : stack) {
            sb.append('/').append(p);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("FtpSession[client=%s, user=%s, dir=%s, state=%s]",
                clientAddress, username, currentDirectory, state);
    }
}
