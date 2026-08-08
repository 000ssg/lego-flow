package ssg.legoflow.media.sip.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.media.common.codec.SdpNegotiator;
import ssg.legoflow.media.common.codec.SdpParser;
import ssg.legoflow.media.common.codec.SdpWriter;
import ssg.legoflow.media.common.sdp.SessionDescription;
import ssg.legoflow.media.sip.dialog.DialogState;
import ssg.legoflow.media.sip.dialog.SipDialog;
import ssg.legoflow.media.sip.header.SipHeaders;
import ssg.legoflow.media.sip.header.ViaHeader;
import ssg.legoflow.media.sip.protocol.SipMethod;
import ssg.legoflow.media.sip.protocol.SipRequest;
import ssg.legoflow.media.sip.protocol.SipResponse;
import ssg.legoflow.media.sip.protocol.SipStatus;
import ssg.legoflow.media.sip.registration.SipRegistrar;
import ssg.legoflow.media.sip.transaction.ClientTransaction;
import ssg.legoflow.media.sip.transaction.ServerTransaction;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * SIP User Agent combining UAC (client) and UAS (server) roles.
 *
 * <p>Provides high-level call setup and teardown using INVITE/ACK/BYE,
 * with SDP offer/answer integration for media negotiation.
 *
 * <p>Usage (UAC - making a call):
 * <pre>{@code
 * var ua = new SipUserAgent("sip:alice@example.com", "sip:alice@192.168.1.1:5060");
 * ua.setLocalSdp(myCapabilities);
 * SipResponse response = ua.invite("sip:bob@example.com");
 * // ... call in progress ...
 * ua.bye(dialog);
 * }</pre>
 *
 * <p>Usage (UAS - receiving a call):
 * <pre>{@code
 * var ua = new SipUserAgent("sip:bob@example.com", "sip:bob@192.168.1.2:5060");
 * ua.setLocalSdp(myCapabilities);
 * ua.setInviteHandler(request -> {
 *     return ua.accept(request); // 200 OK with SDP answer
 * });
 * }</pre>
 *
 * @since 0.1.0
 */
