package ssg.legoflow.http.auth.digest;

import ssg.legoflow.http.auth.*;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
/**
 * HTTP Digest Authentication scheme (RFC 7616).
 * Supports MD5, MD5-sess, and SHA-256 algorithms, qop=auth and qop=auth-int,
 * nonce counting, cnonce handling, proxy authentication (407), and
 * Authentication-Info response header generation.
 *
 * @since 0.1.0
 */
public class DigestAuthScheme implements AuthenticationScheme {

    private static final Logger LOG = LoggerFactory.getLogger(DigestAuthScheme.class);
    private static final String SCHEME_NAME = "Digest";
    private static final String PROXY_AUTHENTICATE = "proxy-authenticate";
    private static final String PROXY_AUTHORIZATION = "proxy-authorization";
    private static final String AUTHENTICATION_INFO = "authentication-info";

    private final NonceManager nonceManager;
    private final String algorithm;
    private final String qop;
    private final String opaque;
    private final boolean proxyMode;

    /**
     * Creates a Digest authentication scheme.
     *
     * @param nonceManager the nonce manager
     * @param algorithm    the hash algorithm ("MD5", "MD5-sess", or "SHA-256")
     * @param qop          the quality of protection ("auth" or "auth,auth-int")
     * @since 0.1.0
     */
    public DigestAuthScheme(NonceManager nonceManager, String algorithm, String qop) {
        this(nonceManager, algorithm, qop, false);
    }

    /**
     * Creates a Digest authentication scheme with proxy mode option.
     *
     * @param nonceManager the nonce manager
     * @param algorithm    the hash algorithm ("MD5", "MD5-sess", or "SHA-256")
     * @param qop          the quality of protection ("auth" or "auth,auth-int")
     * @param proxyMode    true to use 407 Proxy-Authenticate instead of 401 WWW-Authenticate
     * @since 0.1.0
     */
    public DigestAuthScheme(NonceManager nonceManager, String algorithm, String qop, boolean proxyMode) {
        this.nonceManager = Objects.requireNonNull(nonceManager);
        this.algorithm = algorithm != null ? algorithm : "MD5";
        this.qop = qop != null ? qop : "auth";
        this.proxyMode = proxyMode;
        // Generate a random opaque value
        byte[] opaqueBytes = new byte[16];
        new SecureRandom().nextBytes(opaqueBytes);
        this.opaque = bytesToHex(opaqueBytes);
    }

    /**
     * Creates a Digest authentication scheme with MD5 and qop=auth defaults.
     *
     * @param nonceManager the nonce manager
     * @since 0.1.0
     */
    public DigestAuthScheme(NonceManager nonceManager) {
        this(nonceManager, "MD5", "auth");
    }

    @Override
    public String schemeName() {
        return SCHEME_NAME;
    }

    @Override
    public AuthResult authenticate(HttpRequest request, AuthContext context) {
        AuthCredentials creds = extractCredentials(request);

        if (creds instanceof AuthCredentials.None) {
            return AuthResult.challenge(SCHEME_NAME);
        }

        if (creds instanceof AuthCredentials.Digest digest) {
            // Validate nonce
            if (!nonceManager.validateNonce(digest.nonce(), digest.nc())) {
                if (nonceManager.isStale(digest.nonce())) {
                    LOG.debug("Stale nonce for user: {}", digest.username());
                    return AuthResult.challenge(SCHEME_NAME);
                }
                return AuthResult.failure("Invalid nonce");
            }

            // Get password from context's user store
            Optional<String> password = context.getUserStore()
                    .flatMap(store -> store.getPassword(digest.username()));

            if (password.isEmpty()) {
                LOG.debug("User not found: {}", digest.username());
                return AuthResult.failure("User not found");
            }

            String usedAlgorithm = digest.algorithm() != null ? digest.algorithm() : algorithm;

            // Compute expected response
            String expectedResponse = computeDigestResponse(
                    digest.username(), context.getRealm(), password.get(),
                    request.getMethod().name(), digest.uri(),
                    digest.nonce(), digest.nc(), digest.cnonce(), digest.qop(),
                    usedAlgorithm, request.getBodyAsString());

            if (expectedResponse.equals(digest.response())) {
                var principal = context.getUserStore()
                        .flatMap(store -> store.findByUsername(digest.username()));
                AuthPrincipal authed;
                if (principal.isPresent()) {
                    authed = principal.get();
                } else {
                    authed = AuthPrincipal.of(digest.username());
                }
                LOG.debug("Digest auth successful for user: {}", digest.username());

                // Store last successful digest params for Authentication-Info generation
                lastDigest = digest;
                lastPassword = password.get();
                lastAlgorithm = usedAlgorithm;

                return AuthResult.success(authed);
            } else {
                LOG.debug("Digest response mismatch for user: {}", digest.username());
                return AuthResult.failure("Invalid digest response");
            }
        }

        return AuthResult.failure("Unexpected credential type");
    }

