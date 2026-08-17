package ssg.legoflow.network.terminals.base.escape;

import java.util.ArrayList;
import java.util.List;

/**
 * State machine parser for ANSI/DEC escape sequences.
 *
 * <p>Recognizes the following sequence types:
 * <ul>
 *   <li>CSI (ESC [) — Control Sequence Introducer</li>
 *   <li>DCS (ESC P) — Device Control String</li>
 *   <li>OSC (ESC ]) — Operating System Command</li>
 *   <li>APC (ESC _) — Application Program Command</li>
 *   <li>PM  (ESC ^) — Privacy Message</li>
 *   <li>String terminators: ST (ESC \), BEL</li>
 * </ul>
 *
 * <p>CSI parameters use ';' as the primary separator and ':' as the subparameter
 * separator. Both are treated equivalently for terminal emulation purposes, producing
 * a flat list of integer parameters. Unset parameters use -1 as sentinel during parsing;
 * they are resolved to 0 before emission.
 *
 * <p>The parser emits parsed sequences to a {@link SequenceHandler}.
 * Unknown or incomplete sequences are silently discarded on reset.
 *
 * @since 0.2.0
 */
public final class EscapeParser {

    /** Sentinel for unset CSI parameters. */
    private static final int UNSET = -1;

    /** Parser states. */
    enum State {
        GROUND,    // Normal character data
        CSI,       // Inside CSI sequence
        DCS,       // Inside DCS sequence
        OSC,       // Inside OSC sequence
        APC,       // Inside APC sequence
        PM,        // Inside PM sequence
        ESC,       // Just saw ESC, waiting for intro byte
        ST_ESCAPE, // Saw ESC inside a string, expecting '\' for ST
    }

    /**
     * Callback interface for parsed sequences.
     */
    @FunctionalInterface
    public interface SequenceHandler {
        /** Handle a CSI sequence. */
        void handleCSI(CSIParams params);

        /**
         * Handle a printable character in GROUND state.
         *
         * @param codepoint the Unicode code point (typically 0x20–0x7E for ASCII)
         */
        default void handleChar(int codepoint) {}

        /** Handle a DCS string. */
        default void handleDCS(String data) {}

        /** Handle an OSC string (title, etc.). */
        default void handleOSC(String data) {}

        /** Handle an APC string. */
        default void handleAPC(String data) {}

        /** Handle a PM string. */
        default void handlePM(String data) {}
    }

    private final SequenceHandler handler;
    private State state = State.GROUND;
    private final StringBuilder buffer;
    private final List<Integer> csiParams;
    /** The string state we returned to after seeing ESC in a string. */
    private State priorStringState = State.GROUND;

    public EscapeParser(SequenceHandler handler) {
        this.handler = handler;
        this.buffer = new StringBuilder(64);
        this.csiParams = new ArrayList<>();
    }

    /**
     * Process a single byte from the input stream.
     *
     * @param b the byte value (0-255)
     */
    public void feed(int b) {
        switch (state) {
            case GROUND -> handleGround(b);
            case ESC -> handleEsc(b);
            case CSI -> handleCsi(b);
            case DCS -> handleString(b, State.DCS);
            case OSC -> handleString(b, State.OSC);
            case APC -> handleString(b, State.APC);
            case PM -> handleString(b, State.PM);
            case ST_ESCAPE -> handleStringTerminator(b);
        }
    }

    /** Process a batch of bytes. */
    public void feed(byte[] data) {
        for (byte b : data) {
            feed(b & 0xFF);
        }
    }

    /**
     * Process a string of characters.
     *
     * @param text the input text
     */
    public void feed(String text) {
        for (int i = 0; i < text.length(); i++) {
            feed(text.charAt(i));
        }
    }

    private void handleGround(int b) {
        if (b == 0x1B) { // ESC
            state = State.ESC;
        } else if (b >= 0x20 && b <= 0x7E) {
            // Printable ASCII character — emit to handler
            handler.handleChar(b);
        }
        // Control characters (0x00-0x1F except ESC, and 0x7F DEL)
        // are silently passed through; the terminal handles them.
    }

