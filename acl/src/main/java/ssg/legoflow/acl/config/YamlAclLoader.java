package ssg.legoflow.acl.config;

import ssg.legoflow.acl.AclDomainBuilder;
import ssg.legoflow.acl.model.AclDomain;
import ssg.legoflow.acl.model.AclRule;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/**
 * Load ACL domain from YAML.
 */
public final class YamlAclLoader {
    private YamlAclLoader() {}

    public static AclDomain load(InputStream is) throws IOException {
        return parse(new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    }

    public static AclDomain load(File file) throws IOException {
        try (var is = new FileInputStream(file)) { return load(is); }
    }

    public static AclDomain load(Path path) throws IOException {
        return load(path.toFile());
    }

    static AclDomain parse(String yaml) {
        var doc = parseYaml(yaml);
        var builder = new AclDomainBuilder();

        // Domain config
        var domain = (LinkedHashMap) doc.get("domain");
        builder.name((String) domain.get("name"));
        builder.keySize((int) domain.getOrDefault("keySize", 2048));
        builder.validityYears((long) domain.getOrDefault("validityYears", 10));
        if (Boolean.TRUE.equals(domain.get("withCerts"))) builder.withCerts();

        // Roles
        for (var r : (List) domain.get("roles")) {
            var role = (LinkedHashMap) r;
            var name = (String) role.get("name");
            var perms = toSet(role.get("permissions"));
            builder.role(name, perms.isEmpty() ? null : perms);
        }

        // Groups
        for (var g : (List) domain.get("groups")) {
            var group = (LinkedHashMap) g;
            var name = (String) group.get("name");
            var roles = toSet(group.get("roles"));
            var members = toSet(group.get("members"));
            builder.group(name, roles, members, null);
        }

        // ACLs
        for (var a : (List) domain.get("acls")) {
            var acl = (LinkedHashMap) a;
            var name = (String) acl.get("name");
            var uri = (String) acl.get("uri");
            var desc = acl.get("description") != null ? acl.get("description").toString() : null;
            var control = AclRule.Control.valueOf(acl.getOrDefault("control", "ALLOW").toString());
            var level = AclRule.AccessLevel.valueOf(acl.getOrDefault("accessLevel", "READ").toString());
            var aRoles = toSet(acl.get("roles"));
            if (aRoles.isEmpty()) builder.acl(name, uri, desc, control, level);
            else builder.acl(name, uri, desc, control, level, aRoles);
        }

        // Users
        for (var u : (List) domain.get("users")) {
            var user = (LinkedHashMap) u;
            var username = (String) user.get("username");
            var password = (String) user.get("password");
            var roles = toSet(user.get("roles"));
            var groups = toSet(user.get("groups"));
            builder.user(username, password, roles, groups, null);
        }

        return builder.build();
    }

    private static Set<String> toSet(Object o) {
        if (o == null) return Set.of();
        if (o instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(java.util.stream.Collectors.toSet());
        }
        if (o instanceof Set<?>) {
            return ((Set<?>) o).stream().map(Object::toString).collect(java.util.stream.Collectors.toSet());
        }
        return Set.of(o.toString());
    }

    // Minimal YAML parser — handles the subset we need
    static Map<String, Object> parseYaml(String yaml) {
        var result = new LinkedHashMap<String, Object>();
        var lines = yaml.lines().toList();
        parseBlock(result, lines, 0, 0);
        return result;
    }

    static void parseBlock(Map<String, Object> parent, List<String> lines, int start, int baseIndent) {
        for (int i = start; i < lines.size(); i++) {
            var line = lines.get(i);
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            var indent = indentLevel(line);
            if (indent < baseIndent) break;
            if (indent > baseIndent) continue;

            var valuePart = line.trim();
            // List item
            if (valuePart.startsWith("- ")) {
                var itemContent = valuePart.substring(2).trim();
                if (itemContent.contains(": ")) {
                    var map = new LinkedHashMap<String, Object>();
                    var kv = itemContent.split(": ", 2);
                    map.put(kv[0].trim(), parseScalar(kv[1].trim()));
                    // Check for more keys on same line
                    var rest = kv[1].trim();
                    if (rest.isEmpty()) {
                        // Multi-line map item
                        int nextIndent = indent + 2;
                        for (int j = i + 1; j < lines.size(); j++) {
                            var next = lines.get(j);
                            if (next.trim().isEmpty()) continue;
                            var ni = indentLevel(next);
                            if (ni < nextIndent) break;
                            if (ni >= nextIndent && next.trim().contains(": ")) {
                                var nk = next.trim().split(": ", 2);
                                map.put(nk[0].trim(), parseScalar(nk[1].trim()));
                            }
                        }
                        if (!parent.containsKey(lastKey(parent))) {
                            parent.put(lastKey(parent) != null ? lastKey(parent) : "__list__", new ArrayList<>());
                        }
                        ((List) parent.values().stream().filter(v -> v instanceof List).findFirst().orElseGet(() -> {
                            var l = new ArrayList<Object>();
                            parent.put("__list__", l);
                            return l;
                        })).add(map);
                    } else {
                        // Inline key: value after -
                        var list = new ArrayList<>();
                        list.add(map);
                        parent.put("__inline_list__", list);
                    }
                }
            }
            // Key: value
            else if (valuePart.contains(": ")) {
                var kv = valuePart.split(": ", 2);
                var key = kv[0].trim();
                var val = kv[1].trim();
                if (val.isEmpty()) {
                    // Nested block or list
                    if (i + 1 < lines.size()) {
                        var nextLine = lines.get(i + 1);
                        if (!nextLine.trim().isEmpty() && indentLevel(nextLine) > indent) {
                            var childIndent = indentLevel(nextLine);
                            if (nextLine.trim().startsWith("- ")) {
                                var list = new ArrayList<>();
                                parent.put(key, list);
                                parseList(list, lines, i + 1, childIndent);
                            } else {
                                var child = new LinkedHashMap<String, Object>();
                                parent.put(key, child);
                                parseBlock(child, lines, i + 1, childIndent);
                            }
                        } else {
                            parent.put(key, null);
                        }
                    }
                } else {
                    parent.put(key, parseScalar(val));
                }
            }
        }
    }

    static void parseList(List<Object> list, List<String> lines, int start, int baseIndent) {
        for (int i = start; i < lines.size(); i++) {
            var line = lines.get(i);
            if (line.trim().isEmpty()) continue;
            var indent = indentLevel(line);
            if (indent < baseIndent) break;
            if (indent == baseIndent && line.trim().startsWith("- ")) {
                var item = line.trim().substring(2).trim();
                if (item.contains(": ")) {
                    var map = new LinkedHashMap<String, Object>();
                    var kv = item.split(": ", 2);
                    map.put(kv[0].trim(), parseScalar(kv[1].trim()));
                    list.add(map);
                    // Parse continuation lines
                    for (int j = i + 1; j < lines.size(); j++) {
                        var next = lines.get(j);
                        if (next.trim().isEmpty()) continue;
                        var ni = indentLevel(next);
                        if (ni <= baseIndent) break;
                        if (next.trim().contains(": ")) {
                            var nk = next.trim().split(": ", 2);
                            map.put(nk[0].trim(), parseScalar(nk[1].trim()));
                        }
                    }
                } else {
                    list.add(parseScalar(item));
                }
            }
        }
    }

    static int indentLevel(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }

    static Object parseScalar(String s) {
        if (s.isEmpty()) return s;
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))
            return s.substring(1, s.length() - 1);
        if (s.equalsIgnoreCase("true")) return true;
        if (s.equalsIgnoreCase("false")) return false;
        if (s.equalsIgnoreCase("null") || s.equalsIgnoreCase("~")) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e2) {
                return s;
            }
        }
    }

    static String lastKey(Map<String, Object> map) {
        if (map.isEmpty()) return null;
        return map.keySet().stream().findFirst().orElse(null);
    }
}