public final class SipUserAgent implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SipUserAgent.class);

    private final String aor;
    private final String contactUri;
    private final AtomicLong cseqCounter;
    private final Map<String, SipDialog> dialogs;
    private final Map<String, ClientTransaction> clientTransactions;
    private final Map<String, ServerTransaction> serverTransactions;
    private final SipRegistrar registrar;
    private volatile SessionDescription localSdp;
    private volatile SessionDescription remoteSdp;
    private volatile Consumer<SipRequest> inviteHandler;
    private volatile boolean closed;

    /**
     * Creates a SIP User Agent.
     *
     * @param aor        the Address-of-Record (e.g., "sip:alice@example.com")
     * @param contactUri the Contact URI (e.g., "sip:alice@192.168.1.1:5060")
     * @since 0.1.0
     */
    public SipUserAgent(String aor, String contactUri) {
        this.aor = Objects.requireNonNull(aor, "aor");
        this.contactUri = Objects.requireNonNull(contactUri, "contactUri");
        this.cseqCounter = new AtomicLong(0);
        this.dialogs = new ConcurrentHashMap<>();
        this.clientTransactions = new ConcurrentHashMap<>();
        this.serverTransactions = new ConcurrentHashMap<>();
        this.registrar = null;
        this.closed = false;
    }

    /**
     * Creates a SIP User Agent with a local registrar.
     *
     * @param aor        the Address-of-Record
     * @param contactUri the Contact URI
     * @param registrar  the local registrar for handling REGISTER requests
     * @since 0.1.0
     */
    public SipUserAgent(String aor, String contactUri, SipRegistrar registrar) {
        this.aor = Objects.requireNonNull(aor, "aor");
        this.contactUri = Objects.requireNonNull(contactUri, "contactUri");
        this.cseqCounter = new AtomicLong(0);
        this.dialogs = new ConcurrentHashMap<>();
        this.clientTransactions = new ConcurrentHashMap<>();
        this.serverTransactions = new ConcurrentHashMap<>();
        this.registrar = registrar;
        this.closed = false;
    }

    /**
     * Sets the local SDP capabilities for offer/answer negotiation.
     *
     * @param sdp the local SDP session description
     * @since 0.1.0
     */
    public void setLocalSdp(SessionDescription sdp) {
        this.localSdp = sdp;
    }

    /**
     * Sets a handler for incoming INVITE requests.
     *
     * @param handler the INVITE handler
     * @since 0.1.0
     */
    public void setInviteHandler(Consumer<SipRequest> handler) {
        this.inviteHandler = handler;
    }

    /**
     * Creates an INVITE request to initiate a call.
     *
     * @param targetUri the target SIP URI
     * @return the INVITE request
     * @since 0.1.0
     */
    public SipRequest createInvite(String targetUri) {
        String branch = generateBranch();
        String tag = generateTag();
        String callId = generateCallId();

        var builder = SipRequest.builder(SipMethod.INVITE, targetUri)
                .via("SIP/2.0/UDP " + extractHostPort(contactUri) + ";branch=" + branch)
                .from("<" + aor + ">;tag=" + tag)
                .to("<" + targetUri + ">")
                .callId(callId)
                .cseq(cseqCounter.incrementAndGet(), SipMethod.INVITE)
                .maxForwards(70)
                .contact("<" + contactUri + ">")
                .userAgent("LegoFlow-SIP/1.0");

        // Add SDP offer if available
        if (localSdp != null) {
            String sdpBody = SdpWriter.write(localSdp);
            builder.body(sdpBody, "application/sdp");
        }

        return builder.build();
    }

    /**
     * Processes an incoming SIP request (UAS role).
     *
     * @param request the incoming request
     * @return the response
     * @since 0.1.0
     */
    public SipResponse handleRequest(SipRequest request) {
        return switch (request.method()) {
            case INVITE -> handleInvite(request);
            case ACK -> handleAck(request);
            case BYE -> handleBye(request);
            case CANCEL -> handleCancel(request);
            case OPTIONS -> handleOptions(request);
            case REGISTER -> handleRegister(request);
            default -> SipResponse.builder(SipStatus.METHOD_NOT_ALLOWED)
                    .fromRequest(request)
                    .allow("INVITE, ACK, BYE, CANCEL, OPTIONS, REGISTER")
                    .build();
        };
    }

    private SipResponse handleInvite(SipRequest request) {
        String branch = request.headers().topVia().branch();

        // Create server transaction
        var serverTx = new ServerTransaction(branch, SipMethod.INVITE, request);
        serverTx.start();
        serverTransactions.put(branch, serverTx);

        // Send 100 Trying
        var trying = SipResponse.builder(SipStatus.TRYING)
                .fromRequest(request)
                .build();
        serverTx.sendResponse(trying);

        // Notify handler if set
        if (inviteHandler != null) {
            inviteHandler.accept(request);
        }

        // Create dialog
        String localTag = generateTag();
        var dialog = SipDialog.createFromUas(request, localTag);

        // If we have local SDP, do offer/answer
        SessionDescription answer = null;
        if (localSdp != null && request.hasBody()) {
            String offerSdp = request.bodyAsString();
            SessionDescription offer = SdpParser.parse(offerSdp);
            answer = SdpNegotiator.negotiate(offer, localSdp).orElse(null);
            if (answer != null) {
                remoteSdp = offer;
            }
        }

        // Send 180 Ringing
        var ringing = SipResponse.builder(SipStatus.RINGING)
                .fromRequest(request)
                .header(SipHeaders.TO,
                        request.headers().first(SipHeaders.TO).orElse("") + ";tag=" + localTag)
                .build();
        serverTx.sendResponse(ringing);

        // Send 200 OK
        var okBuilder = SipResponse.builder(SipStatus.OK)
                .fromRequest(request)
                .header(SipHeaders.TO,
                        request.headers().first(SipHeaders.TO).orElse("") + ";tag=" + localTag)
                .contact("<" + contactUri + ">");

        if (answer != null) {
            String sdpBody = SdpWriter.write(answer);
            okBuilder.body(sdpBody, "application/sdp");
        }

        var ok = okBuilder.build();
        serverTx.sendResponse(ok);

        // Confirm dialog
        dialog.confirm();
        dialogs.put(dialog.dialogId(), dialog);

        LOG.debug("INVITE handled, dialog created: {}", dialog.dialogId());
        return ok;
    }

    private SipResponse handleAck(SipRequest request) {
        // ACK completes the INVITE transaction
        String branch = request.headers().topVia().branch();
        var serverTx = serverTransactions.get(branch);
        if (serverTx != null) {
            serverTx.processAck();
        }
        // ACK has no response
        return null;
    }

    private SipResponse handleBye(SipRequest request) {
        String callId = request.headers().callId();

        // Find and terminate the dialog
        Optional<SipDialog> dialog = dialogs.values().stream()
                .filter(d -> d.callId().equals(callId) && d.state() != DialogState.TERMINATED)
                .findFirst();

        if (dialog.isEmpty()) {
            return SipResponse.builder(SipStatus.CALL_TRANSACTION_DOES_NOT_EXIST)
                    .fromRequest(request)
                    .build();
        }

        dialog.get().terminate();
        dialogs.remove(dialog.get().dialogId());

        LOG.debug("BYE handled, dialog terminated: {}", dialog.get().dialogId());

        return SipResponse.builder(SipStatus.OK)
                .fromRequest(request)
                .build();
    }

    private SipResponse handleCancel(SipRequest request) {
        String branch = request.headers().topVia().branch();
        var serverTx = serverTransactions.get(branch);

        if (serverTx == null) {
            return SipResponse.builder(SipStatus.CALL_TRANSACTION_DOES_NOT_EXIST)
                    .fromRequest(request)
                    .build();
        }

        // Send 200 OK to CANCEL
        var cancelOk = SipResponse.builder(SipStatus.OK)
                .fromRequest(request)
                .build();

        // Send 487 Request Terminated to original INVITE
        var terminated = SipResponse.builder(SipStatus.REQUEST_TERMINATED)
                .fromRequest(serverTx.originalRequest())
                .build();
        serverTx.sendResponse(terminated);

        return cancelOk;
    }

    private SipResponse handleOptions(SipRequest request) {
        return SipResponse.builder(SipStatus.OK)
                .fromRequest(request)
                .allow("INVITE, ACK, BYE, CANCEL, OPTIONS, REGISTER")
                .header(SipHeaders.ACCEPT, "application/sdp")
                .header(SipHeaders.SUPPORTED, "")
                .build();
    }

    private SipResponse handleRegister(SipRequest request) {
        if (registrar != null) {
            return registrar.handleRegister(request);
        }
        return SipResponse.builder(SipStatus.METHOD_NOT_ALLOWED)
                .fromRequest(request)
                .build();
    }

    /**
     * Creates a BYE request to terminate a call.
     *
     * @param dialog the dialog to terminate
     * @return the BYE request
     * @since 0.1.0
     */
    public SipRequest createBye(SipDialog dialog) {
        return dialog.createRequest(SipMethod.BYE)
                .via("SIP/2.0/UDP " + extractHostPort(contactUri)
                        + ";branch=" + generateBranch())
                .contact("<" + contactUri + ">")
                .build();
    }

    /**
     * Creates an ACK request for a 2xx response to INVITE.
     *
     * @param invite   the original INVITE request
     * @param response the 2xx response
     * @return the ACK request
     * @since 0.1.0
     */
    public SipRequest createAck(SipRequest invite, SipResponse response) {
        String toHeader = response.headers().first(SipHeaders.TO)
                .orElse(invite.headers().first(SipHeaders.TO).orElse(""));

        return SipRequest.builder(SipMethod.ACK, invite.requestUri())
                .via("SIP/2.0/UDP " + extractHostPort(contactUri)
                        + ";branch=" + generateBranch())
                .from(invite.headers().first(SipHeaders.FROM).orElse(""))
                .to(toHeader)
                .callId(invite.headers().callId())
                .cseq(invite.headers().cseq().sequence(), SipMethod.ACK)
                .maxForwards(70)
                .build();
    }

    /**
     * Processes a response received for a client transaction.
     *
     * @param response the received response
     * @since 0.1.0
     */
    public void processResponse(SipResponse response) {
        String branch = response.headers().topVia().branch();
        var clientTx = clientTransactions.get(branch);
        if (clientTx != null) {
            clientTx.processResponse(response);

            // If 2xx to INVITE, create/confirm dialog
            if (clientTx.isInvite() && response.isSuccess()) {
                var dialog = SipDialog.createFromUac(clientTx.originalRequest(), response);
                dialogs.put(dialog.dialogId(), dialog);

                // Parse SDP answer if present
                if (response.hasBody()) {
                    String sdpBody = response.bodyAsString();
                    remoteSdp = SdpParser.parse(sdpBody);
                }

                LOG.debug("2xx received, UAC dialog created: {}", dialog.dialogId());
            }
        }
    }

    /**
     * Registers a client transaction.
     *
     * @param branch the branch ID
     * @param tx     the client transaction
     * @since 0.1.0
     */
    public void addClientTransaction(String branch, ClientTransaction tx) {
        clientTransactions.put(branch, tx);
    }

    /**
     * Returns the Address-of-Record.
     *
     * @return the AOR
     * @since 0.1.0
     */
    public String aor() { return aor; }

    /**
     * Returns the Contact URI.
     *
     * @return the contact URI
     * @since 0.1.0
     */
    public String contactUri() { return contactUri; }

    /**
     * Returns the active dialogs.
     *
     * @return unmodifiable map of dialogs
     * @since 0.1.0
     */
    public Map<String, SipDialog> dialogs() {
        return Map.copyOf(dialogs);
    }

    /**
     * Returns the negotiated remote SDP, if any.
     *
     * @return the remote SDP, or null
     * @since 0.1.0
     */
    public SessionDescription remoteSdp() {
        return remoteSdp;
    }

    /**
     * Returns the local SDP capabilities.
     *
     * @return the local SDP, or null
     * @since 0.1.0
     */
    public SessionDescription localSdp() {
        return localSdp;
    }

    /**
     * Extracts host:port from a SIP URI like "sip:user@host:port".
     */
    private static String extractHostPort(String sipUri) {
        String s = sipUri;
        int atIdx = s.indexOf('@');
        if (atIdx >= 0) {
            s = s.substring(atIdx + 1);
        } else {
            int colonIdx = s.indexOf(':');
            if (colonIdx >= 0) {
                s = s.substring(colonIdx + 1);
            }
        }
        // Remove any trailing parameters
        int semiIdx = s.indexOf(';');
        if (semiIdx >= 0) {
            s = s.substring(0, semiIdx);
        }
        int gtIdx = s.indexOf('>');
        if (gtIdx >= 0) {
            s = s.substring(0, gtIdx);
        }
        return s;
    }

    private String generateBranch() {
        return ViaHeader.BRANCH_MAGIC_COOKIE + UUID.randomUUID().toString().substring(0, 12);
    }

    private String generateTag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String generateCallId() {
        return UUID.randomUUID().toString() + "@" + extractHostPort(contactUri);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            dialogs.values().stream()
                    .filter(d -> d.state().isActive())
                    .forEach(SipDialog::terminate);
            dialogs.clear();
            clientTransactions.values().forEach(tx -> {
                if (!tx.isTerminated()) tx.terminate();
            });
            serverTransactions.values().forEach(tx -> {
                if (!tx.isTerminated()) tx.terminate();
            });
        }
    }

    @Override
    public String toString() {
        return "SipUserAgent[aor=" + aor + ", dialogs=" + dialogs.size() + "]";
    }
}
