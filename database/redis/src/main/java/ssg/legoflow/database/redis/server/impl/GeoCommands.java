package ssg.legoflow.database.redis.server.impl;

import ssg.legoflow.database.redis.command.CommandArgs;
import ssg.legoflow.database.redis.command.CommandRegistry;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.ClientConnection;
import ssg.legoflow.database.redis.server.Database;
import java.util.*;
/**
 * Implements Redis geospatial commands: GEOADD, GEODIST, GEOPOS, GEOSEARCH.
 *
 * <p>Under the hood, geospatial data is stored in sorted sets using a 52-bit
 * geohash as the score. This matches Redis's approach of leveraging sorted sets
 * for geospatial indexing.
 *
 * @since 0.1.0
 */
public final class GeoCommands {

    private static final RespType OK = new RespType.SimpleString("OK");
    private static final double EARTH_RADIUS_M = 6372797.560856;

    private GeoCommands() {}

    /**
     * Registers all geo commands with the given registry.
     *
     * @param registry the command registry
     */
    public static void register(CommandRegistry registry) {
        registry.register("GEOADD", GeoCommands::geoadd);
        registry.register("GEODIST", GeoCommands::geodist);
        registry.register("GEOPOS", GeoCommands::geopos);
        registry.register("GEOSEARCH", GeoCommands::geosearch);
    }

    // ---- Geohash encoding/decoding ----

    /**
     * Encodes longitude and latitude into a 52-bit geohash stored as a double score.
     *
     * @param longitude the longitude (-180 to 180)
     * @param latitude  the latitude (-90 to 90)
     * @return the geohash as a double suitable for sorted set score
     */
    static double encode(double longitude, double latitude) {
        long lonBits = encodeRange(longitude, -180.0, 180.0, 26);
        long latBits = encodeRange(latitude, -90.0, 90.0, 26);
        // Interleave: longitude in even bits, latitude in odd bits
        long hash = interleave(lonBits, latBits);
        return (double) hash;
    }

    /**
     * Decodes a geohash score back to longitude and latitude.
     *
     * @param score the geohash score
     * @return array of [longitude, latitude]
     */
    static double[] decode(double score) {
        long hash = (long) score;
        long lonBits = 0, latBits = 0;
        for (int i = 0; i < 26; i++) {
            lonBits |= ((hash >> (2 * i + 1)) & 1L) << i;
            latBits |= ((hash >> (2 * i)) & 1L) << i;
        }
        double longitude = decodeRange(lonBits, -180.0, 180.0, 26);
        double latitude = decodeRange(latBits, -90.0, 90.0, 26);
        return new double[]{longitude, latitude};
    }

    private static long encodeRange(double value, double min, double max, int bits) {
        double range = max - min;
        double normalized = (value - min) / range;
        return (long) (normalized * ((1L << bits) - 1));
    }

    private static double decodeRange(long bits, double min, double max, int numBits) {
        double range = max - min;
        return min + (bits + 0.5) / (1L << numBits) * range;
    }

    private static long interleave(long x, long y) {
        long result = 0;
        for (int i = 0; i < 26; i++) {
            result |= ((x >> i) & 1L) << (2 * i + 1);
            result |= ((y >> i) & 1L) << (2 * i);
        }
        return result;
    }

    // ---- Haversine distance ----

    /**
     * Calculates the Haversine distance between two points in meters.
     */
    static double haversineDistance(double lon1, double lat1, double lon2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_M * c;
    }

    /**
     * Converts distance in meters to the specified unit.
     */
    private static double convertFromMeters(double meters, String unit) {
        return switch (unit.toLowerCase()) {
            case "km" -> meters / 1000.0;
            case "mi" -> meters / 1609.344;
            case "ft" -> meters / 0.3048;
            default -> meters; // "m"
        };
    }

    /**
     * Converts distance from the specified unit to meters.
     */
    private static double convertToMeters(double value, String unit) {
        return switch (unit.toLowerCase()) {
            case "km" -> value * 1000.0;
            case "mi" -> value * 1609.344;
            case "ft" -> value * 0.3048;
            default -> value; // "m"
        };
    }

    // ---- Command implementations ----

    /**
     * GEOADD key [NX|XX] longitude latitude member [longitude latitude member ...]
     */
    private static RespType geoadd(CommandArgs args, ClientConnection client) {
        if (args.size() < 5) {
            return new RespType.Error("ERR", "wrong number of arguments for 'geoadd' command");
        }
        Database db = client.database();
        String key = args.getString(1);

        boolean nx = false, xx = false;
        int i = 2;
        while (i < args.size()) {
            String opt = args.getString(i).toUpperCase();
            if ("NX".equals(opt)) {
                nx = true;
                i++;
            } else if ("XX".equals(opt)) {
                xx = true;
                i++;
            } else {
                break;
            }
        }

        NavigableMap<Double, Set<String>> zset = db.getOrCreateZSet(key);
        Map<String, Double> scores = db.getOrCreateZSetScores(key);
        int added = 0;

        while (i + 2 < args.size()) {
            double longitude = args.getDouble(i);
            double latitude = args.getDouble(i + 1);
            String member = args.getString(i + 2);
            i += 3;

            // Validate ranges
            if (longitude < -180 || longitude > 180 || latitude < -85.05112878 || latitude > 85.05112878) {
                return new RespType.Error("ERR",
                        "invalid longitude,latitude pair " + longitude + "," + latitude);
            }

            double score = encode(longitude, latitude);
            Double existing = scores.get(member);

            if (existing != null) {
                if (nx) continue;
                // Update existing member
                Set<String> oldBucket = zset.get(existing);
                if (oldBucket != null) {
                    oldBucket.remove(member);
                    if (oldBucket.isEmpty()) zset.remove(existing);
                }
                zset.computeIfAbsent(score, k -> new LinkedHashSet<>()).add(member);
                scores.put(member, score);
            } else {
                if (xx) continue;
                zset.computeIfAbsent(score, k -> new LinkedHashSet<>()).add(member);
                scores.put(member, score);
                added++;
            }
        }

        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(added);
    }

