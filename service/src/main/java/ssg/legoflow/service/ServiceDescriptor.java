package ssg.legoflow.service;

import java.util.List;

public record ServiceDescriptor(
        String name,
        String description,
        int priority,
        List<String> dependencies
) {
    public ServiceDescriptor(String name, String description) {
        this(name, description, 0, List.of());
    }

    public ServiceDescriptor(String name, String description, int priority) {
        this(name, description, priority, List.of());
    }
}
