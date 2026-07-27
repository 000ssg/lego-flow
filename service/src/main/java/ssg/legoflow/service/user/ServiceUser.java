package ssg.legoflow.service.user;

import java.util.Set;

public interface ServiceUser {

    String getId();

    String getName();

    UserType getType();

    Set<ServiceRole> getRoles();

    boolean hasRole(ServiceRole role);

    static ServiceUser anonymous() {
        return new SimpleServiceUser("anonymous", "Anonymous", UserType.ANONYMOUS, Set.of(ServiceRole.GUEST));
    }

    static ServiceUser shared(String name) {
        return new SimpleServiceUser("shared-" + name, name, UserType.SHARED, Set.of(ServiceRole.USER));
    }

    static ServiceUser exact(String id, String name, Set<ServiceRole> roles) {
        return new SimpleServiceUser(id, name, UserType.EXACT, roles);
    }
}
