package ssg.legoflow.http.auth.demo;

import ssg.legoflow.http.auth.AuthContext;
import ssg.legoflow.http.auth.AuthCredentials;
import ssg.legoflow.http.auth.AuthPrincipal;
import ssg.legoflow.http.auth.AuthResult;
import ssg.legoflow.http.auth.AuthSchemeRegistry;
import ssg.legoflow.http.auth.AuthenticationScheme;
import ssg.legoflow.http.auth.session.HttpSession;
import ssg.legoflow.http.auth.session.InMemorySessionStore;
import ssg.legoflow.http.auth.session.SessionCookie;
import ssg.legoflow.http.auth.session.SessionManager;
import ssg.legoflow.http.auth.token.JwtClaims;
import ssg.legoflow.http.auth.token.JwtTokenProvider;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
/**
 * Comprehensive demo of all HTTP Auth Core module features.
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>AuthCredentials sealed interface — Basic, Bearer, Digest, None variants</li>
 *   <li>AuthResult sealed interface — Success, Failure, Challenge with pattern matching</li>
 *   <li>AuthPrincipal — identity with roles and attributes</li>
 *   <li>AuthSchemeRegistry — pluggable scheme registration and lookup</li>
 *   <li>Session management — create, retrieve, expire, destroy sessions</li>
 *   <li>JWT HS256 — HMAC-SHA256 token generation and validation</li>
 *   <li>JWT RS256 — RSA-SHA256 token generation and validation</li>
 *   <li>JWT claims — standard and custom claims, expiration checks</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoHttpAuthCoreAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoHttpAuthCoreAll.class);

    /** Set to {@code true} to use external identity providers. */
    public static boolean USE_EXTERNAL = false;

    private DemoHttpAuthCoreAll() {}

    /**
     * Results from running the full HTTP Auth Core demo.
     *
     * @param authCredentials    true if sealed credential types work correctly
     * @param authResults        true if sealed result types work correctly
     * @param authPrincipal      true if principal with roles and attributes works
     * @param schemeRegistry     true if scheme registration and lookup works
     * @param sessionManagement  true if session lifecycle works correctly
     * @param jwtHs256           true if HMAC-SHA256 JWT generation and validation works
     * @param jwtRs256           true if RSA-SHA256 JWT generation and validation works
     * @param jwtClaims          true if JWT claims parsing and validation works
     * @since 0.1.0
     */
    public record Results(
            boolean authCredentials,
            boolean authResults,
            boolean authPrincipal,
            boolean schemeRegistry,
            boolean sessionManagement,
            boolean jwtHs256,
            boolean jwtRs256,
            boolean jwtClaims
    ) {}

    /**
     * Runs the comprehensive demo covering all HTTP Auth Core features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     * @since 0.1.0
     */
    public static Results runAll() throws Exception {
        boolean creds = demoAuthCredentials();
        boolean results = demoAuthResults();
        boolean principal = demoAuthPrincipal();
        boolean registry = demoSchemeRegistry();
        boolean sessions = demoSessionManagement();
        boolean hs256 = demoJwtHs256();
        boolean rs256 = demoJwtRs256();
        boolean claims = demoJwtClaims();

        return new Results(creds, results, principal, registry, sessions, hs256, rs256, claims);
    }

    // ======================== 1. AUTH CREDENTIALS =============================

    /**
     * Demonstrates the sealed AuthCredentials interface with pattern matching
     * across all four credential types: Basic, Bearer, Digest, None.
     *
     * @return true if all credential types work correctly
     * @since 0.1.0
     */
    static boolean demoAuthCredentials() {
        LOG.info("=== 1. AuthCredentials Sealed Interface ===");

        var basic = new AuthCredentials.Basic("alice", "secret123");
        var bearer = new AuthCredentials.Bearer("eyJhbGciOiJIUzI1NiJ9.test.sig");
        var digest = new AuthCredentials.Digest("bob", "realm", "nonce1", "/api",
                "resp-hash", "MD5", "cnonce1", "00000001", "auth", "opaque1");
        var none = new AuthCredentials.None();

        // Pattern matching via sealed interface
        boolean basicMatch = switch ((AuthCredentials) basic) {
            case AuthCredentials.Basic b -> "alice".equals(b.username()) && "secret123".equals(b.password());
            case AuthCredentials.Bearer _, AuthCredentials.Digest _, AuthCredentials.None _ -> false;
        };
        boolean bearerMatch = switch ((AuthCredentials) bearer) {
            case AuthCredentials.Bearer b -> b.token().startsWith("eyJ");
            case AuthCredentials.Basic _, AuthCredentials.Digest _, AuthCredentials.None _ -> false;
        };
        boolean digestMatch = switch ((AuthCredentials) digest) {
            case AuthCredentials.Digest d -> "bob".equals(d.username()) && "MD5".equals(d.algorithm());
            case AuthCredentials.Basic _, AuthCredentials.Bearer _, AuthCredentials.None _ -> false;
        };
        boolean noneMatch = switch ((AuthCredentials) none) {
            case AuthCredentials.None n -> true;
            case AuthCredentials.Basic _, AuthCredentials.Bearer _, AuthCredentials.Digest _ -> false;
        };

        LOG.info("Basic: {}, Bearer: {}, Digest: {}, None: {}", basicMatch, bearerMatch, digestMatch, noneMatch);
        return basicMatch && bearerMatch && digestMatch && noneMatch;
    }

    // ======================== 2. AUTH RESULTS =================================

    /**
     * Demonstrates the sealed AuthResult interface with Success, Failure,
     * and Challenge variants using factory methods and pattern matching.
     *
     * @return true if all result types work correctly
     * @since 0.1.0
     */
    static boolean demoAuthResults() {
        LOG.info("=== 2. AuthResult Sealed Interface ===");

        var success = AuthResult.success(AuthPrincipal.of("alice", Set.of("admin")));
        var failure = AuthResult.failure("Invalid credentials");
        var challenge = AuthResult.challenge("Basic");

        boolean successOk = switch (success) {
            case AuthResult.Success s -> "alice".equals(s.principal().getName());
            default -> false;
        };
        boolean failureOk = switch (failure) {
            case AuthResult.Failure f -> "Invalid credentials".equals(f.reason());
            default -> false;
        };
        boolean challengeOk = switch (challenge) {
            case AuthResult.Challenge c -> "Basic".equals(c.schemeName());
            default -> false;
        };

        LOG.info("Success: {}, Failure: {}, Challenge: {}", successOk, failureOk, challengeOk);
        return successOk && failureOk && challengeOk;
    }

    // ======================== 3. AUTH PRINCIPAL ===============================

    /**
     * Demonstrates AuthPrincipal with name, roles, and attributes,
     * including role checking and attribute retrieval.
     *
     * @return true if principal operations work correctly
     * @since 0.1.0
     */
    static boolean demoAuthPrincipal() {
        LOG.info("=== 3. AuthPrincipal ===");

        var principal = new AuthPrincipal("alice",
                Set.of("admin", "user"),
                Map.of("email", "alice@example.com", "dept", "engineering"));

        boolean nameOk = "alice".equals(principal.getName());
        boolean hasAdmin = principal.hasRole("admin");
        boolean hasUser = principal.hasRole("user");
        boolean noGuest = !principal.hasRole("guest");
        String email = principal.getAttribute("email");
        boolean emailOk = "alice@example.com".equals(email);
        boolean rolesSize = principal.getRoles().size() == 2;

        // Simple principal
        var simple = AuthPrincipal.of("bob");
        boolean simpleOk = "bob".equals(simple.getName()) && simple.getRoles().isEmpty();

        LOG.info("Name: {}, roles: {}, email: {}, simple: {}", nameOk, hasAdmin && hasUser, emailOk, simpleOk);
        return nameOk && hasAdmin && hasUser && noGuest && emailOk && rolesSize && simpleOk;
    }

    // ======================== 4. SCHEME REGISTRY =============================

    /**
     * Demonstrates the pluggable AuthSchemeRegistry with scheme registration,
     * case-insensitive lookup, and removal.
     *
     * @return true if registry operations work correctly
     * @since 0.1.0
     */
    static boolean demoSchemeRegistry() {
        LOG.info("=== 4. AuthSchemeRegistry ===");

        var registry = new AuthSchemeRegistry();

        // Register a mock scheme
        AuthenticationScheme mockScheme = new AuthenticationScheme() {
            @Override public String schemeName() { return "Mock"; }
            @Override public AuthResult authenticate(HttpRequest req, AuthContext ctx) {
                return AuthResult.success(AuthPrincipal.of("mock-user"));
            }
            @Override public void challenge(HttpResponse resp, AuthContext ctx) {}
            @Override public AuthCredentials extractCredentials(HttpRequest req) {
                return new AuthCredentials.None();
            }
        };

        registry.register(mockScheme);

        boolean found = registry.get("Mock").isPresent();
        boolean caseInsensitive = registry.get("mock").isPresent();
        boolean upperCase = registry.get("MOCK").isPresent();
        boolean notFound = registry.get("Unknown").isEmpty();
        boolean sizeOne = registry.size() == 1;
        boolean nameInSet = registry.schemeNames().contains("mock");

        // Remove and verify
        boolean removed = registry.remove("Mock");
        boolean afterRemove = registry.get("mock").isEmpty();

        LOG.info("Found: {}, caseInsensitive: {}, notFound: {}, removed: {}",
                found, caseInsensitive, notFound, removed);
        return found && caseInsensitive && upperCase && notFound && sizeOne
                && nameInSet && removed && afterRemove;
    }

    // ======================== 5. SESSION MANAGEMENT ==========================

    /**
     * Demonstrates HTTP session lifecycle: creation, attribute storage,
     * session retrieval, expiration checking, and session destruction.
     *
     * @return true if session management works correctly
     * @since 0.1.0
     */
    static boolean demoSessionManagement() {
        LOG.info("=== 5. Session Management ===");

        var store = new InMemorySessionStore();
        var cookieConfig = SessionCookie.defaults();
        try (var manager = new SessionManager(store, cookieConfig, 1800)) {
            // Create a session
            var response = HttpResponse.of(HttpStatus.OK);
            HttpSession session = manager.createSession(response);
            LOG.info("Created session: {}", session.getId());

            // Set attributes
            session.setAttribute("username", "alice");
            session.setAttribute("loginTime", System.currentTimeMillis());

            boolean attrOk = "alice".equals(session.getAttribute("username"));
            boolean countOk = manager.getActiveSessionCount() == 1;
            boolean notExpired = !session.isExpired(1800);

            // Session attributes
            session.removeAttribute("loginTime");
            boolean removedAttr = session.getAttribute("loginTime") == null;

            // Invalidate
            session.invalidate();
            boolean invalidated = session.isInvalidated();

            LOG.info("Attributes: {}, count: {}, notExpired: {}, invalidated: {}",
                    attrOk, countOk, notExpired, invalidated);
            return attrOk && countOk && notExpired && removedAttr && invalidated;
        }
    }

    // ======================== 6. JWT HS256 ====================================

    /**
     * Demonstrates JWT token generation and validation using HMAC-SHA256.
     *
     * @return true if JWT HS256 works correctly
     * @since 0.1.0
     */
    static boolean demoJwtHs256() {
        LOG.info("=== 6. JWT HS256 (HMAC-SHA256) ===");

        String secret = "this-is-a-very-long-secret-key-for-hs256-demo!!";
        var provider = JwtTokenProvider.hmac256(secret, "lego-flow", Duration.ofHours(1));

        // Generate token
        String token = provider.generateToken("alice", Map.of("role", "admin"));
        LOG.info("Generated JWT: {} chars", token.length());

        // Validate
        var claims = provider.validateToken(token);
        boolean valid = claims.isPresent();
        LOG.info("Token valid: {}", valid);

        // Extract subject
        var subject = provider.getSubject(token);
        boolean subjectOk = subject.isPresent() && "alice".equals(subject.get());
        LOG.info("Subject: {}", subject.orElse("none"));

        // Not expired
        boolean notExpired = !provider.isExpired(token);

        // Invalid token
        var invalidClaims = provider.validateToken("invalid.token.here");
        boolean invalidRejected = invalidClaims.isEmpty();

        // Tampered token
        String tampered = token.substring(0, token.length() - 2) + "XX";
        boolean tamperedRejected = provider.validateToken(tampered).isEmpty();

        LOG.info("Valid: {}, subject: {}, notExpired: {}, invalidRejected: {}, tamperedRejected: {}",
                valid, subjectOk, notExpired, invalidRejected, tamperedRejected);
        return valid && subjectOk && notExpired && invalidRejected && tamperedRejected;
    }

    // ======================== 7. JWT RS256 ====================================

    /**
     * Demonstrates JWT token generation and validation using RSA-SHA256
     * with a dynamically generated key pair.
     *
     * @return true if JWT RS256 works correctly
     * @throws Exception if key generation fails
     * @since 0.1.0
     */
    static boolean demoJwtRs256() throws Exception {
        LOG.info("=== 7. JWT RS256 (RSA-SHA256) ===");

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();

        var provider = JwtTokenProvider.rsa256(
                keyPair.getPrivate(), keyPair.getPublic(), "lego-flow", Duration.ofHours(1));

        // Generate token
        String token = provider.generateToken("bob", Map.of("scope", "read write"));
        LOG.info("Generated RS256 JWT: {} chars", token.length());

        // Validate
        var claims = provider.validateToken(token);
        boolean valid = claims.isPresent();

        // Parse header
        var header = provider.parseHeader(token);
        boolean headerOk = header.isPresent() && "RS256".equals(header.get().alg());

        // Subject
        var subject = provider.getSubject(token);
        boolean subjectOk = subject.isPresent() && "bob".equals(subject.get());

        LOG.info("RS256 valid: {}, header: {}, subject: {}", valid, headerOk, subjectOk);
        return valid && headerOk && subjectOk;
    }

    // ======================== 8. JWT CLAIMS ==================================

    /**
     * Demonstrates JWT claims with standard registered claims and custom claims,
     * including expiration and not-before validation.
     *
     * @return true if JWT claims work correctly
     * @since 0.1.0
     */
    static boolean demoJwtClaims() {
        LOG.info("=== 8. JWT Claims ===");

        String secret = "this-is-a-very-long-secret-key-for-claims-demo!!";
        var provider = JwtTokenProvider.hmac256(secret, "lego-flow", Duration.ofHours(1));

        // Token with custom claims
        String token = provider.generateToken("charlie",
                Map.of("email", "charlie@example.com", "groups", "dev,ops"));

        // Parse claims
        var claimsOpt = provider.parseClaims(token);
        boolean parsed = claimsOpt.isPresent();
        JwtClaims claims = claimsOpt.get();

        boolean subOk = "charlie".equals(claims.getSubject());
        boolean issOk = "lego-flow".equals(claims.getIssuer());
        boolean emailOk = "charlie@example.com".equals(claims.getStringClaim("email"));
        boolean groupsOk = "dev,ops".equals(claims.getStringClaim("groups"));
        boolean hasJti = claims.getJwtId() != null;
        boolean notExpired = !claims.isExpired();

        LOG.info("Parsed: {}, sub: {}, iss: {}, email: {}, notExpired: {}",
                parsed, subOk, issOk, emailOk, notExpired);
        return parsed && subOk && issOk && emailOk && groupsOk && hasJti && notExpired;
    }
}
