package ssg.legoflow.media.sip.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.media.sip.header.AddressHeader;
import ssg.legoflow.media.sip.header.SipHeaders;
import ssg.legoflow.media.sip.protocol.SipMethod;
import ssg.legoflow.media.sip.protocol.SipRequest;
import ssg.legoflow.media.sip.protocol.SipResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SIP dialog per RFC 3261 section 12.
 *
 * <p>A dialog is identified by Call-ID, local tag, and remote tag.
 * It tracks the dialog state (early, confirmed, terminated),
 * route set, remote target, and CSeq numbers.
 *
 * @since 1.0.0
 */
public final class SipDialog {

    private static final Logger LOG = LoggerFactory.getLogger(SipDialog.class);

    private final String callId;
    private final String localTag;
    private final String remoteTag;
    private final boolean isUac;
    private volatile DialogState state;
    private final AtomicLong localCSeq;
    private volatile long remoteCSeq;
    private volatile String localUri;
    private volatile String remoteUri;
    private volatile String remoteTarget;
    private final List<String> routeSet;

    /**
     * Creates a new dialog.
     *
     * @param callId    the Call-ID
     * @param localTag  the local tag
     * @param remoteTag the remote tag
     * @param isUac     true if this is the UAC side
     * @param localUri  the local URI
     * @param remoteUri the remote URI
     * @since 1.0.0
     */
    public SipDialog(String callId, String localTag, String remoteTag,
                     boolean isUac, String localUri, String remoteUri) {
        this.callId = Objects.requireNonNull(callId, "callId");
        this.localTag = Objects.requireNonNull(localTag, "localTag");
        this.remoteTag = remoteTag != null ? remoteTag : "";
        this.isUac = isUac;
        this.localUri = Objects.requireNonNull(localUri, "localUri");
        this.remoteUri = Objects.requireNonNull(remoteUri, "remoteUri");
        this.state = DialogState.EARLY;
        this.localCSeq = new AtomicLong(0);
        this.remoteCSeq = 0;
        this.routeSet = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Creates a UAC dialog from an INVITE request and its response.
     *
     * @param request  the INVITE request
     * @param response the response (1xx or 2xx with To tag)
     * @return the created dialog
     * @since 1.0.0
     */
    public static SipDialog createFromUac(SipRequest request, SipResponse response) {
        SipHeaders reqHeaders = request.headers();
        SipHeaders resHeaders = response.headers();

        String callId = reqHeaders.callId();
        AddressHeader from = reqHeaders.from();
        AddressHeader to = resHeaders.to();

        String localTag = from.tag().orElse("");
        String remoteTag = to.tag().orElse("");

        var dialog = new SipDialog(
                callId, localTag, remoteTag, true,
                from.uri().format(), to.uri().format()
        );

        // Set initial CSeq from request
        dialog.localCSeq.set(reqHeaders.cseq().sequence());

        // Build route set from Record-Route headers (in reverse order for UAC)
        List<String> recordRoutes = resHeaders.all(SipHeaders.RECORD_ROUTE);
        List<String> reversed = new ArrayList<>(recordRoutes);
        Collections.reverse(reversed);
        dialog.routeSet.addAll(reversed);

        // Set remote target from Contact header
        resHeaders.first(SipHeaders.CONTACT).ifPresent(c -> {
            AddressHeader contact = AddressHeader.parse(c);
            dialog.remoteTarget = contact.uri().format();
        });

        // Determine initial state
        if (response.isSuccess()) {
            dialog.state = DialogState.CONFIRMED;
        } else {
            dialog.state = DialogState.EARLY;
        }

        LOG.debug("Created UAC dialog: callId={}, localTag={}, remoteTag={}, state={}",
                callId, localTag, remoteTag, dialog.state);
        return dialog;
    }

    /**
     * Creates a UAS dialog from a received INVITE request.
     *
     * @param request   the received INVITE request
     * @param localTag  the local tag to use
     * @return the created dialog
     * @since 1.0.0
     */
    public static SipDialog createFromUas(SipRequest request, String localTag) {
        SipHeaders reqHeaders = request.headers();

        String callId = reqHeaders.callId();
        AddressHeader from = reqHeaders.from();
        AddressHeader to = reqHeaders.to();

        String remoteTag = from.tag().orElse("");

        var dialog = new SipDialog(
                callId, localTag, remoteTag, false,
                to.uri().format(), from.uri().format()
        );

        // Build route set from Record-Route headers (in order for UAS)
        dialog.routeSet.addAll(reqHeaders.all(SipHeaders.RECORD_ROUTE));

        // Set remote target from Contact header
        reqHeaders.first(SipHeaders.CONTACT).ifPresent(c -> {
            AddressHeader contact = AddressHeader.parse(c);
            dialog.remoteTarget = contact.uri().format();
        });

        // Set remote CSeq
        dialog.remoteCSeq = reqHeaders.cseq().sequence();

        dialog.state = DialogState.EARLY;

        LOG.debug("Created UAS dialog: callId={}, localTag={}, remoteTag={}, state={}",
                callId, localTag, remoteTag, dialog.state);
        return dialog;
    }

    /**
     * Confirms the dialog (transitions from EARLY to CONFIRMED).
     *
     * @since 1.0.0
     */
    public void confirm() {
        if (state == DialogState.EARLY) {
            state = DialogState.CONFIRMED;
            LOG.debug("Dialog confirmed: {}", dialogId());
        }
    }

    /**
     * Confirms the dialog with a response that may update remote tag and target.
     *
     * @param response the 2xx response
     * @since 1.0.0
     */
    public void confirm(SipResponse response) {
        // Update remote target from Contact
        response.headers().first(SipHeaders.CONTACT).ifPresent(c -> {
            AddressHeader contact = AddressHeader.parse(c);
            remoteTarget = contact.uri().format();
        });
        confirm();
    }

    /**
     * Terminates the dialog.
     *
     * @since 1.0.0
     */
    public void terminate() {
        state = DialogState.TERMINATED;
        LOG.debug("Dialog terminated: {}", dialogId());
    }

    /**
     * Returns the next local CSeq number for in-dialog requests.
     *
     * @return the next CSeq
     * @since 1.0.0
     */
    public long nextLocalCSeq() {
        return localCSeq.incrementAndGet();
    }

    /**
     * Validates and updates the remote CSeq for an incoming request.
     *
     * @param cseq the CSeq from the incoming request
     * @return true if the CSeq is valid (greater than current remote CSeq)
     * @since 1.0.0
     */
    public boolean validateRemoteCSeq(long cseq) {
        if (cseq > remoteCSeq) {
            remoteCSeq = cseq;
            return true;
        }
        return cseq == remoteCSeq; // Allow retransmissions
    }

    /** Returns the dialog ID string (callId + localTag + remoteTag). */
    public String dialogId() {
        return callId + ":" + localTag + ":" + remoteTag;
    }

    /** Returns the Call-ID. */
    public String callId() { return callId; }

    /** Returns the local tag. */
    public String localTag() { return localTag; }

    /** Returns the remote tag. */
    public String remoteTag() { return remoteTag; }

    /** Returns true if this is the UAC side. */
    public boolean isUac() { return isUac; }

    /** Returns the dialog state. */
    public DialogState state() { return state; }

    /** Returns the local URI. */
    public String localUri() { return localUri; }

    /** Returns the remote URI. */
    public String remoteUri() { return remoteUri; }

    /** Returns the remote target (from Contact header). */
    public String remoteTarget() { return remoteTarget; }

    /** Returns the route set. */
    public List<String> routeSet() { return Collections.unmodifiableList(routeSet); }

    /** Returns the current local CSeq. */
    public long localCSeq() { return localCSeq.get(); }

    /** Returns the current remote CSeq. */
    public long remoteCSeq() { return remoteCSeq; }

    /**
     * Creates an in-dialog request.
     *
     * @param method the request method
     * @return a request builder pre-populated with dialog information
     * @since 1.0.0
     */
    public SipRequest.Builder createRequest(SipMethod method) {
        String target = remoteTarget != null ? remoteTarget : remoteUri;

        var builder = SipRequest.builder(method, target)
                .from(formatLocalAddress())
                .to(formatRemoteAddress())
                .callId(callId)
                .cseq(nextLocalCSeq(), method)
                .maxForwards(70);

        // Add Route headers
        for (String route : routeSet) {
            builder.addHeader(SipHeaders.ROUTE, route);
        }

        return builder;
    }

    private String formatLocalAddress() {
        return "<" + localUri + ">;tag=" + localTag;
    }

    private String formatRemoteAddress() {
        var sb = new StringBuilder();
        sb.append('<').append(remoteUri).append('>');
        if (!remoteTag.isEmpty()) {
            sb.append(";tag=").append(remoteTag);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "SipDialog[" + dialogId() + ", state=" + state + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SipDialog other)) return false;
        return callId.equals(other.callId)
                && localTag.equals(other.localTag)
                && remoteTag.equals(other.remoteTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callId, localTag, remoteTag);
    }
}
