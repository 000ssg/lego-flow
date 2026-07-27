package ssg.legoflow.service.user;

import java.util.Set;

public record SimpleServiceUser(String id, String name, UserType type, Set<ServiceRole> roles) implements ServiceUser {

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UserType getType() {
        return type;
    }

    @Override
    public Set<ServiceRole> getRoles() {
        return roles;
    }

    @Override
    public boolean hasRole(ServiceRole role) {
        return roles.contains(role);
    }
}
