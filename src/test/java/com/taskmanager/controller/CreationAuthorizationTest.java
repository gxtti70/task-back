package com.taskmanager.controller;

import com.taskmanager.dto.request.ProjectRequest;
import com.taskmanager.dto.request.TaskRequest;
import com.taskmanager.entity.User;
import com.taskmanager.repository.*;
import com.taskmanager.service.NotificationEventService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CreationAuthorizationTest.MethodSecurityTestConfig.class)
class CreationAuthorizationTest {
    private static final String EMAIL = "manager@example.com";

    @Autowired private TaskController taskController;
    @Autowired private ProjectController projectController;
    @Autowired private TaskRepository taskRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authorizedRolesCanCreateTasksAndProjects() {
        User actor = User.builder().id(UUID.randomUUID()).email(EMAIL).fullName("Test Manager")
                .role("MANAGER").active(true).build();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(actor));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        for (String role : List.of("ADMIN", "SCRUM", "MANAGER")) {
            var authentication = authentication(role);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            TaskRequest task = new TaskRequest();
            task.setTitle("Task for " + role);
            assertEquals(HttpStatus.CREATED, taskController.createTask(task, authentication).getStatusCode());

            ProjectRequest project = new ProjectRequest();
            project.setName("Project for " + role);
            project.setStatus("ACTIVE");
            assertEquals("Project for " + role,
                    projectController.createProject(project, authentication).name());
        }
    }

    @Test
    void developerCannotCreateTasksOrProjects() {
        var authentication = authentication("DEVELOPER");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        TaskRequest task = new TaskRequest();
        task.setTitle("Forbidden task");
        ProjectRequest project = new ProjectRequest();
        project.setName("Forbidden project");
        project.setStatus("ACTIVE");

        assertThrows(AccessDeniedException.class, () -> taskController.createTask(task, authentication));
        assertThrows(AccessDeniedException.class, () -> projectController.createProject(project, authentication));
    }

    private UsernamePasswordAuthenticationToken authentication(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(EMAIL, "password",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityTestConfig {
        @Bean TaskRepository taskRepository() { return mock(TaskRepository.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean ProjectRepository projectRepository() { return mock(ProjectRepository.class); }
        @Bean ProjectMemberRepository projectMemberRepository() { return mock(ProjectMemberRepository.class); }
        @Bean NotificationEventService notificationEventService() { return mock(NotificationEventService.class); }

        @Bean TaskController taskController(TaskRepository tasks, UserRepository users, ProjectRepository projects,
                                            ProjectMemberRepository members, NotificationEventService notifications) {
            return new TaskController(tasks, users, projects, members, notifications);
        }

        @Bean ProjectController projectController(ProjectRepository projects, TaskRepository tasks,
                                                  ProjectMemberRepository members, UserRepository users,
                                                  NotificationEventService notifications) {
            return new ProjectController(projects, tasks, members, users, notifications);
        }
    }
}