    private void handleEsc(int b) {
        switch (b) {
            case '[' -> {
                state = State.CSI;
                buffer.setLength(0);
                csiParams.clear();
            }
            case 'P' -> {
                state = State.DCS;
                buffer.setLength(0);
            }
            case ']' -> {
                state = State.OSC;
                buffer.setLength(0);
            }
            case '_' -> {
                state = State.APC;
                buffer.setLength(0);
            }
            case '^' -> {
                state = State.PM;
                buffer.setLength(0);
            }
            case '\\' -> {
                // ST (String Terminator): ESC \
                // If we came from a string state, terminate it
                if (priorStringState != State.GROUND) {
                    terminateString(priorStringState);
                    priorStringState = State.GROUND;
                }
                state = State.GROUND;
            }
            default -> {
                // Single-byte control after ESC (e.g., ESC M = RS, ESC D = IND)
                state = State.GROUND;
            }
        }
    }

    private void handleCsi(int b) {
        if (b >= '0' && b <= '9') {
            // Digit: accumulate into current parameter
            if (csiParams.isEmpty()) {
                csiParams.add(b - '0');
            } else {
                int last = csiParams.get(csiParams.size() - 1);
                if (last == UNSET) {
                    csiParams.set(csiParams.size() - 1, b - '0');
                } else {
                    csiParams.set(csiParams.size() - 1, last * 10 + (b - '0'));
                }
            }
        } else if (b == ';' || b == ':') {
            // Parameter or subparameter separator.
            // When the list is empty (e.g., CSI ;5H), we need two UNSETs:
            // one for the leading default parameter, one for the new slot.
            if (csiParams.isEmpty()) {
                csiParams.add(UNSET);
            }
            csiParams.add(UNSET);
        } else if (b >= ' ' && b <= '~'
                && b != ';'
                && b != ':'
                && !(b >= '$' && b <= '/')   // DEC intermediate range 1 ($-/)
                && !(b >= '<' && b <= '?')) { // DEC intermediate range 2 (<, =, >, ?)
            // Final byte — resolve UNSET sentinels to 0 and emit
            List<Integer> resolved = new ArrayList<>(csiParams.size());
            for (Integer p : csiParams) {
                resolved.add(p == UNSET ? 0 : p);
            }
            String intermediates = buffer.toString();
            handler.handleCSI(new CSIParams(resolved, intermediates, (char) b));
            state = State.GROUND;
            buffer.setLength(0);
            csiParams.clear();
        } else if ((b >= '$' && b <= '/')        // DEC intermediate range 1
                || (b >= '<' && b <= '?')) {     // DEC intermediate range 2
            buffer.append((char) b);
        } else {
            // Unknown byte in CSI — discard
            state = State.GROUND;
            buffer.setLength(0);
            csiParams.clear();
        }
    }

    private void handleString(int b, State stringState) {
        if (b == 0x1B) {
            // ESC — potential start of ST (ESC \)
            priorStringState = stringState;
            state = State.ST_ESCAPE;
        } else if (b == 0x07) {
            // BEL = ST for OSC
            terminateString(stringState);
            state = State.GROUND;
        } else if (b < 0x20 && b != 0x07 && b != 0x1B) {
            // Other control characters in string — discard
            state = State.GROUND;
            buffer.setLength(0);
        } else {
            buffer.append((char) b);
        }
    }

    private void handleStringTerminator(int b) {
        if (b == '\\') {
            // Complete ST: ESC \
            terminateString(priorStringState);
            priorStringState = State.GROUND;
            state = State.GROUND;
        } else {
            // Not backslash — ESC starts a new sequence
            state = State.ESC;
            handleEsc(b);
        }
    }

    private void terminateString(State stringState) {
        String data = buffer.toString();
        switch (stringState) {
            case DCS -> handler.handleDCS(data);
            case OSC -> handler.handleOSC(data);
            case APC -> handler.handleAPC(data);
            case PM -> handler.handlePM(data);
        }
        buffer.setLength(0);
    }

    /** Reset the parser to GROUND state, discarding any partial sequence. */
    public void reset() {
        state = State.GROUND;
        priorStringState = State.GROUND;
        buffer.setLength(0);
        csiParams.clear();
    }

    /** Current parser state. */
    public State currentState() { return state; }

    /** True if the parser is in a partial (non-ground) state. */
    public boolean isMidSequence() { return state != State.GROUND; }
}
