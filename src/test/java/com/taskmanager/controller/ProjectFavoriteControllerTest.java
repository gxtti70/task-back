package com.taskmanager.controller;

import com.taskmanager.entity.Project;
import com.taskmanager.entity.ProjectFavorite;
import com.taskmanager.entity.User;
import com.taskmanager.repository.ProjectFavoriteRepository;
import com.taskmanager.repository.ProjectMemberRepository;
import com.taskmanager.repository.ProjectRepository;
import com.taskmanager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectFavoriteControllerTest {
    private final ProjectFavoriteRepository favorites = mock(ProjectFavoriteRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProjectFavoriteController controller =
            new ProjectFavoriteController(favorites, projects, members, users);

    @Test
    void memberCanFavoriteProjectAndIdentityComesFromAuthentication() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User user = User.builder().id(userId).email("member@example.test").build();
        Project project = Project.builder().id(projectId).build();
        var authentication = new UsernamePasswordAuthenticationToken("member@example.test", "");
        when(users.findByEmail("member@example.test")).thenReturn(Optional.of(user));
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(members.existsByProject_IdAndUser_Id(projectId, userId)).thenReturn(true);
        when(favorites.existsByUser_IdAndProject_Id(userId, projectId)).thenReturn(false);
        when(favorites.save(any(ProjectFavorite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        controller.favorite(projectId, authentication);

        verify(favorites).save(argThat(favorite -> favorite.getUser() == user && favorite.getProject() == project));
    }

    @Test
    void nonMemberCannotFavoriteProject() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User user = User.builder().id(userId).email("outsider@example.test").build();
        Project project = Project.builder().id(projectId).build();
        var authentication = new UsernamePasswordAuthenticationToken("outsider@example.test", "");
        when(users.findByEmail("outsider@example.test")).thenReturn(Optional.of(user));
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(members.existsByProject_IdAndUser_Id(projectId, userId)).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.favorite(projectId, authentication));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(favorites, never()).save(any());
    }
}