    /**
     * GEODIST key member1 member2 [m|km|mi|ft]
     */
    private static RespType geodist(CommandArgs args, ClientConnection client) {
        if (args.size() < 4) {
            return new RespType.Error("ERR", "wrong number of arguments for 'geodist' command");
        }
        Database db = client.database();
        String key = args.getString(1);
        String member1 = args.getString(2);
        String member2 = args.getString(3);
        String unit = args.size() > 4 ? args.getString(4) : "m";

        Map<String, Double> scores = db.getZSetScores(key);
        if (scores == null) {
            return new RespType.BulkString(null);
        }

        Double score1 = scores.get(member1);
        Double score2 = scores.get(member2);
        if (score1 == null || score2 == null) {
            return new RespType.BulkString(null);
        }

        double[] pos1 = decode(score1);
        double[] pos2 = decode(score2);
        double distMeters = haversineDistance(pos1[0], pos1[1], pos2[0], pos2[1]);
        double dist = convertFromMeters(distMeters, unit);

        String formatted = String.format("%.4f", dist);
        return new RespType.BulkString(formatted.getBytes());
    }

    /**
     * GEOPOS key member [member ...]
     */
    private static RespType geopos(CommandArgs args, ClientConnection client) {
        if (args.size() < 3) {
            return new RespType.Error("ERR", "wrong number of arguments for 'geopos' command");
        }
        Database db = client.database();
        String key = args.getString(1);

        Map<String, Double> scores = db.getZSetScores(key);
        List<RespType> results = new ArrayList<>();

        for (int i = 2; i < args.size(); i++) {
            String member = args.getString(i);
            if (scores == null || !scores.containsKey(member)) {
                results.add(new RespType.Array(null));
            } else {
                double[] pos = decode(scores.get(member));
                List<RespType> coords = List.of(
                        new RespType.BulkString(String.format("%.6f", pos[0]).getBytes()),
                        new RespType.BulkString(String.format("%.6f", pos[1]).getBytes())
                );
                results.add(new RespType.Array(coords));
            }
        }

        return new RespType.Array(results);
    }

    /**
     * GEOSEARCH key FROMMEMBER member|FROMLONLAT lon lat BYRADIUS radius m|km|mi|ft [COUNT count] [ASC|DESC]
     */
    private static RespType geosearch(CommandArgs args, ClientConnection client) {
        if (args.size() < 6) {
            return new RespType.Error("ERR", "wrong number of arguments for 'geosearch' command");
        }
        Database db = client.database();
        String key = args.getString(1);

        Map<String, Double> scores = db.getZSetScores(key);
        if (scores == null || scores.isEmpty()) {
            return new RespType.Array(List.of());
        }

        double centerLon, centerLat;
        int i = 2;

        // Parse center
        String centerType = args.getString(i).toUpperCase();
        if ("FROMMEMBER".equals(centerType)) {
            i++;
            String member = args.getString(i);
            i++;
            Double memberScore = scores.get(member);
            if (memberScore == null) {
                return new RespType.Error("ERR", "could not decode requested zset member");
            }
            double[] pos = decode(memberScore);
            centerLon = pos[0];
            centerLat = pos[1];
        } else if ("FROMLONLAT".equals(centerType)) {
            i++;
            centerLon = args.getDouble(i);
            i++;
            centerLat = args.getDouble(i);
            i++;
        } else {
            return new RespType.Error("ERR", "syntax error");
        }

        // Parse radius
        double radiusMeters = 0;
        if (i < args.size() && "BYRADIUS".equals(args.getString(i).toUpperCase())) {
            i++;
            double radius = args.getDouble(i);
            i++;
            String unit = args.getString(i);
            i++;
            radiusMeters = convertToMeters(radius, unit);
        } else {
            return new RespType.Error("ERR", "syntax error, BYRADIUS expected");
        }

        // Parse optional COUNT and ASC/DESC
        int count = Integer.MAX_VALUE;
        boolean ascending = true;
        while (i < args.size()) {
            String opt = args.getString(i).toUpperCase();
            switch (opt) {
                case "COUNT" -> {
                    i++;
                    count = args.getInt(i);
                    i++;
                }
                case "ASC" -> {
                    ascending = true;
                    i++;
                }
                case "DESC" -> {
                    ascending = false;
                    i++;
                }
                default -> i++;
            }
        }

        // Find matching members
        record MemberDist(String member, double distance) {}
        List<MemberDist> matches = new ArrayList<>();

        for (var entry : scores.entrySet()) {
            double[] pos = decode(entry.getValue());
            double dist = haversineDistance(centerLon, centerLat, pos[0], pos[1]);
            if (dist <= radiusMeters) {
                matches.add(new MemberDist(entry.getKey(), dist));
            }
        }

        // Sort by distance
        if (ascending) {
            matches.sort(Comparator.comparingDouble(MemberDist::distance));
        } else {
            matches.sort(Comparator.comparingDouble(MemberDist::distance).reversed());
        }

        // Apply COUNT
        if (matches.size() > count) {
            matches = matches.subList(0, count);
        }

        // Build result
        List<RespType> results = new ArrayList<>();
        for (MemberDist md : matches) {
            results.add(new RespType.BulkString(md.member().getBytes()));
        }

        return new RespType.Array(results);
    }
}
