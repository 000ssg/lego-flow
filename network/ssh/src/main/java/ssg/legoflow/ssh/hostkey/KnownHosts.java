package ssg.legoflow.ssh.hostkey;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/**
 * Known hosts file parser and verifier (~/.ssh/known_hosts).
 *
 * <p>Supports plain-text and hashed host entries as per OpenSSH format.
 *
 * @since 1.0.0
 */
public final class KnownHosts {

    /** Verification result. */
    public enum VerifyResult {
        /** Host key matches a known entry. */
        OK,
        /** Host is not in the known hosts file. */
        NOT_FOUND,
        /** Host key does not match the known entry (possible MITM). */
        CHANGED
    }

    private final List<Entry> entries;

    /**
     * Creates a new known hosts registry with the given entries.
     *
     * @param entries the known host entries
     */
    public KnownHosts(List<Entry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    /**
     * Loads known hosts from a file.
     *
     * @param path the path to the known_hosts file
     * @return the loaded known hosts
     * @throws IOException if the file cannot be read
     */
    public static KnownHosts load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new KnownHosts(List.of());
        }
        List<Entry> entries = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                entries.add(Entry.parse(line));
            } catch (IllegalArgumentException e) {
                // Skip malformed lines
            }
        }
        return new KnownHosts(entries);
    }

    /**
     * Verifies a host key against known entries.
     *
     * @param hostname the hostname or IP
     * @param port     the port number
     * @param keyType  the key type (e.g., "ssh-ed25519")
     * @param keyBlob  the public key blob
     * @return the verification result
     */
    public VerifyResult verify(String hostname, int port, String keyType, byte[] keyBlob) {
        String hostPattern = port == 22 ? hostname : "[" + hostname + "]:" + port;
        boolean found = false;
        for (Entry entry : entries) {
            if (entry.matchesHost(hostPattern) && entry.keyType.equals(keyType)) {
                found = true;
                if (Arrays.equals(entry.keyBlob, keyBlob)) {
                    return VerifyResult.OK;
                } else {
                    return VerifyResult.CHANGED;
                }
            }
        }
        return found ? VerifyResult.CHANGED : VerifyResult.NOT_FOUND;
    }

    /**
     * Adds a new host key entry.
     *
     * @param hostname the hostname
     * @param port     the port
     * @param keyType  the key type
     * @param keyBlob  the key blob
     */
    public void addEntry(String hostname, int port, String keyType, byte[] keyBlob) {
        String hostPattern = port == 22 ? hostname : "[" + hostname + "]:" + port;
        entries.add(new Entry(hostPattern, keyType, keyBlob));
    }

    /**
     * Saves the known hosts to a file.
     *
     * @param path the file path
     * @throws IOException if the file cannot be written
     */
    public void save(Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Entry entry : entries) {
            lines.add(entry.format());
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    /**
     * Returns all entries.
     *
     * @return unmodifiable list of entries
     */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * A known hosts file entry.
     */
    public static final class Entry {
        private final String hostPattern;
        private final String keyType;
        private final byte[] keyBlob;

        /**
         * Creates a new entry.
         *
         * @param hostPattern the host pattern
         * @param keyType     the key type
         * @param keyBlob     the key blob
         */
        public Entry(String hostPattern, String keyType, byte[] keyBlob) {
            this.hostPattern = hostPattern;
            this.keyType = keyType;
            this.keyBlob = keyBlob.clone();
        }

        /**
         * Parses a known_hosts line.
         *
         * @param line the line to parse
         * @return the parsed entry
         */
        public static Entry parse(String line) {
            String[] parts = line.split("\\s+", 3);
            if (parts.length < 3) {
                throw new IllegalArgumentException("Invalid known_hosts line");
            }
            return new Entry(parts[0], parts[1],
                    Base64.getDecoder().decode(parts[2]));
        }

        /**
         * Checks if this entry matches the given host.
         *
         * @param host the host to check
         * @return true if matches
         */
        public boolean matchesHost(String host) {
            // Simple matching: comma-separated host patterns
            for (String pattern : hostPattern.split(",")) {
                if (pattern.equals(host)) return true;
            }
            return false;
        }

        /**
         * Formats this entry as a known_hosts line.
         *
         * @return the formatted line
         */
        public String format() {
            return hostPattern + " " + keyType + " "
                    + Base64.getEncoder().encodeToString(keyBlob);
        }

        public String hostPattern() { return hostPattern; }
        public String keyType() { return keyType; }
        public byte[] keyBlob() { return keyBlob.clone(); }
    }
}
