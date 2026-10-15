package com.taskmanager.controller;

import com.taskmanager.dto.request.TaskCommentRequest;
import com.taskmanager.entity.Project;
import com.taskmanager.entity.Task;
import com.taskmanager.entity.TaskComment;
import com.taskmanager.entity.User;
import com.taskmanager.repository.ProjectMemberRepository;
import com.taskmanager.repository.TaskCommentRepository;
import com.taskmanager.repository.TaskRepository;
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

class TaskCommentControllerTest {
    private final TaskCommentRepository comments = mock(TaskCommentRepository.class);
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    private final TaskCommentController controller = new TaskCommentController(comments, tasks, users, members);

    @Test
    void commentAuthorComesFromAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        User actor = User.builder().id(userId).email("member@example.test").fullName("Member").role("DEVELOPER").build();
        Task task = Task.builder().id(taskId).build();
        var authentication = new UsernamePasswordAuthenticationToken("member@example.test", "");
        when(users.findByEmail("member@example.test")).thenReturn(Optional.of(actor));
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(comments.save(any(TaskComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.create(taskId, new TaskCommentRequest("Hello"), authentication);

        assertEquals(userId, response.authorId());
        assertEquals("Hello", response.body());
        verify(comments).save(argThat(comment -> comment.getAuthor() == actor));
    }

    @Test
    void projectNonMemberCannotReadOrAddComments() {
        UUID taskId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Project project = mock(Project.class);
        User owner = User.builder().id(UUID.randomUUID()).build();
        User actor = User.builder().id(actorId).email("outsider@example.test").role("DEVELOPER").build();
        Task task = Task.builder().id(taskId).project(project).build();
        var authentication = new UsernamePasswordAuthenticationToken("outsider@example.test", "");
        when(users.findByEmail("outsider@example.test")).thenReturn(Optional.of(actor));
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(project.getOwner()).thenReturn(owner);
        when(project.getId()).thenReturn(UUID.randomUUID());
        when(members.existsByProject_IdAndUser_Id(project.getId(), actorId)).thenReturn(false);

        ResponseStatusException readError = assertThrows(ResponseStatusException.class,
                () -> controller.list(taskId, authentication));
        ResponseStatusException writeError = assertThrows(ResponseStatusException.class,
                () -> controller.create(taskId, new TaskCommentRequest("Hello"), authentication));

        assertEquals(HttpStatus.FORBIDDEN, readError.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, writeError.getStatusCode());
        verifyNoInteractions(comments);
    }
}
