package ssg.legoflow.network.telnet.negotiation;

import java.util.List;
/**
 * Handler for LINEMODE option subnegotiation (RFC 1143).
 *
 * <p>Linemode allows the server to specify how the client sends data:
 * line-at-a-time or character-at-a-time, with special character handling
 * and line editing support.
 *
 * <p>The handler tracks:
 * <ul>
 *   <li>Send mode (line vs character mode with flags)</li>
 *   <li>Output mode flags</li>
 *   <li>Special character (SLC) assignments</li>
 *   <li>Line buffer for editing in line mode</li>
 * </ul>
 *
 * <p>Known limitations:
 * <ul>
 *   <li>SLC values use default mappings; client may override via SLC command</li>
 *   <li>Line buffer has configurable max length (default 1024)</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class LinemodeHandler {

    // --- Commands ---
    /** LINEMODE IS — send current mode to peer. */
    public static final int IS = 0;
    /** LINEMODE SEND — peer requests current mode. */
    public static final int SEND = 1;
    /** LINEMODE START — start linemode. */
    public static final int START = 2;
    /** LINEMODE OFF — stop linemode. */
    public static final int OFF = 3;
    /** LINEMODE DEFAULT — reset to default mode. */
    public static final int DEFAULT = 4;
    /** LINEMODE SLC — special character values. */
    public static final int SLC = 5;

    // --- Send mode flags ---
    /** No special processing. */
    public static final int SEND_NORMAL = 0x00;
    /** Transparent to macros. */
    public static final int SEND_TRMAC = 0x01;
    /** No characters can change line state (SUPP_LOG interaction). */
    public static final int SEND_NOCHAR = 0x02;
    /** Echo locally. */
    public static final int SEND_ECHO = 0x04;
    /** Output processing by client. */
    public static final int SEND_OUTPUT = 0x08;

    // --- Output mode flags ---
    /** No output processing. */
    public static final int OUTPUT_NORMAL = 0x00;
    /** Suppress logging. */
    public static final int OUTPUT_SUPPRESS_LOG = 0x01;

    // --- SLC commands ---
    /** Set special character values. */
    public static final int SLC_SET = 0x00;
    /** SLC will be defaults (client sets to defaults). */
    public static final int SLC_DEFAULTS = 0x01;
    /** SLC will not change (client ignores changes). */
    public static final int SLC_WONT_CHANGE = 0x02;

    // --- SLC indices ---
    public static final int SLC_EC = 0;     // Erase Character
    public static final int SLC_EL = 1;     // Erase Line
    public static final int SLC_EA = 2;     // Erase Address (screen)
    public static final int SLC_BRK = 3;    // Break connection
    public static final int SLC_IP = 4;     // Interrupt process
    public static final int SLC_AO = 5;     // Abort output
    public static final int SLC_AYT = 6;    // Are you there
    public static final int SLC_EC2 = 7;    // Erase (next) Character
    public static final int SLC_SU = 8;     // Start up (resume output)
    public static final int SLC_SC = 9;     // Stop (suspend) process
    public static final int SLC_HT = 10;    // Hot key 1
    public static final int SLC_BS = 11;    // Hot key 2
    public static final int SLC_CR = 12;    // Hot key 3
    public static final int SLC_COUNT = 13;

    // SLC NOP value — client has no character to assign
    public static final int SLC_NOP = 0;

    private int sendMode;
    private int outputMode;
    private boolean suppressGoAhead;
    private final int[] slcValues;
    private final boolean[] slcAbsent;
    private final StringBuilder lineBuffer;
    private final int maxLineLength;
    private boolean active;
    private LinemodeCallback callback;

    @FunctionalInterface
    public interface LinemodeCallback {
        /**
         * Called when a complete line is submitted (CR received in line mode).
         *
         * @param line the submitted line content (without CR)
         */
        void onLineSubmitted(String line);
    }

    private LinemodeHandler(int maxLineLength) {
        this.sendMode = SEND_NORMAL;
        this.outputMode = OUTPUT_NORMAL;
        this.suppressGoAhead = true;
        this.slcValues = new int[SLC_COUNT];
        this.slcAbsent = new boolean[SLC_COUNT];
        this.maxLineLength = maxLineLength;
        this.lineBuffer = new StringBuilder(maxLineLength);
        this.active = false;
        resetSlics();
    }

    /** Create a new LinemodeHandler with defaults (1024 char line buffer). */
    public static LinemodeHandler create() {
        return new LinemodeHandler(1024);
    }

    /** Create a new LinemodeHandler with custom max line length. */
    public static LinemodeHandler create(int maxLineLength) {
        return new LinemodeHandler(maxLineLength);
    }

    /**
     * Set the callback for line submissions.
     */
    public LinemodeHandler onLineSubmitted(LinemodeCallback callback) {
        this.callback = callback;
        return this;
    }

    /** Check if linemode is currently active. */
    public boolean isActive() {
        return active;
    }

    /** Get the current send mode. */
    public int getSendMode() {
        return sendMode;
    }

    /** Get the current output mode. */
    public int getOutputMode() {
        return outputMode;
    }

    /** Check if SUPPRESS_GO_AHEAD is enabled. */
    public boolean isSuppressGoAhead() {
        return suppressGoAhead;
    }

    /** Get the current line buffer content. */
    public String getLineBuffer() {
        return lineBuffer.toString();
    }

    /** Get a specific SLC value (returns 0 if absent). */
    public int getSlicValue(int index) {
        if (index < 0 || index >= SLC_COUNT) return SLC_NOP;
        return slcValues[index];
    }

    /** Check if an SLC is absent (NOP). */
    public boolean isSlicAbsent(int index) {
        if (index < 0 || index >= SLC_COUNT) return true;
        return slcAbsent[index];
    }

    /**
     * Handle LINEMODE subnegotiation data.
     *
     * @param data the subnegotiation bytes
     * @return response bytes to send back, or null if no response needed
     */
    public byte[] handle(List<Integer> data) {
        if (data.isEmpty()) return null;

        int command = data.get(0) & 0xFF;

        return switch (command) {
            case SEND -> handleSend();
            case IS -> handleIs(data);
            case START -> handleStart();
            case OFF -> handleOff();
            case DEFAULT -> handleDefault();
            case SLC -> handleSlc(data);
            default -> null;
        };
    }

    /**
     * Handle SEND — peer requests our current mode.
     * Respond with IS <send-mode> <output-mode>.
     */
    private byte[] handleSend() {
        return new byte[]{(byte) IS, (byte) sendMode, (byte) outputMode};
    }

    /**
     * Handle IS — peer sends their mode.
     * Format: IS <send-mode> [<output-mode>] [<SLC data>]
     */
    private byte[] handleIs(List<Integer> data) {
        if (data.size() < 2) return null;

        this.sendMode = data.get(1) & 0xFF;

        if (data.size() >= 3) {
            this.outputMode = data.get(2) & 0xFF;
        }

        // Parse optional SLC data (3 bytes per SLC: index, absent-flag, value)
        int pos = Math.min(data.size(), 3);
        while (pos + 2 < data.size()) {
            int index = data.get(pos) & 0xFF;
            int absentFlag = data.get(pos + 1) & 0xFF;
            int value = data.get(pos + 2) & 0xFF;

            if (index < SLC_COUNT) {
                this.slcAbsent[index] = (absentFlag != 0);
                this.slcValues[index] = value;
            }
            pos += 3;
        }

        this.suppressGoAhead = (sendMode & SEND_ECHO) == 0;
        return null;
    }

    /** Handle START — begin linemode. */
    private byte[] handleStart() {
        this.active = true;
        this.lineBuffer.setLength(0);
        return null;
    }

    /** Handle OFF — stop linemode. */
    private byte[] handleOff() {
        this.active = false;
        if (!lineBuffer.isEmpty() && callback != null) {
            callback.onLineSubmitted(lineBuffer.toString());
            lineBuffer.setLength(0);
        }
        return null;
    }

    /** Handle DEFAULT — reset to defaults. */
    private byte[] handleDefault() {
        this.sendMode = SEND_NORMAL;
        this.outputMode = OUTPUT_NORMAL;
        this.suppressGoAhead = true;
        resetSlics();
        this.active = false;
        this.lineBuffer.setLength(0);

        return new byte[]{(byte) IS, (byte) sendMode, (byte) outputMode};
    }

    /**
     * Handle SLC — special character value assignment.
     * Format: SLC <command> <index> [<value>]
     */
    private byte[] handleSlc(List<Integer> data) {
        if (data.size() < 3) return null;

        int slcCommand = data.get(1) & 0xFF;
        int index = data.get(2) & 0xFF;

        if (index >= SLC_COUNT) return null;

        switch (slcCommand) {
            case SLC_SET -> {
                if (data.size() >= 4) {
                    this.slcValues[index] = data.get(3) & 0xFF;
                    this.slcAbsent[index] = (this.slcValues[index] == SLC_NOP);
                }
            }
            case SLC_DEFAULTS, SLC_WONT_CHANGE -> {
                this.slcAbsent[index] = true;
                this.slcValues[index] = SLC_NOP;
            }
            default -> {}
        }
        return null;
    }

    /**
     * Process a character in line mode.
     * In line mode, characters are accumulated in the buffer until CR.
     * Special characters (EC, EL, BS, etc.) are handled for line editing.
     *
     * @param ch the character to process
     * @return true if the character was consumed by line editing, false if it should be forwarded
     */
    public boolean processLineChar(char ch) {
        if (!active) return false;

        return switch (ch) {
            case '\r' -> {
                if (callback != null) {
                    callback.onLineSubmitted(lineBuffer.toString());
                }
                lineBuffer.setLength(0);
                yield true;
            }
            case 127, '\b' -> {
                if (lineBuffer.length() > 0) {
                    lineBuffer.setLength(lineBuffer.length() - 1);
                }
                yield true;
            }
            default -> {
                if (ch >= 32 && ch < 127) {
                    if (lineBuffer.length() < maxLineLength) {
                        lineBuffer.append(ch);
                    }
                    yield true;
                } else {
                    yield false;
                }
            }
        };
    }

    /**
     * Send a LINEMODE IS response with current state.
     */
    public byte[] buildIsResponse() {
        return new byte[]{(byte) IS, (byte) sendMode, (byte) outputMode};
    }

    /** Reset all SLC values to NOP. */
    private void resetSlics() {
        for (int i = 0; i < slcValues.length; i++) {
            slcValues[i] = SLC_NOP;
            slcAbsent[i] = true;
        }
    }
}