    // Temporary state for Authentication-Info generation (per-request, single-threaded use)
    private volatile AuthCredentials.Digest lastDigest;
    private volatile String lastPassword;
    private volatile String lastAlgorithm;

    @Override
    public void challenge(HttpResponse response, AuthContext context) {
        String nonce = nonceManager.generateNonce();
        DigestChallenge challenge = new DigestChallenge(
                context.getRealm(), nonce, opaque, algorithm, qop, false);
        String headerName = proxyMode ? PROXY_AUTHENTICATE : HttpHeaders.WWW_AUTHENTICATE;
        response.getHeaders().add(headerName, challenge.toHeaderValue());
    }

    /**
     * Adds an Authentication-Info response header after successful digest authentication (RFC 7616 Section 3.8).
     * The header contains rspauth (response digest for mutual authentication), cnonce, nc, and qop values.
     *
     * <p>Call this method after a successful {@link #authenticate(HttpRequest, AuthContext)} call
     * and before sending the response.</p>
     *
     * @param response the HTTP response to add the header to
     * @param context  the authentication context
     * @since 0.1.0
     */
    public void addAuthenticationInfo(HttpResponse response, AuthContext context) {
        if (lastDigest == null) {
            return;
        }
        var digest = lastDigest;
        String rspauth = computeRspAuth(
                digest.username(), context.getRealm(), lastPassword,
                digest.uri(), digest.nonce(), digest.nc(), digest.cnonce(),
                digest.qop(), lastAlgorithm, null);

        var sb = new StringBuilder();
        if (rspauth != null) {
            sb.append("rspauth=\"").append(rspauth).append("\"");
        }
        if (digest.cnonce() != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("cnonce=\"").append(digest.cnonce()).append("\"");
        }
        if (digest.nc() != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("nc=").append(digest.nc());
        }
        if (digest.qop() != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("qop=").append(digest.qop());
        }

        if (!sb.isEmpty()) {
            response.getHeaders().set(AUTHENTICATION_INFO, sb.toString());
        }

        // Clear after use
        lastDigest = null;
        lastPassword = null;
        lastAlgorithm = null;
    }

    /**
     * Issues a proxy authentication challenge (407 Proxy Authentication Required).
     * Sets the response status to 407 and adds a Proxy-Authenticate header.
     *
     * @param response the HTTP response
     * @param context  the authentication context
     * @since 0.1.0
     */
    public void proxyChallenge(HttpResponse response, AuthContext context) {
        String nonce = nonceManager.generateNonce();
        DigestChallenge challenge = new DigestChallenge(
                context.getRealm(), nonce, opaque, algorithm, qop, false);
        response.getHeaders().add(PROXY_AUTHENTICATE, challenge.toHeaderValue());
    }

    @Override
    public AuthCredentials extractCredentials(HttpRequest request) {
        // Check standard Authorization header first, then Proxy-Authorization for proxy mode
        String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if ((authHeader == null || !authHeader.regionMatches(true, 0, "Digest ", 0, 7)) && proxyMode) {
            authHeader = request.getHeaders().get(PROXY_AUTHORIZATION);
        }
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Digest ", 0, 7)) {
            return new AuthCredentials.None();
        }

        String params = authHeader.substring(7);
        Map<String, String> parsed = parseDigestParams(params);

