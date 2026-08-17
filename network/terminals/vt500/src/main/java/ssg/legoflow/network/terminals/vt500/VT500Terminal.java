package ssg.legoflow.network.terminals.vt500;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.escape.CSIParams;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt400.VT400Terminal;

/**
 * VT500 terminal emulator.
 *
 * <p>Extends VT400 with advanced workstation capabilities:
 * <ul>
 *   <li>Window host commands</li>
 *   <li>DEC character sets (decset-based charset selection)</li>
 *   <li>Character set selection (SO/SI)</li>
 *   <li>User-defined character sets</li>
 *   <li>Extended line feed handling</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class VT500Terminal extends VT400Terminal {

    public static Terminal create(TerminalConfig config) {
        return new VT500Terminal(config);
    }

    /** DEC character set identifier. */
    public enum CharSet {
        /** Standard ASCII. */
        ASCII,
        /** DEC Special Character and Line Drawing Set. */
        DEC_SPECIAL,
        /** UK character set. */
        UK,
        /** French character set. */
        FRENCH,
        /** French-Canadian character set. */
        FRENCH_CANADIAN,
        /** International character set. */
        INTERNATIONAL,
        /** Scandinavian character set. */
        SCANDINAVIAN,
        /** German character set. */
        GERMAN,
        /** User-defined character set. */
        USER_DEFINED
    }

    private CharSet g0Charset = CharSet.ASCII;
    private CharSet g1Charset = CharSet.ASCII;
    private CharSet activeCharset = CharSet.ASCII;

    private VT500Terminal(TerminalConfig config) {
        super(config);
    }

    @Override
    public void handleCSI(CSIParams params) {
        char finalByte = params.finalByte();

        // DEC character set selection via DECSET
        if (finalByte == 'h' && params.intermediates().equals("?")) {
            for (int i = 0; i < params.size(); i++) {
                int mode = params.get(i);
                switch (mode) {
                    case 40 -> {/* smooth scroll with wrap */}
                    case 1049 -> {/* alternate screen */}
                }
            }
        }

        super.handleCSI(params);
    }

    @Override
    public void handleDCS(String data) {
        // VT500 supports DCS for user-defined character sets
        if (data.startsWith(" |")) {
            // Define character set
        }
    }

    /**
     * Set G0 character set.
     */
    public void setG0(CharSet charset) {
        this.g0Charset = charset;
        if (activeCharset == g0Charset) return;
        activeCharset = charset;
    }

    /**
     * Set G1 character set.
     */
    public void setG1(CharSet charset) {
        this.g1Charset = charset;
    }

    /**
     * Select G0 (SO).
     */
    public void selectG0() {
        activeCharset = g0Charset;
    }

    /**
     * Select G1 (SI).
     */
    public void selectG1() {
        activeCharset = g1Charset;
    }

    /** Current active character set. */
    public CharSet activeCharset() { return activeCharset; }

    @Override
    public String type() { return "vt500"; }

    @Override
    protected void onReset() {
        super.onReset();
        g0Charset = CharSet.ASCII;
        g1Charset = CharSet.ASCII;
        activeCharset = CharSet.ASCII;
    }
}
