package ssg.legoflow.database.redis.command;

import ssg.legoflow.database.redis.protocol.RespType;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Parsed command arguments extracted from a RESP array.
 *
 * <p>Provides convenient typed access to command arguments by index,
 * with methods for string, integer, and double conversions.
 *
 * @since 0.1.0
 */
public final class CommandArgs {

    private final String commandName;
    private final List<RespType> raw;

    /**
     * Creates command args from a RESP array.
     *
     * @param elements the array elements (first element is the command name)
     */
    public CommandArgs(List<RespType> elements) {
        if (elements == null || elements.isEmpty()) {
            throw new IllegalArgumentException("Command array must not be empty");
        }
        this.raw = Collections.unmodifiableList(elements);
        this.commandName = getString(0).toUpperCase();
    }

    /**
     * Returns the uppercase command name.
     *
     * @return command name
     */
    public String commandName() {
        return commandName;
    }

    /**
     * Returns the total number of elements (including the command name).
     *
     * @return element count
     */
    public int size() {
        return raw.size();
    }

    /**
     * Returns the raw RESP type at the given index.
     *
     * @param index zero-based index
     * @return the RESP type
     */
    public RespType get(int index) {
        return raw.get(index);
    }

    /**
     * Returns the argument at the given index as a string.
     *
     * @param index zero-based index
     * @return string value
     */
    public String getString(int index) {
        RespType element = raw.get(index);
        return switch (element) {
            case RespType.BulkString bs -> bs.asString();
            case RespType.SimpleString ss -> ss.value();
            case RespType.Integer i -> Long.toString(i.value());
            default -> element.toString();
        };
    }

    /**
     * Returns the argument at the given index as a byte array.
     *
     * @param index zero-based index
     * @return byte array
     */
    public byte[] getBytes(int index) {
        RespType element = raw.get(index);
        if (element instanceof RespType.BulkString bs && bs.value() != null) {
            return bs.value();
        }
        return getString(index).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns the argument at the given index as a long.
     *
     * @param index zero-based index
     * @return long value
     * @throws NumberFormatException if the value is not a valid long
     */
    public long getLong(int index) {
        RespType element = raw.get(index);
        if (element instanceof RespType.Integer i) {
            return i.value();
        }
        return Long.parseLong(getString(index));
    }

    /**
     * Returns the argument at the given index as an int.
     *
     * @param index zero-based index
     * @return int value
     * @throws NumberFormatException if the value is not a valid int
     */
    public int getInt(int index) {
        return (int) getLong(index);
    }

    /**
     * Returns the argument at the given index as a double.
     *
     * @param index zero-based index
     * @return double value
     * @throws NumberFormatException if the value is not a valid double
     */
    public double getDouble(int index) {
        return Double.parseDouble(getString(index));
    }

    /**
     * Returns all raw elements.
     *
     * @return unmodifiable list of RESP types
     */
    public List<RespType> elements() {
        return raw;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("CommandArgs[");
        sb.append(commandName);
        for (int i = 1; i < raw.size(); i++) {
            sb.append(' ').append(getString(i));
        }
        sb.append(']');
        return sb.toString();
    }
}
