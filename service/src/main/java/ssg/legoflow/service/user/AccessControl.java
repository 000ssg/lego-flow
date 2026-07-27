package ssg.legoflow.service.user;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AccessControl {

    private final Map<String, Set<ServiceRole>> operationRoles = new ConcurrentHashMap<>();

    public void requireRoles(String operation, Set<ServiceRole> roles) {
        operationRoles.put(operation, Set.copyOf(roles));
    }

    public void requireRole(String operation, ServiceRole role) {
        operationRoles.put(operation, Set.of(role));
    }

    public boolean isAllowed(ServiceUser user, String operation) {
        var required = operationRoles.get(operation);
        if (required == null || required.isEmpty()) {
            return true;
        }
        return user.getRoles().stream().anyMatch(required::contains);
    }

    public void checkPermission(ServiceUser user, String operation) {
        if (!isAllowed(user, operation)) {
            throw new AccessDeniedException(user.getName(), operation);
        }
    }

    public static class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String user, String operation) {
            super("User '" + user + "' denied access to operation '" + operation + "'");
        }
    }
}
