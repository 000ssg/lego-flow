package ssg.legoflow.network.telnet.negotiation;

import ssg.legoflow.network.telnet.base.TelnetCommand;

/**
 * Tracks the state of a single Telnet option for both sides of a connection.
 *
 * <p>Per RFC 855, each side independently tracks local and remote state.
 * The record provides state transition logic for WILL/WONT/DO/DONT.
 */
public class OptionRecord {

    private final int optionCode;
    private OptionState localState;
    private OptionState remoteState;

    public OptionRecord(int optionCode) {
        this(optionCode, OptionState.OFF, OptionState.OFF);
    }

    public OptionRecord(int optionCode, OptionState local, OptionState remote) {
        this.optionCode = optionCode;
        this.localState = local;
        this.remoteState = remote;
    }

    /** The option code (0–255). */
    public int optionCode() { return optionCode; }

    /** Our (local) state for this option. */
    public OptionState localState() { return localState; }

    /** The remote peer's state for this option. */
    public OptionState remoteState() { return remoteState; }

    /**
     * Check if this option is effectively enabled (both sides agree ON).
     */
    public boolean isEnabled() {
        return localState == OptionState.ON && remoteState == OptionState.ON;
    }

    /**
     * Process a received WILL command from the peer.
     * Returns the appropriate response command (DO or DONT).
     */
    public TelnetCommand onWill(OptionNegotiator negotiator) {
        TelnetCommand response = negotiator.shouldAcceptRemote(optionCode());
        switch (remoteState) {
            case OFF, OFF_DEF -> {
                if (response == TelnetCommand.DO) {
                    remoteState = OptionState.ON_DEF;
                } else {
                    remoteState = OptionState.OFF;
                }
            }
            case ON_DEF -> {
                if (response == TelnetCommand.DO) {
                    remoteState = OptionState.ON;
                } else {
                    remoteState = OptionState.OFF;
                }
            }
            case ON -> {
                // Already ON; reconfirm if DO, else stay ON
                if (response == TelnetCommand.DONT) {
                    remoteState = OptionState.OFF_DEF;
                }
            }
        }
        return response;
    }

    /**
     * Process a received WONT command from the peer.
     * Returns the appropriate response command (DO or DONT).
     */
    public TelnetCommand onWont(OptionNegotiator negotiator) {
        TelnetCommand response = negotiator.shouldKeepRequesting(optionCode());
        switch (remoteState) {
            case ON, ON_DEF -> {
                if (response == TelnetCommand.DONT) {
                    remoteState = OptionState.OFF;
                } else {
                    remoteState = OptionState.ON_DEF;
                }
            }
            case OFF, OFF_DEF -> {
                if (response == TelnetCommand.DO) {
                    remoteState = OptionState.ON_DEF;
                } else {
                    remoteState = OptionState.OFF;
                }
            }
        }
        return response;
    }

    /**
     * Process a received DO command from the peer.
     * Returns the appropriate response command (WILL or WONT).
     */
    public TelnetCommand onDo(OptionNegotiator negotiator) {
        TelnetCommand response = negotiator.shouldEnableLocal(optionCode());
        switch (localState) {
            case OFF, OFF_DEF -> {
                if (response == TelnetCommand.WILL) {
                    localState = OptionState.ON_DEF;
                } else {
                    localState = OptionState.OFF;
                }
            }
            case ON_DEF -> {
                if (response == TelnetCommand.WILL) {
                    localState = OptionState.ON;
                } else {
                    localState = OptionState.OFF;
                }
            }
            case ON -> {
                if (response == TelnetCommand.WONT) {
                    localState = OptionState.OFF_DEF;
                }
            }
        }
        return response;
    }

    /**
     * Process a received DONT command from the peer.
     * Returns the appropriate response command (WILL or WONT).
     */
    public TelnetCommand onDont(OptionNegotiator negotiator) {
        TelnetCommand response = negotiator.shouldKeepLocalEnabled(optionCode());
        switch (localState) {
            case ON, ON_DEF -> {
                if (response == TelnetCommand.WONT) {
                    localState = OptionState.OFF;
                } else {
                    localState = OptionState.ON_DEF;
                }
            }
            case OFF, OFF_DEF -> {
                if (response == TelnetCommand.WILL) {
                    localState = OptionState.ON_DEF;
                } else {
                    localState = OptionState.OFF;
                }
            }
        }
        return response;
    }

    @Override
    public String toString() {
        return "OptionRecord[" + optionCode + " local=" + localState
                + " remote=" + remoteState + "]";
    }
}
