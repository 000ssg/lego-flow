package ssg.legoflow.acl.config;

import ssg.legoflow.acl.AclDomainBuilder;
import ssg.legoflow.acl.model.AclDomain;
import ssg.legoflow.acl.model.AclRule;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/**
 * Load ACL domain from JSON.
 *
 * <p>Schema (minimal dependencies — uses only JDK JSON parsing):
 * <pre>
 * {
 *   "domain": { "name": "Test", "keySize": 2048, "validityYears": 10, "withCerts": true },
 *   "roles": [ { "name": "admin", "permissions": ["*"] } ],
 *   "groups": [ { "name": "admins", "roles": ["admin"], "members": [] } ],
 *   "acls": [ { "name": "admin-all", "uri": "**", "control": "ALLOW", "accessLevel": "ALL", "roles": ["admin"] } ],
 *   "users": [ { "username": "admin", "password": "admin", "roles": ["admin"], "groups": ["admins"] } ]
 * }
 * </pre>
 */
public final class JsonAclLoader {
    private JsonAclLoader() {}

    public static AclDomain load(InputStream is) throws IOException {
        var json = new BufferedReader(new InputStreamReader(is)).lines().collect(java.util.stream.Collectors.joining("\n"));
        return parseJson(json);
    }

    public static AclDomain load(File file) throws IOException {
        try (var is = new FileInputStream(file)) { return load(is); }
    }

    public static AclDomain load(Path path) throws IOException {
        return load(path.toFile());
    }

    /** Parse a JSON object into an AclDomain. Uses a simple manual parser to avoid external deps. */
    static AclDomain parseJson(String json) {
        var obj = parseObject(json.trim());
        var domainObj = (List) obj.get("domain");
        var builder = new AclDomainBuilder();
        builder.name((String) domainObj.get(0));
        var ds = (List) domainObj.get(1);
        for (var d : ds) {
            var kv = (List) d;
            String k = (String) kv.get(0);
            Object v = kv.get(1);
            if ("keySize".equals(k)) builder.keySize((int) v);
            else if ("validityYears".equals(k)) builder.validityYears((long) v);
            else if ("withCerts".equals(k) && (boolean) v) builder.withCerts();
        }

        // Roles
        for (var r : (List) obj.get("roles")) {
            var rn = (List) r;
            String name = (String) rn.get(0);
            var permsList = (List) rn.get(1);
            var perms = listToSet(permsList);
            builder.role(name, perms.isEmpty() ? null : perms);
        }

        // Groups
        for (var g : (List) obj.get("groups")) {
            var gn = (List) g;
            String name = (String) gn.get(0);
            var roles = listToSet((List) gn.get(1));
            var members = listToSet((List) gn.get(2));
            builder.group(name, roles, members, null);
        }

        // ACLs
        for (var a : (List) obj.get("acls")) {
            var an = (List) a;
            String name = (String) an.get(0);
            var props = (List) an.get(1);
            String uri = null, desc = null, control = "ALLOW", level = "READ";
            var aRoles = new ArrayList<String>();
            for (var p : props) {
                var kv = (List) p;
                switch ((String) kv.get(0)) {
                    case "uri" -> uri = (String) kv.get(1);
                    case "description" -> desc = (String) kv.get(1);
                    case "control" -> control = (String) kv.get(1);
                    case "accessLevel" -> level = (String) kv.get(1);
                    case "roles" -> aRoles.addAll((List) kv.get(1));
                }
            }
            builder.acl(name, uri, desc, AclRule.Control.valueOf(control), AclRule.AccessLevel.valueOf(level), Set.copyOf(aRoles));
        }

        // Users
        for (var u : (List) obj.get("users")) {
            var un = (List) u;
            String username = (String) un.get(0);
            var props = (List) un.get(1);
            String password = null;
            var roles = new ArrayList<String>();
            var groups = new ArrayList<String>();
            for (var p : props) {
                var kv = (List) p;
                switch ((String) kv.get(0)) {
                    case "password" -> password = (String) kv.get(1);
                    case "roles" -> roles.addAll((List) kv.get(1));
                    case "groups" -> groups.addAll((List) kv.get(1));
                }
            }
            builder.user(username, password, Set.copyOf(roles), Set.copyOf(groups), null);
        }

        return builder.build();
    }

    private static List parseArray(String s) {
        var list = new ArrayList<Object>();
        var trimmed = s.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            var inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (!inner.isEmpty()) {
                for (var item : tokenize(inner)) {
                    list.add(parseValue(item));
                }
            }
        }
        return list;
    }

    private static Map<String, Object> parseObject(String s) {
        var map = new LinkedHashMap<String, Object>();
        var trimmed = s.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            var inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (!inner.isEmpty()) {
                for (var item : tokenize(inner)) {
                    var colon = item.indexOf(':');
                    if (colon > 0) {
                        String key = item.substring(0, colon).trim().replaceAll("^\"|\"$", "");
                        String value = item.substring(colon + 1).trim();
                        map.put(key, parseValue(value));
                    }
                }
            }
        }
        return map;
    }

    private static Object parseValue(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        if (s.equals("true")) return true;
        if (s.equals("false")) return false;
        if (s.startsWith("[") || s.startsWith("{")) {
            if (s.startsWith("[")) return parseArray(s);
            return parseObject(s);
        }
        try { return Long.parseLong(s); } catch (NumberFormatException e) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e2) {
                return s;
            }
        }
    }

    private static List<String> tokenize(String s) {
        var tokens = new ArrayList<String>();
        int depth = 0;
        var current = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[' || c == '{') { depth++; current.append(c); }
            else if (c == ']' || c == '}') { depth--; current.append(c); }
            else if (c == ',' && depth == 0) {
                tokens.add(current.toString());
                current = new StringBuilder();
            } else { current.append(c); }
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }

    private static Set<String> listToSet(List<?> list) {
        return list.stream().map(Object::toString).collect(java.util.stream.Collectors.toSet());
    }
}
