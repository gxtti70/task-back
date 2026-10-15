package com.taskmanager.controller;

import com.taskmanager.dto.request.CreateTaskAttachmentRequest;
import com.taskmanager.dto.response.TaskAttachmentResponse;
import com.taskmanager.service.TaskAttachmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/attachments")
@RequiredArgsConstructor
public class TaskAttachmentController {
    private final TaskAttachmentService attachments;

    @PostMapping
    public TaskAttachmentResponse prepare(@PathVariable UUID taskId,
                                          @Valid @RequestBody CreateTaskAttachmentRequest request,
                                          Authentication authentication) {
        return attachments.prepare(taskId, request, authentication.getName());
    }

    @GetMapping
    public List<TaskAttachmentResponse> list(@PathVariable UUID taskId, Authentication authentication) {
        return attachments.list(taskId, authentication.getName());
    }

    @PutMapping("/{attachmentId}/content")
    public void uploadLocal(@PathVariable UUID taskId, @PathVariable UUID attachmentId,
                           Authentication authentication, HttpServletRequest request) throws IOException {
        attachments.uploadLocal(taskId, attachmentId, authentication.getName(), request);
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<Resource> downloadLocal(@PathVariable UUID taskId, @PathVariable UUID attachmentId,
                                                   Authentication authentication) {
        return attachments.downloadLocal(taskId, attachmentId, authentication.getName());
    }

    @PostMapping("/{attachmentId}/complete")
    public void completeS3Upload(@PathVariable UUID taskId, @PathVariable UUID attachmentId,
                                 Authentication authentication) throws IOException {
        attachments.completeS3Upload(taskId, attachmentId, authentication.getName());
    }

    @DeleteMapping("/{attachmentId}")
    public void delete(@PathVariable UUID taskId, @PathVariable UUID attachmentId,
                       Authentication authentication) throws IOException {
        attachments.delete(taskId, attachmentId, authentication.getName());
    }
}
