package ssg.legoflow.media.sip.demo;

import ssg.legoflow.media.sip.dialog.DialogState;
import ssg.legoflow.media.sip.dialog.SipDialog;
import ssg.legoflow.media.sip.header.SipHeaders;
import ssg.legoflow.media.sip.protocol.*;
import ssg.legoflow.media.sip.registration.SipRegistrar;
import ssg.legoflow.media.sip.registration.SipRegistrationClient;
import ssg.legoflow.media.sip.transaction.ClientTransaction;
import ssg.legoflow.media.sip.transaction.ServerTransaction;
import ssg.legoflow.media.sip.transaction.TransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
/**
 * Comprehensive demo of all SIP module features.
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>SIP codec — encode/decode requests and responses</li>
 *   <li>Request builder — fluent request construction with headers</li>
 *   <li>Response builder — create responses from requests</li>
 *   <li>SIP URI parsing — parse sip: and sips: URIs</li>
 *   <li>Registration — registrar binding management</li>
 *   <li>Registration client — REGISTER request construction</li>
 *   <li>Client transaction — INVITE and non-INVITE state machines</li>
 *   <li>Server transaction — request handling state machine</li>
 *   <li>Dialog management — dialog lifecycle from INVITE/response</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoSipAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoSipAll.class);

    /** Set to {@code true} to connect to an external SIP server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external SIP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external SIP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 5060;

    private DemoSipAll() {}

    /**
     * Results from running the full demo.
     *
     * @param sipCodec            true if encode/decode round-trip succeeded
     * @param requestBuilder      true if request builder produced valid request
     * @param responseBuilder     true if response builder produced valid response
     * @param uriParsing          true if SIP URI parsing succeeded
     * @param registration        true if registrar binding management succeeded
     * @param registrationClient  true if registration client request construction succeeded
     * @param clientTransaction   true if client transaction state machine worked
     * @param serverTransaction   true if server transaction state machine worked
     * @param dialogManagement    true if dialog lifecycle management succeeded
     * @since 0.1.0
     */
    public record Results(
            boolean sipCodec,
            boolean requestBuilder,
            boolean responseBuilder,
            boolean uriParsing,
            boolean registration,
            boolean registrationClient,
            boolean clientTransaction,
            boolean serverTransaction,
            boolean dialogManagement
    ) {}

    /**
     * Runs the comprehensive demo covering all SIP features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     * @since 0.1.0
     */
    public static Results runAll() throws Exception {
        boolean codec = demoSipCodec();
        boolean reqBuilder = demoRequestBuilder();
        boolean resBuilder = demoResponseBuilder();
        boolean uriParsing = demoUriParsing();
        boolean registration = demoRegistration();
        boolean regClient = demoRegistrationClient();
        boolean clientTx = demoClientTransaction();
        boolean serverTx = demoServerTransaction();
        boolean dialog = demoDialogManagement();

        return new Results(
                codec, reqBuilder, resBuilder, uriParsing,
                registration, regClient, clientTx, serverTx, dialog
        );
    }

    // ======================== 1. SIP CODEC ===================================

    /**
     * Demonstrates SIP message encode/decode for requests and responses.
     *
     * @since 0.1.0
     */
    static boolean demoSipCodec() throws IOException {
        LOG.info("=== 1. SIP Codec ===");

        // Build and encode a request
        var request = SipRequest.builder(SipMethod.INVITE, "sip:bob@example.com")
                .via("SIP/2.0/UDP 10.0.0.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@example.com>;tag=1928301774")
                .to("<sip:bob@example.com>")
                .callId("a84b4c76e66710@10.0.0.1")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .contact("<sip:alice@10.0.0.1>")
                .build();

        byte[] encoded = SipCodec.encode(request);
        LOG.info("Encoded request: {} bytes", encoded.length);

        // Decode it back
        SipMessage decoded = SipCodec.decode(encoded);
        boolean isRequest = decoded instanceof SipRequest;
        if (isRequest) {
            var decodedReq = (SipRequest) decoded;
            LOG.info("Decoded: {} {} {}", decodedReq.method(), decodedReq.requestUri(), decodedReq.version());
            boolean methodOk = decodedReq.method() == SipMethod.INVITE;
            boolean uriOk = "sip:bob@example.com".equals(decodedReq.requestUri());

            // Encode and decode a response
            var response = SipResponse.builder(SipStatus.OK)
                    .fromRequest(request)
                    .contact("<sip:bob@10.0.0.2>")
                    .build();

            byte[] resEncoded = SipCodec.encode(response);
            SipMessage resDecoded = SipCodec.decode(resEncoded);
            boolean isResponse = resDecoded instanceof SipResponse;
            boolean statusOk = false;
            if (isResponse) {
                var decodedRes = (SipResponse) resDecoded;
                statusOk = decodedRes.statusCode() == 200;
                LOG.info("Decoded response: {} {}", decodedRes.statusCode(), decodedRes.reasonPhrase());
            }

            return methodOk && uriOk && isResponse && statusOk;
        }
        return false;
    }

    // ======================== 2. REQUEST BUILDER =============================

    /**
     * Demonstrates fluent SIP request builder with all header types.
     *
     * @since 0.1.0
     */
    static boolean demoRequestBuilder() {
        LOG.info("=== 2. Request Builder ===");

        var invite = SipRequest.builder(SipMethod.INVITE, "sip:bob@biloxi.example.com")
                .via("SIP/2.0/UDP alice.atlanta.example.com:5060;branch=z9hG4bKnashds8")
                .from("\"Alice\" <sip:alice@atlanta.example.com>;tag=abc123")
                .to("<sip:bob@biloxi.example.com>")
                .callId("3848276298220188511@atlanta.example.com")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .contact("<sip:alice@10.0.0.1:5060>")
                .userAgent("Lego-Flow-SIP/1.0")
                .body("v=0\r\no=alice 123 1 IN IP4 10.0.0.1\r\ns=Call\r\n", "application/sdp")
                .build();

        LOG.info("Request line: {}", invite.requestLine());
        LOG.info("Method: {}", invite.method());
        LOG.info("Has body: {}", invite.hasBody());
        LOG.info("Body length: {}", invite.body().length);

        boolean methodOk = invite.method() == SipMethod.INVITE;
        boolean hasVia = invite.headers().first(SipHeaders.VIA).isPresent();
        boolean hasFrom = invite.headers().first(SipHeaders.FROM).isPresent();
        boolean hasCallId = invite.headers().first(SipHeaders.CALL_ID).isPresent();
        boolean hasBody = invite.hasBody();
        boolean hasContentType = invite.headers().first(SipHeaders.CONTENT_TYPE).isPresent();
        boolean hasContentLength = invite.headers().first(SipHeaders.CONTENT_LENGTH).isPresent();

        return methodOk && hasVia && hasFrom && hasCallId && hasBody
                && hasContentType && hasContentLength;
    }

    // ======================== 3. RESPONSE BUILDER ============================

    /**
     * Demonstrates SIP response construction from a request.
     *
     * @since 0.1.0
     */
    static boolean demoResponseBuilder() {
        LOG.info("=== 3. Response Builder ===");

        var request = SipRequest.builder(SipMethod.INVITE, "sip:bob@example.com")
                .via("SIP/2.0/UDP 10.0.0.1:5060;branch=z9hG4bK776")
                .from("<sip:alice@example.com>;tag=tag1")
                .to("<sip:bob@example.com>")
                .callId("call-123")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .build();

        // 100 Trying
        var trying = SipResponse.builder(SipStatus.TRYING)
                .fromRequest(request)
                .build();
        LOG.info("100: {} {}", trying.statusCode(), trying.reasonPhrase());
        boolean tryingOk = trying.isProvisional() && trying.statusCode() == 100;

        // 180 Ringing
        var ringing = SipResponse.builder(SipStatus.RINGING)
                .fromRequest(request)
                .to("<sip:bob@example.com>;tag=tag2")
                .build();
        LOG.info("180: {} {}", ringing.statusCode(), ringing.reasonPhrase());
        boolean ringingOk = ringing.isProvisional() && !ringing.isFinal();

        // 200 OK
        var ok = SipResponse.builder(SipStatus.OK)
                .fromRequest(request)
                .to("<sip:bob@example.com>;tag=tag2")
                .contact("<sip:bob@10.0.0.2>")
                .server("Lego-Flow-SIP/1.0")
                .allow("INVITE, ACK, BYE, CANCEL, OPTIONS")
                .build();
        LOG.info("200: {} {}", ok.statusCode(), ok.reasonPhrase());
        boolean okIsSuccess = ok.isSuccess() && ok.isFinal();

        // Response preserves Via, From, To, Call-ID, CSeq
        boolean hasVia = !ok.headers().all(SipHeaders.VIA).isEmpty();
        boolean hasFrom = ok.headers().first(SipHeaders.FROM).isPresent();
        boolean hasCallId = ok.headers().callId().equals("call-123");

        return tryingOk && ringingOk && okIsSuccess && hasVia && hasFrom && hasCallId;
    }

    // ======================== 4. SIP URI PARSING =============================

    /**
     * Demonstrates SIP URI parsing for various URI formats.
     *
     * @since 0.1.0
     */
    static boolean demoUriParsing() {
        LOG.info("=== 4. SIP URI Parsing ===");

        // Basic SIP URI
        var uri1 = SipUri.parse("sip:alice@atlanta.example.com");
        LOG.info("URI1: user={}, host={}, scheme={}",
                uri1.user().orElse("none"), uri1.host(), uri1.scheme());
        boolean uri1Ok = uri1.user().isPresent() && "alice".equals(uri1.user().get())
                && "atlanta.example.com".equals(uri1.host()) && "sip".equals(uri1.scheme());

        // SIPS URI with port
        var uri2 = SipUri.parse("sips:bob@biloxi.example.com:5061");
        LOG.info("URI2: user={}, host={}, port={}, scheme={}",
                uri2.user().orElse("none"), uri2.host(), uri2.port(), uri2.scheme());
        boolean uri2Ok = "sips".equals(uri2.scheme()) && uri2.port() == 5061;

        // URI with parameters
        var uri3 = SipUri.parse("sip:alice@atlanta.example.com;transport=tcp");
        LOG.info("URI3: transport={}", uri3.parameters().get("transport"));
        boolean uri3Ok = "tcp".equals(uri3.parameters().get("transport"));

        // URI without user
        var uri4 = SipUri.parse("sip:atlanta.example.com");
        LOG.info("URI4: user={}, host={}", uri4.user().orElse("none"), uri4.host());
        boolean uri4Ok = uri4.user().isEmpty() && "atlanta.example.com".equals(uri4.host());

        // Format back to string
        String formatted = uri1.format();
        LOG.info("Formatted: {}", formatted);
        boolean formatOk = formatted.contains("alice") && formatted.contains("atlanta.example.com");

        return uri1Ok && uri2Ok && uri3Ok && uri4Ok && formatOk;
    }

    // ======================== 5. REGISTRATION ================================

    /**
     * Demonstrates SIP registrar: register, refresh, query, and unregister.
     *
     * @since 0.1.0
     */
    static boolean demoRegistration() {
        LOG.info("=== 5. Registration ===");
        var registrar = new SipRegistrar("example.com");

        // Register
        var regRequest = SipRequest.builder(SipMethod.REGISTER, "sip:registrar.example.com")
                .via("SIP/2.0/UDP 10.0.0.1:5060;branch=z9hG4bK001")
                .from("<sip:alice@example.com>;tag=reg1")
                .to("<sip:alice@example.com>")
                .callId("reg-call-1")
                .cseq(1, SipMethod.REGISTER)
                .maxForwards(70)
                .contact("<sip:alice@10.0.0.1:5060>")
                .expires(3600)
                .build();

        var regResponse = registrar.handleRegister(regRequest);
        LOG.info("Register response: {}", regResponse.statusCode());
        boolean registerOk = regResponse.isSuccess();

        // Query bindings
        var bindings = registrar.lookup("sip:alice@example.com");
        LOG.info("Bindings after register: {}", bindings.size());
        boolean bindingOk = bindings.size() == 1;

        // Second registration (different contact)
        var regRequest2 = SipRequest.builder(SipMethod.REGISTER, "sip:registrar.example.com")
                .via("SIP/2.0/UDP 10.0.0.2:5060;branch=z9hG4bK002")
                .from("<sip:alice@example.com>;tag=reg2")
                .to("<sip:alice@example.com>")
                .callId("reg-call-2")
                .cseq(1, SipMethod.REGISTER)
                .maxForwards(70)
                .contact("<sip:alice@10.0.0.2:5060>")
                .expires(3600)
                .build();
        registrar.handleRegister(regRequest2);
        var bindings2 = registrar.lookup("sip:alice@example.com");
        LOG.info("Bindings after 2nd register: {}", bindings2.size());
        boolean twoBindings = bindings2.size() == 2;

        // Unregister (expires=0)
        var unregRequest = SipRequest.builder(SipMethod.REGISTER, "sip:registrar.example.com")
                .via("SIP/2.0/UDP 10.0.0.1:5060;branch=z9hG4bK003")
                .from("<sip:alice@example.com>;tag=reg3")
                .to("<sip:alice@example.com>")
                .callId("reg-call-3")
                .cseq(1, SipMethod.REGISTER)
                .maxForwards(70)
                .contact("<sip:alice@10.0.0.1:5060>")
                .expires(0)
                .build();
        registrar.handleRegister(unregRequest);
        var afterUnreg = registrar.lookup("sip:alice@example.com");
        LOG.info("Bindings after unregister: {}", afterUnreg.size());
        boolean afterUnregOk = afterUnreg.size() == 1; // only second contact remains

        return registerOk && bindingOk && twoBindings && afterUnregOk;
    }

    // ======================== 6. REGISTRATION CLIENT ==========================

    /**
     * Demonstrates the SipRegistrationClient for building REGISTER requests.
     *
     * @since 0.1.0
     */
    static boolean demoRegistrationClient() {
        LOG.info("=== 6. Registration Client ===");
        var client = new SipRegistrationClient(
                "sip:registrar.example.com",
                "sip:alice@example.com",
                "sip:alice@10.0.0.1:5060"
        );

        // Build register request
        var regReq = client.register(3600);
        LOG.info("Register: method={}, uri={}", regReq.method(), regReq.requestUri());
        boolean methodOk = regReq.method() == SipMethod.REGISTER;
        boolean uriOk = regReq.requestUri().contains("registrar.example.com");

        // Build unregister request
        var unregReq = client.unregister();
        LOG.info("Unregister: expires={}",
                unregReq.headers().expires().orElse(-1));
        boolean expiresZero = unregReq.headers().expires().orElse(-1) == 0;

        // Call-ID is consistent across requests
        boolean callIdConsistent = client.callId() != null && !client.callId().isEmpty();
        LOG.info("Call-ID: {}", client.callId());

        return methodOk && uriOk && expiresZero && callIdConsistent;
    }

    // ======================== 7. CLIENT TRANSACTION ===========================

    /**
     * Demonstrates client transaction state machine (INVITE).
     *
     * @since 0.1.0
     */
    static boolean demoClientTransaction() {
        LOG.info("=== 7. Client Transaction ===");

        var invite = SipRequest.builder(SipMethod.INVITE, "sip:bob@example.com")
                .via("SIP/2.0/UDP 10.0.0.1:5060;branch=z9hG4bKtx1")
                .from("<sip:alice@example.com>;tag=tx1")
                .to("<sip:bob@example.com>")
                .callId("tx-call-1")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .build();

        var tx = new ClientTransaction("z9hG4bKtx1", SipMethod.INVITE, invite);
        LOG.info("Initial state: {}", tx.state());
        boolean initialOk = tx.state() == TransactionState.INITIAL;

        // Start transaction -> CALLING (INVITE client tx)
        tx.start();
        LOG.info("After start: {}", tx.state());
        boolean callingOk = tx.state() == TransactionState.CALLING;

        // Process provisional response -> PROCEEDING
        var trying = SipResponse.builder(SipStatus.TRYING)
                .fromRequest(invite)
                .build();
        tx.processResponse(trying);
        LOG.info("After 100: {}", tx.state());
        boolean proceedingOk = tx.state() == TransactionState.PROCEEDING;

        // Process final 2xx response -> TERMINATED
        var ok = SipResponse.builder(SipStatus.OK)
                .fromRequest(invite)
                .to("<sip:bob@example.com>;tag=res1")
                .build();
        tx.processResponse(ok);
        LOG.info("After 200: {}", tx.state());
        boolean terminatedOk = tx.state() == TransactionState.TERMINATED;

        return initialOk && callingOk && proceedingOk && terminatedOk;
    }

    // ======================== 8. SERVER TRANSACTION ===========================

    /**
     * Demonstrates server transaction state machine.
     *
     * @since 0.1.0
     */
    static boolean demoServerTransaction() {
        LOG.info("=== 8. Server Transaction ===");

        var invite = SipRequest.builder(SipMethod.INVITE, "sip:bob@example.com")
                .via("SIP/2.0/UDP 10.0.0.1:5060;branch=z9hG4bKstx1")
                .from("<sip:alice@example.com>;tag=stx1")
                .to("<sip:bob@example.com>")
                .callId("stx-call-1")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .build();

        var tx = new ServerTransaction("z9hG4bKstx1", SipMethod.INVITE, invite);
        LOG.info("Initial state: {}", tx.state());
        boolean initialOk = tx.state() == TransactionState.INITIAL;

        // Start server transaction -> PROCEEDING (INVITE auto-sends 100)
        tx.start();
        LOG.info("After start: {}", tx.state());
        boolean proceedingOk = tx.state() == TransactionState.PROCEEDING;

        // Send provisional response
        var ringing = SipResponse.builder(SipStatus.RINGING)
                .fromRequest(invite)
                .to("<sip:bob@example.com>;tag=stag1")
                .build();
        tx.sendResponse(ringing);
        LOG.info("After 180: {}", tx.state());
        boolean stillProceeding = tx.state() == TransactionState.PROCEEDING;

        // Send final response -> COMPLETED (for INVITE server tx)
        var ok = SipResponse.builder(SipStatus.OK)
                .fromRequest(invite)
                .to("<sip:bob@example.com>;tag=stag1")
                .build();
        tx.sendResponse(ok);
        LOG.info("After 200: {}", tx.state());
        boolean completedOrTerminated = tx.state() == TransactionState.COMPLETED
                || tx.state() == TransactionState.TERMINATED;

        return initialOk && proceedingOk && stillProceeding && completedOrTerminated;
    }

    // ======================== 9. DIALOG MANAGEMENT ===========================

    /**
     * Demonstrates SIP dialog lifecycle: creation, confirmation, termination.
     *
     * @since 0.1.0
     */
    static boolean demoDialogManagement() {
        LOG.info("=== 9. Dialog Management ===");

        // Create INVITE request
        var invite = SipRequest.builder(SipMethod.INVITE, "sip:bob@example.com")
                .via("SIP/2.0/UDP 10.0.0.1:5060;branch=z9hG4bKdlg1")
                .from("<sip:alice@example.com>;tag=dlg-alice")
                .to("<sip:bob@example.com>")
                .callId("dialog-call-1")
                .cseq(1, SipMethod.INVITE)
                .maxForwards(70)
                .contact("<sip:alice@10.0.0.1>")
                .build();

        // 200 OK with To-tag creates dialog
        var ok = SipResponse.builder(SipStatus.OK)
                .fromRequest(invite)
                .to("<sip:bob@example.com>;tag=dlg-bob")
                .contact("<sip:bob@10.0.0.2>")
                .build();

        var dialog = SipDialog.createFromUac(invite, ok);
        LOG.info("Dialog: callId={}, state={}", dialog.callId(), dialog.state());

        boolean confirmed = dialog.state() == DialogState.CONFIRMED;
        boolean hasCallId = "dialog-call-1".equals(dialog.callId());
        boolean isUac = dialog.isUac();

        LOG.info("Dialog confirmed: {}, isUac: {}", confirmed, isUac);

        // Terminate dialog
        dialog.terminate();
        LOG.info("After terminate: {}", dialog.state());
        boolean terminated = dialog.state() == DialogState.TERMINATED;

        // UAS dialog from same request
        var uasDialog = SipDialog.createFromUas(invite, "uas-tag");
        LOG.info("UAS dialog: state={}, isUac={}", uasDialog.state(), uasDialog.isUac());
        boolean uasEarly = uasDialog.state() == DialogState.EARLY;
        boolean uasNotUac = !uasDialog.isUac();

        // Confirm UAS dialog
        uasDialog.confirm();
        LOG.info("UAS after confirm: {}", uasDialog.state());
        boolean uasConfirmed = uasDialog.state() == DialogState.CONFIRMED;

        return confirmed && hasCallId && isUac && terminated
                && uasEarly && uasNotUac && uasConfirmed;
    }
}
