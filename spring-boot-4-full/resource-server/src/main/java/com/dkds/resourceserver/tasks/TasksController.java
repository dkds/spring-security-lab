package com.dkds.resourceserver.tasks;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// Dummy demo endpoint — a static in-memory list, secured the same way as
/// every other endpoint in this app (common-security's
/// ResourceServerSecurityConfig, anyRequest().authenticated()). Exists to
/// give the UI a second, more realistic thing to fetch and render as a list
/// (beyond /api/profile, which just echoes the token's own claims back).
@RestController
public class TasksController {

    private static final List<Task> TASKS = List.of(
            new Task(1L, "Review pull request", false),
            new Task(2L, "Deploy resource-server", true),
            new Task(3L, "Write integration tests", false),
            new Task(4L, "Update documentation", false)
    );

    @GetMapping("/api/tasks")
    public List<Task> tasks() {
        return TASKS;
    }

    public record Task(Long id, String title, boolean done) {
    }
}
