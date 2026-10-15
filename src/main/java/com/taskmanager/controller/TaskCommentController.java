package com.taskmanager.controller;

import com.taskmanager.dto.request.TaskCommentRequest;
import com.taskmanager.dto.response.TaskCommentResponse;
import com.taskmanager.entity.Task;
import com.taskmanager.entity.TaskComment;
import com.taskmanager.entity.User;
import com.taskmanager.repository.TaskCommentRepository;
import com.taskmanager.repository.ProjectMemberRepository;
import com.taskmanager.repository.TaskRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.security.RoleNames;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/comments")
@RequiredArgsConstructor
public class TaskCommentController {
    private final TaskCommentRepository comments;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final ProjectMemberRepository members;

    @GetMapping
    @Transactional(readOnly = true)
    public List<TaskCommentResponse> list(@PathVariable UUID taskId, Authentication authentication) {
        requireTaskAccess(taskId, authentication);
        return comments.findAllByTaskIdWithAuthor(taskId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TaskCommentResponse create(@PathVariable UUID taskId, @Valid @RequestBody TaskCommentRequest request,
                                      Authentication authentication) {
        User author = currentUser(authentication);
        Task task = requireTaskAccess(taskId, author);
        TaskComment comment = comments.save(TaskComment.builder().task(task).author(author)
                .body(request.body().trim()).build());
        return toResponse(comment);
    }

    private Task requireTaskAccess(UUID taskId, Authentication authentication) {
        return requireTaskAccess(taskId, currentUser(authentication));
    }

    private Task requireTaskAccess(UUID taskId, User actor) {
        Task task = tasks.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarea no encontrada"));
        if (task.getProject() != null && !RoleNames.normalize(actor.getRole()).equals("ADMIN")
                && !task.getProject().getOwner().getId().equals(actor.getId())
                && !members.existsByProject_IdAndUser_Id(task.getProject().getId(), actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Debes pertenecer al proyecto para ver o comentar sus tareas");
        }
        return task;
    }

    private User currentUser(Authentication authentication) {
        return users.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private TaskCommentResponse toResponse(TaskComment comment) {
        User author = comment.getAuthor();
        String name = author.getFullName() == null || author.getFullName().isBlank()
                ? author.getEmail() : author.getFullName();
        return new TaskCommentResponse(comment.getId(), comment.getBody(), author.getId(), name,
                author.getEmail(), comment.getCreatedAt());
    }
}
