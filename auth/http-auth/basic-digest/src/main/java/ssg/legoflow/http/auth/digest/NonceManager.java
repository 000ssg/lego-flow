package ssg.legoflow.http.auth.digest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Manages nonce generation, tracking, and expiry for HTTP Digest authentication.
 * Thread-safe — uses ConcurrentHashMap for nonce tracking.
 *
 * @since 0.1.0
 */
public class NonceManager {

    private static final Logger LOG = LoggerFactory.getLogger(NonceManager.class);

    private final Duration nonceLifetime;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, NonceEntry> nonces = new ConcurrentHashMap<>();

    private record NonceEntry(Instant created, int maxCount, int currentCount) {}

    /**
     * Creates a nonce manager with the given nonce lifetime.
     *
     * @param nonceLifetime how long nonces remain valid
     * @since 0.1.0
     */
    public NonceManager(Duration nonceLifetime) {
        this.nonceLifetime = nonceLifetime;
    }

    /**
     * Creates a nonce manager with a 5-minute nonce lifetime.
     *
     * @since 0.1.0
     */
    public NonceManager() {
        this(Duration.ofMinutes(5));
    }

    /**
     * Generates a new nonce.
     *
     * @return the nonce string
     * @since 0.1.0
     */
    public String generateNonce() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        String nonce = sb.toString();
        nonces.put(nonce, new NonceEntry(Instant.now(), Integer.MAX_VALUE, 0));
        LOG.debug("Generated nonce: {}", nonce);
        return nonce;
    }

    /**
     * Validates a nonce and its count. Returns true if the nonce is valid and
     * the count is acceptable (i.e., monotonically increasing).
     *
     * @param nonce the nonce to validate
     * @param nc    the nonce count as a hex string
     * @return true if valid
     * @since 0.1.0
     */
    public boolean validateNonce(String nonce, String nc) {
        if (nonce == null) return false;

        var entry = nonces.get(nonce);
        if (entry == null) {
            LOG.debug("Unknown nonce: {}", nonce);
            return false;
        }

        // Check expiry
        if (Instant.now().isAfter(entry.created().plus(nonceLifetime))) {
            nonces.remove(nonce);
            LOG.debug("Nonce expired: {}", nonce);
            return false;
        }

        // Check nonce count if provided
        if (nc != null) {
            int count;
            try {
                count = Integer.parseInt(nc, 16);
            } catch (NumberFormatException e) {
                return false;
            }
            if (count <= entry.currentCount()) {
                LOG.debug("Nonce count replay detected: {} <= {}", count, entry.currentCount());
                return false;
            }
            nonces.put(nonce, new NonceEntry(entry.created(), entry.maxCount(), count));
        }

        return true;
    }

    /**
     * Checks if a nonce is stale (expired but was once valid).
     *
     * @param nonce the nonce to check
     * @return true if the nonce is stale
     * @since 0.1.0
     */
    public boolean isStale(String nonce) {
        var entry = nonces.get(nonce);
        if (entry == null) return true;
        return Instant.now().isAfter(entry.created().plus(nonceLifetime));
    }

    /**
     * Removes a nonce.
     *
     * @param nonce the nonce to remove
     * @since 0.1.0
     */
    public void removeNonce(String nonce) {
        nonces.remove(nonce);
    }

    /**
     * Removes all expired nonces.
     *
     * @return the number of expired nonces removed
     * @since 0.1.0
     */
    public int cleanExpired() {
        Instant now = Instant.now();
        var expired = nonces.entrySet().stream()
                .filter(e -> now.isAfter(e.getValue().created().plus(nonceLifetime)))
                .map(Map.Entry::getKey)
                .toList();
        expired.forEach(nonces::remove);
        return expired.size();
    }

    /**
     * Returns the number of tracked nonces.
     *
     * @return the nonce count
     * @since 0.1.0
     */
    public int size() {
        return nonces.size();
    }
}