        String username = parsed.get("username");
        if (username == null) return new AuthCredentials.None();

        return new AuthCredentials.Digest(
                username,
                parsed.get("realm"),
                parsed.get("nonce"),
                parsed.get("uri"),
                parsed.get("response"),
                parsed.getOrDefault("algorithm", algorithm),
                parsed.get("cnonce"),
                parsed.get("nc"),
                parsed.get("qop"),
                parsed.get("opaque")
        );
    }

    /**
     * Computes the digest response hash per RFC 7616.
     * Supports MD5, MD5-sess, SHA-256, and SHA-256-sess algorithm variants.
     * For -sess variants, HA1 = H(H(username:realm:password):nonce:cnonce) per RFC 7616 Section 3.4.3.
     *
     * @param username  the username
     * @param realm     the realm
     * @param password  the password
     * @param method    the HTTP method
     * @param uri       the request URI
     * @param nonce     the server nonce
     * @param nc        the nonce count
     * @param cnonce    the client nonce
     * @param qop       the quality of protection
     * @param algorithm the hash algorithm ("MD5", "MD5-sess", "SHA-256", "SHA-256-sess")
     * @param body      the request body (for auth-int)
     * @return the computed response hash
     * @since 0.1.0
     */
    public static String computeDigestResponse(String username, String realm, String password,
                                                String method, String uri, String nonce,
                                                String nc, String cnonce, String qop,
                                                String algorithm, String body) {
        String alg = algorithm != null ? algorithm : "MD5";
        String ha1 = computeHA1(username, realm, password, nonce, cnonce, alg);

        // HA2 = H(method:uri) for qop=auth, H(method:uri:H(body)) for qop=auth-int
        String ha2;
        String baseAlg = baseAlgorithm(alg);
        if ("auth-int".equals(qop)) {
            String bodyHash = hash(body != null ? body : "", baseAlg);
            ha2 = hash(method + ":" + uri + ":" + bodyHash, baseAlg);
        } else {
            ha2 = hash(method + ":" + uri, baseAlg);
        }

        // Response
        if (qop != null && !qop.isEmpty()) {
            return hash(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2, baseAlg);
        } else {
            return hash(ha1 + ":" + nonce + ":" + ha2, baseAlg);
        }
    }

    /**
     * Computes the rspauth value for the Authentication-Info header (RFC 7616 Section 3.8).
     * The rspauth is computed like the request-digest but with an empty method string for A2.
     *
     * @param username  the username
     * @param realm     the realm
     * @param password  the password
     * @param uri       the request URI
     * @param nonce     the server nonce
     * @param nc        the nonce count
     * @param cnonce    the client nonce
     * @param qop       the quality of protection
     * @param algorithm the hash algorithm
     * @param body      the response body (for auth-int)
     * @return the rspauth hash
     * @since 0.1.0
     */
    public static String computeRspAuth(String username, String realm, String password,
                                         String uri, String nonce, String nc, String cnonce,
                                         String qop, String algorithm, String body) {
        String alg = algorithm != null ? algorithm : "MD5";
        String ha1 = computeHA1(username, realm, password, nonce, cnonce, alg);
        String baseAlg = baseAlgorithm(alg);

        // For rspauth, A2 uses empty string instead of method: ":" + uri
        String ha2;
        if ("auth-int".equals(qop)) {
            String bodyHash = hash(body != null ? body : "", baseAlg);
            ha2 = hash(":" + uri + ":" + bodyHash, baseAlg);
        } else {
            ha2 = hash(":" + uri, baseAlg);
        }

        if (qop != null && !qop.isEmpty()) {
            return hash(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2, baseAlg);
        } else {
            return hash(ha1 + ":" + nonce + ":" + ha2, baseAlg);
        }
    }

    /**
     * Computes HA1 according to the algorithm variant. For standard algorithms,
     * HA1 = H(username:realm:password). For -sess variants (RFC 7616 Section 3.4.3),
     * HA1 = H(H(username:realm:password):nonce:cnonce).
     *
     * @param username the username
     * @param realm    the realm
     * @param password the password
     * @param nonce    the server nonce
     * @param cnonce   the client nonce
     * @param algorithm the algorithm (MD5, MD5-sess, SHA-256, SHA-256-sess)
     * @return the computed HA1
     * @since 0.1.0
     */
    static String computeHA1(String username, String realm, String password,
                             String nonce, String cnonce, String algorithm) {
        String baseAlg = baseAlgorithm(algorithm);
        String ha1Base = hash(username + ":" + realm + ":" + password, baseAlg);

        if (isSessionAlgorithm(algorithm)) {
            // MD5-sess / SHA-256-sess: HA1 = H(H(username:realm:password):nonce:cnonce)
            return hash(ha1Base + ":" + nonce + ":" + cnonce, baseAlg);
        }
        return ha1Base;
    }

    /**
     * Returns the base hash algorithm name (strips -sess suffix).
     *
     * @param algorithm the algorithm identifier
     * @return the base algorithm (MD5 or SHA-256)
     * @since 0.1.0
     */
    static String baseAlgorithm(String algorithm) {
        if (algorithm == null) return "MD5";
        String upper = algorithm.toUpperCase(Locale.ROOT);
        if (upper.endsWith("-SESS")) {
            return upper.substring(0, upper.length() - 5);
        }
        return upper;
    }

    /**
     * Returns whether the algorithm is a session variant (-sess suffix).
     *
     * @param algorithm the algorithm
     * @return true if it is a session variant
     * @since 0.1.0
     */
    static boolean isSessionAlgorithm(String algorithm) {
        return algorithm != null && algorithm.toUpperCase(Locale.ROOT).endsWith("-SESS");
    }

    /**
     * Hashes a string with the specified algorithm (MD5 or SHA-256).
     *
     * @param input     the input string
     * @param algorithm the algorithm name
     * @return the hex-encoded hash
     * @since 0.1.0
     */
    public static String hash(String input, String algorithm) {
        try {
            String base = baseAlgorithm(algorithm);
            String javaAlg = switch (base) {
                case "MD5" -> "MD5";
                case "SHA-256", "SHA256" -> "SHA-256";
                default -> base;
            };
            MessageDigest md = MessageDigest.getInstance(javaAlg);
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm not available: " + algorithm, e);
        }
    }

    /**
     * Parses digest authentication parameters from the header value.
     *
     * @param params the parameter string
     * @return map of parameter name to value
     * @since 0.1.0
     */
    static Map<String, String> parseDigestParams(String params) {
        Map<String, String> result = new LinkedHashMap<>();
        int i = 0;
        while (i < params.length()) {
            // Skip whitespace and commas
            while (i < params.length() && (params.charAt(i) == ' ' || params.charAt(i) == ','
                    || params.charAt(i) == '\t')) i++;
            if (i >= params.length()) break;

            // Parse key
            int eqIndex = params.indexOf('=', i);
            if (eqIndex < 0) break;
            String key = params.substring(i, eqIndex).trim().toLowerCase(Locale.ROOT);
            i = eqIndex + 1;

            // Parse value
            if (i < params.length() && params.charAt(i) == '"') {
                // Quoted value
                i++;
                int end = params.indexOf('"', i);
                if (end < 0) break;
                result.put(key, params.substring(i, end));
                i = end + 1;
            } else {
                // Unquoted value
                int end = i;
                while (end < params.length() && params.charAt(end) != ',' && params.charAt(end) != ' ') end++;
                result.put(key, params.substring(i, end).trim());
                i = end;
            }
        }
        return result;
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Returns the configured algorithm.
     *
     * @return the algorithm
     * @since 0.1.0
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * Returns the configured qop.
     *
     * @return the qop
     * @since 0.1.0
     */
    public String getQop() {
        return qop;
    }

    /**
     * Returns the nonce manager.
     *
     * @return the nonce manager
     * @since 0.1.0
     */
    public NonceManager getNonceManager() {
        return nonceManager;
    }

    /**
     * Returns whether this scheme operates in proxy mode (407 Proxy-Authenticate).
     *
     * @return true if proxy mode
     * @since 0.1.0
     */
    public boolean isProxyMode() {
        return proxyMode;
    }
}
