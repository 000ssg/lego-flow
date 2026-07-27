package ssg.legoflow.service.user;

public record ServiceRole(String name) {

    public static final ServiceRole ADMIN = new ServiceRole("admin");
    public static final ServiceRole USER = new ServiceRole("user");
    public static final ServiceRole GUEST = new ServiceRole("guest");
}
