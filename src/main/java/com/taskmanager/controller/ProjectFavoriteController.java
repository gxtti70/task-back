package com.taskmanager.controller;

import com.taskmanager.dto.response.FavoriteProjectResponse;
import com.taskmanager.entity.Project;
import com.taskmanager.entity.ProjectFavorite;
import com.taskmanager.entity.User;
import com.taskmanager.repository.ProjectFavoriteRepository;
import com.taskmanager.repository.ProjectMemberRepository;
import com.taskmanager.repository.ProjectRepository;
import com.taskmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProjectFavoriteController {
    private final ProjectFavoriteRepository favorites;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UserRepository users;

    @PostMapping("/api/projects/{projectId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void favorite(@PathVariable UUID projectId, Authentication authentication) {
        User user = currentUser(authentication);
        Project project = requireMembership(projectId, user);
        if (!favorites.existsByUser_IdAndProject_Id(user.getId(), projectId)) {
            favorites.save(ProjectFavorite.builder().user(user).project(project).build());
        }
    }

    @DeleteMapping("/api/projects/{projectId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void unfavorite(@PathVariable UUID projectId, Authentication authentication) {
        User user = currentUser(authentication);
        requireMembership(projectId, user);
        favorites.deleteByUser_IdAndProject_Id(user.getId(), projectId);
    }

    @GetMapping("/api/users/me/favorites")
    @Transactional(readOnly = true)
    public List<FavoriteProjectResponse> listFavorites(Authentication authentication) {
        User user = currentUser(authentication);
        return favorites.findAllByUserIdWithProject(user.getId()).stream()
                .map(favorite -> response(favorite.getProject())).toList();
    }

    private Project requireMembership(UUID projectId, User user) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        if (!members.existsByProject_IdAndUser_Id(projectId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo los miembros pueden guardar proyectos favoritos");
        }
        return project;
    }

    private User currentUser(Authentication authentication) {
        return users.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private FavoriteProjectResponse response(Project project) {
        return new FavoriteProjectResponse(project.getId(), project.getName(), project.getStatus());
    }
}
