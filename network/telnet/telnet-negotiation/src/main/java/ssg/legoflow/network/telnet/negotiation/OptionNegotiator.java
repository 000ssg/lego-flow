package ssg.legoflow.network.telnet.negotiation;

import ssg.legoflow.network.telnet.base.TelnetCommand;
import java.util.Map;
/**
 * Manages Telnet option negotiation state (RFC 855).
 *
 * <p>Tracks the local and remote state for each option and provides
 * decision hooks for responding to negotiation commands. Subclasses
 * or custom implementations control policy (which options to accept/reject).
 *
 * @since 0.2.0
 */
public class OptionNegotiator {

    private final Map<Integer, OptionRecord> options;

    public OptionNegotiator() {
        this.options = new java.util.IdentityHashMap<>();
    }

    /**
     * Get or create the record for an option.
     */
    public OptionRecord getOption(int code) {
        return options.computeIfAbsent(code, OptionRecord::new);
    }

    /**
     * Process a received negotiation command and determine the response.
     *
     * @param command WILL, WONT, DO, or DONT from the peer
     * @param option  the option code
     * @return the response command to send back
     */
    public TelnetCommand negotiate(TelnetCommand command, int option) {
        OptionRecord rec = getOption(option);
        return switch (command) {
            case WILL -> rec.onWill(this);
            case WONT -> rec.onWont(this);
            case DO -> rec.onDo(this);
            case DONT -> rec.onDont(this);
            default -> throw new IllegalArgumentException("Not a negotiation command: " + command);
        };
    }

    /**
     * Decide whether to accept the remote's request to enable an option (WILL).
     * Override to customize policy. Default: accept all.
     *
     * @return DO to accept, DONT to reject
     */
    public TelnetCommand shouldAcceptRemote(int option) {
        return TelnetCommand.DO;
    }

    /**
     * Decide whether to keep requesting option enable after WONT.
     * Override to customize. Default: stop requesting.
     *
     * @return DO to keep requesting, DONT to give up
     */
    public TelnetCommand shouldKeepRequesting(int option) {
        return TelnetCommand.DONT;
    }

    /**
     * Decide whether to enable an option locally after DO from peer.
     * Override to customize. Default: enable.
     *
     * @return WILL to enable, WONT to refuse
     */
    public TelnetCommand shouldEnableLocal(int option) {
        return TelnetCommand.WILL;
    }

    /**
     * Decide whether to keep an option locally enabled after DONT from peer.
     * Override to customize. Default: disable.
     *
     * @return WILL to keep enabled, WONT to disable
     */
    public TelnetCommand shouldKeepLocalEnabled(int option) {
        return TelnetCommand.WONT;
    }

    /**
     * Check if an option is fully enabled.
     */
    public boolean isOptionEnabled(int option) {
        OptionRecord rec = options.get(option);
        return rec != null && rec.isEnabled();
    }
}
