package ssg.legoflow.http.transfer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ByteRangeHandler {

    public record ByteRange(long start, long end) {
        public long length() { return end - start + 1; }
    }

    public static List<ByteRange> parseRangeHeader(String rangeHeader, long totalSize) {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return List.of();
        }
        var specs = rangeHeader.substring(6).split(",");
        var ranges = new ArrayList<ByteRange>();
        for (var spec : specs) {
            spec = spec.trim();
            if (spec.startsWith("-")) {
                long suffixLength = Long.parseLong(spec.substring(1));
                ranges.add(new ByteRange(totalSize - suffixLength, totalSize - 1));
            } else if (spec.endsWith("-")) {
                long start = Long.parseLong(spec.substring(0, spec.length() - 1));
                ranges.add(new ByteRange(start, totalSize - 1));
            } else {
                var parts = spec.split("-");
                ranges.add(new ByteRange(Long.parseLong(parts[0]), Long.parseLong(parts[1])));
            }
        }
        return ranges;
    }

    public static String formatContentRange(ByteRange range, long totalSize) {
        return "bytes " + range.start() + "-" + range.end() + "/" + totalSize;
    }

    public static ByteBuffer extractRange(ByteBuffer content, ByteRange range) {
        int start = (int) range.start();
        int length = (int) range.length();
        var dup = content.duplicate();
        dup.position(dup.position() + start);
        dup.limit(dup.position() + length);
        var result = ByteBuffer.allocate(length);
        result.put(dup);
        result.flip();
        return result;
    }

    public static boolean isRangeSatisfiable(ByteRange range, long totalSize) {
        return range.start() >= 0 && range.start() < totalSize && range.end() < totalSize;
    }
}
