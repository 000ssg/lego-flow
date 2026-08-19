package ssg.legoflow.network.terminals.base.escape;

import java.util.ArrayList;
import java.util.Collections;
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
 *   <li>Single-byte ESC sequences: ESC followed by a single letter (IND, RI, HTS, etc.)</li>
 *   <li>VT52-style ESC # n sequences (reverse video, bold, etc.)</li>
 *   <li>DEC charset selection: ESC ( letter / ESC ) letter (G0/G1 assignment)</li>
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
        GROUND,       // Normal character data
        CSI,          // Inside CSI sequence
        DCS,          // Inside DCS sequence
        OSC,          // Inside OSC sequence
        APC,          // Inside APC sequence
        PM,           // Inside PM sequence
        ESC,          // Just saw ESC, waiting for intro byte
        ST_ESCAPE,    // Saw ESC inside a string, expecting '\' for ST
        ESC_SEQUENCE, // ESC followed by # or other 2-byte intro
        ESC_CHARSET,  // ESC ( or ) — waiting for charset selector letter
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

        /**
         * Handle a single-byte ESC sequence (ESC + letter).
         * Common sequences: D=IND, M=RI, H=HTS, 7=DECSC, 8=DECRC, Z=DECKPAM
         *
         * @param letter the byte following ESC (e.g., 'D', 'M', 'H', '7', '8')
         */
        default void handleEscSequence(char letter) {}

        /**
         * Handle a VT52-style ESC # n sequence.
         * Common sequences: #3=reversed video, #8=bold, #4=underline, #6=double-height
         *
         * @param digit the digit or character after ESC # (e.g., '3', '8', '4')
         */
        default void handleEscHash(char digit) {}

        /**
         * Handle DEC charset selection (ESC ( letter or ESC ) letter).
         *
         * <p>Used to assign character sets to G0/G1:
         * <ul>
         *   <li>ESC ( B — set G0 to ASCII</li>
         *   <li>ESC ( 0 — set G0 to DEC Special</li>
         *   <li>ESC ) K — set G1 to French</li>
         * </ul>
         *
         * @param g0 true if ESC ( (G0 assignment), false if ESC ) (G1 assignment)
         * @param selector the charset descriptor character (e.g., 'B', '0', 'K')
         */
        default void handleEscCharset(boolean g0, char selector) {}

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
    /** True if current charset intermediate is '(' (G0), false if ')' (G1). */
    private boolean charsetG0;

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
            case ESC_SEQUENCE -> handleEscSequence(b);
            case ESC_CHARSET -> handleEscCharset(b);
        }
    }

    /**
     * Process a batch of bytes.
     */
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
        // Control characters (0x00-0x09, 0x0B-0x0C, 0x0E-0x1F, 0x7F, 0x80+)
        // are silently ignored in GROUND state (handled by the terminal)
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
                if (priorStringState != State.GROUND) {
                    terminateString(priorStringState);
                    priorStringState = State.GROUND;
                }
                state = State.GROUND;
            }
            case '#' -> {
                state = State.ESC_SEQUENCE;
            }
            case '(' -> {
                // DEC charset selection for G0
                charsetG0 = true;
                state = State.ESC_CHARSET;
            }
            case ')' -> {
                // DEC charset selection for G1
                charsetG0 = false;
                state = State.ESC_CHARSET;
            }
            default -> {
                // Single-byte control after ESC (e.g., ESC M = RS, ESC D = IND,
                // ESC H = HTS, ESC 7/8 = DECSC/DECRC, ESC Z = DECKPAM)
                if ((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z')
                        || (b >= '0' && b <= '9')) {
                    handler.handleEscSequence((char) b);
                }
                state = State.GROUND;
            }
        }
    }

    private void handleEscSequence(int b) {
        // ESC # was already consumed; now handle the second byte
        char c = (char) b;
        if ((b >= '0' && b <= '9') || (b >= 'A' && b <= 'Z')) {
            handler.handleEscHash(c);
        }
        state = State.GROUND;
    }

    private void handleEscCharset(int b) {
        // Handle the charset selector letter after ESC ( or ESC )
        char selector = (char) b;
        if (selector == 0 || (selector >= 'A' && selector <= 'Z') || (selector >= 'a' && selector <= 'z')
                || (selector >= '0' && selector <= '9')) {
            handler.handleEscCharset(charsetG0, selector);
        }
        state = State.GROUND;
    }

    private void handleCsi(int b) {
        if (b >= '0' && b <= '9') {
            // Digit: accumulate into current parameter.
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
            if (csiParams.isEmpty()) {
                csiParams.add(UNSET);
            }
            csiParams.add(UNSET);
        } else if (b >= '@' && b <= '~'
                && !(b >= '<' && b <= '?')) { // final byte: 0x40-0x7E excluding DEC intermediates
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
        } else if (b >= ' ' && b <= '/'           // CSI intermediate 0x20-0x2F
                || (b >= '<' && b <= '?')) {     // DEC intermediate range 2
            buffer.append((char) b);
        } else if (b == 0x1B) {
            // ESC while in CSI — emit pending digit-only sequence (CSI 7/8) and start new sequence
            if (csiParams.size() == 1 && buffer.isEmpty() && csiParams.get(0) >= 0 && csiParams.get(0) <= 9) {
                char digitByte = (char) ('0' + csiParams.get(0));
                handler.handleCSI(new CSIParams(Collections.emptyList(), "", digitByte));
            }
            buffer.setLength(0);
            csiParams.clear();
            state = State.ESC;
            // Don't call handleEsc here — ESC transition is complete.
            // The next byte will be dispatched to handleEsc by the main feed loop.
            return;
        } else {
            // Other unknown byte in CSI — emit pending digit-only sequence and discard
            if (csiParams.size() == 1 && buffer.isEmpty() && csiParams.get(0) >= 0 && csiParams.get(0) <= 9) {
                char digitByte = (char) ('0' + csiParams.get(0));
                handler.handleCSI(new CSIParams(Collections.emptyList(), "", digitByte));
            }
            state = State.GROUND;
            buffer.setLength(0);
            csiParams.clear();
        }
    }

    private void handleString(int b, State stringState) {
        if (b == 0x1B) {
            priorStringState = stringState;
            state = State.ST_ESCAPE;
        } else if (b == 0x07) {
            terminateString(stringState);
            state = State.GROUND;
        } else if (b < 0x20 && b != 0x07 && b != 0x1B) {
            state = State.GROUND;
            buffer.setLength(0);
        } else {
            buffer.append((char) b);
        }
    }

    private void handleStringTerminator(int b) {
        if (b == '\\') {
            terminateString(priorStringState);
            priorStringState = State.GROUND;
            state = State.GROUND;
        } else {
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
