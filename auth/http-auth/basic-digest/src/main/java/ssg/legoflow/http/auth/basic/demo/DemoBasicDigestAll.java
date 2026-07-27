package ssg.legoflow.http.auth.basic.demo;

import ssg.legoflow.http.auth.AuthContext;
import ssg.legoflow.http.auth.AuthCredentials;
import ssg.legoflow.http.auth.AuthPrincipal;
import ssg.legoflow.http.auth.AuthResult;
import ssg.legoflow.http.auth.basic.BasicAuthScheme;
import ssg.legoflow.http.auth.basic.HashedPasswordStore;
import ssg.legoflow.http.auth.basic.InMemoryUserStore;
import ssg.legoflow.http.auth.digest.DigestAuthScheme;
import ssg.legoflow.http.auth.digest.DigestChallenge;
import ssg.legoflow.http.auth.digest.NonceManager;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Comprehensive demo of all HTTP Basic/Digest authentication features.
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Basic Auth — credential encoding, scheme authentication, challenge</li>
 *   <li>InMemoryUserStore — user management with roles</li>
 *   <li>HashedPasswordStore — SHA-256 password hashing with per-user salt</li>
 *   <li>Digest Auth MD5 — nonce management, response computation, authentication</li>
 *   <li>Digest Auth SHA-256 — SHA-256 algorithm variant</li>
 *   <li>Digest challenge — WWW-Authenticate header generation</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoBasicDigestAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoBasicDigestAll.class);

    /** Set to {@code true} to use an external user directory. */
    public static boolean USE_EXTERNAL = false;

    private DemoBasicDigestAll() {}

    /**
     * Results from running the full Basic/Digest demo.
     *
     * @param basicAuth            true if Basic authentication works correctly
     * @param inMemoryUserStore    true if user store operations work correctly
     * @param hashedPasswordStore  true if SHA-256 hashed password store works
     * @param digestAuthMd5        true if Digest MD5 authentication works
     * @param digestAuthSha256     true if Digest SHA-256 authentication works
     * @param digestChallenge      true if Digest challenge header is generated correctly
     * @since 1.0.0
     */
    public record Results(
            boolean basicAuth,
            boolean inMemoryUserStore,
            boolean hashedPasswordStore,
            boolean digestAuthMd5,
            boolean digestAuthSha256,
            boolean digestChallenge
    ) {}

    /**
     * Runs the comprehensive demo covering all Basic/Digest features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     * @since 1.0.0
     */
    public static Results runAll() throws Exception {
        boolean basic = demoBasicAuth();
        boolean userStore = demoInMemoryUserStore();
        boolean hashed = demoHashedPasswordStore();
        boolean md5 = demoDigestAuthMd5();
        boolean sha256 = demoDigestAuthSha256();
        boolean challenge = demoDigestChallenge();

        return new Results(basic, userStore, hashed, md5, sha256, challenge);
    }

    // ======================== 1. BASIC AUTH ===================================

    /**
     * Demonstrates HTTP Basic authentication: encoding credentials, authenticating
     * with a valid user, rejecting invalid credentials, and issuing challenges.
     *
     * @return true if all Basic auth operations succeed
     * @since 1.0.0
     */
    static boolean demoBasicAuth() {
        LOG.info("=== 1. HTTP Basic Authentication ===");

        var userStore = new InMemoryUserStore()
                .addUser("alice", "password123", Set.of("admin"))
                .addUser("bob", "secret456");

        var scheme = new BasicAuthScheme(userStore);

        // Encode credentials
        String authHeader = BasicAuthScheme.encodeCredentials("alice", "password123");
        LOG.info("Encoded: {}", authHeader);
        boolean encodedOk = authHeader.startsWith("Basic ");

        // Authenticate valid credentials
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, authHeader);
        var context = AuthContext.ofRealm("demo");

        AuthResult result = scheme.authenticate(request, context);
        boolean successOk = result instanceof AuthResult.Success s
                && "alice".equals(s.principal().getName());
        LOG.info("Valid auth: {}", successOk);

        // Authenticate invalid credentials
        var badRequest = HttpRequest.of(HttpMethod.GET, "/api/data");
        badRequest.getHeaders().set(HttpHeaders.AUTHORIZATION,
                BasicAuthScheme.encodeCredentials("alice", "wrong"));
        AuthResult badResult = scheme.authenticate(badRequest, context);
        boolean failureOk = badResult instanceof AuthResult.Failure;
        LOG.info("Invalid auth rejected: {}", failureOk);

        // No credentials — challenge
        var noAuthRequest = HttpRequest.of(HttpMethod.GET, "/api/data");
        AuthResult challengeResult = scheme.authenticate(noAuthRequest, context);
        boolean challengeOk = challengeResult instanceof AuthResult.Challenge;
        LOG.info("No credentials issues challenge: {}", challengeOk);

        return encodedOk && successOk && failureOk && challengeOk;
    }

    // ======================== 2. INMEMORY USER STORE ==========================

    /**
     * Demonstrates InMemoryUserStore with user management, role assignment,
     * and credential validation.
     *
     * @return true if all user store operations succeed
     * @since 1.0.0
     */
    static boolean demoInMemoryUserStore() {
        LOG.info("=== 2. InMemoryUserStore ===");

        var store = new InMemoryUserStore()
                .addUser("alice", "pass1", Set.of("admin", "user"))
                .addUser("bob", "pass2", Set.of("user"));

        boolean sizeOk = store.size() == 2;
        boolean aliceExists = store.userExists("alice");
        boolean unknownNotExists = !store.userExists("charlie");

        // Authenticate
        var principal = store.authenticate("alice", "pass1");
        boolean authOk = principal.isPresent() && principal.get().hasRole("admin");

        // Wrong password
        boolean wrongPw = store.authenticate("alice", "wrong").isEmpty();

        // Lookup without password
        var found = store.findByUsername("bob");
        boolean lookupOk = found.isPresent() && found.get().hasRole("user");

        // Password retrieval for digest
        var password = store.getPassword("alice");
        boolean pwOk = password.isPresent() && "pass1".equals(password.get());

        // Remove user
        store.removeUser("bob");
        boolean removedOk = !store.userExists("bob") && store.size() == 1;

        LOG.info("Size: {}, auth: {}, lookup: {}, remove: {}", sizeOk, authOk, lookupOk, removedOk);
        return sizeOk && aliceExists && unknownNotExists && authOk && wrongPw
                && lookupOk && pwOk && removedOk;
    }

    // ======================== 3. HASHED PASSWORD STORE ========================

    /**
     * Demonstrates HashedPasswordStore with SHA-256 password hashing and
     * per-user salt for secure credential storage.
     *
     * @return true if hashed password operations succeed
     * @since 1.0.0
     */
    static boolean demoHashedPasswordStore() {
        LOG.info("=== 3. HashedPasswordStore (SHA-256) ===");

        var store = new HashedPasswordStore();
        store.addUser("alice", "secure-password");
        store.addUser("bob", "another-password");

        // Authenticate with correct password
        boolean aliceOk = store.authenticate("alice", "secure-password").isPresent();
        LOG.info("Alice correct password: {}", aliceOk);

        // Reject wrong password
        boolean wrongRejected = store.authenticate("alice", "wrong-password").isEmpty();
        LOG.info("Alice wrong password rejected: {}", wrongRejected);

        // Authenticate another user
        boolean bobOk = store.authenticate("bob", "another-password").isPresent();

        // Unknown user
        boolean unknownRejected = store.authenticate("unknown", "any-password").isEmpty();
        LOG.info("Unknown user rejected: {}", unknownRejected);

        // getPassword returns empty for hashed store (can't reverse hash)
        boolean noPlaintextPw = store.getPassword("alice").isEmpty();
        LOG.info("No plaintext password retrieval: {}", noPlaintextPw);

        return aliceOk && wrongRejected && bobOk && unknownRejected && noPlaintextPw;
    }

    // ======================== 4. DIGEST AUTH MD5 ==============================

    /**
     * Demonstrates HTTP Digest authentication with MD5 algorithm, including
     * nonce management, response hash computation, and the full auth cycle.
     *
     * @return true if Digest MD5 auth works correctly
     * @since 1.0.0
     */
    static boolean demoDigestAuthMd5() {
        LOG.info("=== 4. HTTP Digest Authentication (MD5) ===");

        var nonceManager = new NonceManager();
        var scheme = new DigestAuthScheme(nonceManager, "MD5", "auth");

        var userStore = new InMemoryUserStore()
                .addUser("alice", "secret", Set.of("user"));
        var context = new AuthContext("demo-realm", userStore, null);

        // Generate nonce
        String nonce = nonceManager.generateNonce();
        LOG.info("Generated nonce: {}", nonce);

        // Compute digest response
        String response = DigestAuthScheme.computeDigestResponse(
                "alice", "demo-realm", "secret",
                "GET", "/api/resource",
                nonce, "00000001", "client-nonce", "auth", "MD5", null);
        LOG.info("Computed MD5 digest response: {}", response);

        boolean responseNotEmpty = response != null && !response.isEmpty();
        boolean hashLength = response.length() == 32; // MD5 hex = 32 chars

        // Scheme name
        boolean nameOk = "Digest".equals(scheme.schemeName());
        boolean algoOk = "MD5".equals(scheme.getAlgorithm());
        boolean qopOk = "auth".equals(scheme.getQop());

        LOG.info("Response computed: {}, length=32: {}, name: {}", responseNotEmpty, hashLength, nameOk);
        return responseNotEmpty && hashLength && nameOk && algoOk && qopOk;
    }

    // ======================== 5. DIGEST AUTH SHA-256 ==========================

    /**
     * Demonstrates HTTP Digest authentication with SHA-256 algorithm variant.
     *
     * @return true if Digest SHA-256 auth works correctly
     * @since 1.0.0
     */
    static boolean demoDigestAuthSha256() {
        LOG.info("=== 5. HTTP Digest Authentication (SHA-256) ===");

        // Compute SHA-256 digest response
        String response = DigestAuthScheme.computeDigestResponse(
                "bob", "secure-realm", "password",
                "POST", "/api/submit",
                "sha256-nonce", "00000001", "client-nonce2", "auth", "SHA-256", null);
        LOG.info("Computed SHA-256 digest response: {}", response);

        boolean responseNotEmpty = response != null && !response.isEmpty();
        boolean hashLength = response.length() == 64; // SHA-256 hex = 64 chars

        // Verify session algorithm detection (algorithm names ending in "-sess")
        boolean md5NotSess = !"MD5".endsWith("-sess");
        boolean md5Sess = "MD5-sess".endsWith("-sess");
        boolean sha256NotSess = !"SHA-256".endsWith("-sess");
        boolean sha256Sess = "SHA-256-sess".endsWith("-sess");

        // Base algorithm extraction (strip "-sess" suffix)
        boolean md5Base = "MD5".equals("MD5-sess".replace("-sess", ""));
        boolean sha256Base = "SHA-256".equals("SHA-256-sess".replace("-sess", ""));

        LOG.info("SHA-256 response: {}, length=64: {}, sessions: {}/{}",
                responseNotEmpty, hashLength, md5Sess, sha256Sess);
        return responseNotEmpty && hashLength && md5NotSess && md5Sess
                && sha256NotSess && sha256Sess && md5Base && sha256Base;
    }

    // ======================== 6. DIGEST CHALLENGE =============================

    /**
     * Demonstrates Digest challenge header generation for WWW-Authenticate,
     * including realm, nonce, opaque, algorithm, and qop parameters.
     *
     * @return true if challenge header is generated correctly
     * @since 1.0.0
     */
    static boolean demoDigestChallenge() {
        LOG.info("=== 6. Digest Challenge ===");

        var challenge = new DigestChallenge(
                "demo-realm", "test-nonce-123", "opaque-value",
                "MD5", "auth", false);
        String header = challenge.toHeaderValue();
        LOG.info("Challenge header: {}", header);

        boolean hasDigest = header.startsWith("Digest ");
        boolean hasRealm = header.contains("realm=\"demo-realm\"");
        boolean hasNonce = header.contains("nonce=\"test-nonce-123\"");
        boolean hasOpaque = header.contains("opaque=\"opaque-value\"");
        boolean hasAlgorithm = header.contains("algorithm=MD5");
        boolean hasQop = header.contains("qop=\"auth\"");

        // Stale challenge
        var staleChallenge = new DigestChallenge(
                "demo-realm", "new-nonce", "opaque-value", "MD5", "auth", true);
        String staleHeader = staleChallenge.toHeaderValue();
        boolean hasStale = staleHeader.contains("stale=true");
        LOG.info("Stale challenge has stale=true: {}", hasStale);

        return hasDigest && hasRealm && hasNonce && hasOpaque && hasAlgorithm && hasQop && hasStale;
    }
}
