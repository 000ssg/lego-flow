package ssg.legoflow.network.telnet.base;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Telnet protocol parser — a byte-level state machine per RFC 854.
 *
 * <p>The parser accepts bytes from the wire and dispatches events
 * to a {@link TelnetListener}. It handles IAC escaping (doubled IAC
 * bytes), negotiation commands, and subnegotiations.
 *
 * <p>This class is <b>not thread-safe</b>; use from a single reader thread.
 *
 * @since 0.2.0
 */
public class TelnetParser {

    private static final int IAC  = 255;
    private static final int SE   = 240;
    private static final int WILL = 251;
    private static final int WONT = 252;
    private static final int DO   = 253;
    private static final int DONT = 254;
    private static final int SB   = 250;

    private final TelnetListener listener;
    private ParserState state = ParserState.DATA;

    // Negotiation command awaiting option byte
    private int pendingCmd;

    // Subnegotiation state
    private int sbOption;
    private final List<Integer> sbData;
    private boolean sbAfterIac; // true after seeing IAC within SB

    // Data accumulator
    private final List<Integer> dataAccum;

    public TelnetParser(TelnetListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.pendingCmd = -1;
        this.sbOption = -1;
        this.sbData = new ArrayList<>();
        this.sbAfterIac = false;
        this.dataAccum = new ArrayList<>();
    }

    /** Feed a byte array. */
    public void feed(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        for (byte b : bytes) {
            process(b & 0xFF);
        }
    }

    /** Feed a single byte (0–255). */
    public void feed(int b) {
        process(b & 0xFF);
    }

    /** Flush accumulated data to listener. */
    public void flush() {
        if (!dataAccum.isEmpty()) {
            listener.onData(List.copyOf(dataAccum));
            dataAccum.clear();
        }
    }

    private void process(int b) {
        switch (state) {
            case DATA -> inData(b);
            case COMMAND -> inCommand(b);
            case NEGOTIATE -> inNegotiate(b);
            case SUBNEGOTIATION -> inSub(b);
        }
    }

    /* DATA — normal transfer */
    private void inData(int b) {
        if (b == IAC) {
            flush();
            state = ParserState.COMMAND;
        } else {
            dataAccum.add(b);
        }
    }

    /* COMMAND — byte after IAC */
    private void inCommand(int b) {
        if (b == IAC) {
            // IAC IAC → literal 255
            state = ParserState.DATA;
            dataAccum.add(IAC);
            return;
        }
        if (b == WILL || b == WONT || b == DO || b == DONT) {
            pendingCmd = b;
            state = ParserState.NEGOTIATE;
            return;
        }
        if (b == SB) {
            sbOption = -1;
            sbData.clear();
            sbAfterIac = false;
            state = ParserState.SUBNEGOTIATION;
            return;
        }
        // Single-byte command
        state = ParserState.DATA;
        TelnetCommand cmd = TelnetCommand.fromCode(b);
        if (cmd != null) {
            listener.onCommand(cmd);
        }
    }

    /* NEGOTIATE — option byte after WILL/WONT/DO/DONT */
    private void inNegotiate(int b) {
        state = ParserState.DATA;
        TelnetCommand cmd = TelnetCommand.fromCode(pendingCmd);
        if (cmd != null) {
            listener.onNegotiate(cmd, b);
        }
        pendingCmd = -1;
    }

    /* SUBNEGOTIATION — inside SB...SE */
    private void inSub(int b) {
        if (sbAfterIac) {
            // We just saw IAC; now handle the byte after it
            sbAfterIac = false;
            if (b == SE) {
                // End of subnegotiation
                listener.onSubnegotiation(sbOption, List.copyOf(sbData));
                sbOption = -1;
                sbData.clear();
                state = ParserState.DATA;
            } else if (b == IAC) {
                // IAC IAC inside SB → literal 255 in sub data
                sbData.add(IAC);
            } else {
                // Any other byte after IAC (not SE, not IAC) is data
                sbData.add(b);
            }
            return;
        }

        if (b == IAC) {
            sbAfterIac = true;
            return;
        }

        if (sbOption == -1) {
            // First byte after SB is the option code
            sbOption = b;
        } else {
            sbData.add(b);
        }
    }

    /** Reset to initial state. */
    public void reset() {
        state = ParserState.DATA;
        pendingCmd = -1;
        sbOption = -1;
        sbData.clear();
        sbAfterIac = false;
        dataAccum.clear();
    }

    /** Current parser state. */
    public ParserState state() {
        return state;
    }
}
