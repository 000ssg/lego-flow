package ssg.legoflow.network.terminals.base.escape;

import java.util.Collections;
import java.util.List;
/**
 * Parsed parameters from a CSI (Control Sequence Introducer) escape sequence.
 *
 * <p>CSI sequences have the format: ESC [ {params} {intermediates} {final}
 * where params are semicolon-separated numeric values (defaulting to 0).
 * Subparameters (colon-separated) are flattened into the same parameter list.
 *
 * @since 0.2.0
 */
public final class CSIParams {

    private static final List<Integer> EMPTY = Collections.emptyList();

    private final List<Integer> params;
    private final String intermediates;
    private final char finalByte;

    public CSIParams(List<Integer> params, String intermediates, char finalByte) {
        this.params = params != null ? List.copyOf(params) : EMPTY;
        this.intermediates = intermediates != null ? intermediates : "";
        this.finalByte = finalByte;
    }

    /** List of numeric parameters (subparameters flattened). */
    public List<Integer> params() { return params; }

    /** Intermediate bytes (if any). */
    public String intermediates() { return intermediates; }

    /** Final byte that terminates the sequence. */
    public char finalByte() { return finalByte; }

    /**
     * Get the value at the given index, or the default if not present.
     * Parameters are 0-indexed.
     */
    public int get(int index, int defaultValue) {
        if (index < 0 || index >= params.size()) return defaultValue;
        return params.get(index);
    }

    /**
     * Get the value at the given index, or 0 if not present.
     */
    public int get(int index) {
        return get(index, 0);
    }

    /** Number of parameters. */
    public int size() { return params.size(); }

    /** True if there are no parameters. */
    public boolean isEmpty() { return params.isEmpty(); }

    @Override
    public String toString() {
        return "CSI[" + String.join(";", params.stream().map(String::valueOf).toList()) +
                "] " + intermediates + " '" + finalByte + "'";
    }
}
