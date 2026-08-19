package ssg.legoflow.http.auth.spnego.demo;

import ssg.legoflow.auth.gssapi.GssConfig;
import ssg.legoflow.auth.gssapi.SpnegoTokenHandler;
import ssg.legoflow.http.auth.AuthContext;
import ssg.legoflow.http.auth.AuthCredentials;
import ssg.legoflow.http.auth.AuthResult;
import ssg.legoflow.http.auth.spnego.NegotiateAuthScheme;
import ssg.legoflow.http.auth.spnego.SpnegoConfig;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Base64;
/**
 * Comprehensive demo of all HTTP SPNEGO module features.
 *
 * <h2>Configuration</h2>
 * <p><b>Preferred (default): No external KDC required.</b> Demonstrates configuration
 * building, scheme registration, credential extraction, challenge generation, and
 * SPNEGO token handling without a live Kerberos infrastructure.</p>
 *
 * <p><b>Alternative: External KDC.</b> Set {@link #USE_EXTERNAL}{@code =true} for
 * full Kerberos context establishment with a real KDC.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>SpnegoConfig — builder pattern combining GssConfig with SPNEGO options</li>
 *   <li>NegotiateAuthScheme — scheme name, credential extraction, challenge</li>
 *   <li>Token extraction — Bearer credentials from Negotiate header</li>
 *   <li>Challenge generation — WWW-Authenticate: Negotiate header</li>
 *   <li>SPNEGO integration — token detection and processing flow</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoSpnegoAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoSpnegoAll.class);

    /** Set to {@code true} to test with a live Kerberos KDC. */
    public static boolean USE_EXTERNAL = false;

    private DemoSpnegoAll() {}

    /**
     * Results from running the full SPNEGO demo.
     *
     * @param spnegoConfig        true if SpnegoConfig builder works correctly
     * @param negotiateScheme     true if NegotiateAuthScheme properties work
     * @param tokenExtraction     true if credential extraction from headers works
     * @param challengeGeneration true if WWW-Authenticate challenge is generated
     * @param spnegoIntegration   true if SPNEGO token processing flow works
     * @since 0.1.0
     */
    public record Results(
            boolean spnegoConfig,
            boolean negotiateScheme,
            boolean tokenExtraction,
            boolean challengeGeneration,
            boolean spnegoIntegration
    ) {}

    /**
     * Runs the comprehensive demo covering all SPNEGO features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     * @since 0.1.0
     */
    public static Results runAll() throws Exception {
        boolean config = demoSpnegoConfig();
        boolean scheme = demoNegotiateScheme();
        boolean extraction = demoTokenExtraction();
        boolean challenge = demoChallengeGeneration();
        boolean integration = demoSpnegoIntegration();

        return new Results(config, scheme, extraction, challenge, integration);
    }

    // ======================== 1. SPNEGO CONFIG ================================

    /**
     * Demonstrates SpnegoConfig builder combining GssConfig with SPNEGO-specific
     * options like realm stripping from principal names.
     *
     * @return true if configuration is built correctly
     * @since 0.1.0
     */
    static boolean demoSpnegoConfig() {
        LOG.info("=== 1. SpnegoConfig Builder ===");

        GssConfig gssConfig = GssConfig.builder()
                .realm("CORP.EXAMPLE.COM")
                .kdc("kdc.corp.example.com")
                .servicePrincipal("HTTP/web.corp.example.com@CORP.EXAMPLE.COM")
                .keytabPath("/etc/http.keytab")
                .useSubjectCredsOnly(false)
                .build();

        // Builder with custom options
        SpnegoConfig config = SpnegoConfig.builder()
                .gssConfig(gssConfig)
                .stripRealmFromPrincipal(true)
                .build();

        boolean gssOk = config.gssConfig() != null;
        boolean realmOk = "CORP.EXAMPLE.COM".equals(config.gssConfig().realm());
        boolean stripOk = config.stripRealmFromPrincipal();
        LOG.info("Config: gss={}, realm={}, strip={}", gssOk, realmOk, stripOk);

        // Factory method
        SpnegoConfig factory = SpnegoConfig.of(gssConfig);
        boolean factoryGss = factory.gssConfig() != null;
        boolean factoryStrip = factory.stripRealmFromPrincipal(); // default true

        // Builder with strip=false
        SpnegoConfig noStrip = SpnegoConfig.builder()
                .gssConfig(gssConfig)
                .stripRealmFromPrincipal(false)
                .build();
        boolean noStripOk = !noStrip.stripRealmFromPrincipal();

        LOG.info("Factory: {}, noStrip: {}", factoryGss, noStripOk);
        return gssOk && realmOk && stripOk && factoryGss && factoryStrip && noStripOk;
    }

    // ======================== 2. NEGOTIATE SCHEME =============================

    /**
     * Demonstrates NegotiateAuthScheme properties: scheme name and behavior
     * when no Authorization header is present (issues challenge).
     *
     * @return true if scheme properties are correct
     * @since 0.1.0
     */
    static boolean demoNegotiateScheme() {
        LOG.info("=== 2. NegotiateAuthScheme ===");

        GssConfig gssConfig = GssConfig.builder()
                .realm("EXAMPLE.COM")
                .kdc("kdc.example.com")
                .servicePrincipal("HTTP/server.example.com@EXAMPLE.COM")
                .build();
        SpnegoConfig spnegoConfig = SpnegoConfig.of(gssConfig);

        var scheme = new NegotiateAuthScheme(spnegoConfig);

        // Scheme name
        boolean nameOk = "Negotiate".equals(scheme.schemeName());
        LOG.info("Scheme name: {}", scheme.schemeName());

        // No auth header — should issue challenge
        var request = HttpRequest.of(HttpMethod.GET, "/api/secured");
        var context = AuthContext.ofRealm("EXAMPLE.COM");
        AuthResult result = scheme.authenticate(request, context);
        boolean challengeIssued = result instanceof AuthResult.Challenge c
                && "Negotiate".equals(c.schemeName());
        LOG.info("No auth header issues challenge: {}", challengeIssued);

        // Empty Negotiate header — should issue challenge
        var emptyRequest = HttpRequest.of(HttpMethod.GET, "/api/secured");
        emptyRequest.getHeaders().set(HttpHeaders.AUTHORIZATION, "Negotiate ");
        AuthResult emptyResult = scheme.authenticate(emptyRequest, context);
        boolean emptyChallenge = emptyResult instanceof AuthResult.Challenge;
        LOG.info("Empty Negotiate header issues challenge: {}", emptyChallenge);

        return nameOk && challengeIssued && emptyChallenge;
    }

    // ======================== 3. TOKEN EXTRACTION =============================

    /**
     * Demonstrates credential extraction from the Authorization: Negotiate header.
     *
     * @return true if token extraction works correctly
     * @since 0.1.0
     */
    static boolean demoTokenExtraction() {
        LOG.info("=== 3. Token Extraction ===");

        GssConfig gssConfig = GssConfig.builder()
                .realm("EXAMPLE.COM")
                .kdc("kdc.example.com")
                .servicePrincipal("HTTP/server.example.com@EXAMPLE.COM")
                .build();
        var scheme = new NegotiateAuthScheme(SpnegoConfig.of(gssConfig));

        // With Negotiate token
        String mockToken = Base64.getEncoder().encodeToString("mock-spnego-token".getBytes());
        var request = HttpRequest.of(HttpMethod.GET, "/api/secured");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Negotiate " + mockToken);

        AuthCredentials creds = scheme.extractCredentials(request);
        boolean bearerExtracted = creds instanceof AuthCredentials.Bearer b
                && mockToken.equals(b.token());
        LOG.info("Bearer credentials extracted: {}", bearerExtracted);

        // Without header — None
        var noAuthRequest = HttpRequest.of(HttpMethod.GET, "/api/secured");
        AuthCredentials noneCreds = scheme.extractCredentials(noAuthRequest);
        boolean noneOk = noneCreds instanceof AuthCredentials.None;
        LOG.info("No header returns None: {}", noneOk);

        // Wrong scheme — None
        var basicRequest = HttpRequest.of(HttpMethod.GET, "/api/secured");
        basicRequest.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
        AuthCredentials wrongScheme = scheme.extractCredentials(basicRequest);
        boolean wrongNone = wrongScheme instanceof AuthCredentials.None;
        LOG.info("Wrong scheme returns None: {}", wrongNone);

        return bearerExtracted && noneOk && wrongNone;
    }

    // ======================== 4. CHALLENGE GENERATION =========================

    /**
     * Demonstrates WWW-Authenticate: Negotiate challenge header generation.
     *
     * @return true if challenge header is generated correctly
     * @since 0.1.0
     */
    static boolean demoChallengeGeneration() {
        LOG.info("=== 4. Challenge Generation ===");

        GssConfig gssConfig = GssConfig.builder()
                .realm("EXAMPLE.COM")
                .kdc("kdc.example.com")
                .servicePrincipal("HTTP/server.example.com@EXAMPLE.COM")
                .build();
        var scheme = new NegotiateAuthScheme(SpnegoConfig.of(gssConfig));

        var response = HttpResponse.of(HttpStatus.UNAUTHORIZED);
        var context = AuthContext.ofRealm("EXAMPLE.COM");
        scheme.challenge(response, context);

        String wwwAuth = response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE);
        boolean headerSet = "Negotiate".equals(wwwAuth);
        LOG.info("WWW-Authenticate header: {} (correct={})", wwwAuth, headerSet);

        return headerSet;
    }

    // ======================== 5. SPNEGO INTEGRATION ===========================

    /**
     * Demonstrates the full SPNEGO token processing flow: creating a SPNEGO
     * NegTokenInit, encoding it for HTTP, and having the scheme process it.
     * Without a real KDC, authentication will fail at the GSS-API level, but
     * the token parsing and detection pipeline is exercised.
     *
     * @return true if the integration flow works correctly
     * @since 0.1.0
     */
    static boolean demoSpnegoIntegration() {
        LOG.info("=== 5. SPNEGO Integration ===");

        // Create a mock SPNEGO token
        byte[] mockMechToken = "fake-kerberos-ap-req".getBytes();
        byte[] spnegoToken = SpnegoTokenHandler.createNegTokenInit(mockMechToken);

        // Verify it's a SPNEGO token
        boolean isSpnego = SpnegoTokenHandler.isSpnegoToken(spnegoToken);
        LOG.info("Created SPNEGO token: {} bytes, isSpnego={}", spnegoToken.length, isSpnego);

        // Encode for HTTP header
        String base64Token = Base64.getEncoder().encodeToString(spnegoToken);
        LOG.info("Base64-encoded for HTTP: {} chars", base64Token.length());

        // Simulate the authentication flow
        GssConfig gssConfig = GssConfig.builder()
                .realm("EXAMPLE.COM")
                .kdc("kdc.example.com")
                .servicePrincipal("HTTP/server.example.com@EXAMPLE.COM")
                .build();
        var scheme = new NegotiateAuthScheme(SpnegoConfig.of(gssConfig));

        // The scheme will attempt to create a GSS context, which will fail without a KDC.
        // This exercises the token parsing path but expects a failure result.
        var request = HttpRequest.of(HttpMethod.GET, "/api/secured");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Negotiate " + base64Token);
        var context = AuthContext.ofRealm("EXAMPLE.COM");

        AuthResult result = scheme.authenticate(request, context);
        // Without a real KDC, we expect either Failure (GSS-API error) or Challenge
        boolean expectedResult = result instanceof AuthResult.Failure
                || result instanceof AuthResult.Challenge;
        LOG.info("Auth result without KDC: {} (expected non-success={})",
                result.getClass().getSimpleName(), expectedResult);

        return isSpnego && expectedResult;
    }
}
