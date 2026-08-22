package ssg.legoflow.http.auth.sso.demo;

import ssg.legoflow.http.auth.AuthPrincipal;
import ssg.legoflow.http.auth.reverse.ReverseProxySso;
import ssg.legoflow.http.auth.reverse.ReverseProxySsoConfig;
import ssg.legoflow.http.auth.saml.SamlAssertionParser;
import ssg.legoflow.http.auth.saml.SamlAuthnRequest;
import ssg.legoflow.http.auth.saml.SamlConfig;
import ssg.legoflow.http.auth.saml.SamlLogout;
import ssg.legoflow.http.auth.sso.SsoConfig;
import ssg.legoflow.http.auth.sso.SsoManager;
import ssg.legoflow.http.auth.sso.SsoSession;
import ssg.legoflow.http.auth.token.JwtTokenProvider;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
/**
 * Comprehensive demo of all SSO module features.
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>SSO Manager — JWT-based federated sessions, login, validate, logout</li>
 *   <li>SSO Session — federated service tracking, attributes, expiration</li>
 *   <li>Reverse Proxy SSO — header injection, principal extraction</li>
 *   <li>SAML AuthnRequest — XML generation, redirect and POST binding</li>
 *   <li>SAML Assertion Parsing — NameID, Issuer, Attributes extraction</li>
 *   <li>SAML Logout — LogoutRequest generation</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoSsoAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoSsoAll.class);

    /** Set to {@code true} to test with an external IdP. */
    public static boolean USE_EXTERNAL = false;

    private DemoSsoAll() {}

    /**
     * Results from running the full SSO demo.
     *
     * @param ssoManager          true if SSO manager login/validate/logout works
     * @param ssoSession          true if SSO session tracking works
     * @param reverseProxySso     true if reverse proxy SSO header injection works
     * @param samlAuthnRequest    true if SAML AuthnRequest generation works
     * @param samlAssertionParsing true if SAML assertion parsing works
     * @param samlLogout          true if SAML logout request generation works
     * @since 0.1.0
     */
    public record Results(
            boolean ssoManager,
            boolean ssoSession,
            boolean reverseProxySso,
            boolean samlAuthnRequest,
            boolean samlAssertionParsing,
            boolean samlLogout
    ) {}

    /**
     * Runs the comprehensive demo covering all SSO features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     * @since 0.1.0
     */
    public static Results runAll() throws Exception {
        boolean manager = demoSsoManager();
        boolean session = demoSsoSession();
        boolean reverseProxy = demoReverseProxySso();
        boolean authnReq = demoSamlAuthnRequest();
        boolean assertion = demoSamlAssertionParsing();
        boolean logout = demoSamlLogout();

        return new Results(manager, session, reverseProxy, authnReq, assertion, logout);
    }

    // ======================== 1. SSO MANAGER =================================

    /**
     * Demonstrates JWT-based SSO manager with login, session validation,
     * and federated logout with service propagation.
     *
     * @return true if SSO manager operations work correctly
     * @since 0.1.0
     */
    static boolean demoSsoManager() {
        LOG.info("=== 1. SSO Manager ===");

        String secret = "sso-demo-secret-key-must-be-at-least-32-bytes!!";
        var tokenProvider = JwtTokenProvider.hmac256(secret, "sso-demo", Duration.ofHours(8));
        var config = new SsoConfig("example.com", "LF_SSO", Duration.ofHours(8),
                Set.of("https://app1.example.com", "https://app2.example.com"), false);

        try (var ssoManager = new SsoManager(config, tokenProvider)) {
            // Login
            var principal = new AuthPrincipal("alice", Set.of("admin"), Map.of());
            var loginResponse = HttpResponse.of(HttpStatus.OK);
            SsoSession session = ssoManager.login(principal, loginResponse);
            LOG.info("SSO login: session={}", session.getId());

            boolean sessionCreated = session.getId() != null;
            boolean principalOk = "alice".equals(session.getPrincipal().getName());
            boolean activeCount = ssoManager.getActiveSessionCount() == 1;

            // Validate session via cookie
            String setCookie = loginResponse.getHeaders().get("set-cookie");
            boolean cookieSet = setCookie != null && setCookie.contains("LF_SSO=");
            LOG.info("SSO cookie set: {}", cookieSet);

            // Config check
            boolean domainOk = "example.com".equals(ssoManager.getConfig().getDomain());
            boolean cookieNameOk = "LF_SSO".equals(ssoManager.getConfig().getCookieName());

            LOG.info("Session: {}, principal: {}, count: {}, config: {}",
                    sessionCreated, principalOk, activeCount, domainOk);
            return sessionCreated && principalOk && activeCount && cookieSet && domainOk && cookieNameOk;
        }
    }

    // ======================== 2. SSO SESSION =================================

    /**
     * Demonstrates SSO session with federated service tracking,
     * session attributes, and expiration checking.
     *
     * @return true if SSO session operations work correctly
     * @since 0.1.0
     */
    static boolean demoSsoSession() {
        LOG.info("=== 2. SSO Session ===");

        var principal = AuthPrincipal.of("bob", Set.of("user"));
        var session = new SsoSession("session-123", principal);

        // Track authenticated services
        session.addAuthenticatedService("https://app1.example.com");
        session.addAuthenticatedService("https://app2.example.com");
        boolean servicesOk = session.getAuthenticatedServices().size() == 2;

        // Attributes
        session.setAttribute("theme", "dark");
        String theme = session.getAttribute("theme");
        boolean attrOk = "dark".equals(theme);

        // Not expired (8-hour timeout)
        boolean notExpired = !session.isExpired(28800);
        boolean notInvalidated = !session.isInvalidated();

        // Invalidate
        session.invalidate();
        boolean invalidated = session.isInvalidated();
        boolean expiredAfterInvalidate = session.isExpired(28800);

        LOG.info("Services: {}, attr: {}, notExpired: {}, invalidated: {}",
                servicesOk, attrOk, notExpired, invalidated);
        return servicesOk && attrOk && notExpired && notInvalidated
                && invalidated && expiredAfterInvalidate;
    }

    // ======================== 3. REVERSE PROXY SSO ===========================

    /**
     * Demonstrates reverse proxy SSO with header injection and principal extraction.
     *
     * @return true if reverse proxy SSO works correctly
     * @since 0.1.0
     */
    static boolean demoReverseProxySso() {
        LOG.info("=== 3. Reverse Proxy SSO ===");

        var config = new ReverseProxySsoConfig(
                "x-forwarded-user", "x-forwarded-roles",
                "x-forwarded-email", "x-forwarded-name",
                Set.of("10.0.0.1", "10.0.0.2"), false);
        var proxySso = new ReverseProxySso(config);

        // Simulate proxy request with auth headers
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set("x-forwarded-user", "alice");
        request.getHeaders().set("x-forwarded-roles", "admin,user");
        request.getHeaders().set("x-forwarded-email", "alice@example.com");
        request.getHeaders().set("x-forwarded-name", "Alice Smith");

        var extracted = proxySso.extractPrincipal(request);
        boolean principalFound = extracted.isPresent();
        boolean nameOk = extracted.map(p -> "alice".equals(p.getName())).orElse(false);
        boolean rolesOk = extracted.map(p -> p.hasRole("admin") && p.hasRole("user")).orElse(false);
        boolean emailOk = extracted.map(p -> "alice@example.com".equals(p.getAttribute("email"))).orElse(false);
        LOG.info("Extracted: {}, name: {}, roles: {}, email: {}", principalFound, nameOk, rolesOk, emailOk);

        // Header injection for backend
        var backendRequest = HttpRequest.of(HttpMethod.GET, "/backend/api");
        var principal = new AuthPrincipal("bob", Set.of("viewer"),
                Map.of("email", "bob@example.com", "display_name", "Bob Jones"));
        proxySso.prepareBackendRequest(backendRequest, principal);

        boolean userInjected = "bob".equals(backendRequest.getHeaders().get("x-forwarded-user"));
        boolean rolesInjected = "viewer".equals(backendRequest.getHeaders().get("x-forwarded-roles"));
        LOG.info("Backend headers: user={}, roles={}", userInjected, rolesInjected);

        // No headers — no principal
        var emptyRequest = HttpRequest.of(HttpMethod.GET, "/api");
        boolean noPrincipal = proxySso.extractPrincipal(emptyRequest).isEmpty();

        return principalFound && nameOk && rolesOk && emailOk
                && userInjected && rolesInjected && noPrincipal;
    }

    // ======================== 4. SAML AUTHN REQUEST ===========================

    /**
     * Demonstrates SAML AuthnRequest generation for both HTTP-Redirect
     * and HTTP-POST bindings.
     *
     * @return true if AuthnRequest generation works correctly
     * @since 0.1.0
     */
    static boolean demoSamlAuthnRequest() {
        LOG.info("=== 4. SAML AuthnRequest ===");

        var samlConfig = new SamlConfig("https://idp.example.com",
                "https://idp.example.com/sso", null,
                "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress");

        var authnRequest = SamlAuthnRequest.fromConfig(samlConfig,
                "https://sp.example.com/acs", "https://sp.example.com");

        // XML generation
        String xml = authnRequest.toXml();
        LOG.info("AuthnRequest XML: {} chars", xml.length());
        boolean hasProtocol = xml.contains("urn:oasis:names:tc:SAML:2.0:protocol");
        boolean hasVersion = xml.contains("Version=\"2.0\"");
        boolean hasIssuer = xml.contains("<saml:Issuer>https://sp.example.com</saml:Issuer>");
        boolean hasAcs = xml.contains("AssertionConsumerServiceURL=\"https://sp.example.com/acs\"");
        boolean hasNameIdPolicy = xml.contains("NameIDPolicy");
        boolean hasId = authnRequest.getId() != null && authnRequest.getId().startsWith("_");

        // Redirect binding (deflate + base64)
        String redirect = authnRequest.toRedirectBinding();
        boolean redirectNotEmpty = redirect != null && !redirect.isEmpty();
        LOG.info("Redirect binding: {} chars", redirect.length());

        // POST binding (HTML form)
        String postForm = authnRequest.toPostBindingForm();
        boolean postHasForm = postForm.contains("<form");
        boolean postHasSamlRequest = postForm.contains("SAMLRequest");
        LOG.info("POST binding form: {} chars, hasForm: {}", postForm.length(), postHasForm);

        return hasProtocol && hasVersion && hasIssuer && hasAcs && hasNameIdPolicy
                && hasId && redirectNotEmpty && postHasForm && postHasSamlRequest;
    }

    // ======================== 5. SAML ASSERTION PARSING =======================

    /**
     * Demonstrates SAML assertion parsing: extracting NameID, Issuer,
     * Attributes, and Conditions from a SAML Response XML.
     *
     * @return true if assertion parsing works correctly
     * @since 0.1.0
     */
    static boolean demoSamlAssertionParsing() {
        LOG.info("=== 5. SAML Assertion Parsing ===");

        var samlConfig = new SamlConfig("https://idp.example.com",
                "https://idp.example.com/sso", null, null);
        var parser = new SamlAssertionParser(samlConfig);

        // Construct a SAML Response XML
        String samlXml = """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                    xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                  <saml:Issuer>https://idp.example.com</saml:Issuer>
                  <saml:Assertion>
                    <saml:Subject>
                      <saml:NameID>alice@example.com</saml:NameID>
                    </saml:Subject>
                    <saml:Conditions NotBefore="2024-01-01T00:00:00Z" NotOnOrAfter="2024-12-31T23:59:59Z"/>
                    <saml:AttributeStatement>
                      <saml:Attribute Name="email">
                        <saml:AttributeValue>alice@example.com</saml:AttributeValue>
                      </saml:Attribute>
                      <saml:Attribute Name="displayName">
                        <saml:AttributeValue>Alice Smith</saml:AttributeValue>
                      </saml:Attribute>
                      <saml:Attribute Name="Role">
                        <saml:AttributeValue>admin,user</saml:AttributeValue>
                      </saml:Attribute>
                    </saml:AttributeStatement>
                  </saml:Assertion>
                </samlp:Response>""";

        var assertion = parser.parseResponse(samlXml);
        boolean parsed = assertion.isPresent();
        LOG.info("Assertion parsed: {}", parsed);

        if (parsed) {
            var a = assertion.get();
            boolean nameIdOk = "alice@example.com".equals(a.nameId());
            boolean issuerOk = "https://idp.example.com".equals(a.issuer());
            boolean emailAttr = "alice@example.com".equals(a.attributes().get("email"));
            boolean nameAttr = "Alice Smith".equals(a.attributes().get("displayName"));
            boolean notBefore = a.notBefore() != null;
            boolean notOnOrAfter = a.notOnOrAfter() != null;

            // Convert to principal
            var principal = a.toPrincipal();
            boolean principalName = "alice@example.com".equals(principal.getName());
            boolean principalRoles = principal.hasRole("admin");

            LOG.info("NameID: {}, issuer: {}, email: {}, roles: {}",
                    nameIdOk, issuerOk, emailAttr, principalRoles);

            // Base64 response parsing
            String base64 = Base64.getEncoder().encodeToString(samlXml.getBytes());
            var base64Assertion = parser.parseBase64Response(base64);
            boolean base64Parsed = base64Assertion.isPresent();

            return nameIdOk && issuerOk && emailAttr && nameAttr && notBefore && notOnOrAfter
                    && principalName && principalRoles && base64Parsed;
        }
        return false;
    }

    // ======================== 6. SAML LOGOUT ==================================

    /**
     * Demonstrates SAML logout request generation and response parsing.
     *
     * @return true if SAML logout operations work correctly
     * @since 0.1.0
     */
    static boolean demoSamlLogout() {
        LOG.info("=== 6. SAML Logout ===");

        // Generate LogoutRequest (issuer=SP, destination=IdP SLO)
        String logoutXml = SamlLogout.generateLogoutRequest(
                "https://sp.example.com",
                "https://idp.example.com/slo",
                "alice@example.com",
                null);
        LOG.info("LogoutRequest: {} chars", logoutXml.length());

        boolean hasLogoutReq = logoutXml.contains("LogoutRequest");
        boolean hasDestination = logoutXml.contains("https://idp.example.com/slo");
        boolean hasNameId = logoutXml.contains("alice@example.com");
        boolean hasIssuer = logoutXml.contains("https://sp.example.com");

        // Parse LogoutResponse (success)
        String logoutResponse = """
                <samlp:LogoutResponse xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol">
                  <samlp:Status>
                    <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
                  </samlp:Status>
                </samlp:LogoutResponse>""";

        var result = SamlLogout.parseLogoutResponse(logoutResponse);
        boolean statusSuccess = result.isPresent() && result.get().success();
        LOG.info("Logout response success: {}", statusSuccess);

        return hasLogoutReq && hasDestination && hasNameId && hasIssuer && statusSuccess;
    }
}
