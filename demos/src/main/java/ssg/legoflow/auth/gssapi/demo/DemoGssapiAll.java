package ssg.legoflow.auth.gssapi.demo;

import ssg.legoflow.auth.gssapi.GssConfig;
import ssg.legoflow.auth.gssapi.GssException;
import ssg.legoflow.auth.gssapi.GssOids;
import ssg.legoflow.auth.gssapi.SpnegoTokenHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comprehensive demo of all GSSAPI module features.
 *
 * <h2>Configuration</h2>
 * <p><b>Preferred (default): No external KDC required.</b> All demos exercise
 * the module's configuration building, OID constants, SPNEGO token encoding/decoding,
 * and parameter validation without needing a live Kerberos infrastructure.</p>
 *
 * <p><b>Alternative: External KDC.</b> Set {@link #USE_EXTERNAL}{@code =true} and
 * configure realm/kdc/principal for live Kerberos context establishment. Required for:</p>
 * <ul>
 *   <li>Full GSS-API context initiation and acceptance</li>
 *   <li>Kerberos ticket-based authentication</li>
 *   <li>MIC generation and verification</li>
 * </ul>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>OID constants — Kerberos V5, SPNEGO, KRB5_PRINCIPAL_NAME</li>
 *   <li>GssConfig builder — realm, KDC, service principal, keytab, system properties</li>
 *   <li>SPNEGO NegTokenInit — creation and mechToken extraction</li>
 *   <li>SPNEGO NegTokenResp — creation with accept/reject status</li>
 *   <li>SPNEGO token detection — isSpnegoToken for NegTokenInit and NegTokenResp</li>
 *   <li>Base64 encoding/decoding — round-trip token serialization</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoGssapiAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoGssapiAll.class);

    /** Set to {@code true} to test with a live Kerberos KDC. */
    public static boolean USE_EXTERNAL = false;

    /** Kerberos realm for external KDC. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_REALM = "EXAMPLE.COM";

    /** KDC hostname for external KDC. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_KDC = "kdc.example.com";

    private DemoGssapiAll() {}

    /**
     * Results from running the full GSSAPI demo.
     *
     * @param oidConstants      true if all OID constants are valid
     * @param configBuilder     true if GssConfig builder produces valid config
     * @param spnegoNegTokenInit true if NegTokenInit creation and extraction works
     * @param spnegoNegTokenResp true if NegTokenResp creation works
     * @param spnegoDetection   true if SPNEGO token detection works correctly
     * @param base64RoundTrip   true if Base64 encode/decode round-trip succeeds
     * @since 0.1.0
     */
    public record Results(
            boolean oidConstants,
            boolean configBuilder,
            boolean spnegoNegTokenInit,
            boolean spnegoNegTokenResp,
            boolean spnegoDetection,
            boolean base64RoundTrip
    ) {}

    /**
     * Runs the comprehensive demo covering all GSSAPI features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     * @since 0.1.0
     */
    public static Results runAll() throws Exception {
        boolean oids = demoOidConstants();
        boolean config = demoConfigBuilder();
        boolean negTokenInit = demoSpnegoNegTokenInit();
        boolean negTokenResp = demoSpnegoNegTokenResp();
        boolean detection = demoSpnegoDetection();
        boolean base64 = demoBase64RoundTrip();

        return new Results(oids, config, negTokenInit, negTokenResp, detection, base64);
    }

    // ======================== 1. OID CONSTANTS ================================

    /**
     * Demonstrates the standard GSS-API OID constants for Kerberos V5, SPNEGO,
     * and KRB5 principal name type.
     *
     * @return true if all OIDs are valid
     * @since 0.1.0
     */
    static boolean demoOidConstants() {
        LOG.info("=== 1. OID Constants ===");

        boolean kerbV5 = GssOids.KERBEROS_V5 != null
                && "1.2.840.113554.1.2.2".equals(GssOids.KERBEROS_V5.toString());
        LOG.info("KERBEROS_V5 OID: {} (valid={})", GssOids.KERBEROS_V5, kerbV5);

        boolean spnego = GssOids.SPNEGO != null
                && "1.3.6.1.5.5.2".equals(GssOids.SPNEGO.toString());
        LOG.info("SPNEGO OID: {} (valid={})", GssOids.SPNEGO, spnego);

        boolean krb5Name = GssOids.KRB5_PRINCIPAL_NAME != null
                && "1.2.840.113554.1.2.2.1".equals(GssOids.KRB5_PRINCIPAL_NAME.toString());
        LOG.info("KRB5_PRINCIPAL_NAME OID: {} (valid={})", GssOids.KRB5_PRINCIPAL_NAME, krb5Name);

        return kerbV5 && spnego && krb5Name;
    }

    // ======================== 2. CONFIG BUILDER ===============================

    /**
     * Demonstrates building a GssConfig using the builder pattern with realm,
     * KDC, service principal, keytab path, and useSubjectCredsOnly settings.
     *
     * @return true if config is built correctly
     * @since 0.1.0
     */
    static boolean demoConfigBuilder() {
        LOG.info("=== 2. GssConfig Builder ===");

        GssConfig config = GssConfig.builder()
                .realm("EXAMPLE.COM")
                .kdc("kdc.example.com")
                .servicePrincipal("HTTP/server.example.com@EXAMPLE.COM")
                .keytabPath("/etc/krb5.keytab")
                .useSubjectCredsOnly(false)
                .build();

        LOG.info("Config: {}", config);

        boolean realmOk = "EXAMPLE.COM".equals(config.realm());
        boolean kdcOk = "kdc.example.com".equals(config.kdc());
        boolean spnOk = "HTTP/server.example.com@EXAMPLE.COM".equals(config.servicePrincipal());
        boolean keytabOk = "/etc/krb5.keytab".equals(config.keytabPath());
        boolean subjOk = !config.useSubjectCredsOnly();

        return realmOk && kdcOk && spnOk && keytabOk && subjOk;
    }

    // ======================== 3. SPNEGO NEGTOKENINIT ==========================

    /**
     * Demonstrates creating a SPNEGO NegTokenInit wrapping a mock Kerberos
     * AP-REQ token, and extracting the mechanism token back from it.
     *
     * @return true if the extracted token matches the original
     * @throws Exception if token operations fail
     * @since 0.1.0
     */
    static boolean demoSpnegoNegTokenInit() throws Exception {
        LOG.info("=== 3. SPNEGO NegTokenInit ===");

        // Simulate a Kerberos AP-REQ token
        byte[] mockMechToken = "mock-kerberos-ap-req-token-data".getBytes();

        // Create NegTokenInit
        byte[] negTokenInit = SpnegoTokenHandler.createNegTokenInit(mockMechToken);
        LOG.info("NegTokenInit created: {} bytes", negTokenInit.length);

        // Extract mechanism token back
        byte[] extracted = SpnegoTokenHandler.extractMechToken(negTokenInit);
        LOG.info("Extracted mechToken: {} bytes", extracted.length);

        // Verify round-trip
        boolean match = java.util.Arrays.equals(mockMechToken, extracted);
        LOG.info("MechToken round-trip match: {}", match);

        return match && negTokenInit.length > mockMechToken.length;
    }

    // ======================== 4. SPNEGO NEGTOKENRESP ==========================

    /**
     * Demonstrates creating SPNEGO NegTokenResp tokens with both
     * accept-completed and accept-incomplete statuses.
     *
     * @return true if both token variants are created successfully
     * @since 0.1.0
     */
    static boolean demoSpnegoNegTokenResp() {
        LOG.info("=== 4. SPNEGO NegTokenResp ===");

        byte[] mockResponseToken = "mock-kerberos-ap-rep-data".getBytes();

        // Accept-completed
        byte[] acceptCompleted = SpnegoTokenHandler.createNegTokenResp(mockResponseToken, true);
        LOG.info("NegTokenResp (accept-completed): {} bytes", acceptCompleted.length);

        // Accept-incomplete
        byte[] acceptIncomplete = SpnegoTokenHandler.createNegTokenResp(mockResponseToken, false);
        LOG.info("NegTokenResp (accept-incomplete): {} bytes", acceptIncomplete.length);

        boolean completedOk = acceptCompleted.length > mockResponseToken.length;
        boolean incompleteOk = acceptIncomplete.length > mockResponseToken.length;

        // Both are NegTokenResp (context tag 0xa1) tokens
        boolean completedIsResp = (acceptCompleted[0] & 0xff) == 0xa1;
        boolean incompleteIsResp = (acceptIncomplete[0] & 0xff) == 0xa1;

        LOG.info("accept-completed tag=0xa1: {}, accept-incomplete tag=0xa1: {}",
                completedIsResp, incompleteIsResp);

        return completedOk && incompleteOk && completedIsResp && incompleteIsResp;
    }

    // ======================== 5. SPNEGO DETECTION =============================

    /**
     * Demonstrates SPNEGO token detection: distinguishing SPNEGO tokens
     * from raw mechanism tokens using the isSpnegoToken check.
     *
     * @return true if detection works correctly for all cases
     * @since 0.1.0
     */
    static boolean demoSpnegoDetection() {
        LOG.info("=== 5. SPNEGO Token Detection ===");

        byte[] mockMechToken = "raw-mechanism-token".getBytes();

        // NegTokenInit should be detected as SPNEGO
        byte[] negTokenInit = SpnegoTokenHandler.createNegTokenInit(mockMechToken);
        boolean initDetected = SpnegoTokenHandler.isSpnegoToken(negTokenInit);
        LOG.info("NegTokenInit detected as SPNEGO: {}", initDetected);

        // NegTokenResp should be detected as SPNEGO
        byte[] negTokenResp = SpnegoTokenHandler.createNegTokenResp(mockMechToken, true);
        boolean respDetected = SpnegoTokenHandler.isSpnegoToken(negTokenResp);
        LOG.info("NegTokenResp detected as SPNEGO: {}", respDetected);

        // Raw bytes should NOT be detected as SPNEGO
        boolean rawNotDetected = !SpnegoTokenHandler.isSpnegoToken(mockMechToken);
        LOG.info("Raw token NOT detected as SPNEGO: {}", rawNotDetected);

        // Null/empty should NOT be detected
        boolean nullNotDetected = !SpnegoTokenHandler.isSpnegoToken(null);
        boolean emptyNotDetected = !SpnegoTokenHandler.isSpnegoToken(new byte[0]);
        LOG.info("Null/empty NOT detected: {}/{}", nullNotDetected, emptyNotDetected);

        return initDetected && respDetected && rawNotDetected
                && nullNotDetected && emptyNotDetected;
    }

    // ======================== 6. BASE64 ROUND-TRIP ============================

    /**
     * Demonstrates Base64 encoding and decoding of SPNEGO tokens, as used
     * in HTTP Authorization: Negotiate headers.
     *
     * @return true if round-trip preserves the token
     * @since 0.1.0
     */
    static boolean demoBase64RoundTrip() {
        LOG.info("=== 6. Base64 Round-Trip ===");

        byte[] mockMechToken = "kerberos-token-for-base64-test".getBytes();
        byte[] spnegoToken = SpnegoTokenHandler.createNegTokenInit(mockMechToken);

        // Encode to Base64
        String encoded = SpnegoTokenHandler.encodeBase64(spnegoToken);
        LOG.info("Base64 encoded: {} chars", encoded.length());

        // Decode from Base64
        byte[] decoded = SpnegoTokenHandler.decodeBase64(encoded);
        LOG.info("Base64 decoded: {} bytes", decoded.length);

        // Verify match
        boolean match = java.util.Arrays.equals(spnegoToken, decoded);
        LOG.info("Base64 round-trip match: {}", match);

        // Verify decoded is still a valid SPNEGO token
        boolean stillSpnego = SpnegoTokenHandler.isSpnegoToken(decoded);
        LOG.info("Decoded still detected as SPNEGO: {}", stillSpnego);

        return match && stillSpnego;
    }
}
